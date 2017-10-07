package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

class CollectResourceTerraformer extends ActionMaster {

    private final TilePos targetedTile;

    static WeakHashMap<Action, CollectResourceTerraformer> actionDataWeakHashMap = new WeakHashMap<>();

    CollectResourceTerraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill,
                               int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                               ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions, TilePos targetedTile) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
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

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return -1.0d;
        double difficulty = 1 + (TileUtilities.getSteepestSlope(this.targetedTile) / 5);
        switch (TileUtilities.getSurfaceTypeId(this.targetedTile)) {
            case Tiles.TILE_TYPE_CLAY:
                difficulty += 20;
                break;
            case Tiles.TILE_TYPE_PEAT:
                difficulty += 20;
                break;
            case Tiles.TILE_TYPE_TAR:
                difficulty += 35;
                break;
            case Tiles.TILE_TYPE_MOSS:
                difficulty += 10;
                break;
        }
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    @SuppressWarnings("UnusedReturnValue")
    Item makeDugItemOnGround(double power) {
        Integer createdItemTemplate = null;
        switch (TileUtilities.getSurfaceTypeId(this.targetedTile)) {
            case Tiles.TILE_TYPE_CLAY:
                createdItemTemplate = ItemList.clay;
                break;
            case Tiles.TILE_TYPE_PEAT:
                createdItemTemplate = ItemList.peat;
                break;
            case Tiles.TILE_TYPE_TAR:
                createdItemTemplate = ItemList.tar;
                break;
            case Tiles.TILE_TYPE_MOSS:
                createdItemTemplate = ItemList.moss;
                break;
        }
        if (createdItemTemplate == null)
            return null;
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
