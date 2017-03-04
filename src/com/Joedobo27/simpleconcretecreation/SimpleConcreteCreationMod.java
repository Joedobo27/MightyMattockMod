package com.joedobo27.simpleconcretecreation;


import com.joedobo27.common.Common;
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.behaviours.Terraforming;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.*;
import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.simpleconcretecreation.BytecodeTools.addConstantPoolReference;
import static com.joedobo27.simpleconcretecreation.BytecodeTools.findConstantPoolReference;
import static com.joedobo27.simpleconcretecreation.ClassPathAndMethodDescriptors.*;


public class SimpleConcreteCreationMod implements  WurmServerMod, Initable, Configurable, ServerStartedListener, ItemTemplatesCreatedListener {

    private static boolean makeSimpleConcrete = false;
    private static int shardWeight = 20;
    private static int clayWeight = 5;
    private static ArrayList<Integer> concreteSpecs = new ArrayList<>(Arrays.asList(10, 10, 10, 3));
    private static boolean useCustomConcreteSlopeMax = false;
    private static double pvpMaxConcreteSlopeMultiplier = 1;
    private static double pveMaxConcreteSlopeMultiplier = 3;
    private static boolean useCustomConcreteDepthMax = false;
    private static double pvpMaxConcreteDepthMultiplier = 1;
    private static double pveMaxConcreteDepthMultiplier = 3;
    private static boolean useFixedClayElevation = false;
    private static boolean useCustomMiningDepthMax = false;
    private static double pvpMaxMiningDepthMultiplier = 1;
    private static double pveMaxMiningDepthMultiplier = 3;

    private static ClassPool classPool;
    private static final Logger logger= Logger.getLogger(SimpleConcreteCreationMod.class.getName());


    @Override
    public void configure(Properties properties) {
        makeSimpleConcrete = Boolean.parseBoolean(properties.getProperty("makeSimpleConcrete", Boolean.toString(makeSimpleConcrete)));
        shardWeight = Integer.parseInt(properties.getProperty("shardWeight", Integer.toString(shardWeight)));
        clayWeight = Integer.parseInt(properties.getProperty("clayWeight", Integer.toString(clayWeight)));
        concreteSpecs.clear();
        for (String a :  properties.getProperty("concreteSpecs", concreteSpecs.toString().replaceAll("]\\[|", "")).split(",")){
            concreteSpecs.add(Integer.parseInt(a));
        }
        useCustomConcreteSlopeMax = Boolean.parseBoolean(properties.getProperty("useCustomConcreteSlopeMax", Boolean.toString(useCustomConcreteSlopeMax)));
        pvpMaxConcreteSlopeMultiplier = Double.parseDouble(properties.getProperty("pvpMaxConcreteSlopeMultiplier", Double.toString(pvpMaxConcreteSlopeMultiplier)));
        pveMaxConcreteSlopeMultiplier = Double.parseDouble(properties.getProperty("pveMaxConcreteSlopeMultiplier", Double.toString(pveMaxConcreteSlopeMultiplier)));
        useCustomConcreteDepthMax = Boolean.parseBoolean(properties.getProperty("useCustomConcreteDepthMax", Boolean.toString(useCustomConcreteDepthMax)));
        pvpMaxConcreteDepthMultiplier = Double.parseDouble(properties.getProperty("pvpMaxConcreteDepthMultiplier", Double.toString(pvpMaxConcreteDepthMultiplier)));
        pveMaxConcreteDepthMultiplier = Double.parseDouble(properties.getProperty("pveMaxConcreteDepthMultiplier", Double.toString(pveMaxConcreteDepthMultiplier)));
        useFixedClayElevation = Boolean.parseBoolean(properties.getProperty("useFixedClayElevation", Boolean.toString(useFixedClayElevation)));
        useCustomMiningDepthMax = Boolean.parseBoolean(properties.getProperty("useCustomMiningDepthMax", Boolean.toString(useCustomMiningDepthMax)));
        pvpMaxMiningDepthMultiplier = Double.parseDouble(properties.getProperty("pvpMaxMiningDepthMultiplier", Double.toString(pvpMaxMiningDepthMultiplier)));
        pveMaxMiningDepthMultiplier = Double.parseDouble(properties.getProperty("pveMaxMiningDepthMultiplier", Double.toString(pveMaxMiningDepthMultiplier)));
    }

    @Override
    public void onItemTemplatesCreated() {
        try {
            addConcreteItemTemplate();
        } catch (NoSuchFieldException | IOException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void onServerStarted() {
        try {
            addConcreteToCreationEntry();
            JAssistClassData.voidClazz();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void init() {
        try {
            classPool = HookManager.getInstance().getClassPool();

            if (useCustomConcreteDepthMax)
                raiseRockLevelBytecodeAlter1();
            if (useCustomConcreteSlopeMax)
                raiseRockLevelBytecodeAlter2();
            if (makeSimpleConcrete)
                checkSaneAmountsBytecodeAlter();
            if (useFixedClayElevation)
                useFixedClayElevationBytecode();
            if (useCustomMiningDepthMax)
                actionBytecodeAlter();

            //actionBytecodeAlter1();
        } catch (NotFoundException | BadBytecode | CannotCompileException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    private static void useFixedClayElevationBytecode() throws NotFoundException, CannotCompileException {
        int[] successes = new int[1];
        Arrays.fill(successes, 0);
        JAssistClassData terraforming = JAssistClassData.getClazz(TERRAFORMING_CLASS.getName());
        if (terraforming == null)
            terraforming = new JAssistClassData(TERRAFORMING_CLASS.getPath(), classPool);
        JAssistMethodData dig = new JAssistMethodData(terraforming, DIG_METHOD.getDescriptor(), DIG_METHOD.getName());
        dig.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("getDigCount", methodCall.getMethodName())){
                    logger.log(Level.FINE, "dig method,  edit call to " +
                            methodCall.getMethodName() + " at index " + methodCall.getLineNumber());
                    methodCall.replace("$_ = 99;");
                    successes[0] = 1;
                }
            }
        });
        evaluateChangesArray(successes, "useFixedClayElevation");
    }

    /**
     * In CreationEntry.checkSaneAmounts()
     * Byte code change: this.objectCreated != 73 to this.getObjectCreated() == 73
     * Do this because it's not possible to instrument on a field and have the replace function use a returned value
     * form a hook method.
     * Then, expression editor hook into getObjectCreated and replace returned with checkSaneAmountsExceptionsHook.
     * Using the hook instead of straight bytecode because it lets me use variable names form WU code.
     *
     * This change needs to be made because it blocks using very small things that can be combined.
     *
     * @return boolean type. Was the change successful?
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static boolean checkSaneAmountsBytecodeAlter() throws BadBytecode, NotFoundException, CannotCompileException {
        final boolean[] toReturn = {false};
        JAssistClassData creationEntry = JAssistClassData.getClazz(CREATION_ENTRY_CLASS.getName());
        if (creationEntry == null)
            creationEntry = new JAssistClassData(CREATION_ENTRY_CLASS.getPath(), classPool);
        JAssistMethodData checkSaneAmounts = new JAssistMethodData(creationEntry,
                CHECK_SANE_AMOUNTS_METHOD.getDescriptor(), CHECK_SANE_AMOUNTS_METHOD.getName());

        boolean isModifiedCheckSaneAmounts = true;
        byte[] findPoolResult;
        try {
            findConstantPoolReference(creationEntry.getConstPool(),
                    "// Method com/joedobo27/common/Common.checkSaneAmountsExceptionsHook:(III)I");
        } catch (UnsupportedOperationException e) {
            isModifiedCheckSaneAmounts = false;
        }
        if (isModifiedCheckSaneAmounts)
            toReturn[0] = true;
        if (!isModifiedCheckSaneAmounts) {
            Bytecode find = new Bytecode(creationEntry.getConstPool());
            find.addOpcode(Opcode.ALOAD_0);
            find.addOpcode(Opcode.GETFIELD);
            findPoolResult = findConstantPoolReference(creationEntry.getConstPool(), "// Field objectCreated:I");
            find.add(findPoolResult[0], findPoolResult[1]);
            find.addOpcode(Opcode.BIPUSH);
            find.add(73);


            Bytecode replace = new Bytecode(creationEntry.getConstPool());
            replace.addOpcode(Opcode.ALOAD_0);
            replace.addOpcode(Opcode.INVOKEVIRTUAL);
            findPoolResult = addConstantPoolReference(creationEntry.getConstPool(), "// Method getObjectCreated:()I");
            replace.add(findPoolResult[0], findPoolResult[1]);
            replace.addOpcode(Opcode.BIPUSH);
            replace.add(73);

            CodeReplacer codeReplacer = new CodeReplacer(checkSaneAmounts.getCodeAttribute());
            try {
                codeReplacer.replaceCode(find.get(), replace.get());
            } catch (NotFoundException e){
                toReturn[0] = false;
            }

            checkSaneAmounts.getCtMethod().instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall methodCall) throws CannotCompileException {
                    if (Objects.equals("getObjectCreated", methodCall.getMethodName())) {
                        methodCall.replace("$_ = com.joedobo27.common.Common.checkSaneAmountsExceptionsHook( $0.getObjectCreated(), sourceMax, targetMax);");
                        logger.log(Level.FINE, "CreationEntry.class, checkSaneAmounts(), installed hook at line: " + methodCall.getLineNumber());
                        toReturn[0] = true;
                    }
                }
            });
        }
        return toReturn[0];
    }

    @SuppressWarnings("unused")
    public static boolean isWorkingBeyondMaxDepthHook(int h, Creature performer, Action action){
        return h < getMaxDepthHook(performer, action);
    }

    @SuppressWarnings("WeakerAccess")
    public static int getMaxDepthHook(Creature performer, Action action) {
        int buildDeeperThanMine = 0;
        if (action.getNumber() == Actions.MINE )
            buildDeeperThanMine = 0;
        if (action.getNumber() == Actions.RAISE_GROUND)
            buildDeeperThanMine = -2;
        return  (int)(buildDeeperThanMine + (-1 * performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge(0.0d) *
                (Servers.localServer.PVPSERVER ? pvpMaxConcreteDepthMultiplier : pveMaxConcreteDepthMultiplier)));
    }

    @SuppressWarnings("unused")
    public static boolean isTooSteepHook(Creature performer, int tileX, int tileY){
        com.wurmonline.server.skills.Skill miningSkill = performer.getSkills().getSkillOrLearn(SkillList.MINING);
        int slopeDown = Terraforming.getMaxSurfaceDownSlope(tileX, tileY);
        int maxSlope = (int) (miningSkill.getKnowledge(0.0) *
                (com.wurmonline.server.Servers.localServer.PVPSERVER ? pvpMaxConcreteSlopeMultiplier : pveMaxConcreteSlopeMultiplier));
        return (Math.abs(slopeDown) > maxSlope);
    }

    private static void actionBytecodeAlter1() throws NotFoundException, CannotCompileException {

        JAssistClassData tileRockBehaviour = JAssistClassData.getClazz(TILE_ROCK_BEHAVIOUR_CLASS.getName());
        if (tileRockBehaviour == null)
            tileRockBehaviour = new JAssistClassData(TILE_ROCK_BEHAVIOUR_CLASS.getPath(), classPool);
        JAssistMethodData action = new JAssistMethodData(tileRockBehaviour, ACTION_METHOD.getDescriptor(), ACTION_METHOD.getName());
        action.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("sendNormalServerMessage", methodCall.getMethodName())) {
                    String byteCodeLine = Integer.toString(methodCall.indexOfBytecode());
                    methodCall.replace("$1 = \"error tileRockBehaviour.action\" + " + byteCodeLine + ".; $proceed($$);");
                }
            }
        });

        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz("CaveTileBehaviour");
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.CaveTileBehaviour", classPool);
        JAssistMethodData handle_MINE = new JAssistMethodData(caveTileBehaviour,
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IISFI)Z",
                "handle_MINE");
        handle_MINE.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("sendNormalServerMessage", methodCall.getMethodName())) {
                    String byteCodeLine = Integer.toString(methodCall.indexOfBytecode());
                    methodCall.replace("$1 = \"error caveTileBehaviour.handle_MINE\" + " + byteCodeLine + ".; $proceed($$);");
                }
            }
        });

    }

    /**
     * Change 1802+ in action of tileRockBehaviour to allow custom max water depth for surface mine.
     * was-
     *      if (h >= -25) {...}
     * becomes-
     *      boolean isWorkingBeyondMaxDepth = com.joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook(h, performer, action);
     *      if (isWorkingBeyondMaxDepth) {...}
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void actionBytecodeAlter() throws NotFoundException, CannotCompileException, BadBytecode {
        int[] successes = new int[2];
        Arrays.fill(successes, 0);
        JAssistClassData tileRockBehaviour = JAssistClassData.getClazz(TILE_ROCK_BEHAVIOUR_CLASS.getName());
        if (tileRockBehaviour == null)
            tileRockBehaviour = new JAssistClassData(TILE_ROCK_BEHAVIOUR_CLASS.getPath(), classPool);
        JAssistMethodData action = new JAssistMethodData(tileRockBehaviour, ACTION_METHOD.getDescriptor(), ACTION_METHOD.getName());

        byte[] findPoolResult;
        Bytecode find = new Bytecode(tileRockBehaviour.getConstPool());
        find.addOpcode(Opcode.ILOAD);
        find.add(14);
        find.addOpcode(Opcode.BIPUSH);
        find.add(-25);
        find.addOpcode(Opcode.IF_ICMPLE); // 1806: if_icmple     3824; difference of 2018 or 0x07E2
        find.add(7, 226);

        //com.joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook(h, performer, act);
        int hSlot = BytecodeTools.findSlotInLocalVariableTable(action, "h");
        int performerSlot = BytecodeTools.findSlotInLocalVariableTable(action, "performer");
        int actSlot = BytecodeTools.findSlotInLocalVariableTable(action, "act");
        if (hSlot == -1 || performerSlot == -1 || actSlot == -1)
            successes[0] = 0;
        else
            successes[0] = 1;

        Bytecode replace = new Bytecode(tileRockBehaviour.getConstPool());
        replace.addOpcode(Opcode.ILOAD);
        replace.add(hSlot);
        replace.addOpcode(Opcode.ALOAD);
        replace.add(performerSlot);
        replace.addOpcode(Opcode.ALOAD);
        replace.add(actSlot);
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook:" +
                        "(ILcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/behaviours/Action;)Z");
        replace.add(findPoolResult[0], findPoolResult[1]);

        replace.addOpcode(Opcode.IFNE);
        replace.add(7, 226); // Jump forward 2018 or 0x07E2 lines.

        CodeReplacer codeReplacer = new CodeReplacer(action.getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            successes[1] = 1;
            action.getMethodInfo().rebuildStackMapIf6(classPool, tileRockBehaviour.getClassFile());
        } catch (NotFoundException e){
            successes[1] = 0;
        }
        finally {
            evaluateChangesArray(successes, "useCustomMiningDepthMax");
        }
    }

    /**
     * Change 766+ in raiseRockLevel of CaveTileBehaviour to allow custom max water depth for concrete.
     * was-
     *      if (h >= -25) {...}
     * becomes-
     *      boolean isWorkingBeyondMaxDepth = com.joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook(h, performer, action);
     *      if (isWorkingBeyondMaxDepth) {...}
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void raiseRockLevelBytecodeAlter1() throws BadBytecode, NotFoundException, CannotCompileException {
        int[] successes = new int[2];
        Arrays.fill(successes, 0);

        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz(CAVE_TILE_BEHAVIOUR_CLASS.getName());
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData(CAVE_TILE_BEHAVIOUR_CLASS.getPath(), classPool);

        JAssistMethodData raiseRockLevel = new JAssistMethodData(caveTileBehaviour,
                RAISE_ROCK_LEVEL_METHOD.getDescriptor(), RAISE_ROCK_LEVEL_METHOD.getName());

        byte[] findPoolResult;
        Bytecode find = new Bytecode(caveTileBehaviour.getConstPool());
        find.addOpcode(Opcode.ILOAD);
        find.add(8);
        find.addOpcode(Opcode.BIPUSH);
        find.add(-25);
        find.addOpcode(Opcode.IF_ICMPGE); // 853: if_icmpge     866; difference of 13.
        find.add(0, 13);

        //com.joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook(h, performer, act);
        int hSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevel, "h");
        int performerSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevel, "performer");
        int actSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevel, "act");
        if (hSlot == -1 || performerSlot == -1 || actSlot == -1)
            successes[0] = 0;
        else
            successes[0] = 1;

        Bytecode replace = new Bytecode(caveTileBehaviour.getConstPool());
        replace.addOpcode(Opcode.ILOAD);
        replace.add(hSlot);
        replace.addOpcode(Opcode.ALOAD);
        replace.add(performerSlot);
        replace.addOpcode(Opcode.ALOAD);
        replace.add(actSlot);
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(caveTileBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook:" +
                        "(ILcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/behaviours/Action;)Z");
        replace.add(findPoolResult[0], findPoolResult[1]);

        replace.addOpcode(Opcode.IFEQ);
        replace.add(0, 13);

        CodeReplacer codeReplacer = new CodeReplacer(raiseRockLevel.getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            successes[1] = 1;
            //raiseRockLevel.getMethodInfo().rebuildStackMapIf6(classPool, caveTileBehaviour.getClassFile());
        } catch (NotFoundException e){
            successes[1] = 0;
        }
        evaluateChangesArray(successes, "useCustomConcreteDepthMax");
    }

    /**
     * Change raiseRockLevel in CaveTileBehaviour to allow a configurable max slope when applying concrete.
     * bytecode lines 630+
     *
     * was-
     *      final int maxSlope = Servers.localServer.PVPSERVER ? -25 : -40;
     * becomes-
     *      int maxSlope = com.joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.getMaxDepthHook(Creature performer, Action act)
     *
     * was-
     *      if (slopeDown < -300) {...}
     * becomes-
     *      if (slopeDown < maxSlope) {...}
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void raiseRockLevelBytecodeAlter2() throws BadBytecode, NotFoundException, CannotCompileException {
        int[] successes = new int[4];
        Arrays.fill(successes, 0);

        JAssistClassData caveTileBehaviour = JAssistClassData.getClazz(CAVE_TILE_BEHAVIOUR_CLASS.getName());
        if (caveTileBehaviour == null)
            caveTileBehaviour = new JAssistClassData(CAVE_TILE_BEHAVIOUR_CLASS.getPath(), classPool);
        JAssistMethodData raiseRockLevel = new JAssistMethodData(caveTileBehaviour,
                RAISE_ROCK_LEVEL_METHOD.getDescriptor(), RAISE_ROCK_LEVEL_METHOD.getName());

        byte[] findPoolResult;
        Bytecode find = new Bytecode(caveTileBehaviour.getConstPool());
        find.addOpcode(Opcode.GETSTATIC);
        findPoolResult = findConstantPoolReference(caveTileBehaviour.getConstPool(),
                "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.GETFIELD);
        findPoolResult = findConstantPoolReference(caveTileBehaviour.getConstPool(),
                "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.IFEQ); //  636: ifeq          644;  difference of 8
        find.add(0,8);
        find.addOpcode(Opcode.BIPUSH);
        find.add(-25);
        find.addOpcode(Opcode.GOTO); // 641: goto          646; difference of 5
        find.add(0, 5);
        find.addOpcode(Opcode.BIPUSH);
        find.add(-40);
        find.addOpcode(Opcode.ISTORE);
        find.add(8);

        //com.joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.getMaxDepthHook(Creature performer, Action act)
        int performerSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevel, "performer");
        int actSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevel, "act");
        if (performerSlot == -1 || actSlot == -1)
            successes[0] = 0;
        else
            successes[0] = 1;

        Bytecode replace = new Bytecode(caveTileBehaviour.getConstPool());
        replace.addOpcode(Opcode.ALOAD);
        replace.add(performerSlot);
        replace.addOpcode(Opcode.ALOAD);
        replace.add(actSlot);
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(caveTileBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.getMaxDepthHook:" +
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/behaviours/Action;)I");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.ISTORE);
        replace.add(8);

        CodeReplacer codeReplacer = new CodeReplacer(raiseRockLevel.getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            successes[1] = 1;
            //raiseRockLevel.getMethodInfo().rebuildStackMapIf6(classPool, caveTileBehaviour.getClassFile());
        } catch (NotFoundException e){
            successes[1] = 0;
        }


        Bytecode find1 = new Bytecode(caveTileBehaviour.getConstPool());
        find1.addOpcode(Opcode.ILOAD);
        find1.add(7);
        find1.addOpcode(Opcode.SIPUSH);
        find1.add(254, 212); // -300 is 0xFED4
        find1.addOpcode(Opcode.IF_ICMPGE); // 671: if_icmpge     762; difference of 91
        find1.add(0, 91);

        int maxSlopeSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevel, "maxSlope");
        if (maxSlopeSlot == -1)
            successes[2] = 0;
        else
            successes[2] = 1;

        Bytecode replace1 = new Bytecode(caveTileBehaviour.getConstPool());
        replace1.addOpcode(Opcode.ILOAD);
        replace1.add(7);
        replace1.addOpcode(Opcode.ILOAD);
        replace1.add(maxSlopeSlot);
        replace1.addOpcode(Opcode.IF_ICMPGE); // replace code is one shorter then find so a nop will be added to end, update branch value.
        replace1.add(0, 92);

        CodeReplacer codeReplacer1 = new CodeReplacer(raiseRockLevel.getCodeAttribute());
        try {
            codeReplacer1.replaceCode(find1.get(), replace1.get());
            successes[3] = 1;
            raiseRockLevel.getMethodInfo().rebuildStackMapIf6(classPool, caveTileBehaviour.getClassFile());
        } catch (NotFoundException e){
            successes[3] = 0;
        }
        evaluateChangesArray(successes, "useCustomConcreteSlopeMax");
    }

    private static void addConcreteToCreationEntry() throws NoSuchFieldException, IllegalAccessException {
        if (!makeSimpleConcrete)
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

    private static void addConcreteItemTemplate() throws NoSuchFieldException, IllegalAccessException, IOException{
        if (!makeSimpleConcrete)
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
