package com.joedobo27.rocksurfaceterraforming;


import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import static com.wurmonline.server.skills.SkillList.*;

import java.util.*;

public class LevelAction implements ModAction, BehaviourProvider, ActionPerformer {
    private final ActionEntry actionEntry;
    private final short actionId;
    private static WeakHashMap<Action, LevelActionData> actionListener = new WeakHashMap<>();
    private static Random r = new Random();

    LevelAction(){
        actionEntry = Actions.actionEntrys[Actions.LEVEL];
        actionId = Actions.LEVEL;
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, int tileX, int tileY, boolean onSurface, int encodedTile) {
        return null;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int encodedTile) {
        if (isLevelOrFlattenTool(source) || isTargetingSurfaceRock(encodedTile)){
            return Collections.singletonList(actionEntry);
        }
        else
            return null;
    }

    @Override
    public boolean action(Action action, Creature performer, int tileX, int tileY, boolean onSurface, int encodedTile, short aActionId, float counter) {
        return false;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short aActionId, float counter) {
        if (isLevelOrFlattenTool(source) || isTargetingSurfaceRock(encodedTile)){
            final float ACTION_START_TIME = 1.0f;
            final float TIME_TO_COUNTER_DIVISOR = 10.0f;
            String youMessage;
            String broadcastMessage;
            LevelActionData levelActionData;
            int time;

            if(counter == ACTION_START_TIME){
                if (hasAFailureCondition(performer, source, tileX, tileY, onSurface, heightOffset, encodedTile, aActionId, counter))
                    return true;
                levelActionData = new LevelActionData(action, performer, source, tileX, tileY);
                actionListener.put(action, levelActionData);
                youMessage = String.format("You start %s.", action.getActionEntry().getVerbString());
                broadcastMessage = String.format("%s starts to %s.", performer.getName(), action.getActionString());
                performer.getCommunicator().sendNormalServerMessage(youMessage);
                Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
                action.setTimeLeft(levelActionData.getTotalTime());
                performer.sendActionControl(action.getActionEntry().getVerbString(), true, levelActionData.getTotalTime());
                return false;
            }
            time = action.getTimeLeft();
            levelActionData = actionListener.get(action);
            boolean isTimeLeft = counter - 1 < time / TIME_TO_COUNTER_DIVISOR;
            if (levelActionData.unitSkillTimeJustTicked(counter) && isTimeLeft && levelActionData.isActionsUnfinished()) {
                double bonus;
                try {
                    bonus = performer.getSkills().getSkillOrLearn(source.getPrimarySkill()).skillCheck(1.0, source, 0.0, false, counter) / 5.0;
                } catch (NoSuchSkillException e){
                    bonus = 0;
                }
                performer.getSkills().getSkillOrLearn(MINING).skillCheck(1, source, bonus, false, counter);
            }
            if (levelActionData.unitRockRemovalTimeJustTicked(counter) && isTimeLeft && levelActionData.isActionsUnfinished()){
                if (hasAFailureCondition(performer, source, tileX, tileY, onSurface, heightOffset, encodedTile, aActionId, counter))
                    return true;
                TilePos mineTile = levelActionData.getNextMineTile();
                double quality = Math.min(getSkillDerivedQl(performer, source), getTileQlCap(mineTile, source));
                if (source.isCrude()) {
                    quality = 1.0;
                }
                float modifier = 1.0f;
                if (source.getSpellEffects() != null && source.getSpellEffects().getRuneEffect() != -10L) {
                    modifier += RuneUtilities.getModifier(source.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
                }
                float orePower = GeneralUtilities.calcOreRareQuality(quality * modifier, action.getRarity(), source.getRarity());
                Item minedItem;
                try{
                    minedItem = ItemFactory.createItem(ItemList.rock, orePower, action.getRarity(), null);
                }catch(FailedException | NoSuchTemplateException e){
                    RockSurfaceTerraformingMod.logger.warning(e.getMessage());
                    performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
                    return true;
                }
                minedItem.setLastOwnerId(performer.getWurmId());
                minedItem.setDataXY(mineTile.x, mineTile.y);
                try{
                    minedItem.putItemInfrontof(performer, 0.0f);
                }catch(NoSuchCreatureException | NoSuchItemException | NoSuchPlayerException | NoSuchZoneException e){
                    RockSurfaceTerraformingMod.logger.warning(e.getMessage());
                    minedItem.setWeight(0, true); // destroy the created item.
                    performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
                    return true;
                }
                youMessage = String.format("You mine some %s.", minedItem.getName());
                broadcastMessage = String.format("%s mines some %s.", performer.getName(), minedItem.getName());
                performer.getCommunicator().sendNormalServerMessage(youMessage);
                Server.getInstance().broadCastAction(broadcastMessage, performer, 5);
                invokeCreateGem(mineTile.x, mineTile.y, performer, quality, true, action);
                removeOneRock(mineTile, performer);
                levelActionData.incrementActionsDone();
                return false;
            }
            if (!isTimeLeft && !levelActionData.isActionsUnfinished()){
                performer.getCommunicator().sendNormalServerMessage("You finish leveling.");
                return true;
            }
        }
        return false;
    }

    private void removeOneRock(TilePos mineTile, Creature performer){
        short currentHeight = Tiles.decodeHeight(Server.surfaceMesh.getTile(mineTile));
        Server.surfaceMesh.setTile(mineTile.x, mineTile.y, Tiles.encode(--currentHeight, Tiles.Tile.TILE_ROCK.id,
                Tiles.decodeData(Server.surfaceMesh.getTile(mineTile))));
        Server.rockMesh.setTile(mineTile.x, mineTile.y, Tiles.encode(--currentHeight, (short)0));
        Players.getInstance().sendChangedTile(mineTile.x, mineTile.y, performer.isOnSurface(), true);
    }

    /**
     * Get a power form a skill roll but don't actually give skill gains.
     *
     * @param performer WU Creature object
     * @param source WU Item object
     * @return double primitive, What is quality value from skill roll?
     */
    private double getSkillDerivedQl(Creature performer, Item source){
        double bonus;
        final boolean noSkillGain = true;
        try {
            bonus = performer.getSkills().getSkillOrLearn(source.getPrimarySkill()).skillCheck(1.0, source, 0.0, noSkillGain, 1) / 5.0;
        } catch (NoSuchSkillException e){
            bonus = 0;
        }
        double power = Math.max(1, performer.getSkills().getSkillOrLearn(MINING).skillCheck(1, source, bonus, noSkillGain, 1));
        double imbueEnhancement = 1.0 + (0.23047 * source.getSkillSpellImprovement(MINING) / 100.0);
        if (performer.getSkills().getSkillOrLearn(MINING).getKnowledge(0.0) * imbueEnhancement < power) {
            power = performer.getSkills().getSkillOrLearn(MINING).getKnowledge(0.0) * imbueEnhancement;
        }
        return power;
    }

    /**
     * Get a tile's quality cap using a seeded RNG algorithm. It's know this will cause a hashing collision with the seed.
     * But changing it would randomize all the existent vein qualities players have grown accustomed too.
     *
     * Returned arg is used in a double context. We don't need decimal precision for vein qualities so it's calculated using int
     * RNG.
     *
     * @param mineTile WU TilePos object
     * @param source WU Item object
     * @return double primitive, What is the quality cap for the tile?
     */
    private double getTileQlCap(TilePos mineTile, Item source ){
        final long MAGIC_PRIME_MULTIPLIER = 789221L;
        double imbueEnhancement = 1.0 + (0.23047 * source.getSkillSpellImprovement(MINING) / 100.0);
        r.setSeed((mineTile.x + mineTile.y * Zones.worldTileSizeY) * MAGIC_PRIME_MULTIPLIER);
        return Math.min(100, (20 + r.nextInt(80)) * imbueEnhancement);
    }

    private void invokeCreateGem(int minedTileX, int minedTileY, Creature performer, double power, boolean surfaced, Action action){

    }

    private static boolean isLevelOrFlattenTool(Item item){
        return item.isMiningtool() || item.getTemplateId() == ItemList.concrete;
    }

    private static boolean isTargetingSurfaceRock(int encodedTile){
        return Tiles.decodeTileData(encodedTile) == Tiles.Tile.TILE_ROCK.id || Tiles.decodeTileData(encodedTile) == Tiles.Tile.TILE_CLIFF.id;
    }

    private static boolean hasAFailureCondition(Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset, int encodedTile, short aActionId, float counter){
        int depthMultipliers = 3;
        TilePos targetTilePos = TilePos.fromXY(tileX, tileY);
        TilePos performerTilePos = TilePos.fromXY(performer.getTileX(), performer.getTileY());
        TilePos[] adjacentTilePos = new TilePos[]{targetTilePos.North(), targetTilePos.NorthEast(), targetTilePos.East(),
                targetTilePos.SouthEast(),targetTilePos.South(), targetTilePos.SouthWest(), targetTilePos.West(), targetTilePos.NorthWest()};

        boolean isTooFarAway = Arrays.stream(adjacentTilePos)
                .filter(tilePos -> tilePos.equals(performerTilePos))
                .count() == 0;
        if (isTooFarAway){
            performer.getCommunicator().sendNormalServerMessage("That tile is too far away.", (byte)3);
            return true;
        }
        boolean isTooCloseToServerBoarder = tileX < 0 || tileX > 1 << Constants.meshSize || tileY < 0 || tileY > 1 << Constants.meshSize;
        if (isTooCloseToServerBoarder){
            performer.getCommunicator().sendNormalServerMessage("You can't mine this close to the server boarder.", (byte)3);
            return true;
        }
        if (Zones.isTileProtected(tileX, tileY)) {
            performer.getCommunicator().sendNormalServerMessage("This tile is protected by the gods. You can not mine here.", (byte)3);
            return true;
        }
        boolean isMiningTooDeep = Tiles.decodeHeight(Server.surfaceMesh.getTile(performerTilePos)) <
                (-1 * performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge() * depthMultipliers);
        if (isMiningTooDeep){
            performer.getCommunicator().sendNormalServerMessage("The water is too deep to mine here.", (byte)3);
            return true;
        }
        VolaTile volaTile = Zones.getTileOrNull(targetTilePos.x, targetTilePos.y, onSurface);
        boolean isMiningInsideHouse = (volaTile != null && volaTile.getStructure() != null && volaTile.getStructure().isTypeHouse());
        if (isMiningInsideHouse){
            performer.getCommunicator().sendNormalServerMessage("You cannot mine in a building.", (byte)3);
            return true;
        }
        boolean isMiningNextToHouse = Arrays.stream(adjacentTilePos)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface) != null)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface).getStructure() != null)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface).getStructure().isTypeHouse())
                .count() != 0;
        if (isMiningNextToHouse){
            performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a building.", (byte)3);
            return true;
        }
        boolean isMiningInsideBridgeSupport = volaTile != null && volaTile.getStructure() != null &&
                Arrays.stream(volaTile.getBridgeParts())
                        .filter(bridgePart -> bridgePart.getType().isSupportType())
                        .count() != 0;
        if (isMiningInsideBridgeSupport){
            performer.getCommunicator().sendNormalServerMessage("You cannot mine in a bridge support.", (byte)3);
            return true;
        }
        boolean isMiningNextToBridgeSupport = Arrays.stream(adjacentTilePos)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface) != null)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface).getStructure() != null)
                .filter(tilePos -> Arrays.stream(Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface).getBridgeParts())
                        .filter(bridgePart -> bridgePart.getType().isSupportType())
                        .count() != 0)
                .count() != 0;
        if (isMiningNextToBridgeSupport){
            performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a bridge support.", (byte)3);
            return true;
        }
        TilePos[] downFenceTilePos = new TilePos[]{targetTilePos, targetTilePos.North(), targetTilePos.NorthWest(), targetTilePos.West(), targetTilePos.SouthWest(),
                targetTilePos.South()};
        boolean isBlockedByDownFence = Arrays.stream(downFenceTilePos)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface) != null)
                .filter(tilePos -> Arrays.stream(Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface).getFencesForDir(Tiles.TileBorderDirection.DIR_DOWN))
                        .count() != 0)
                .count() != 0;
        if (isBlockedByDownFence){
            performer.getCommunicator().sendNormalServerMessage("A fence is blocking mining.", (byte)3);
            return true;
        }
        TilePos[] horizontalFenceTilePos = new TilePos[]{targetTilePos, targetTilePos.West(), targetTilePos.SouthWest(), targetTilePos.South(),
                targetTilePos.SouthEast(), targetTilePos.East()};
        boolean isBlockedByHorizontalFence = Arrays.stream(horizontalFenceTilePos)
                .filter(tilePos -> Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface) != null)
                .filter(tilePos -> Arrays.stream(Zones.getTileOrNull(tilePos.x, tilePos.y, onSurface).getFencesForDir(Tiles.TileBorderDirection.DIR_HORIZ))
                        .count() != 0)
                .count() != 0;
        if (isBlockedByHorizontalFence){
            performer.getCommunicator().sendNormalServerMessage("A fence is blocking mining.", (byte)3);
            return true;
        }
        VolaTile dropTile = Zones.getTileOrNull(performerTilePos.x, performerTilePos.y, onSurface);
        boolean hasTooManyItems = dropTile != null && dropTile.getNumberOfItems(performer.getFloorLevel()) > 99;
        if (hasTooManyItems){
            performer.getCommunicator().sendNormalServerMessage("There is no space to mine here. Clear the area first.", (byte)3);
            return true;
        }
        final int maxSlope = (int)(performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge(0.0) * (Servers.localServer.PVPSERVER ? RockSurfaceTerraformingMod.getPvpSlopeMultiplier() :
                RockSurfaceTerraformingMod.getPveSlopeMultiplier()));
        boolean upSlopeTooSteepForSkill = Arrays.stream(adjacentTilePos)
                .filter(tilePos -> {
                    int slope = Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos)) - Tiles.decodeHeight(Server.surfaceMesh.getTile(performerTilePos));
                    return Math.signum(slope) == 1.0f && slope > maxSlope;
                })
                .count() != 0;
        if (upSlopeTooSteepForSkill){
            performer.getCommunicator().sendNormalServerMessage("You are too unskilled to mine a up-slope.", (byte)3);
            return true;
        }
        boolean downSlopeTooSteepForSkill = Arrays.stream(adjacentTilePos)
                .filter(tilePos -> {
                    int slope = Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos)) - Tiles.decodeHeight(Server.surfaceMesh.getTile(performerTilePos));
                    return Math.signum(slope) == -1.0f && -1 - slope > maxSlope;
                })
                .count() != 0;
        if (downSlopeTooSteepForSkill){
            performer.getCommunicator().sendNormalServerMessage("You are too unskilled to mine a down-slope.", (byte)3);
            return true;
        }
        boolean caveCeilingTooClose = Arrays.stream(adjacentTilePos)
                .filter(tilePos -> Tiles.decodeHeight(Server.surfaceMesh.getTile(tilePos)) - 1 <= Tiles.decodeHeight(Server.caveMesh.getTile(tilePos)) +
                        (Tiles.decodeData(Server.caveMesh.getTile(tilePos)) & 0xFF))
                .count() != 0;
        if (caveCeilingTooClose){
            performer.getCommunicator().sendNormalServerMessage("A cave ceiling is blocking mining here.", (byte)3);
            return true;
        }
        if (Terraforming.isAltarBlocking(performer, targetTilePos.x, targetTilePos.y)) {
            performer.getCommunicator().sendSafeServerMessage("You cannot mine here, since this is holy ground.", (byte)2);
            return true;
        }

        return false;
    }
}

