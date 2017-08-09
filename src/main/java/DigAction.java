import com.joedobo27.libs.ActionUtilities;
import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DigAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final ActionEntry actionEntry;
    private final short actionId;

    DigAction(short actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;

    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, int tileX, int tileY, boolean onSurface,
                                              Tiles.TileBorderDirection borderDirection, boolean border, int heightOffset) {
        if (subject.getTemplateId() != MightyMattockMod.getPickMattockTemplateID() || !border)
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection, border, heightOffset);
        return Collections.singletonList(this.actionEntry);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset,
                          Tiles.TileBorderDirection borderDirection, long borderId, short actionId, float counter) {
        // ACTION, SHOULD IT BE DONE
        if (actionId != this.actionId || source.getTemplateId() != MightyMattockMod.getPickMattockTemplateID())
            return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, borderDirection, borderId, actionId, counter);
        String youMessage;
        String broadcastMessage;
        final float ACTION_START_TIME = 1.0f;
        final float TIME_TO_COUNTER_DIVISOR = 10.0f;
        //  ACTION SET UP
        if(counter == ACTION_START_TIME && hasAFailureCondition(performer, tileX, tileY, borderDirection))
            return true;
        if (counter == ACTION_START_TIME) {
            youMessage = String.format("You start %s.", action.getActionString());
            performer.getCommunicator().sendNormalServerMessage(youMessage);
            broadcastMessage = String.format("%s starts to %s.", performer.getName(), action.getActionString());
            Server.getInstance().broadCastAction(broadcastMessage, performer, 5);

            int time = (int)ActionUtilities.getActionTime(10, 95, 200, 10,
                    source, performer, action, SkillList.DIGGING);
            action.setTimeLeft(time);
            performer.sendActionControl(this.actionEntry.getVerbString(), true, time);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
        }
        // ACTION HAS FINISHED
        boolean isTimedOut = counter >= action.getTimeLeft() / TIME_TO_COUNTER_DIVISOR;
        if (!isTimedOut)
            return false;
        if (hasAFailureCondition(performer, tileX, tileY, borderDirection))
            return true;
        // CHECK TILES AND CHANGE TILES
        // TODO need code to deal with tile which don't change elevation.
        TilePos opposingCorner = getOpposingCorner(performer, tileX, tileY, borderDirection);
        TileUtilities.setSurfaceHeight(opposingCorner , TileUtilities.getSurfaceHeight(opposingCorner) - 1);
        Players.getInstance().sendChangedTile(opposingCorner.x, opposingCorner.y, performer.isOnSurface(), true);
        shouldDirtBeRock(opposingCorner, performer);
        shouldMutableBeDirt(opposingCorner, performer);
        // DO SKILL CHECK
        double power = doSkillCheck(opposingCorner, performer, source, counter);
        // DAMAGE TOOL
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        // CONSUME STAMINA
        performer.getStatus().modifyStamina(-5000.0f);
        // MAKE AND DO SOMETHING WITH A DIRT
        int createdItemTemplate;
        switch (TileUtilities.getSurfaceType(opposingCorner)) {
            case Tiles.TILE_TYPE_CLAY:
                createdItemTemplate = ItemList.clay;
                break;
            case Tiles.TILE_TYPE_SAND:
                createdItemTemplate = ItemList.sand;
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
            default:
                createdItemTemplate = ItemList.dirtPile;
        }
        float modifier = 1.0f;
        if (source.getSpellEffects() != null && source.getSpellEffects().getRuneEffect() != -10L) {
            modifier += RuneUtilities.getModifier(source.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }
        Item created = null;
        try {
            created = ItemFactory.createItem(createdItemTemplate, Math.min((float) (power + source.getRarity()) * modifier, 100.0f),
                    (opposingCorner.x * 4) + 2, (opposingCorner.y * 4) + 2, Server.rand.nextFloat() * 360.0f,
                    performer.isOnSurface(), action.getRarity(), -10L, null);
        }catch (NoSuchTemplateException | FailedException ignored){}
        if (created == null) {
            performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return true;
        }
        created.setLastOwnerId(performer.getWurmId());
        created.setRarity(action.getRarity());
        return true;
    }

    private double doSkillCheck(TilePos opposingCorner, Creature performer, Item source, float counter) {
        TilePos[] tilePos = {opposingCorner.North(), opposingCorner.East(), opposingCorner.South(), opposingCorner.West()};
        int[] ints = Arrays.stream(tilePos)
                .mapToInt(value -> Math.abs(TileUtilities.getSurfaceHeight(opposingCorner) - TileUtilities.getSurfaceHeight(value)))
                .toArray();
        Arrays.sort(ints);
        double difficulty = 1 + (ints[ints.length-1] / 5);
        switch (TileUtilities.getSurfaceType(opposingCorner)) {
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
        Skill toolSkill = null;
        double bonus = 0;
        if (source != null && source.hasPrimarySkill()) {
            try {
                toolSkill = performer.getSkills().getSkillOrLearn(source.getPrimarySkill());
            } catch (NoSuchSkillException ignore) {}
        }
        if (toolSkill != null) {
            bonus = toolSkill.getKnowledge() / 10;
        }
        return performer.getSkills().getSkillOrLearn(SkillList.DIGGING).skillCheck(difficulty, source, bonus, false, counter);
    }

    /**
     * For the corner being dug there are 4 tiles affect. And for these 4 affected tiles check each's 4 corners to see if
     * the tile should be converted form dirt to rock. In the case where it's converted do the transformation and update
     * the appropriate tile state values.
     *
     * @param opposingCorner the corner being modified by digging.
     * @param performer the creature doing the digging
     */
    private void shouldDirtBeRock(TilePos opposingCorner, Creature performer) {
        TilePos[] tilePos = {opposingCorner, opposingCorner.West(), opposingCorner.NorthWest(), opposingCorner.North()};
        TilePos[] makeRockPos = Arrays.stream(tilePos)
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
                    TileUtilities.setSurfaceType(tilePos1, Tiles.Tile.TILE_ROCK.id);
                    performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos1.x, tilePos1.y, performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos1, performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos1.x, tilePos1.y);
                });
    }

    /**
     * For the corner being dug there are 4 tiles affect. Check to see if the tile should be converted to dirt.
     * In the case where it's converted do the transformation and update the appropriate tile state values.
     *
     * @param opposingCorner the corner being modified by digging.
     * @param performer the creature doing digging.
     */
    private void shouldMutableBeDirt(TilePos opposingCorner, Creature performer) {
        TilePos[] tilePos = {opposingCorner, opposingCorner.West(), opposingCorner.NorthWest(), opposingCorner.North()};
        TilePos[] makeDirtPos = Arrays.stream(tilePos)
                .filter(tilePos1 -> TileUtilities.isTileOverriddenByDirt(TileUtilities.getSurfaceType(tilePos1)))
                .toArray(TilePos[]::new);
        if (makeDirtPos == null || makeDirtPos.length == 0) {
            return;
        }
        Arrays.stream(makeDirtPos)
                .forEach(tilePos1 -> {
                    Server.modifyFlagsByTileType(tilePos1.x, tilePos1.y, Tiles.Tile.TILE_DIRT.id);
                    TileUtilities.setSurfaceType(tilePos1, Tiles.Tile.TILE_DIRT.id);
                    performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos1.x, tilePos1.y, performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos1, performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos1.x, tilePos1.y);
                });
    }


    private boolean hasAFailureCondition(Creature performer, int tileX, int tileY, Tiles.TileBorderDirection borderDirection) {
        if (!cornerCanBeDug(performer, tileX, tileY, borderDirection)){
            performer.getCommunicator().sendNormalServerMessage("That corner is dug down to rock.");
            return true;
        }
        TilePos opposingCorner = getOpposingCorner(performer, tileX, tileY, borderDirection);
        Village village = Zones.getVillage(opposingCorner.x, opposingCorner.y, performer.isOnSurface());
        if (village != null && !village.isActionAllowed((short)144, performer, false,
                TileUtilities.getSurfaceEncodedValue(opposingCorner), 0)) {
            if (!Zones.isOnPvPServer(opposingCorner.x, opposingCorner.y)) {
                performer.getCommunicator().sendNormalServerMessage("This tile is control by a deed which hasn't given you permission to change it.", (byte)3);
                return true;
            }
            if (!village.isEnemy(performer) && performer.isLegal()) {
                performer.getCommunicator().sendNormalServerMessage("That would be illegal here. You can check the settlement token for the local laws.", (byte)3);
                return true;
            }
        }
        // TODO fence detect and fail
        // TODO building detect and fail
        // TODO bridge support detect and fail

        return false;
    }

    private static TilePos getOpposingCorner(Creature performer, int tileX, int tileY, Tiles.TileBorderDirection borderDirection) {
        TilePos tilePosPerformer = TileUtilities.getPerformerNearestTile(performer);
        TilePos tilePosTarget = TilePos.fromXY(tileX, tileY);
        TilePos tilePosOpposing = TilePos.fromXY(-1, -1);
        if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_HORIZ.getCode() &&
                (int)Math.signum(tilePosPerformer.x - tilePosTarget.x) == 0)
            tilePosOpposing = tilePosTarget.East();
        else if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_HORIZ.getCode() &&
                (int)Math.signum(tilePosPerformer.x - tilePosTarget.x) == 1)
            tilePosOpposing = tilePosTarget;
        else if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_DOWN.getCode() &&
                (int)Math.signum(tilePosPerformer.y - tilePosTarget.y) == 0)
            tilePosOpposing = tilePosTarget.South();
        else if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_DOWN.getCode() &&
                (int)Math.signum(tilePosPerformer.y - tilePosTarget.y) == 1)
            tilePosOpposing = tilePosTarget;
        return tilePosOpposing;
    }

    private static boolean cornerCanBeDug(Creature performer, int tileX, int tileY, Tiles.TileBorderDirection borderDirection) {
        TilePos tilePosOpposing = getOpposingCorner(performer, tileX, tileY, borderDirection);
        return TileUtilities.getSurfaceHeight(tilePosOpposing) > TileUtilities.getRockHeight(tilePosOpposing);
    }
}
