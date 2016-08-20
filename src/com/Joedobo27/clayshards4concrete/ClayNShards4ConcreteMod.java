package com.Joedobo27.clayshards4concrete;


import com.Joedobo27.common.Common;
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.*;
import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static com.Joedobo27.clayshards4concrete.BytecodeTools.addConstantPoolReference;
import static com.Joedobo27.clayshards4concrete.BytecodeTools.findConstantPoolReference;
import static com.Joedobo27.clayshards4concrete.BytecodeTools.findReplaceCodeIterator;

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
    private static JAssistClassData caveTileBehaviour;
    private static JAssistMethodData raiseRockLevel;

    private static MethodInfo raiseRockLevelMInfo;
    private static CodeAttribute raiseRockLevelAttribute;
    private static CodeIterator raiseRockLevelIterator;

    private static final Logger logger= Logger.getLogger(ClayNShards4ConcreteMod.class.getName());


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
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", pool);
            raiseRockLevel = new JAssistMethodData(caveTileBehaviour,
                    "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z",
                    "raiseRockLevel");

            customSlopeDepthBytecode();
        } catch (NotFoundException | FileNotFoundException | BadBytecode | CannotCompileException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    public static boolean isWorkingBeyondMaxDepthHook(int h, Creature performer){
        return h >= -1 * (int)performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge(0.0d) *
                (Servers.localServer.PVPSERVER ? pvpDepthMultiplier : pveDepthMultiplier);
    }

    public static boolean isTooSteepHook(Creature performer, int tileX, int tileY){
        com.wurmonline.server.skills.Skill miningSkill = performer.getSkills().getSkillOrLearn(SkillList.MINING);
        int slopeDown = Terraforming.getMaxSurfaceDownSlope(tileX, tileY);
        int maxSlope = (int) (miningSkill.getKnowledge(0.0) * (com.wurmonline.server.Servers.localServer.PVPSERVER ? pvpSlopeMultiplier : pveSlopeMultiplier));
        return (Math.abs(slopeDown) > maxSlope);
    }

    private static void customSlopeDepthBytecode() throws NotFoundException, FileNotFoundException, BadBytecode, CannotCompileException {
        if (!useCustomSlopeMax && !useCustomDepthMax && !shardNClayToConcrete)
            return;

        int[] shardNClayToConcreteSuccesses = new int[2];
        Arrays.fill(shardNClayToConcreteSuccesses, 0);

        int[] useCustomDepthMaxSuccesses = new int[2];
        Arrays.fill(useCustomDepthMaxSuccesses, 0);

        int[] useCustomSlopeMaxSuccesses = new int[2];
        Arrays.fill(useCustomSlopeMaxSuccesses, 0);

        if (shardNClayToConcrete) {
            // In CreationEntry.checkSaneAmounts()
            // Byte code change: this.objectCreated != 73 to this.getObjectCreated() == 73
            // Do this because it's not possible to instrument on a field and have the replace function use a returned value from a hook method.
            JAssistClassData creationEntry = new JAssistClassData("com.wurmonline.server.items.CreationEntry", pool);
            JAssistMethodData checkSaneAmounts = new JAssistMethodData(creationEntry,
                    "(Lcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/ItemTemplate;Lcom/wurmonline/server/creatures/Creature;Z)V",
                    "checkSaneAmounts");

            boolean isModifiedCheckSaneAmounts = true;
            byte[] findPoolResult;
            try {
                findConstantPoolReference(creationEntry.getConstPool(),
                        "// Method com/Joedobo27/common/Common.checkSaneAmountsExceptionsHook:(III)I");
            } catch (UnsupportedOperationException e){
                isModifiedCheckSaneAmounts = false;
            }

            if (isModifiedCheckSaneAmounts)
                Arrays.fill(shardNClayToConcreteSuccesses, 1);
            if (!isModifiedCheckSaneAmounts) {
                Bytecode find = new Bytecode(creationEntry.getConstPool());
                find.addOpcode(Opcode.ALOAD_0);
                find.addOpcode(Opcode.GETFIELD);
                findPoolResult = findConstantPoolReference(creationEntry.getConstPool(), "// Field objectCreated:I");
                find.add(findPoolResult[0], findPoolResult[1]);
                find.addOpcode(Opcode.BIPUSH);
                find.add(73);
                logger.log(Level.INFO, Arrays.toString(find.get()));

                Bytecode replace = new Bytecode(creationEntry.getConstPool());
                replace.addOpcode(Opcode.ALOAD_0);
                replace.addOpcode(Opcode.INVOKEVIRTUAL);
                findPoolResult = addConstantPoolReference(creationEntry.getConstPool(), "// Method getObjectCreated:()I");
                replace.add(findPoolResult[0], findPoolResult[1]);
                replace.addOpcode(Opcode.BIPUSH);
                replace.add(73);
                logger.log(Level.INFO, Arrays.toString(replace.get()));

                boolean replaceResult = findReplaceCodeIterator(checkSaneAmounts.getCodeIterator(), find, replace);
                shardNClayToConcreteSuccesses[0] = replaceResult ? 1 : 0;
                logger.log(Level.FINE, "checkSaneAmounts find and replace: " + Boolean.toString(replaceResult));

                checkSaneAmounts.getCtMethod().instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall methodCall) throws CannotCompileException {
                        if (Objects.equals("getObjectCreated", methodCall.getMethodName())) {
                            methodCall.replace("$_ = com.Joedobo27.common.Common.checkSaneAmountsExceptionsHook( $0.getObjectCreated(), sourceMax, targetMax);");
                            logger.log(Level.FINE, "CreationEntry.class, checkSaneAmounts(), installed hook at line: " + methodCall.getLineNumber());
                            shardNClayToConcreteSuccesses[1] = 1;
                        }
                    }
                });
            }

            BytecodeTools.byteCodePrint("C:\\Program Files (x86)\\Steam\\SteamApps\\common\\Wurm Unlimited Dedicated Server\\byte code prints\\checkSaneAmounts bytecode.txt",
                    checkSaneAmounts.getCodeIterator());
            creationEntry.constantPoolPrint("C:\\Program Files (x86)\\Steam\\SteamApps\\common\\Wurm Unlimited Dedicated Server\\byte code prints\\creationEntry CP.txt");

            boolean changesSuccessful = !Arrays.stream(shardNClayToConcreteSuccesses).anyMatch(value -> value == 0);
            if (changesSuccessful) {
                logger.log(Level.INFO, "shardNClayToConcrete option changes SUCCESSFUL");
            } else {
                logger.log(Level.INFO, "shardNClayToConcrete option changes FAILURE");
                logger.log(Level.FINE, "shardNClayToConcreteSuccesses: " + Arrays.toString(shardNClayToConcreteSuccesses));
            }
        }
        if (useCustomDepthMax) {
            //<editor-fold desc="Modify raiseRockLevel() in CaveTileBehaviour">
                /*
                //Change 766+ in raiseRockLevel of CaveTileBehaviour to allow custom max water depth for concrete.
                was-
                    if (h >= -25) {
                becomes-
                    boolean isWorkingBeyondMaxDepth = dummy(int h, Creature performer);
                    if (isWorkingBeyondMaxDepth) {...}
                Note- the method "dummy()" is just a place holder so the mod can instrument an expression editor on it and do the actual boolean work.
                */
            //</editor-fold>

            CtMethod dummy = CtNewMethod.make("private static boolean dummy(){return true;}", caveTileBehaviour.getCtClass());
            caveTileBehaviour.getCtClass().addMethod(dummy);

            //raiseRockLevel.getCodeIterator().insert(766, new byte[]{0, 0, 0});

            byte[] findPoolResult;
            Bytecode find = new Bytecode(caveTileBehaviour.getConstPool());
            find.addOpcode(Opcode.ILOAD);
            find.add(8);
            find.addOpcode(Opcode.BIPUSH);
            find.add(231);
            find.addOpcode(Opcode.IF_ICMPLT);
            find.add(2, 29); // Jump forward 541 (0x21D) lines.

            Bytecode replace = new Bytecode(caveTileBehaviour.getConstPool());
            replace.addOpcode(Opcode.INVOKESTATIC);
            findPoolResult = addConstantPoolReference(caveTileBehaviour.getConstPool(), "// Method com/wurmonline/server/behaviours/CaveTileBehaviour.dummy:()Z");
            replace.add(findPoolResult[0], findPoolResult[1]);
            replace.addOpcode(Opcode.NOP);
            replace.addOpcode(Opcode.IFEQ);
            replace.add(2, 29); // Jump forward 541 (0x21D) lines.

            boolean replaceResult = findReplaceCodeIterator(raiseRockLevel.getCodeIterator(), find, replace);
            useCustomDepthMaxSuccesses[0] = replaceResult ? 1 : 0;
            logger.log(Level.FINE, "raiseRockLevel find and replace:" + Boolean.toString(replaceResult));
            //BytecodeTools.byteCodePrint("C:\\Program Files (x86)\\Steam\\SteamApps\\common\\Wurm Unlimited Dedicated Server\\byte code prints\\raiseRockLevel bytecode.txt", raiseRockLevel.getCodeIterator());


        }
        if (useCustomSlopeMax) {

            //<editor-fold desc="Modify raiseRockLevel() in CaveTileBehaviour">
                /*
                Change raiseRockLevel in CaveTileBehaviour to allow a configurable max slope when applying concrete.
                623 - 688
                was -
                    final int slopeDown = Terraforming.getMaxSurfaceDownSlope(tilex, tiley);
                    if (slopeDown < (Servers.localServer.PVPSERVER ? -25 : -40)) {
                        performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " would only flow away.");
                        return true;
                    }
                becomes -
                   boolean isTooSteep = dummy2(int h, Creature performer);
                    if (isTooSteep) {...}
                Note- the method "dummy2()" is just a place holder so the mod can instrument an expression editor on it and do the actual boolean work.
                */
            //</editor-fold>
            CtMethod dummy2 = CtNewMethod.make("private static boolean dummy2(){return true;}", caveTileBehaviour.getCtClass());
            caveTileBehaviour.getCtClass().addMethod(dummy2);

            byte[] findPoolResult;
            Bytecode find = new Bytecode(caveTileBehaviour.getConstPool());
            find.addOpcode(Opcode.ILOAD_2);
            find.addOpcode(Opcode.ILOAD_3);
            find.addOpcode(Opcode.INVOKESTATIC);
            findPoolResult = findConstantPoolReference(caveTileBehaviour.getConstPool(),
                    "// Method com/wurmonline/server/behaviours/Terraforming.getMaxSurfaceDownSlope:(II)I");
            find.add(findPoolResult[0], findPoolResult[1]);
            find.addOpcode(Opcode.ISTORE);
            find.add(7);
            find.addOpcode(Opcode.ILOAD);
            find.add(7);
            find.addOpcode(Opcode.GETSTATIC);
            findPoolResult = findConstantPoolReference(caveTileBehaviour.getConstPool(),
                    "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;");
            find.add(findPoolResult[0], findPoolResult[1]);
            find.addOpcode(Opcode.GETFIELD);
            findPoolResult = findConstantPoolReference(caveTileBehaviour.getConstPool(),
                    "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z");
            find.add(findPoolResult[0], findPoolResult[1]);
            find.addOpcode(Opcode.IFEQ);
            find.add(0,8);
            find.addOpcode(Opcode.BIPUSH);
            find.add(-25);
            find.addOpcode(Opcode.GOTO);
            find.add(0, 5);
            find.addOpcode(Opcode.BIPUSH);
            find.add(-40);
            find.addOpcode(Opcode.IF_ICMPGE);
            find.add(0,41); // Jump forward 41 (0x029) lines.

            Bytecode replace = new Bytecode(caveTileBehaviour.getConstPool());
            replace.addOpcode(Opcode.INVOKESTATIC);
            findPoolResult = addConstantPoolReference(caveTileBehaviour.getConstPool(),
                    "// Method com/wurmonline/server/behaviours/CaveTileBehaviour.dummy2:()Z");
            replace.add(findPoolResult[0], findPoolResult[1]);
            IntStream.range(1,23).forEach(v -> replace.addOpcode(Opcode.NOP)); // add 22 NOP instructions.
            replace.addOpcode(Opcode.IFEQ);
            replace.add(0,41); // Jump forward 41 (0x029) lines.

            boolean findResult = findReplaceCodeIterator(raiseRockLevel.getCodeIterator(), find, replace);
            useCustomSlopeMaxSuccesses[0] = findResult ? 1 : 0;
            logger.log(Level.FINE, "raiseRockLevel find and replace:" + Boolean.toString(findResult));
        }
        raiseRockLevel.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("dummy", methodCall.getMethodName())) {
                    methodCall.replace("$_ = com.Joedobo27.clayshards4concrete.ClayNShards4ConcreteMod.isWorkingBeyondMaxDepthHook(h, performer);");
                    useCustomDepthMaxSuccesses[1] = 1;
                    logger.log(Level.FINE, "Added isWorkingBeyondMaxDepthHook:" + methodCall.getLineNumber());
                } else if (Objects.equals("dummy2", methodCall.getMethodName())) {
                    methodCall.replace("$_ = com.Joedobo27.clayshards4concrete.ClayNShards4ConcreteMod.isTooSteepHook(performer, tilex, tiley);");
                    useCustomSlopeMaxSuccesses[1] = 1;
                    logger.log(Level.FINE, "Added isTooSteepHook:" + methodCall.getLineNumber());
                }
            }
        });

        boolean bool = !Arrays.stream(useCustomDepthMaxSuccesses).anyMatch(value -> value == 0);
        if (useCustomDepthMax) {
            if (bool) {
                logger.log(Level.INFO, "useCustomDepthMax option changes SUCCESSFUL");
            } else {
                logger.log(Level.INFO, "useCustomDepthMax option changes FAILURE");
                logger.log(Level.FINE, "useCustomDepthMaxSuccesses: " + Arrays.toString(useCustomDepthMaxSuccesses));
            }
        }

        if (useCustomSlopeMax) {
            bool = !Arrays.stream(useCustomSlopeMaxSuccesses).anyMatch(value -> value == 0);
            if (bool) {
                logger.log(Level.INFO, "useCustomSlopeMax option changes SUCCESSFUL");
            } else {
                logger.log(Level.INFO, "useCustomSlopeMax option changes FAILURE");
                logger.log(Level.FINE, "useCustomSlopeMaxSuccesses: " + Arrays.toString(useCustomSlopeMaxSuccesses));
            }
        }
    }

    private static void shardNClayToConcreteReflection() throws NoSuchFieldException, IllegalAccessException {
        if (!shardNClayToConcrete )
            return;
        int[] exceptions = {ItemList.clay, ItemList.rock, ItemList.lye};
        Common.addExceptions(exceptions);

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
}
