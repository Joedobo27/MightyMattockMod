package com.joedobo27.mmm;


import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.BushData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.server.FailedException;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.zones.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Function;

class ChopTerraformer extends MightyMattockAction {

    private final TilePos targetedTile;

    static WeakHashMap<Action, ChopTerraformer> actionDataWeakHashMap = new WeakHashMap<>();

    ChopTerraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill,
                    int maxSkill, int longestTime, int shortestTime, int minimumStamina,
                    ArrayList<Function<MightyMattockAction, Boolean>> failureTestFunctions, TilePos targetedTile) {

        super(action, performer, activeTool, usedSkill, minSkill, maxSkill, longestTime, shortestTime, minimumStamina,
                failureTestFunctions);
        this.targetedTile = targetedTile;
        actionDataWeakHashMap.put(action, this);
    }

    static boolean hashMapHasInstance(Action action) {
        return actionDataWeakHashMap.containsKey(action);
    }

    boolean hasAFailureCondition() {
        return this.getFailureTestFunctions().stream()
                .anyMatch(function -> function.apply(this));
    }

    double doSkillCheckAndGetPower(float counter) {
        if (this.usedSkill == null)
            return -1.0d;
        double difficulty = 1.0d;
        int encodedTile = Server.surfaceMesh.getTile(this.targetedTile);
        Tiles.Tile tileType = Tiles.getTile(encodedTile);

        if (tileType.isBush()) {
            BushData.BushType bushType = BushData.BushType.fromTileData(encodedTile);
            difficulty = bushType.getDifficulty();
        }
        if (tileType.isTree()) {
            TreeData.TreeType treeType = TreeData.TreeType.fromTileData(encodedTile);
            difficulty = treeType.getDifficulty();
        }
        return Math.max(1.0d,
                this.performer.getSkills().getSkillOrLearn(this.usedSkill).skillCheck(difficulty, this.activeTool,
                        0, false, counter));
    }

    void alterTileState() {
        TileUtilities.setSurfaceTypeId(this.targetedTile, Tiles.TILE_TYPE_DIRT);
        Server.modifyFlagsByTileType(this.targetedTile.x, this.targetedTile.y, Tiles.Tile.TILE_DIRT.id);
        this.performer.getMovementScheme().touchFreeMoveCounter();
        Players.getInstance().sendChangedTile(this.targetedTile.x, this.targetedTile.y, performer.isOnSurface(), true);
        Zone zone = TileUtilities.getZoneSafe(this.targetedTile, this.performer.isOnSurface());
        if (zone != null)
            zone.changeTile(this.targetedTile.x, this.targetedTile.y);
    }

    @SuppressWarnings("UnusedReturnValue")
    Item makeFelledTreeOnGround(double power) {
        if (!TileUtilities.getSurfaceType(this.targetedTile).isTree())
            return null;
        float runeModifier = 1.0f;
        if (this.activeTool != null && this.activeTool.getSpellEffects() != null &&
                this.activeTool.getSpellEffects().getRuneEffect() != -10L) {
            runeModifier += RuneUtilities.getModifier(this.activeTool.getSpellEffects().getRuneEffect(),
                    RuneUtilities.ModifierEffect.ENCH_RESGATHERED);
        }

        double rarityModifier = this.activeTool == null ? 0 : this.activeTool.getRarity();

        byte material;
        TreeData.TreeType treeType = TileUtilities.getTreeType(this.targetedTile);
        if (treeType == null) {
            this.performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return null;
        }
        material = treeType.getMaterial();

        int itemTemplateId;
        byte treeAge = (byte)TileUtilities.getTreeAge(this.targetedTile);
        itemTemplateId = ItemList.log;
        if (treeAge >= 8 && !treeType.isFruitTree()) {
            itemTemplateId = ItemList.logHuge;
        }
        double sizeModifier = treeAge / 15.0;
        if (treeType.isFruitTree()) {
            sizeModifier *= 0.25;
        }
        ItemTemplate itemTemplate = getItemTemplate(itemTemplateId);
        if (itemTemplate == null){
            this.performer.getCommunicator().sendNormalServerMessage("Sorry, something went wrong.");
            return null;
        }
        int weight = (int)Math.max(1000.0, sizeModifier * itemTemplate.getWeightGrams());
        if (weight < 1500) {
            itemTemplateId = ItemList.scrapwood;
        }
        float rotation = (this.getPerformer().getStatus().getRotation() + 90) % 360;
        Item created = null;
        try {
            created = ItemFactory.createItem(itemTemplateId,
                    Math.min((float) (power + rarityModifier) * runeModifier, 100.0f),(this.targetedTile.x * 4) + 2,
                    (this.targetedTile.y * 4) + 2, rotation, performer.isOnSurface(), material, action.getRarity(),
                    -10L, null, treeAge);
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

    @Override
    Item getTargetItem() {
        return null;
    }

    @Override
    public Item getActiveTool() {
        return activeTool;
    }

    private ItemTemplate getItemTemplate(int itemTemplateId) {
        try {
            return ItemTemplateFactory.getInstance().getTemplate(itemTemplateId);
        } catch (NoSuchTemplateException e) {
            return null;
        }
    }
}
