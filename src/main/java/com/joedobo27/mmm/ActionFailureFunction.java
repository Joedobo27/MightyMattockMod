package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Constants;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Fence;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class ActionFailureFunction {

    private final String name;
    private final Function<MightyMattockAction, Boolean> function;

    public static final int FAILURE_FUNCTION_EMPTY = 0;
    public static final int FAILURE_FUNCTION_INSUFFICIENT_STAMINA = 1;
    public static final int FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE = 2;
    public static final int FAILURE_FUNCTION_GOD_PROTECTED = 3;
    public static final int FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION = 4;
    public static final int FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION = 5;
    public static final int FAILURE_FUNCTION_ROCK_MESH_AND_CAVE_CEILING_TOO_CLOSE = 6;
    public static final int FAILURE_FUNCTION_CORNER_OCCUPIED_BY_FENCE = 7;
    public static final int FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE = 8;
    public static final int FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT = 9;
    public static final int FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_EXIT = 10;
    public static final int FAILURE_FUNCTION_IS_OCCUPIED_BY_CAVE_ENTRANCE = 11;
    public static final int FAILURE_FUNCTION_IS_DIGGING_ROCK = 12;
    public static final int FAILURE_FUNCTION_NO_DIRT_NEARBY = 13;
    public static final int FAILURE_FUNCTION_NO_CONCRETE_NEARBY = 14;
    public static final int FAILURE_FUNCTION_CAVE_FLOOR_AND_CEILING_PROXIMITY = 15;
    public static final int FAILURE_FUNCTION_CAVE_ENTRANCE_BORDER = 16;
    private static HashMap<Integer, ActionFailureFunction> failureFunctions = new HashMap<>();
    static {
        initializeFailureFunctions();
    }

    @SuppressWarnings("WeakerAccess")
    public ActionFailureFunction(String name, Function<MightyMattockAction, Boolean> function) {
        this.name = name;
        this.function = function;
    }

    private static void initializeFailureFunctions() {
        failureFunctions.put(0, new ActionFailureFunction("FAILURE_FUNCTION_EMPTY", null));
        failureFunctions.put(1, new ActionFailureFunction("FAILURE_FUNCTION_INSUFFICIENT_STAMINA",
                (MightyMattockAction mightyMattockAction) -> {
                    if (mightyMattockAction.getPerformer().getStatus().getStamina() < mightyMattockAction.getMinimumStamina()) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage(
                                "You don't have enough stamina to " + mightyMattockAction.getAction().getActionString() + ".");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(2, new ActionFailureFunction("FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE",
                (MightyMattockAction mightyMattockAction) -> {
                    if (mightyMattockAction.getTargetTile().x < 0 ||
                            mightyMattockAction.getTargetTile().x > 1 << Constants.meshSize ||
                            mightyMattockAction.getTargetTile().y < 0 || mightyMattockAction.getTargetTile().y > 1 << Constants.meshSize) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage(
                                "You can't " + mightyMattockAction.getAction().getActionString() + " this close to the server boarder.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(3, new ActionFailureFunction("FAILURE_FUNCTION_GOD_PROTECTED",
                (MightyMattockAction mightyMattockAction) -> {
                    if (Zones.isTileProtected(mightyMattockAction.getTargetTile().x, mightyMattockAction.getTargetTile().y)) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "This tile is protected by the gods. You can not " + mightyMattockAction.getAction().getActionString() + " here.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(4, new ActionFailureFunction("FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION",
                (MightyMattockAction mightyMattockAction) -> {
                    Village village = Zones.getVillage(mightyMattockAction.getTargetTile().x, mightyMattockAction.getTargetTile().y,
                            mightyMattockAction.getPerformer().isOnSurface());
                    if (village != null && !village.isActionAllowed((short) mightyMattockAction.getActionId(),
                            mightyMattockAction.getPerformer(), false,
                            TileUtilities.getSurfaceEncodedValue(mightyMattockAction.getTargetTile()), 0) &&
                            !Zones.isOnPvPServer(mightyMattockAction.getTargetTile().x, mightyMattockAction.getTargetTile().y)) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "This tile is controlled by a deed which hasn't given you permission to change it.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(5, new ActionFailureFunction("FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION",
                (MightyMattockAction mightyMattockAction) -> {
                    Village village = Zones.getVillage(mightyMattockAction.getTargetTile().x, mightyMattockAction.getTargetTile().y,
                            mightyMattockAction.getPerformer().isOnSurface());
                    if (village != null && !village.isActionAllowed((short) mightyMattockAction.getActionId(), mightyMattockAction.getPerformer(),
                            false, TileUtilities.getSurfaceEncodedValue(mightyMattockAction.getTargetTile()), 0) &&
                            !village.isEnemy(mightyMattockAction.getPerformer()) && mightyMattockAction.getPerformer().isLegal()) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "That would be illegal here. You can check the settlement token for the local laws.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(6, new ActionFailureFunction("FAILURE_FUNCTION_ROCK_MESH_AND_CAVE_CEILING_TOO_CLOSE",
                (MightyMattockAction mightyMattockAction) -> {
                    if (!mightyMattockAction.getPerformer().isOnSurface())
                        return false;
                    if (TileUtilities.getCaveFloorHeight(mightyMattockAction.getTargetTile()) +
                            TileUtilities.getCaveCeilingOffset(mightyMattockAction.getTargetTile()) +
                            1 >= TileUtilities.getRockHeight(mightyMattockAction.getTargetTile())) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A cave is preventing the " + mightyMattockAction.getAction().getActionString() + " action.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(7, new ActionFailureFunction("FAILURE_FUNCTION_CORNER_OCCUPIED_BY_FENCE",
                (MightyMattockAction mightyMattockAction) -> {
                    Tiles.TileBorderDirection[] tileBorderDirections = {Tiles.TileBorderDirection.DIR_HORIZ,
                            Tiles.TileBorderDirection.DIR_HORIZ, Tiles.TileBorderDirection.DIR_DOWN,
                            Tiles.TileBorderDirection.DIR_DOWN};
                    TilePos[] fenceTiles = {mightyMattockAction.getTargetTile(), mightyMattockAction.getTargetTile().West(),
                            mightyMattockAction.getTargetTile().North(), mightyMattockAction.getTargetTile()};
                    if (IntStream.range(0, fenceTiles.length)
                            .anyMatch(value -> {
                                VolaTile volaTile1 = Zones.getOrCreateTile(fenceTiles[value],
                                        mightyMattockAction.getPerformer().isOnSurface());
                                Fence[] fences = volaTile1.getFencesForDir(tileBorderDirections[value]);
                                return fences != null && fences.length > 0;
                            })) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+mightyMattockAction.getAction().getActionString()+
                                " action can't modify a corner occupied by a fence.");
                        return true;
                    } else
                        return false;
                }));
        failureFunctions.put(8, new ActionFailureFunction("FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE",
                (MightyMattockAction mightyMattockAction) -> {
                    TilePos[] buildings = {mightyMattockAction.getTargetTile(), mightyMattockAction.getTargetTile().West(),
                            mightyMattockAction.getTargetTile().NorthWest(), mightyMattockAction.getTargetTile().North()};
                    if (IntStream.range(0,4)
                            .anyMatch(value -> {
                                VolaTile volaTile = Zones.getTileOrNull(buildings[value],
                                        mightyMattockAction.getPerformer().isOnSurface());
                                if (volaTile == null)
                                    return false;
                                Structure structure = volaTile.getStructure();
                                return structure != null && volaTile.getStructure().isTypeHouse();
                            })) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+mightyMattockAction.getAction().getActionString()+
                                " action can't modify a corner or tile occupied by a house.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(9, new ActionFailureFunction("FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT",
                (MightyMattockAction mightyMattockAction) -> {
                    TilePos[] buildings = {mightyMattockAction.getTargetTile(), mightyMattockAction.getTargetTile().West(),
                            mightyMattockAction.getTargetTile().NorthWest(), mightyMattockAction.getTargetTile().North()};
                    if (IntStream.range(0,4)
                            .anyMatch(value -> {
                                VolaTile volaTile = Zones.getTileOrNull(buildings[value],
                                        mightyMattockAction.getPerformer().isOnSurface());
                                if (volaTile == null)
                                    return false;
                                BridgePart[] bridgeParts = volaTile.getBridgeParts();
                                if (bridgeParts == null || bridgeParts.length == 0)
                                    return false;
                                return Arrays.stream(bridgeParts)
                                        .anyMatch(bridgePart -> bridgePart.getType().isSupportType());
                            })) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+mightyMattockAction.getAction().getActionString()+
                                " action can't modify a corner or tile occupied by a bridge support.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(10, new ActionFailureFunction("FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_EXIT",
                (MightyMattockAction mightyMattockAction) -> {
                    TilePos[] bridgeExits = {mightyMattockAction.getTargetTile().West(),
                            mightyMattockAction.getTargetTile().North(), mightyMattockAction.getTargetTile().East(),
                            mightyMattockAction.getTargetTile().South()};
                    ArrayList<Function<BridgePart, Boolean>> functions = new ArrayList<>(Arrays.asList(
                            BridgePart::hasEastExit, BridgePart::hasWestExit, BridgePart::hasSouthExit,
                            BridgePart::hasNorthExit));
                    if (IntStream.range(0, functions.size())
                            .anyMatch(value -> {
                                VolaTile volaTile = Zones.getTileOrNull(bridgeExits[value],
                                        mightyMattockAction.getPerformer().isOnSurface());
                                if (volaTile == null)
                                    return false;
                                BridgePart[] bridgeParts = volaTile.getBridgeParts();
                                if (bridgeParts == null || bridgeParts.length == 0)
                                    return false;
                                return Arrays.stream(bridgeParts)
                                        .anyMatch(bridgePart -> functions.get(value).apply(bridgePart));
                            })){
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+mightyMattockAction.getAction().getActionString()+
                                " action can't modify a corner or tile occupied by a bridge exit.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(11, new ActionFailureFunction("FAILURE_FUNCTION_IS_OCCUPIED_BY_CAVE_ENTRANCE",
                (MightyMattockAction mightyMattockAction) -> {
                    if (!mightyMattockAction.getPerformer().isOnSurface())
                        return false;
                    TilePos[] caveOpenings = {mightyMattockAction.getTargetTile(), mightyMattockAction.getTargetTile().West(),
                            mightyMattockAction.getTargetTile().NorthWest(), mightyMattockAction.getTargetTile().North()};
                    if (IntStream.range(0,4)
                            .anyMatch(value -> {
                                Tiles.Tile tileType = Tiles.getTile(TileUtilities.getSurfaceTypeId(caveOpenings[value]));
                                return tileType.isCaveDoor() || tileType.getId() == Tiles.Tile.TILE_HOLE.id;
                            })) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "A "+mightyMattockAction.getAction().getActionString()+
                                " action can't modify a corner or tile occupied by a cave entrance.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(12, new ActionFailureFunction("FAILURE_FUNCTION_IS_DIGGING_ROCK",
                (MightyMattockAction mightyMattockAction) -> {
            if (TileUtilities.getDirtDepth(mightyMattockAction.getTargetTile()) <= 0){
                mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                        "You can't do "+mightyMattockAction.getAction().getActionEntry().getVerbString()+
                        " in rock.");
                return true;
            }
                    return false;
                }));
        failureFunctions.put(13, new ActionFailureFunction("FAILURE_FUNCTION_NO_DIRT_NEARBY",
                (MightyMattockAction mightyMattockAction) -> {
                    Item[] groundItems = mightyMattockAction.getGroundItems(ItemList.dirtPile, mightyMattockAction.getTargetTile());
                    Item[] inventoryItems = mightyMattockAction.getInventoryItems(ItemList.dirtPile);
                    if ((groundItems == null || groundItems.length == 0) && (inventoryItems == null ||
                            inventoryItems.length == 0)) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "There is no dirt nearby to do "+mightyMattockAction.getAction().getActionEntry().getVerbString()+
                                " with.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(14, new ActionFailureFunction("FAILURE_FUNCTION_NO_CONCRETE_NEARBY",
                (MightyMattockAction mightyMattockAction) -> {
                    Item[] groundItems = mightyMattockAction.getGroundItems(ItemList.concrete, mightyMattockAction.getTargetTile());
                    Item[] inventoryItems = mightyMattockAction.getInventoryItems(ItemList.concrete);
                    if ((groundItems == null || groundItems.length == 0) && (inventoryItems == null ||
                            inventoryItems.length == 0)) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "There is no concrete nearby to do "+mightyMattockAction.getAction().getActionEntry().getVerbString()+
                                " with.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(15, new ActionFailureFunction("FAILURE_FUNCTION_CAVE_FLOOR_AND_CEILING_PROXIMITY",
                (MightyMattockAction mightyMattockAction) -> {
                    if (!mightyMattockAction.getPerformer().isOnSurface() &&
                            TileUtilities.getCaveCeilingOffset(mightyMattockAction.getTargetTile()) <= 20) {
                        mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                "The cave floor and ceiling can't be any closer.");
                        return true;
                    }
                    return false;
                }));
        failureFunctions.put(16, new ActionFailureFunction("FAILURE_FUNCTION_CAVE_ENTRANCE_BORDER",
                (MightyMattockAction mightyMattockAction) -> {
                    TilePos tilePos = mightyMattockAction.getTargetTile();
                    int surfaceHeight = TileUtilities.getSurfaceHeight(tilePos);
                    if (surfaceHeight == TileUtilities.getCaveFloorHeight(tilePos) && surfaceHeight ==
                            TileUtilities.getRockHeight(tilePos)) {
                            mightyMattockAction.getPerformer().getCommunicator().sendNormalServerMessage("" +
                                    "The cave entrance boarder tile can't be changed.");
                            return true;
                        }
                        return false;
                }));
    }


    static Function<MightyMattockAction, Boolean> getFunction(int functionId) {
        if (failureFunctions.containsKey(functionId))
            return failureFunctions.get(functionId).function;
        else
            return failureFunctions.get(0).function;
    }

    public static Function<MightyMattockAction, Boolean> getFunction(String functionName) {
        Function<MightyMattockAction, Boolean> toReturn = failureFunctions.values().stream()
                .filter(integerActionFailureFunctionEntry -> Objects.equals(
                        integerActionFailureFunctionEntry.name, functionName))
                .map(integerActionFailureFunctionEntry -> integerActionFailureFunctionEntry.function)
                .findFirst()
                .orElseGet(null);
        if (toReturn == null)
            toReturn = failureFunctions.get(0).function;
        return toReturn;
    }
}
