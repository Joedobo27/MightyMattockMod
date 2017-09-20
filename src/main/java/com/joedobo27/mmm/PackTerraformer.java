package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

class PackTerraformer extends ActionMaster {

    private final TilePos targetedTile;

    static WeakHashMap<Action, PackTerraformer> actionDataWeakHashMap = new WeakHashMap<>();

    PackTerraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill,
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

    void packTile() {
        Server.modifyFlagsByTileType(this.targetedTile.x, this.targetedTile.y, Tiles.Tile.TILE_DIRT_PACKED.id);
        TileUtilities.setSurfaceTypeId(this.targetedTile, Tiles.Tile.TILE_DIRT_PACKED.id);
        Zone zone = TileUtilities.getZoneSafe(this.targetedTile, this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(this.targetedTile.x, this.targetedTile.y);
        Players.getInstance().sendChangedTile(this.targetedTile.x, this.targetedTile.y, performer.isOnSurface(), true);
    }

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return -1.0d;
        double difficulty = 1 + (TileUtilities.getSteepestSlope(this.targetedTile) / 5);
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    @Override
    public TilePos getTargetTile() {
        return this.targetedTile;
    }
}
