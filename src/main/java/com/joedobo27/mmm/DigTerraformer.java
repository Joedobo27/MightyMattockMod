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

class DigTerraformer extends ActionMaster {

    private final TilePos targetedTile;

    static WeakHashMap<Action, DigTerraformer> actionDataWeakHashMap = new WeakHashMap<>();

    DigTerraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill,
                   int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                   ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, TilePos targetedTile) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina,
                failureTestFunctions);
        this.targetedTile = targetedTile;
        actionDataWeakHashMap.put(action, this);
    }

    static boolean hashMapHasInstance(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    boolean hasAFailureCondition() {
        return failureTestFunctions.stream()
                .anyMatch(function -> function.apply(this));
    }

    void modifyHeight(int modifier) {
        TileUtilities.setSurfaceHeight(this.targetedTile, TileUtilities.getSurfaceHeight(this.targetedTile) + modifier);
        Players.getInstance().sendChangedTile(this.targetedTile.x, this.targetedTile.y, this.performer.isOnSurface(),
                true);
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

    /**
     * For the corner being dug there are 4 tiles affect. And for these 4 affected tiles check each's 4 corners to see if
     * the tile should be converted form dirt to rock. In the case where it's converted do the transformation and update
     * the appropriate tile state values.
     */
    void shouldDirtBeRock() {
        TilePos[] tilePos = {this.targetedTile, this.targetedTile.West(), this.targetedTile.NorthWest(),
                this.targetedTile.North()};
        TilePos[] makeRockPos = Arrays.stream(tilePos)
                .filter(tilePos1 -> !Tiles.isTree(TileUtilities.getSurfaceTypeId(tilePos1)) && !TileUtilities.isImmutableTile(tilePos1))
                .filter(tilePos1 -> TileUtilities.getSurfaceHeight(tilePos1) <= TileUtilities.getRockHeight(tilePos1))
                .filter(tilePos1 -> TileUtilities.getSurfaceHeight(tilePos1.East()) <= TileUtilities.getRockHeight(tilePos1.East()))
                .filter(tilePos1 -> TileUtilities.getSurfaceHeight(tilePos1.SouthEast()) <= TileUtilities.getRockHeight(tilePos1.SouthEast()))
                .filter(tilePos1 -> TileUtilities.getSurfaceHeight(tilePos1.South()) <= TileUtilities.getRockHeight(tilePos1.South()))
                .toArray(TilePos[]::new);
        if (makeRockPos == null || makeRockPos.length == 0) {
            return;
        }
        Arrays.stream(makeRockPos)
                .forEach(tilePos1 -> {
                    Server.modifyFlagsByTileType(tilePos1.x, tilePos1.y, Tiles.Tile.TILE_ROCK.id);
                    TileUtilities.setSurfaceTypeId(tilePos1, Tiles.Tile.TILE_ROCK.id);
                    this.performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos1.x, tilePos1.y, this.performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos1, this.performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos1.x, tilePos1.y);
                });
    }

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return -1.0d;
        double difficulty = 1 + (TileUtilities.getSteepestSlope(this.targetedTile) / 5);
        switch (TileUtilities.getSurfaceTypeId(this.targetedTile)) {
            case Tiles.TILE_TYPE_CLAY:
                difficulty += 20;
                break;
            case Tiles.TILE_TYPE_SAND:
                difficulty += 10;
                break;
            case Tiles.TILE_TYPE_TAR:
                difficulty += 35;
                break;
            case Tiles.TILE_TYPE_MOSS:
                difficulty += 10;
                break;
            case Tiles.TILE_TYPE_MARSH:
                difficulty += 30;
                break;
            case Tiles.TILE_TYPE_STEPPE:
                difficulty += 40;
                break;
            case Tiles.TILE_TYPE_TUNDRA:
                difficulty += 20;
        }
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    @SuppressWarnings("UnusedReturnValue")
    Item makeDugItemOnGround(double power) {
        int createdItemTemplate;
        switch (TileUtilities.getSurfaceTypeId(this.targetedTile)) {
            case Tiles.TILE_TYPE_SAND:
                createdItemTemplate = ItemList.sand;
                break;
            default:
                createdItemTemplate = ItemList.dirtPile;
        }
        float runeModifier = 1.0f;
        if (this.activeTool != null && this.activeTool.getSpellEffects() != null && this.activeTool.getSpellEffects().getRuneEffect() != -10L) {
            runeModifier += RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }
        Item created = null;
        double rarityModifier = this.activeTool == null ? 0 : this.activeTool.getRarity();
        try {
            created = ItemFactory.createItem(createdItemTemplate, Math.min((float) (power + rarityModifier) * runeModifier, 100.0f),
                    (this.targetedTile.x * 4) + 2, (this.targetedTile.y * 4) + 2, Server.rand.nextFloat() * 360.0f,
                    performer.isOnSurface(), action.getRarity(), -10L, null);
        }catch (NoSuchTemplateException | FailedException ignored){}
        if (created == null) {
            this.performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return null;
        }
        created.setLastOwnerId(this.performer.getWurmId());
        created.setRarity(this.action.getRarity());
        return created;
    }

    @Override
    public TilePos getTargetTile() {
        return this.targetedTile;
    }
}
