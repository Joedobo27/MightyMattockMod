package com.joedobo27.rocksurfaceterraforming;

import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Server;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.RuneUtilities;

import java.util.Arrays;
import java.util.Comparator;

import static com.wurmonline.server.skills.SkillList.*;

class LevelActionData{
    private int actionCountToDo;
    private int rockRemoved;
    private double unitActionTime;
    private double unitRockRemovalTime;
    private int lastWholeSkillUnitTime;
    private int lastWholeRockRemovalUnitTime;
    private TilePos[] tilePos;
    private TilePos performerTilePos;
    private TilePos cycleTilePos;


    LevelActionData(Action action, Creature performer, Item pickAxe, int tileX, int tileY){
        TilePos targetPos = TilePos.fromXY(tileX, tileY);
        this.tilePos = new TilePos[]{targetPos, targetPos.East(), targetPos.SouthEast(), targetPos.South()};
        this.performerTilePos = TilePos.fromXY(performer.getTileX(), performer.getTileY());
        setBaseUnitActionTime(pickAxe, performer, action);
        this.lastWholeSkillUnitTime = 0;
        setUnitRockRemovalTime(performer);
        this.lastWholeRockRemovalUnitTime = 0;
        this.rockRemoved = 0;
        tallyTotalRockToRemove();

    }

    private void setUnitRockRemovalTime(Creature performer){
        this.unitRockRemovalTime = this.unitActionTime * (1 / Math.max(0.2f, performer.getSkills().getSkillOrLearn(MINING).getKnowledge(0.0) / 200.0f));
    }

    void getNextLevelTile(){
        int performerTileElevation = Tiles.decodeHeight(Server.surfaceMesh.getTile(performerTilePos.x, performerTilePos.y));
        this.cycleTilePos = Arrays.stream(tilePos)
                .filter(tilePos1 -> Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos1.x, tilePos1.y)) != performerTileElevation)
                .sorted(Comparator.comparingInt((TilePos tilePos) -> Math.abs(Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos.x, tilePos.y))) -  performerTileElevation).reversed())
                .findFirst()
                .orElseThrow(LevelActionException::new);
    }

    boolean levelActionRequiresMine(TilePos mineTile){
        return Tiles.decodeHeight(Server.surfaceMesh.getTile(this.performerTilePos)) < Tiles.decodeHeight(Server.surfaceMesh.getTile(mineTile));
    }

    boolean levelActionRequiresConcrete(TilePos mineTile){
        return Tiles.decodeHeight(Server.surfaceMesh.getTile(performerTilePos)) > Tiles.decodeHeight(Server.surfaceMesh.getTile(mineTile));
    }

    int getTotalTime(){
        return (int) (this.unitRockRemovalTime * this.actionCountToDo);
    }

    private void tallyTotalRockToRemove(){
        int performerTileElevation = Tiles.decodeHeight(Server.surfaceMesh.getTile(performerTilePos.x, performerTilePos.y));
        int totalMineDownCount = Arrays.stream(this.tilePos)
                .filter(tilePos1 -> Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos1.x, tilePos1.y)) > performerTileElevation)
                .mapToInt(tilePos1 -> Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos1.x, tilePos1.y) - performerTileElevation))
                .sum();
        int totalRaiseUpCount = Arrays.stream(this.tilePos)
                .filter(tilePos1 -> Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos1.x, tilePos1.y)) < performerTileElevation)
                .mapToInt(tilePos1 -> Tiles.decodeHeight(performerTileElevation - Server.surfaceMesh.getTile(tilePos1.x, tilePos1.y)))
                .sum();
        int staminaForActions = 99;
        // I need to figure out how stamina works so available stamina can serve as a possible action count cap.
        this.actionCountToDo = (totalMineDownCount + totalRaiseUpCount) > staminaForActions ? staminaForActions : (totalMineDownCount + totalRaiseUpCount);
    }

    private TilePos getTargetPos(){
        if (this.tilePos.length == 4)
            return this.tilePos[0];
        else throw new LevelActionException();
    }

    boolean unitSkillTimeJustTicked(float counter){
        int unitTime = (int)(Math.floor((counter * 100) / (this.unitActionTime * 10)));
        if (unitTime != this.lastWholeSkillUnitTime){
            this.lastWholeSkillUnitTime = unitTime;
            return true;
        }
        return false;
    }

    boolean unitRockChangeTimeJustTicked(float counter){
        int unitTime = (int) (Math.floor((counter * 100) / (this.unitRockRemovalTime * 10)));
        if (unitTime != this.lastWholeRockRemovalUnitTime){
            this.lastWholeRockRemovalUnitTime = unitTime;
            return true;
        }
        return false;
    }

    public void incrementActionsDone() {
        this.rockRemoved++;
    }

    public boolean isActionsUnfinished(){
        return this.rockRemoved < this.actionCountToDo;
    }

    public double getUnitActionTime() {
        return unitActionTime;
    }

    public TilePos getCycleTilePos() {
        return this.cycleTilePos;
    }

    /**
     * It shouldn't be necessary to have a fantastic, 104woa, speed rune, 99ql, 99 skill in order to get the fastest time.
     * Aim for just skill as getting close to shortest time and the other boosts help at lower levels but aren't needed to have
     * the best at end game.
     */
    private void setBaseUnitActionTime(Item pickAxe, Creature performer, Action action){
        final float MINIMUM_TIME = RockSurfaceTerraformingMod.getMinimumUnitLevelTime();
        final int MAX_BONUS = 10;
        final double MAX_WOA_EFFECT = 0.20;
        final double TOOL_RARITY_EFFECT = 0.1;
        final double ACTION_RARITY_EFFECT = 0.33;
        final double MAX_SKILL = 100.0d;
        double time;
        double modifiedKnowledge = Math.min(MAX_SKILL, performer.getSkills().getSkillOrLearn(FARMING).getKnowledge(pickAxe,
                Math.min(MAX_BONUS, performer.getSkills().getSkillOrLearn(BODY_CONTROL).getKnowledge() / 5)));
        time = Math.max(MINIMUM_TIME, (130.0 - modifiedKnowledge) * 1.3f / Servers.localServer.getActionTimer());

        // woa
        if (pickAxe != null && pickAxe.getSpellSpeedBonus() > 0.0f)
            time = Math.max(MINIMUM_TIME, time * (1 - (MAX_WOA_EFFECT * pickAxe.getSpellSpeedBonus() / 100.0)));
        //rare barrel item, 10% speed reduction per rarity level.
        if (pickAxe != null && pickAxe.getRarity() > 0)
            time = Math.max(MINIMUM_TIME, time * (1 - (pickAxe.getRarity() * TOOL_RARITY_EFFECT)));
        //rare sowing action, 33% speed reduction per rarity level.
        if (action.getRarity() > 0)
            time = Math.max(MINIMUM_TIME, time * (1 - (action.getRarity() * ACTION_RARITY_EFFECT)));
        // rune effects
        if (pickAxe != null && pickAxe.getSpellEffects() != null && pickAxe.getSpellEffects().getRuneEffect() != -10L)
            time = Math.max(MINIMUM_TIME, time * (1 - RuneUtilities.getModifier(pickAxe.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_USESPEED)));
        this.unitActionTime = time;
    }
}
