package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.function.Function;

class MineTerraformer extends ActionMaster {

    private final TilePos targetedTile;
    private final boolean onSurface;

    static WeakHashMap<Action, MineTerraformer> actionDataWeakHashMap = new WeakHashMap<>();

    MineTerraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill,
                    int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                    ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, TilePos targetedTile,
                    boolean onSurface) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina,
                failureTestFunctions);
        this.targetedTile = targetedTile;
        this.onSurface = onSurface;
        actionDataWeakHashMap.put(action, this);
    }

    static boolean hashMapHasInstance(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    boolean hasAFailureCondition() {
        return failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
    }

    /**
     * For the corner being dug there are 4 tiles affect. Check to see if the tile should be converted to dirt.
     * In the case where it's converted do the transformation and update the appropriate tile state values.
     */
    void shouldMutableBeDirt() {
        TilePos[] checkTiles = {this.targetedTile, this.targetedTile.West(), this.targetedTile.NorthWest(),
                this.targetedTile.North()};
        TilePos[] makeDirtPos = Arrays.stream(checkTiles)
                .filter(TileUtilities::isTileOverriddenByDirt)
                .toArray(TilePos[]::new);
        if (makeDirtPos == null || makeDirtPos.length == 0) {
            return;
        }
        Arrays.stream(makeDirtPos)
                .forEach(tilePos -> {
                    Server.modifyFlagsByTileType(tilePos.x, tilePos.y, Tiles.Tile.TILE_DIRT.id);
                    TileUtilities.setSurfaceTypeId(tilePos, Tiles.Tile.TILE_DIRT.id);
                    this.performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos.x, tilePos.y, performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos, this.performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos.x, tilePos.y);
                });
    }

    void modifyHeight(int modifier) {
        if (this.onSurface) {
            TileUtilities.setRockHeight(this.targetedTile, TileUtilities.getRockHeight(this.targetedTile) + modifier);
            TileUtilities.setSurfaceHeight(this.targetedTile, TileUtilities.getSurfaceHeight(this.targetedTile) + modifier);
            Players.getInstance().sendChangedTile(this.targetedTile.x, this.targetedTile.y, this.performer.isOnSurface(),
                    true);
        }
        else {
            TileUtilities.setCaveFloorHeight(this.targetedTile, TileUtilities.getCaveFloorHeight(this.targetedTile) + modifier);
            TileUtilities.setCaveCeilingOffset(this.targetedTile, TileUtilities.getCaveCeilingOffset(this.targetedTile) + modifier);
            Players.getInstance().sendChangedTile(this.targetedTile.x, this.targetedTile.y, this.performer.isOnSurface(),
                    true);
        }
    }

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return -1.0d;
        double difficulty = 1 + (TileUtilities.getSteepestSlope(this.targetedTile) / 5);
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    @SuppressWarnings("UnusedReturnValue")
    Item makeMinedItemOnGround(double power) {
        float runeModifier = 1.0f;
        if (this.activeTool != null && this.activeTool.getSpellEffects() != null &&
                this.activeTool.getSpellEffects().getRuneEffect() != -10L) {
            runeModifier += RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(),
                    RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }
        Item created = null;
        double rarityModifier = this.activeTool == null ? 0 : this.activeTool.getRarity();
        try {
            created = ItemFactory.createItem(ItemList.rock, Math.min((float) (power + rarityModifier) * runeModifier,
                    100.0f),(this.targetedTile.x * 4) + 2, (this.targetedTile.y * 4) + 2,
                    Server.rand.nextFloat() * 360.0f, performer.isOnSurface(), action.getRarity(), -10L,
                    null);
        }catch (NoSuchTemplateException | FailedException ignored){}
        if (created == null) {
            this.performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return null;
        }
        created.setDataXY(this.targetedTile.x, this.targetedTile.y);
        created.setLastOwnerId(this.performer.getWurmId());
        created.setRarity(this.action.getRarity());
        return created;
    }

    @Override
    public TilePos getTargetTile() {
        return this.targetedTile;
    }
}
