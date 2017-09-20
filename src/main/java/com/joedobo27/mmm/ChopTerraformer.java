package com.joedobo27.mmm;


import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.BushData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
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

class ChopTerraformer extends ActionMaster {

    private final TilePos targetedTile;

    static WeakHashMap<Action, ChopTerraformer> actionDataWeakHashMap = new WeakHashMap<>();

    ChopTerraformer(Action action, Creature performer, @Nullable Item activeTool, int usedSkill, int minSkill,
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
        //TODO change tile to grass. remember flags, surfaceMesh, and ??resourceMesh??
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
        Tiles.Tile tileType = TileUtilities.getSurfaceType(this.targetedTile);
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

        Item created = null;
        try {
            Item newItem = ItemFactory.createItem(itemTemplateId, Math.min(100.0f, (float)power),
                    2f + (4f * this.targetedTile.x), 2f + (4f * this.targetedTile.y),
                                                  Server.rand.nextInt(360), this.performer.isOnSurface(), material, this.action.getRarity(),
                                                  -10L, null, treeAge
                                                 );
            created = ItemFactory.createItem(itemTemplateId,
                    Math.min((float) (power + rarityModifier) * runeModifier, 100.0f),
                    (this.targetedTile.x * 4) + 2,
                    (this.targetedTile.y * 4) + 2,
                    Server.rand.nextFloat() * 360.0f, performer.isOnSurface(), material, action.getRarity(),
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

    private ItemTemplate getItemTemplate(int itemTemplateId) {
        try {
            return ItemTemplateFactory.getInstance().getTemplate(itemTemplateId);
        } catch (NoSuchTemplateException e) {
            return null;
        }
    }
}
