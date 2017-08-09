import com.joedobo27.libs.ActionUtilities;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.shared.constants.ItemMaterials;
import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MightyMattockMod implements  WurmServerMod, Initable, Configurable, ServerStartedListener, ItemTemplatesCreatedListener {

    private static boolean useFixedClayElevation = false;
    private static boolean useCustomDepth = false;
    private static float pveDepthMultiplier = 3;
    private static float pvpDepthMultiplier = 1;
    private static boolean useCustomMaxSlope = false;
    private static float pveSlopeMultiplier = 3;
    private static float pvpSlopeMultiplier = 1;
    private static boolean useLevelAction = false;
    private static float minimumUnitLevelTime = 1.0f; // tenths of a second.
    private static int pickMattockHeadTemplateID;
    private static int pickMattockTemplateID;

    static final Logger logger = Logger.getLogger(MightyMattockMod.class.getName());
    static Random r = new Random();

    @Override
    public void configure(Properties properties) {
        useFixedClayElevation = Boolean.parseBoolean(properties.getProperty("useFixedClayElevation", Boolean.toString(useFixedClayElevation)));
        useCustomDepth = Boolean.parseBoolean(properties.getProperty("useCustomDepth", Boolean.toString(useCustomDepth)));
        pveDepthMultiplier = Float.parseFloat(properties.getProperty("pveDepthMultiplier", Float.toString(pveDepthMultiplier)));
        pvpDepthMultiplier = Float.parseFloat(properties.getProperty("pvpDepthMultiplier", Float.toString(pvpDepthMultiplier)));
        useCustomMaxSlope = Boolean.parseBoolean(properties.getProperty("useCustomMaxSlope", Boolean.toString(useCustomMaxSlope)));
        pveSlopeMultiplier = Float.parseFloat(properties.getProperty("pveSlopeMultiplier", Float.toString(pveSlopeMultiplier)));
        pvpSlopeMultiplier = Float.parseFloat(properties.getProperty("pvpSlopeMultiplier", Float.toString(pvpSlopeMultiplier)));

        useLevelAction = Boolean.parseBoolean(properties.getProperty("useLevelAction", Boolean.toString(useLevelAction)));
        minimumUnitLevelTime = Float.parseFloat(properties.getProperty("minimumUnitLevelTime", Float.toString(minimumUnitLevelTime)));
    }

    @Override
    public void init() {
        useFixedClayElevationBytecode();
    }

    @Override
    public void onItemTemplatesCreated() {
        addPickMattockTemplates();
    }

    @Override
    public void onServerStarted() {

        LevelAction levelAction = new LevelAction(Actions.LEVEL, Actions.actionEntrys[Actions.LEVEL]);
        ModActions.registerAction(levelAction);
        ActionUtilities.maxRangeReflect(Actions.actionEntrys[Actions.LEVEL], 6, logger);

        MineAction mineAction = new MineAction(Actions.MINE, Actions.actionEntrys[Actions.MINE]);
        ModActions.registerAction(mineAction);
        ActionUtilities.maxRangeReflect(Actions.actionEntrys[Actions.MINE], 6, logger);

        RaiseRockAction raiseRockAction = new RaiseRockAction(Actions.RAISE_GROUND, Actions.actionEntrys[Actions.RAISE_GROUND]);
        ModActions.registerAction(raiseRockAction);
        ActionUtilities.maxRangeReflect(Actions.actionEntrys[Actions.RAISE_GROUND], 6, logger);

        DigAction digAction = new DigAction(Actions.DIG, Actions.actionEntrys[Actions.DIG]);
        ModActions.registerAction(digAction);
        ActionUtilities.maxRangeReflect(Actions.actionEntrys[Actions.DIG], 6, logger);

        RaiseDirtAction raiseDirtAction = new RaiseDirtAction(Actions.RAISE_GROUND, Actions.actionEntrys[Actions.RAISE_GROUND]);
        ModActions.registerAction(raiseDirtAction);

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
        pickMattockTempBuilder.name("pick mattock","pick mattocks", "A multipurpose terraforming tool designed for dirt and rock.");
        pickMattockTempBuilder.size(3);
        //pickMattockTempBuilder.descriptions();
        pickMattockTempBuilder.itemTypes(new short[]{ItemTypes.ITEM_TYPE_TOOL, ItemTypes.ITEM_TYPE_NAMED, ItemTypes.ITEM_TYPE_REPAIRABLE, ItemTypes.ITEM_TYPE_METAL,
                ItemTypes.ITEM_TYPE_WEAPON_SLASH});
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

    private static void useFixedClayElevationBytecode() {
        int[] successes = new int[]{0};
        try {
            CtClass terraformingCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Terraforming");
            CtClass[] paramTypes = {
                    HookManager.getInstance().getClassPool().get("com/wurmonline/server/creatures/Creature"),
                    HookManager.getInstance().getClassPool().get("com/wurmonline/server/items/Item"),
                    CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.floatType, CtPrimitiveType.booleanType,
                    HookManager.getInstance().getClassPool().get("com/wurmonline/mesh/MeshIO")
            };
            CtMethod digCt = terraformingCt.getDeclaredMethod("dig", paramTypes);
            digCt.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getDigCount", methodCall.getMethodName())) {
                        logger.log(Level.FINE, "dig method,  edit call to " +
                                methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                        methodCall.replace("$_ = 99;");
                        successes[0] = 1;
                    }
                }
            });
        } catch (NotFoundException | CannotCompileException e){
            logger.info(e.getMessage());
        }
        evaluateChangesArray(successes, "useFixedClayElevation");
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static float getPveSlopeMultiplier() {
        return pveSlopeMultiplier;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static float getPvpSlopeMultiplier() {
        return pvpSlopeMultiplier;
    }

    public static float getMinimumUnitLevelTime() {
        return minimumUnitLevelTime;
    }

    public static int getPickMattockTemplateID() {
        return pickMattockTemplateID;
    }

    static boolean isMattock(Item source) {
        return source.getTemplateId() == getPickMattockTemplateID() || source.isWand();
    }

    private static void evaluateChangesArray(int[] ints, String option) {
        boolean changesSuccessful = Arrays.stream(ints).noneMatch(value -> value == 0);
        if (changesSuccessful) {
            logger.log(Level.INFO, option + " option changes SUCCESSFUL");
        } else {
            logger.log(Level.INFO, option + " option changes FAILURE");
            logger.log(Level.FINE, Arrays.toString(ints));
        }
    }
}
