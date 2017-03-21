package com.joedobo27.simpleconcretecreation;


import com.wurmonline.mesh.CaveTile;
import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Server;
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


import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static com.joedobo27.simpleconcretecreation.BytecodeTools.addConstantPoolReference;
import static com.joedobo27.simpleconcretecreation.BytecodeTools.findConstantPoolReference;
import static com.joedobo27.simpleconcretecreation.ClassPathAndMethodDescriptors.*;


public class SimpleConcreteCreationMod implements  WurmServerMod, Initable, Configurable, ServerStartedListener {

    private static boolean makeSimpleConcrete = false;
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
    private static boolean useCustomMiningSlopeMax = false;
    private static double pvpMaxMiningSlopeMultiplier = 1;
    private static double pveMaxMiningSlopeMultiplier = 3;
    private static final String[] STEAM_VERSION = new String[]{"1.3.1.3"};
    private static boolean versionCompliant = false;

    private static ClassPool classPool;
    private static final Logger logger= Logger.getLogger(SimpleConcreteCreationMod.class.getName());


    @Override
    public void configure(Properties properties) {


        makeSimpleConcrete = Boolean.parseBoolean(properties.getProperty("makeSimpleConcrete", Boolean.toString(makeSimpleConcrete)));
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
        useCustomMiningSlopeMax = Boolean.parseBoolean(properties.getProperty("useCustomMiningSlopeMax", Boolean.toString(useCustomMiningSlopeMax)));
        pvpMaxMiningSlopeMultiplier = Double.parseDouble(properties.getProperty("pvpMaxMiningSlopeMultiplier", Double.toString(pvpMaxMiningSlopeMultiplier)));
        pveMaxMiningSlopeMultiplier = Double.parseDouble(properties.getProperty("pveMaxMiningSlopeMultiplier", Double.toString(pveMaxMiningSlopeMultiplier)));

        if (Arrays.stream(STEAM_VERSION)
                .filter(s -> Objects.equals(s, properties.getProperty("steamVersion", null)))
                .count() > 0)
            versionCompliant = true;
        else
            logger.log(Level.WARNING, "WU version mismatch. Your " + properties.getProperty(" steamVersion", null)
                    + "version doesn't match one of SimpleConcreteCreationMod's required versions " + Arrays.toString(STEAM_VERSION));
    }

    @Override
    public void init() {
        if (!versionCompliant)
            return;

        try {
            classPool = HookManager.getInstance().getClassPool();

            if (useCustomConcreteDepthMax)
                raiseRockLevelBytecodeAlter1();
            if (useCustomConcreteSlopeMax)
                raiseRockLevelBytecodeAlter2();
            if (useFixedClayElevation)
                useFixedClayElevationBytecode();
            if (useCustomMiningDepthMax)
                actionBytecodeAlter();
            if (useCustomMiningSlopeMax)
                actionBytecodeAlter1();

            //actionBytecodeAlter1();
        } catch (NotFoundException | BadBytecode | CannotCompileException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    @Override
    public void onServerStarted() {
        if (!versionCompliant)
            return;

            MeshIO meshIO = Server.caveMesh;
            int[] tiles = Arrays.copyOf(meshIO.getData(),meshIO.getData().length);
            logger.log(Level.INFO, "size: " + tiles.length);
            final int mapSizeExponent = 11;
            IntStream.range(0, tiles.length)
                    .parallel()
                    .forEach(i -> {
                        if (Tiles.decodeHeight(tiles[i]) == Short.MIN_VALUE + 1){
                            int[] cords = getMatrixCordsFromArrayIndex(i, mapSizeExponent);
                            Server.caveMesh.setTile(cords[0], cords[1], Tiles.encode((short)(-100), Tiles.decodeType(tiles[i]), (byte)(CaveTile.decodeCeilingHeight(tiles[i]))));
                        }
                    });
            logger.log(Level.INFO, "Finished caveMesh conversion.");

        try {
            addConcreteToCreationEntry();
            JAssistClassData.voidClazz();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    private static int[] getMatrixCordsFromArrayIndex(int arrayOrdinal, int mapSizeExponent){
        double squareSideDimension = Math.pow(2, mapSizeExponent);
        // mapSizeExponent of 11 is 2^11=2048.
        int cordY = (int) (Math.floor(arrayOrdinal / squareSideDimension));
        int cordX = (int) (arrayOrdinal - (cordY * squareSideDimension));

        //logger.log(Level.INFO, String.format("array %d, x/y %d/%d", arrayOrdinal, cordX, cordY));
        return new int[]{cordX, cordY};
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

    @SuppressWarnings("unused")
    public static boolean isWorkingBeyondMaxDepthHook(int h, Creature performer, Action action){
        return h < getMaxDepthHook(performer, action);
    }

    @SuppressWarnings("WeakerAccess")
    public static int getMaxDepthHook(Creature performer, Action action) {
        int raiseDeeperThanMine = 0;
        if (action.getNumber() == Actions.MINE )
            raiseDeeperThanMine = 0;
        if (action.getNumber() == Actions.RAISE_GROUND)
            raiseDeeperThanMine = -2;
        return  (int)(raiseDeeperThanMine + (-1 * performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge(0.0d) *
                (Servers.localServer.PVPSERVER ? pvpMaxConcreteDepthMultiplier : pveMaxConcreteDepthMultiplier)));
    }

    @SuppressWarnings("unused")
    public static boolean isTooSteepHook(Creature performer, int tileX, int tileY){
        com.wurmonline.server.skills.Skill miningSkill = performer.getSkills().getSkillOrLearn(SkillList.MINING);
        int slopeDown = Terraforming.getMaxSurfaceDownSlope(tileX, tileY);
        int maxSlope = (int) (miningSkill.getKnowledge(0.0) *
                (com.wurmonline.server.Servers.localServer.PVPSERVER ? pvpMaxConcreteSlopeMultiplier : pveMaxConcreteSlopeMultiplier)
                - 2); // -2 to make sure concrete application doesn't mining it back down assuming multipliers are the same.
        return (Math.abs(slopeDown) > maxSlope);
    }

    @SuppressWarnings("unused")
    private static void actionBytecodeAlter2() throws NotFoundException, CannotCompileException {

        JAssistClassData tileRockBehaviour = JAssistClassData.getClazz(TILE_ROCK_BEHAVIOUR_CLASS.getName());
        if (tileRockBehaviour == null)
            tileRockBehaviour = new JAssistClassData(TILE_ROCK_BEHAVIOUR_CLASS.getPath(), classPool);
        JAssistMethodData action = new JAssistMethodData(tileRockBehaviour, TILE_ROCK_ACTION_METHOD.getDescriptor(), TILE_ROCK_ACTION_METHOD.getName());
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
        JAssistMethodData action = new JAssistMethodData(tileRockBehaviour, TILE_ROCK_ACTION_METHOD.getDescriptor(), TILE_ROCK_ACTION_METHOD.getName());

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
     * Change these in TileRockBehaviour.action().
     * 1) Change 2508 +
     * was-
     *      final int maxSlope = (int)(mining2.getKnowledge(0.0) * (Servers.localServer.PVPSERVER ? 1 : 3));
     * becomes-
     *      final int maxSlope = (int)(mining2.getKnowledge(0.0) *
     *          (Servers.localServer.PVPSERVER ? getPvpMaxMiningSlopeMultiplier() : getPveMaxMiningSlopeMultiplier()));
     *
     * 2)  Change 2895 +
     * was-
     *      if (slope > mining2.getKnowledge(0.0) * (Servers.localServer.PVPSERVER ? 1 : 3)) {...}
     * becomes-
     *      replace the "1" and "3" with the max slope getters.
     *
     * 3) change 3048 +
     * was-
     *      final short maxDiff = (short)Math.max(10.0, mining2.getKnowledge(0.0) * 3.0 * imbueEnhancement2);
     * becomes-
     *      replace the "3" with a a method that returns which ever slop max is greatest.
     *
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static void actionBytecodeAlter1() throws NotFoundException, CannotCompileException, BadBytecode {
        int[] successes = new int[3];
        Arrays.fill(successes, 0);

        JAssistClassData tileRockBehaviour = JAssistClassData.getClazz(TILE_ROCK_BEHAVIOUR_CLASS.getName());
        if (tileRockBehaviour == null)
            tileRockBehaviour = new JAssistClassData(TILE_ROCK_BEHAVIOUR_CLASS.getPath(), classPool);
        JAssistMethodData action = new JAssistMethodData(tileRockBehaviour, TILE_ROCK_ACTION_METHOD.getDescriptor(),
                TILE_ROCK_ACTION_METHOD.getName());
        byte[] findPoolResult;

        //<editor-fold desc="** 1 **">
        Bytecode find = new Bytecode(tileRockBehaviour.getConstPool());
        find.addOpcode(Opcode.GETSTATIC);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.GETFIELD);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.IFEQ); // 2514: ifeq          2521;  7
        find.add(0, 7);
        find.addOpcode(Opcode.ICONST_1);
        find.addOpcode(Opcode.GOTO); // 2518: goto          2522; 4
        find.add(0, 4);
        find.addOpcode(Opcode.ICONST_3);
        find.addOpcode(Opcode.I2D);
        find.addOpcode(Opcode.DMUL);
        find.addOpcode(Opcode.D2I);
        find.addOpcode(Opcode.ISTORE);
        find.add(23);

        Bytecode replace = new Bytecode(tileRockBehaviour.getConstPool());
        replace.addOpcode(Opcode.GETSTATIC);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.GETFIELD);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.IFEQ); // 2514: ifeq          2523; 9
        replace.add(0, 9);
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.getPvpMaxMiningSlopeMultiplier:()D");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.GOTO); // 2520: goto          2526; 6
        replace.add(0, 6);
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.getPveMaxMiningSlopeMultiplier:()D");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.DMUL);
        replace.addOpcode(Opcode.D2I);
        replace.addOpcode(Opcode.ISTORE);
        replace.add(23);

        CodeReplacer codeReplacer = new CodeReplacer(action.getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            successes[0] = 1;
            action.getMethodInfo().rebuildStackMapIf6(classPool, tileRockBehaviour.getClassFile());
        } catch (NotFoundException e){
            successes[0] = 0;
        }
        //</editor-fold>

        //<editor-fold desc="** 2 **">
        find = new Bytecode(tileRockBehaviour.getConstPool());
        find.addOpcode(Opcode.ILOAD);
        find.add(22);
        find.addOpcode(Opcode.I2D);
        find.addOpcode(Opcode.ALOAD);
        find.add(16);
        find.addOpcode(Opcode.DCONST_0);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/wurmonline/server/skills/Skill.getKnowledge:(D)D");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.GETSTATIC);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.GETFIELD);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.IFEQ); // 2910: ifeq          2917
        find.add(0,7);
        find.addOpcode(Opcode.ICONST_1);
        find.addOpcode(Opcode.GOTO); // 2914: goto          2918
        find.add(0,4 );
        find.addOpcode(Opcode.ICONST_3);
        find.addOpcode(Opcode.I2D);
        find.addOpcode(Opcode.DMUL);
        find.addOpcode(Opcode.DCMPL);
        find.addOpcode(Opcode.IFLE); // 2921: ifle          2937
        find.add(0, 16);

        replace = new Bytecode(tileRockBehaviour.getConstPool());
        replace.addOpcode(Opcode.ILOAD);
        replace.add(22);
        replace.addOpcode(Opcode.I2D);
        replace.addOpcode(Opcode.ALOAD);
        replace.add(16);
        replace.addOpcode(Opcode.DCONST_0);
        replace.addOpcode(Opcode.INVOKEVIRTUAL);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/wurmonline/server/skills/Skill.getKnowledge:(D)D");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.GETSTATIC);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/Servers.localServer:Lcom/wurmonline/server/ServerEntry;");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.GETFIELD);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Field com/wurmonline/server/ServerEntry.PVPSERVER:Z");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.IFEQ); // 2910: ifeq          2919
        replace.add(0,9);
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.getPvpMaxMiningSlopeMultiplier:()D");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.GOTO); // 2915: goto          2920
        replace.add(0,5 );
        replace.addOpcode(Opcode.INVOKESTATIC);
        findPoolResult = addConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/joedobo27/simpleconcretecreation/SimpleConcreteCreationMod.getPveMaxMiningSlopeMultiplier:()D");
        replace.add(findPoolResult[0], findPoolResult[1]);
        replace.addOpcode(Opcode.DMUL);
        replace.addOpcode(Opcode.DCMPL);
        replace.addOpcode(Opcode.IFLE); // 2921: ifle          2937
        replace.add(0, 16);

        codeReplacer = new CodeReplacer(action.getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            successes[1] = 1;
            action.getMethodInfo().rebuildStackMapIf6(classPool, tileRockBehaviour.getClassFile());
        } catch (NotFoundException e){
            successes[1] = 0;
        }
        //</editor-fold>

        find = new Bytecode(tileRockBehaviour.getConstPool());
        find.addOpcode(Opcode.LDC2_W);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(), "// double 10.0d");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.ALOAD);
        find.add(16);
        find.addOpcode(Opcode.DCONST_0);
        find.addOpcode(Opcode.INVOKEVIRTUAL);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(),
                "// Method com/wurmonline/server/skills/Skill.getKnowledge:(D)D");
        find.add(findPoolResult[0], findPoolResult[1]);
        find.addOpcode(Opcode.LDC2_W);
        findPoolResult = findConstantPoolReference(tileRockBehaviour.getConstPool(), "// double 3.0d");
        find.add(findPoolResult[0], findPoolResult[1]);



        evaluateChangesArray(successes, "useCustomMiningSlopeMax");
    }


    /**
     * Change 766+ in raiseRockLevel of CaveTileBehaviour to allow custom max water depth for concrete/
     * was-
     *      if (h >= -25 || source.getTemplateId() == 176) {
     * becomes-
     *      boolean isWorkingBeyondMaxDepth = com/joedobo27.simpleconcretecreation.SimpleConcreteCreationMod.isWorkingBeyondMaxDepthHook(h, performer, action);
     *      if (isWorkingBeyondMaxDepth || source.getTemplateId() == 176) {...}
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
        CreationEntryCreator.createSimpleEntry(SkillList.ALCHEMY_NATURAL,
                ItemList.rock, ItemList.clay, ItemList.concrete, true, true, 0.0f, false, false,
                CreationCategories.RESOURCES);
        logger.log(Level.INFO, "Mortar + Lye removed and Concrete creation modded.");
    }

    @SuppressWarnings("unused")
    public static double getPvpMaxMiningSlopeMultiplier() {
        return pvpMaxMiningSlopeMultiplier;
    }

    @SuppressWarnings("unused")
    public static double getPveMaxMiningSlopeMultiplier() {
        return pveMaxMiningSlopeMultiplier;
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
