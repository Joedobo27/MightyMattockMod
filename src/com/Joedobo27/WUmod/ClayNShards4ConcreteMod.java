package com.Joedobo27.WUmod;


import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.*;
import javassist.*;
import javassist.bytecode.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({"Convert2Lambda", "Convert2streamapi", "unused"})
public class ClayNShards4ConcreteMod implements  WurmServerMod, Initable, Configurable, ServerStartedListener, ItemTemplatesCreatedListener {

    private static boolean shardNClayToConcrete = false;
    private static short shardWeight = 20;
    private static short clayWeight = 5;
    private static ArrayList<Integer> concreteSpecs = new ArrayList<>(Arrays.asList(10, 10, 10, 3));
    private static boolean useCustomSlopeMax = false;
    private static double pvpSlopeMultiplier = 1;
    private static double pveSlopeMultiplier = 3;
    private static boolean useCustomDepthMax = false;
    private static double pvpDepthMultiplier = 1;
    private static double pveDepthMultiplier = 3;

    private static ClassPool pool;

    private static MethodInfo raiseRockLevelMInfo;
    private static CodeAttribute raiseRockLevelAttribute;
    private static CodeIterator raiseRockLevelIterator;



    private static final Logger logger;
    static {
        logger = Logger.getLogger(ClayNShards4ConcreteMod.class.getName());
        logger.setUseParentHandlers(false);
        for (Handler a : logger.getHandlers()) {
            logger.removeHandler(a);
        }
        logger.addHandler(aaaJoeCommon.joeFileHandler);
        logger.setLevel(Level.ALL);
    }

    @Override
    public void configure(Properties properties) {
        shardNClayToConcrete = Boolean.valueOf(properties.getProperty("shardNClayToConcrete", Boolean.toString(shardNClayToConcrete)));
        shardWeight = Short.valueOf(properties.getProperty("shardWeight", Short.toString(shardWeight)));
        clayWeight = Short.valueOf(properties.getProperty("clayWeight", Short.toString(clayWeight)));
        concreteSpecs.clear();
        for (String a :  properties.getProperty("concreteSpecs", concreteSpecs.toString().replaceAll("\\[|\\]", "")).split(",")){
            concreteSpecs.add(Integer.valueOf(a));
        }
        useCustomSlopeMax = Boolean.valueOf(properties.getProperty("useCustomSlopeMax", Boolean.toString(useCustomSlopeMax)));
        pvpSlopeMultiplier = Double.valueOf(properties.getProperty("pvpSlopeMultiplier", Double.toString(pvpSlopeMultiplier)));
        pveSlopeMultiplier = Double.valueOf(properties.getProperty("pveSlopeMultiplier", Double.toString(pveSlopeMultiplier)));
        useCustomDepthMax = Boolean.valueOf(properties.getProperty("useCustomDepthMax", Boolean.toString(useCustomDepthMax)));
        pvpDepthMultiplier = Double.valueOf(properties.getProperty("pvpDepthMultiplier", Double.toString(pvpDepthMultiplier)));
        pveDepthMultiplier = Double.valueOf(properties.getProperty("pveDepthMultiplier", Double.toString(pveDepthMultiplier)));
    }

    @Override
    public void onItemTemplatesCreated() {
        try {
            shardNClayToConcreteTemplate();
        } catch (NoSuchFieldException | IOException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void onServerStarted() {
        try {
            shardNClayToConcreteReflection();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void init() {
        pool = HookManager.getInstance().getClassPool();
        try {
            customSlopeDepthBytecode();
        } catch (NotFoundException | FileNotFoundException | BadBytecode | CannotCompileException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    private static void customSlopeDepthBytecode() throws NotFoundException, FileNotFoundException, BadBytecode, CannotCompileException {
        if (!useCustomSlopeMax && !useCustomDepthMax && !shardNClayToConcrete)
            return;
        if (shardNClayToConcrete) {
            if (!aaaJoeCommon.modifiedCheckSaneAmounts){
                aaaJoeCommon.jsCheckSaneAmountsExclusions();
            }
        }
        JDBByteCode find;
        JDBByteCode subFind;
        JDBByteCode replace;
        CtClass ctcCaveTileBehaviour = pool.get("com.wurmonline.server.behaviours.CaveTileBehaviour");
        ClassFile cfCaveTileBehaviour = ctcCaveTileBehaviour.getClassFile();
        ConstPool cpCaveTileBehaviour = cfCaveTileBehaviour.getConstPool();

        setRaiseRockLevel(cfCaveTileBehaviour,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z",
                "raiseRockLevel");
        if (useCustomDepthMax){
            //<editor-fold desc="Modify raiseRockLevel() in CaveTileBehaviour">
                /*
                //Change 732+ in raiseRockLevel of CaveTileBehaviour to allow custom max water depth for concrete.
                Inserting 34 gaps to add new code.
                replaced-
                    if (h >= -25) {
                with-
                    if (h >= -1 * (int)performer.getSkills().getSkillOrLearn(1008).getKnowledge(0.0d) *
                        (Servers.localServer.PVPSERVER ? pvpDepthMultiplier : pveDepthMultiplier)) {
                */
            //</editor-fold>

            raiseRockLevelIterator.insertGap(732,34);
            replace = new JDBByteCode();
            replace.setOpCodeStructure(new ArrayList<>(Arrays.asList(Opcode.ALOAD_0, Opcode.INVOKEVIRTUAL, Opcode.SIPUSH,
                    Opcode.INVOKEVIRTUAL, Opcode.DCONST_0, Opcode.INVOKEVIRTUAL, Opcode.GETSTATIC, Opcode.GETFIELD,
                    Opcode.IFEQ, Opcode.LDC2_W, Opcode.GOTO, Opcode.LDC2_W, Opcode.DMUL, Opcode.D2I, Opcode.INEG, Opcode.ILOAD,
                    Opcode.SWAP)));
            replace.setOperandStructure(new ArrayList<>(Arrays.asList("",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, "// Method com/wurmonline/server/creatures/Creature.getSkills:()Lcom/wurmonline/server/skills/Skills;"),
                    "03f0",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, "// Method com/wurmonline/server/skills/Skills.getSkillOrLearn:(I)Lcom/wurmonline/server/skills/Skill;"),
                    "",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, "// Method com/wurmonline/server/skills/Skill.getKnowledge:(D)D"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z"),
                    "0009",
                    String.format("%04X", cpCaveTileBehaviour.addDoubleInfo(pvpDepthMultiplier) & 0xffff),
                    "0006",
                    String.format("%04X", cpCaveTileBehaviour.addDoubleInfo(pveDepthMultiplier) & 0xffff),
                    "","","","08","")));
            replace.setOpcodeOperand();
            String replaceResult = JDBByteCode.byteCodeFindReplace(
                    "00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,1508,10e7,a1021e",
                    "00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,1508,10e7",
                    replace.getOpcodeOperand(),raiseRockLevelIterator,"raiseRockLevel");
            logger.log(Level.INFO, replaceResult);
            raiseRockLevelMInfo.rebuildStackMapIf6(pool,cfCaveTileBehaviour);
        }
        if (useCustomSlopeMax) {
            //<editor-fold desc="Modify raiseRockLevel() in CaveTileBehaviour">
                /*
                Change raiseRockLevel in CaveTileBehaviour to allow a configurable max slope when applying concrete.
                Lines 597 - 659
                removing -
                    final int slopeDown = Terraforming.getMaxSurfaceDownSlope(tilex, tiley);
                    if (slopeDown < (Servers.localServer.PVPSERVER ? -25 : -40)) {
                        performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " would only flow away.");
                        return true;
                    }
                Code to replace the removed section's function was inserted below it and consists of the insertAt string.
                */
            //</editor-fold>
            find = new JDBByteCode();
            find.setOpCodeStructure(new ArrayList<>(Arrays.asList(Opcode.ILOAD_2, Opcode.ILOAD_3, Opcode.INVOKESTATIC, Opcode.ISTORE,
                    Opcode.ILOAD, Opcode.GETSTATIC, Opcode.GETFIELD, Opcode.IFEQ, Opcode.BIPUSH, Opcode.GOTO, Opcode.BIPUSH,
                    Opcode.IF_ICMPGE, Opcode.ALOAD_0, Opcode.INVOKEVIRTUAL, Opcode.NEW, Opcode.DUP, Opcode.LDC_W, Opcode.INVOKESPECIAL,
                    Opcode.ALOAD_1, Opcode.INVOKEVIRTUAL, Opcode.INVOKEVIRTUAL, Opcode.LDC_W, Opcode.INVOKEVIRTUAL, Opcode.INVOKEVIRTUAL,
                    Opcode.INVOKEVIRTUAL, Opcode.ICONST_1, Opcode.IRETURN)));
            find.setOperandStructure(new ArrayList<>(Arrays.asList("", "",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "com/wurmonline/server/behaviours/Terraforming.getMaxSurfaceDownSlope:(II)I"),
                    "07", "07",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Fieldref, "com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Fieldref, "com/wurmonline/server/ServerEntry.PVPSERVER:Z"),
                    "0008", "e7", "0005", "d8", "0032", "",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "com/wurmonline/server/creatures/Creature.getCommunicator:()Lcom/wurmonline/server/creatures/Communicator;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Class, "java/lang/StringBuilder"),
                    "",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_String, "The "),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "java/lang/StringBuilder.<init>:(Ljava/lang/String;)V"),
                    "",
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "com/wurmonline/server/items/Item.getName:()Ljava/lang/String;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_String, " would only flow away."),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "java/lang/StringBuilder.toString:()Ljava/lang/String;"),
                    JDBByteCode.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "com/wurmonline/server/creatures/Communicator.sendNormalServerMessage:(Ljava/lang/String;)V"),
                    "", "")));
            find.setOpcodeOperand();
            String replaceResult = JDBByteCode.byteCodeFindReplace(find.getOpcodeOperand(), find.getOpcodeOperand(),
                    "00,00,000000,0000,0000,000000,000000,000000,0000,000000,0000,000000,00,000000,000000,00,000000,000000,00,000000,000000,000000,000000,000000,a70011,00,00",
                    raiseRockLevelIterator, "raiseRockLevel");
            logger.log(Level.INFO, replaceResult);
            raiseRockLevelMInfo.rebuildStackMapIf6(pool,cfCaveTileBehaviour);

            CtMethod ctmRaiseRockLevel = ctcCaveTileBehaviour.getMethod("raiseRockLevel",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z");
            String s = "";
            s += "com.wurmonline.server.skills.Skills skills2 = $1.getSkills();com.wurmonline.server.skills.Skill mining2 = null;";
            s += "try {mining2 = skills2.getSkill(1008);} catch (com.wurmonline.server.skills.NoSuchSkillException e) {mining2 = skills2.learn(1008, 1.0f);}";
            s += "int slopeDown1 = com.wurmonline.server.behaviours.Terraforming#getMaxSurfaceDownSlope($3, $4);";
            s += "int maxSlope = (int) (mining2.getKnowledge(0.0) * (com.wurmonline.server.Servers.localServer.PVPSERVER ? " + pvpSlopeMultiplier + " : " + pveSlopeMultiplier + "));";
            s += "if (Math.abs(slopeDown1) > maxSlope)";
            s += "{$1.getCommunicator().sendNormalServerMessage(\"The \" + source.getName() + \" would only flow away.\");return true;}";
            ctmRaiseRockLevel.insertAt(1269, s); // line 140 and before "if (performer.getLayer() < 0) {"
        }

    }

    private static void shardNClayToConcreteReflection() throws NoSuchFieldException, IllegalAccessException {
        if (!shardNClayToConcrete )
            return;
        // Add ignore item entries to largeMaterialRatioDifferentials field in CreationEntry.
        if (aaaJoeCommon.modifiedCheckSaneAmounts) {
            ArrayList<Integer> abc = ReflectionUtil.getPrivateField(CreationEntry.class, ReflectionUtil.getField(CreationEntry.class,
                    "largeMaterialRatioDifferentials"));
            ArrayList<Integer> b = new ArrayList<>(Arrays.asList(ItemList.rock, ItemList.clay, ItemList.concrete));
            for (int a : b) {
                if (!abc.contains(a))
                    abc.add(a);
            }
        }
        // Remove the Mortar + Lye entries from simpleEntries and Matrix.
        Map<Integer, List<CreationEntry>> simpleEntries = ReflectionUtil.getPrivateField(CreationMatrix.class,
                ReflectionUtil.getField(CreationMatrix.class, "simpleEntries"));
        Map<Integer, List<CreationEntry>> matrix = ReflectionUtil.getPrivateField(CreationMatrix.class,
                ReflectionUtil.getField(CreationMatrix.class, "matrix"));
        HashMap<Integer, List<CreationEntry>> toDeleteSimple = new HashMap<>();
        for (HashMap.Entry<Integer, List<CreationEntry>> simpleEntries1 : simpleEntries.entrySet()) {
            if (simpleEntries1.getKey() == ItemList.concrete) {
                toDeleteSimple.put(simpleEntries1.getKey(), simpleEntries1.getValue());
            }
        }
        for (HashMap.Entry<Integer, List<CreationEntry>> delete : toDeleteSimple.entrySet()) {
            simpleEntries.remove(delete.getKey(), delete.getValue());
        }
        List<CreationEntry> toDeleteMatrix = new ArrayList<>();
        for (HashMap.Entry<Integer, List<CreationEntry>> matrix1 : matrix.entrySet()) {
            if (matrix1.getKey() == ItemList.mortar) {
                for (CreationEntry entry : matrix1.getValue()) {
                    if (entry.getObjectCreated() == ItemList.concrete) {
                        toDeleteMatrix.add(entry);
                    }
                }
            }
        }
        for (CreationEntry delete : toDeleteMatrix) {
            matrix.get(ItemList.mortar).remove(delete);
        }

        // Add concrete back to the simple creation list using updated values.
        CreationEntry concrete = CreationEntryCreator.createSimpleEntry(SkillList.ALCHEMY_NATURAL,
                ItemList.rock, ItemList.clay, ItemList.concrete, true, true, 0.0f, false, false,
                CreationCategories.RESOURCES);
        concrete.setDepleteFromSource(shardWeight * 1000);
        concrete.setDepleteFromTarget(clayWeight * 1000);
        logger.log(Level.INFO, "Mortar + Lye removed and Concrete creation modded.");
    }

    private static void shardNClayToConcreteTemplate() throws NoSuchFieldException, IllegalAccessException, IOException{
        if (!shardNClayToConcrete)
            return;
        // Remove the existent concrete entry from item templates and add it back in with updated values.
        Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(ItemTemplateFactory.class,
                ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
        fieldTemplates.remove(782);
        ItemTemplateFactory.getInstance().createItemTemplate(782, 3, "concrete", "concrete", "excellent", "good", "ok", "poor",
                "Concrete, for instance used to raise cave floors.", new short[]{25, 146, 46, 112, 113, 158}, (short) 591, (short) 1,
                0, 18000L, concreteSpecs.get(0), concreteSpecs.get(1), concreteSpecs.get(2), -10, MiscConstants.EMPTY_BYTE_PRIMITIVE_ARRAY,
                "model.resource.concrete.", 25.0f, concreteSpecs.get(3) * 1000, (byte) 18, 10, false, -1);
        logger.log(Level.INFO, "Concrete default item modded.");
    }

    private static void setRaiseRockLevel(ClassFile cf, String desc, String name) {
        if (raiseRockLevelMInfo == null || raiseRockLevelIterator == null || raiseRockLevelAttribute == null) {
            for (List a : new List[]{cf.getMethods()}){
                for(Object b : a){
                    MethodInfo MInfo = (MethodInfo) b;
                    if (Objects.equals(MInfo.getDescriptor(), desc) && Objects.equals(MInfo.getName(), name)){
                        raiseRockLevelMInfo = MInfo;
                        break;
                    }
                }
            }
            if (raiseRockLevelMInfo == null){
                throw new NullPointerException();
            }
            raiseRockLevelAttribute = raiseRockLevelMInfo.getCodeAttribute();
            raiseRockLevelIterator = raiseRockLevelAttribute.iterator();
        }
    }

}
