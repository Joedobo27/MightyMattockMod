package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Constants;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.stream.IntStream;

class Terraformer extends ActionMaster {

    private final TilePos targetedTile;
    private final boolean onSurface;
    private final int heightOffset;
    private final Tiles.TileBorderDirection borderDirection;
    private final long boarderId;
    private final int actionId;
    private final TilePos opposingCorner;

    static WeakHashMap<Action, Terraformer> actionDataWeakHashMap = new WeakHashMap<>();

    Terraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill, int maxSkill,
                int longestTime, int shortestTime, TilePos targetedTile, boolean onSurface, int heightOffset,
                Tiles.TileBorderDirection borderDirection, long borderId, short actionId, TilePos opposingCorner) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime);
        this.targetedTile = targetedTile;
        this.onSurface = onSurface;
        this.heightOffset = heightOffset;
        this.borderDirection = borderDirection;
        this.boarderId = borderId;
        this.actionId = actionId;
        this.opposingCorner = opposingCorner;
        actionDataWeakHashMap.put(action, this);
    }

    static boolean hashMapHasInstance(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    boolean hasAFailureCondition() {

        if (this.performer.getStatus().getStamina() < 6000) {
            this.performer.getCommunicator().sendNormalServerMessage("You don't have enough stamina to terraform.");
            return true;
        }

        boolean isTooCloseToServerBoarder = this.opposingCorner.x < 0 || this.opposingCorner.x > 1 << Constants.meshSize ||
                this.opposingCorner.y < 0 || this.opposingCorner.y > 1 << Constants.meshSize;
        if (isTooCloseToServerBoarder){
            performer.getCommunicator().sendNormalServerMessage("You can't terraform this close to the server boarder.", (byte)3);
            return true;
    }

        if (Zones.isTileProtected(this.opposingCorner.x, this.opposingCorner.y)) {
            performer.getCommunicator().sendNormalServerMessage("This tile is protected by the gods. You can not terraform here.", (byte)3);
            return true;
        }

        if (this.actionId == Actions.DIG && TileUtilities.getDirtDepth(this.opposingCorner) <= 0) {
            performer.getCommunicator().sendNormalServerMessage("That corner has been dug down to rock.");
            return true;
        }

        Item[] groundItems = this.getGroundItems(ItemList.dirtPile);
        Item[] inventoryItems = this.getInventoryItems(ItemList.dirtPile);
        boolean noDigItems = (groundItems == null || groundItems.length == 0) && (inventoryItems == null || inventoryItems.length == 0);
        if (this.actionId == MightyMattockMod.getRaiseDirtEntryId() && noDigItems) {
            performer.getCommunicator().sendNormalServerMessage("There is no dirt nearby to raise with.");
            return true;
        }

        groundItems = this.getGroundItems(ItemList.concrete);
        inventoryItems = this.getInventoryItems(ItemList.concrete);
        boolean noMineItems = (groundItems == null || groundItems.length == 0) && (inventoryItems == null || inventoryItems.length == 0);
        if (this.actionId == MightyMattockMod.getRaiseRockEntryId() && noMineItems) {
            performer.getCommunicator().sendNormalServerMessage("There is no concrete nearby to raise with.");
            return true;
        }

        Village village = Zones.getVillage(this.opposingCorner.x, this.opposingCorner.y, this.performer.isOnSurface());
        if (village != null && !village.isActionAllowed((short)144, this.performer, false,
                TileUtilities.getSurfaceEncodedValue(this.opposingCorner), 0)) {
            if (!Zones.isOnPvPServer(this.opposingCorner.x, this.opposingCorner.y)) {
                this.performer.getCommunicator().sendNormalServerMessage("This tile is control by a deed which hasn't given you permission to change it.", (byte)3);
                return true;
            }
            if (!village.isEnemy(this.performer) && this.performer.isLegal()) {
                this.performer.getCommunicator().sendNormalServerMessage("That would be illegal here. You can check the settlement token for the local laws.", (byte)3);
                return true;
            }
        }

        Tiles.TileBorderDirection[] directions = {Tiles.TileBorderDirection.DIR_HORIZ, Tiles.TileBorderDirection.DIR_HORIZ,
                Tiles.TileBorderDirection.DIR_DOWN, Tiles.TileBorderDirection.DIR_DOWN};
        TilePos[] fences = {this.opposingCorner, this.opposingCorner.West(), this.opposingCorner.North(), this.opposingCorner};
        int fenceCount = IntStream.range(0,4)
                .map(value -> {
                    VolaTile volaTile = Zones.getTileOrNull(fences[value], this.onSurface);
                    return volaTile == null ? 0 : volaTile.getFencesForDir(directions[value]).length;
                })
                .sum();
        if (fenceCount > 0){
            this.performer.getCommunicator().sendNormalServerMessage("You can't modify a corner occupied by a fence.");
            return true;
        }

        TilePos[] buildings = {this.opposingCorner, this.opposingCorner.West(), this.opposingCorner.NorthWest(),
                this.opposingCorner.North()};
        int buildingCount = IntStream.range(0,4)
                .map(value -> {
                    VolaTile volaTile = Zones.getTileOrNull(buildings[value], this.onSurface);
                    if (volaTile == null)
                        return 0;
                    Structure structure = volaTile.getStructure();
                    if (structure == null)
                        return 0;
                    return volaTile.getStructure().isTypeHouse() ? 1 : 0;
                })
                .sum();
        if (buildingCount > 0) {
            this.performer.getCommunicator().sendNormalServerMessage("You can't modify a corner occupied by a house.");
            return true;
        }

        int bridgeSupportCount = IntStream.range(0,4)
                .map(value -> {
                    VolaTile volaTile = Zones.getTileOrNull(buildings[value], this.onSurface);
                    if (volaTile == null)
                        return 0;
                    BridgePart[] bridgeParts = volaTile.getBridgeParts();
                    if (bridgeParts == null || bridgeParts.length == 0)
                        return 0;
                    return (int)Arrays.stream(bridgeParts)
                            .filter(bridgePart -> bridgePart.getType().isSupportType())
                            .count();
                })
                .sum();
        if (bridgeSupportCount > 0) {
            this.performer.getCommunicator().sendNormalServerMessage("You can't modify a corner occupied by a bridge support.");
            return true;
        }
        return false;
    }

    void modifyHeight(int modifier) {
        if (this.actionId == Actions.DIG && !TileUtilities.isImmutableTile(this.opposingCorner)) {
            TileUtilities.setSurfaceHeight(this.opposingCorner, TileUtilities.getSurfaceHeight(this.opposingCorner) + modifier);
            Players.getInstance().sendChangedTile(this.opposingCorner.x, this.opposingCorner.y, this.performer.isOnSurface(),
                    true);
        }
        if (this.actionId == MightyMattockMod.getRaiseDirtEntryId()) {
            TileUtilities.setSurfaceHeight(this.opposingCorner, TileUtilities.getSurfaceHeight(this.opposingCorner) + modifier);
            Players.getInstance().sendChangedTile(this.opposingCorner.x, this.opposingCorner.y, this.performer.isOnSurface(),
                    true);
        }

        if (this.actionId == Actions.MINE) {
            TileUtilities.setRockHeight(this.opposingCorner, TileUtilities.getRockHeight(this.opposingCorner) + modifier);
            TileUtilities.setSurfaceHeight(this.opposingCorner, TileUtilities.getSurfaceHeight(this.opposingCorner) + modifier);
            Players.getInstance().sendChangedTile(this.opposingCorner.x, this.opposingCorner.y, this.performer.isOnSurface(),
                    true);
        }
        if (this.actionId == MightyMattockMod.getRaiseRockEntryId()) {
            TileUtilities.setRockHeight(this.opposingCorner, TileUtilities.getRockHeight(this.opposingCorner) + modifier);
            TileUtilities.setSurfaceHeight(this.opposingCorner, TileUtilities.getSurfaceHeight(this.opposingCorner) + modifier);
            Players.getInstance().sendChangedTile(this.opposingCorner.x, this.opposingCorner.y, this.performer.isOnSurface(),
                    true);
        }
    }

    void destroyRaiseResource() {
        Integer destroyItemTemplateId = null;
        if (this.actionId == MightyMattockMod.getRaiseDirtEntryId())
            destroyItemTemplateId = ItemList.dirtPile;
        if (this.actionId == MightyMattockMod.getRaiseRockEntryId())
            destroyItemTemplateId = ItemList.concrete;
        if (destroyItemTemplateId == null)
            return;
        Item[] items = this.getInventoryItems(destroyItemTemplateId);
        if (items == null || items.length == 0) {
            items = this.getGroundItems(destroyItemTemplateId);
        }
        if (items == null)
            return;
        Item item = items[Server.rand.nextInt(items.length)];
        if (item == null)
            return;
        item.setWeight(0, true);
    }

    private @Nullable Item[] getInventoryItems(int findItemTemplateId) {
        Item[] inventoryItems = this.performer.getInventory().getItemsAsArray();
        Item[] itemsAll = Arrays.stream(inventoryItems)
                .filter(item -> item.getTemplateId() == findItemTemplateId)
                .toArray(Item[]::new);
        if (itemsAll == null || itemsAll.length == 0)
            return null;
        return itemsAll;
    }

    private @Nullable Item[] getGroundItems(int findItemTemplateId) {
        TilePos[] tilesToCheck = {this.opposingCorner, this.opposingCorner.West(), this.opposingCorner.NorthWest(), this.opposingCorner.North()};
        ArrayList<Item[]> itemsGrouped = new ArrayList<>();
        Arrays.stream(tilesToCheck)
                .forEach(tilePos -> {
                    VolaTile volaTile = Zones.getTileOrNull(tilePos, this.onSurface);
                    if (volaTile == null)
                        return;
                    Item[] items = volaTile.getItems();
                    if (items == null || items.length == 0)
                        return;
                    itemsGrouped.add(items);
                });
        if (itemsGrouped.size() == 0)
            return null;
        Item[] itemsUnfiltered = itemsGrouped.stream().flatMap(Arrays::stream).toArray(Item[]::new);
        Item[] itemsAll = Arrays.stream(itemsUnfiltered)
                .filter(item -> item.getTemplateId() == findItemTemplateId)
                .toArray(Item[]::new);
        if (itemsAll == null || itemsAll.length == 0)
            return null;
        return itemsAll;
    }

    /**
     * For the corner being dug there are 4 tiles affect. And for these 4 affected tiles check each's 4 corners to see if
     * the tile should be converted form dirt to rock. In the case where it's converted do the transformation and update
     * the appropriate tile state values.
     */
    void shouldDirtBeRock() {
        TilePos[] tilePos = {this.opposingCorner, this.opposingCorner.West(), this.opposingCorner.NorthWest(),
                this.opposingCorner.North()};
        TilePos[] makeRockPos = Arrays.stream(tilePos)
                .filter(tilePos1 -> !Tiles.isTree(TileUtilities.getSurfaceType(tilePos1)) && !TileUtilities.isImmutableTile(tilePos1))
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
                    this.performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos1.x, tilePos1.y, this.performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos1, this.performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos1.x, tilePos1.y);
                });
    }

    /**
     * For the corner being dug there are 4 tiles affect. Check to see if the tile should be converted to dirt.
     * In the case where it's converted do the transformation and update the appropriate tile state values.
     */
    void shouldMutableBeDirt() {
        TilePos[] checkTiles = {this.opposingCorner, this.opposingCorner.West(), this.opposingCorner.NorthWest(),
                this.opposingCorner.North()};
        TilePos[] makeDirtPos = Arrays.stream(checkTiles)
                .filter(TileUtilities::isTileOverriddenByDirt)
                .toArray(TilePos[]::new);
        if (makeDirtPos == null || makeDirtPos.length == 0) {
            return;
        }
        Arrays.stream(makeDirtPos)
                .forEach(tilePos -> {
                    Server.modifyFlagsByTileType(tilePos.x, tilePos.y, Tiles.Tile.TILE_DIRT.id);
                    TileUtilities.setSurfaceType(tilePos, Tiles.Tile.TILE_DIRT.id);
                    this.performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos.x, tilePos.y, performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos, this.performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos.x, tilePos.y);
                });
    }

    void shouldRockBeDirt() {
        if (TileUtilities.getDirtDepth(this.opposingCorner) <= 0)
            return;
        TilePos[] checkTiles = {this.opposingCorner, this.opposingCorner.West(), this.opposingCorner.NorthWest(),
                this.opposingCorner.North()};
        Arrays.stream(checkTiles)
                .filter(tilePos -> TileUtilities.getSurfaceType(tilePos) == Tiles.TILE_TYPE_ROCK ||
                        TileUtilities.getSurfaceType(tilePos) == Tiles.TILE_TYPE_CLIFF)
                .forEach(tilePos -> {
                    Server.modifyFlagsByTileType(tilePos.x, tilePos.y, Tiles.Tile.TILE_DIRT.id);
                    TileUtilities.setSurfaceType(tilePos, Tiles.Tile.TILE_DIRT.id);
                    this.performer.getMovementScheme().touchFreeMoveCounter();
                    Players.getInstance().sendChangedTile(tilePos.x, tilePos.y, performer.isOnSurface(), true);
                    Zone zone = TileUtilities.getZoneSafe(tilePos, this.performer.isOnSurface());
                    if (zone != null)
                        zone.changeTile(tilePos.x, tilePos.y);
                });
    }

    double doSkillCheckAndGetPower(float counter) {
        TilePos[] tilePos = {this.opposingCorner.North(), this.opposingCorner.East(), this.opposingCorner.South(), this.opposingCorner.West()};
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
        if (this.activeTool != null && this.activeTool.hasPrimarySkill()) {
            try {
                toolSkill = this.performer.getSkills().getSkillOrLearn(this.activeTool.getPrimarySkill());
            } catch (NoSuchSkillException ignore) {}
        }
        if (toolSkill != null) {
            bonus = toolSkill.getKnowledge() / 10;
        }
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(SkillList.DIGGING).skillCheck(difficulty, this.activeTool, bonus, false, counter));
    }

    Item makeDugItemOnGround(double power) {
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
        float runeModifier = 1.0f;
        if (this.activeTool != null && this.activeTool.getSpellEffects() != null && this.activeTool.getSpellEffects().getRuneEffect() != -10L) {
            runeModifier += RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }
        Item created = null;
        double rarityModifier = this.activeTool == null ? 0 : this.activeTool.getRarity();
        try {
            created = ItemFactory.createItem(createdItemTemplate, Math.min((float) (power + rarityModifier) * runeModifier, 100.0f),
                    (this.opposingCorner.x * 4) + 2, (this.opposingCorner.y * 4) + 2, Server.rand.nextFloat() * 360.0f,
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

    Item makeMinedItemOnGround(double power) {
        float runeModifier = 1.0f;
        if (this.activeTool != null && this.activeTool.getSpellEffects() != null && this.activeTool.getSpellEffects().getRuneEffect() != -10L) {
            runeModifier += RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(), RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }
        Item created = null;
        double rarityModifier = this.activeTool == null ? 0 : this.activeTool.getRarity();
        try {
            created = ItemFactory.createItem(ItemList.rock, Math.min((float) (power + rarityModifier) * runeModifier, 100.0f),
                    (this.opposingCorner.x * 4) + 2, (this.opposingCorner.y * 4) + 2, Server.rand.nextFloat() * 360.0f,
                    performer.isOnSurface(), action.getRarity(), -10L, null);
        }catch (NoSuchTemplateException | FailedException ignored){}
        if (created == null) {
            this.performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return null;
        }
        created.setDataXY(this.opposingCorner.x, this.opposingCorner.y);
        created.setLastOwnerId(this.performer.getWurmId());
        created.setRarity(this.action.getRarity());
        return created;
    }

    static TilePos getOpposingCorner(Creature performer, int tileX, int tileY, Tiles.TileBorderDirection borderDirection) {
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
}

