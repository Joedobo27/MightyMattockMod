package com.joedobo27.mmm;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zones;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

public abstract class MightyMattockAction extends ActionMaster {

    private final ArrayList<Function<MightyMattockAction, Boolean>> failureTestFunctions;

    protected MightyMattockAction(Action action, Creature performer, @Nullable Item activeTool, @Nullable Integer usedSkill,
                                  int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                                  ArrayList<Function<MightyMattockAction, Boolean>> failureTestFunctions) {
        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
        this.failureTestFunctions = failureTestFunctions;
    }

    abstract boolean hasAFailureCondition();

    abstract TilePos getTargetTile();

    abstract Item getTargetItem();

    public abstract Item getActiveTool();

    protected ArrayList<Function<MightyMattockAction, Boolean>> getFailureTestFunctions() {
        return failureTestFunctions;
    }

    @Override
    protected @Nullable Item[] getInventoryItems(int findItemTemplateId) {
        Item[] inventoryItems = this.performer.getInventory().getItemsAsArray();
        Item[] itemsAll = Arrays.stream(inventoryItems)
                .filter(item -> item.getTemplateId() == findItemTemplateId)
                .toArray(Item[]::new);
        if (itemsAll == null || itemsAll.length == 0)
            return null;
        return itemsAll;
    }

    @Override
    protected @Nullable Item[] getGroundItems(int findItemTemplateId, TilePos targetedTile) {
        TilePos[] tilesToCheck = {targetedTile, targetedTile.West(), targetedTile.NorthWest(), targetedTile.North()};
        ArrayList<Item[]> itemsGrouped = new ArrayList<>();
        Arrays.stream(tilesToCheck)
                .forEach(tilePos -> {
                    VolaTile volaTile = Zones.getTileOrNull(tilePos, this.performer.isOnSurface());
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
}
