package com.joedobo27.mmm;

import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.CreationCategories;
import com.wurmonline.server.items.CreationEntryCreator;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTypes;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.libs.action.ActionMaster.setActionEntryMaxRangeReflect;


public class MightyMattockMod implements  WurmServerMod, PreInitable, Configurable, ServerStartedListener,
                                          ItemTemplatesCreatedListener, PlayerMessageListener {

    private static int pickMattockHeadTemplateID;
    private static int pickMattockTemplateID;

    @SuppressWarnings("WeakerAccess")
    static final Logger logger = Logger.getLogger(MightyMattockMod.class.getName());

    @Override public boolean onPlayerMessage(Communicator communicator, String s) {
        return false;
    }

    @Override
    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title) {
        if (communicator.getPlayer().getPower() == 5 && message.startsWith("/MightyMattockMod properties")) {
            communicator.getPlayer().getCommunicator().sendNormalServerMessage(
                    "Reloading properties for MightMattockMod."
                                                                              );
            ConfigureOptions.resetOptions();
            return MessagePolicy.DISCARD;
        }
        return MessagePolicy.PASS;
    }

    @Override
    public void configure(Properties properties) {
        ConfigureOptions.setOptions(properties);
    }

    @Override
    public void preInit() {
        ModActions.init();
    }

    @Override
    public void onItemTemplatesCreated() {
        addPickMattockTemplates();
    }

    @Override
    public void onServerStarted() {
        ModActions.registerAction(new TerraformBehaviours());

        ChopActionPerformer chopActionPerformer = ChopActionPerformer.getChopActionPerformer();
        ModActions.registerAction(chopActionPerformer);
        setActionEntryMaxRangeReflect(chopActionPerformer.getActionEntry(), 8, logger);

        CollectResourceActionPerformer collectResourceActionPerformer = CollectResourceActionPerformer.getCollectResourceActionPerformer();
        ModActions.registerAction(collectResourceActionPerformer);
        ModActions.registerAction(collectResourceActionPerformer.getActionEntry());
        setActionEntryMaxRangeReflect(collectResourceActionPerformer.getActionEntry(), 8, logger);
        TerraformBehaviours.setCollectResourceAction(collectResourceActionPerformer.getActionEntry());

        DigActionPerformer digActionPerformer = DigActionPerformer.getDigActionPerformer();
        ModActions.registerAction(digActionPerformer);
        setActionEntryMaxRangeReflect(digActionPerformer.getActionEntry(), 8, logger);

        MineActionPerformer mineActionPerformer = MineActionPerformer.getMineActionPerformer();
        ModActions.registerAction(mineActionPerformer);
        setActionEntryMaxRangeReflect(mineActionPerformer.getActionEntry(), 8, logger);

        PackActionPerformer packActionPerformer = PackActionPerformer.getPackActionPerformer();
        ModActions.registerAction(packActionPerformer);
        setActionEntryMaxRangeReflect(packActionPerformer.getActionEntry(), 8, logger);

        RaiseDirtActionPerformer raiseDirtActionPerformer = RaiseDirtActionPerformer.getRaiseDirtActionPerformer();
        ModActions.registerAction(raiseDirtActionPerformer);
        ModActions.registerAction(raiseDirtActionPerformer.getActionEntry());
        setActionEntryMaxRangeReflect(raiseDirtActionPerformer.getActionEntry(), 8, logger);
        TerraformBehaviours.setRaiseDirtAction(raiseDirtActionPerformer.getActionEntry());

        RaiseRockActionPerformer raiseRockActionPerformer = RaiseRockActionPerformer.getRaiseRockActionPerformer();
        ModActions.registerAction(raiseRockActionPerformer);
        ModActions.registerAction(raiseRockActionPerformer.getActionEntry());
        setActionEntryMaxRangeReflect(raiseRockActionPerformer.getActionEntry(), 8, logger);
        TerraformBehaviours.setRaiseRockAction(raiseRockActionPerformer.getActionEntry());


        CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_BLACKSMITHING,
                ItemList.anvilLarge, ItemList.ironBar, pickMattockHeadTemplateID, false, true, 0.0f, false, false,
                CreationCategories.TOOL_PARTS);
        CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_BLACKSMITHING,
                ItemList.anvilLarge, ItemList.steelBar, pickMattockHeadTemplateID, false, true, 0.0f, false, false,
                CreationCategories.TOOL_PARTS);
        CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_BLACKSMITHING,
                ItemList.anvilLarge, ItemList.adamantineBar, pickMattockHeadTemplateID, false, true, 0.0f, false, false,
                CreationCategories.TOOL_PARTS);
        CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_BLACKSMITHING,
                ItemList.anvilLarge, ItemList.seryllBar, pickMattockHeadTemplateID, false, true, 0.0f, false, false,
                CreationCategories.TOOL_PARTS);
        CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_BLACKSMITHING,
                ItemList.anvilLarge, ItemList.glimmerSteelBar, pickMattockHeadTemplateID, false, true, 0.0f, false, false,
                CreationCategories.TOOL_PARTS);
        CreationEntryCreator.createSimpleEntry(SkillList.SMITHING_BLACKSMITHING,
                ItemList.shaft, pickMattockHeadTemplateID, pickMattockTemplateID, true, true, 0.0f, false, false,
                CreationCategories.TOOLS);
    }

    private void addPickMattockTemplates(){
        pickMattockHeadTemplateID = IdFactory.getIdFor("jdbPickMattockHead", IdType.ITEMTEMPLATE);
        ItemTemplateBuilder pickMattockHeadTempBuilder = new ItemTemplateBuilder("jdbPickMattockHead");
        pickMattockHeadTempBuilder.name("pick-mattock head","pick-mattock head", "A tool head that is part pick and part adze.");
        pickMattockHeadTempBuilder.size(3);
        //pickMattockHeadTempBuilder.descriptions();
        pickMattockHeadTempBuilder.itemTypes(new short[]{ItemTypes.ITEM_TYPE_REPAIRABLE, ItemTypes.ITEM_TYPE_METAL});
        pickMattockHeadTempBuilder.imageNumber((short) 723);
        pickMattockHeadTempBuilder.behaviourType((short) 1);
        pickMattockHeadTempBuilder.combatDamage(0);
        pickMattockHeadTempBuilder.decayTime(3024000L);
        pickMattockHeadTempBuilder.dimensions(1, 5, 30);
        pickMattockHeadTempBuilder.primarySkill(-10);
        //pickMattockHeadTempBuilder.bodySpaces();
        pickMattockHeadTempBuilder.modelName("model.tool.pickaxe.blade.");
        pickMattockHeadTempBuilder.difficulty(15.0f);
        pickMattockHeadTempBuilder.weightGrams(1000);
        pickMattockHeadTempBuilder.material(ItemMaterials.MATERIAL_IRON);
        pickMattockHeadTempBuilder.value(100);
        pickMattockHeadTempBuilder.isTraded(true);
        //pickMattockHeadTempBuilder.armourType();
        try {
            pickMattockHeadTempBuilder.build();
        } catch (IOException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }

        pickMattockTemplateID = IdFactory.getIdFor("jdbPickMattock", IdType.ITEMTEMPLATE);
        ItemTemplateBuilder pickMattockTempBuilder = new ItemTemplateBuilder("jdbPickMattock");
        pickMattockTempBuilder.name("pick mattock","pick mattocks", "A multipurpose terraforming tool.");
        pickMattockTempBuilder.size(3);
        //pickMattockTempBuilder.descriptions();
        pickMattockTempBuilder.itemTypes(new short[]{ItemTypes.ITEM_TYPE_TOOL, ItemTypes.ITEM_TYPE_NAMED,
                ItemTypes.ITEM_TYPE_REPAIRABLE, ItemTypes.ITEM_TYPE_METAL, ItemTypes.ITEM_TYPE_WEAPON_SLASH});
        pickMattockTempBuilder.imageNumber((short) 743);
        pickMattockTempBuilder.behaviourType((short) 1);
        pickMattockTempBuilder.combatDamage(0);
        pickMattockTempBuilder.decayTime(9072000L);
        pickMattockTempBuilder.dimensions(1, 30, 70);
        pickMattockTempBuilder.primarySkill(-10);
        //pickMattockTempBuilder.bodySpaces();
        pickMattockTempBuilder.modelName("model.tool.pickaxe.");
        pickMattockTempBuilder.difficulty(20.0f);
        pickMattockTempBuilder.weightGrams(2000);
        pickMattockTempBuilder.material(ItemMaterials.MATERIAL_IRON);
        pickMattockTempBuilder.value(1000);
        pickMattockTempBuilder.isTraded(true);
        //pickMattockTempBuilder.armourType();
        try {
            pickMattockTempBuilder.build();
        } catch (IOException e){
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    static int getPickMattockTemplateID() {
        return pickMattockTemplateID;
    }
}
