package com.joedobo27.rocksurfaceterraforming;


import com.wurmonline.server.Servers;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.skills.*;
import javassist.*;
import javassist.bytecode.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class RockSurfaceTerraformingMod implements  WurmServerMod, Initable, Configurable {

    private static boolean useFixedClayElevation = false;
    private static boolean useCustomDepth = false;
    private static float pveDepthMultiplier = 3;
    private static float pvpDepthMultiplier = 1;
    private static boolean useCustomMaxSlope = false;
    private static float pveSlopeMultiplier = 3;
    private static float pvpSlopeMultiplier = 1;
    private static final String[] STEAM_VERSION = new String[]{"1.3.1.3"};
    private static boolean versionCompliant = false;

    private static final Logger logger= Logger.getLogger(RockSurfaceTerraformingMod.class.getName());


    @Override
    public void configure(Properties properties) {
        useFixedClayElevation = Boolean.parseBoolean(properties.getProperty("useFixedClayElevation", Boolean.toString(useFixedClayElevation)));

        useCustomDepth = Boolean.parseBoolean(properties.getProperty("useCustomDepth", Boolean.toString(useCustomDepth)));
        pveDepthMultiplier = Float.parseFloat(properties.getProperty("pveDepthMultiplier", Float.toString(pveDepthMultiplier)));
        pvpDepthMultiplier = Float.parseFloat(properties.getProperty("pvpDepthMultiplier", Float.toString(pvpDepthMultiplier)));
        useCustomMaxSlope = Boolean.parseBoolean(properties.getProperty("useCustomMaxSlope", Boolean.toString(useCustomMaxSlope)));
        pveSlopeMultiplier = Float.parseFloat(properties.getProperty("pveSlopeMultiplier", Float.toString(pveSlopeMultiplier)));
        pvpSlopeMultiplier = Float.parseFloat(properties.getProperty("pvpSlopeMultiplier", Float.toString(pvpSlopeMultiplier)));

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
        int[] useCustomDepthSuccess = {0,0,0,0};
        int[] useCustomMaxSlopeSuccess = {0,0,0,0,0,0,0};
        int[] result;
        try {
            if (useCustomDepth){
                result = raiseRockLevelBytecodeAlter1();
                System.arraycopy(result,0, useCustomDepthSuccess, 0, 2);
                
                result = actionBytecodeAlter1();
                System.arraycopy(result,0, useCustomDepthSuccess, 2, 2);

                evaluateChangesArray(useCustomDepthSuccess, "useCustomDepth");
            }
            if (useCustomMaxSlope){
                result = raiseRockLevelBytecodeAlter2();
                System.arraycopy(result,0, useCustomMaxSlopeSuccess, 0, 4);

                result = actionBytecodeAlter2();
                System.arraycopy(result,0, useCustomMaxSlopeSuccess, 4, 3);

                evaluateChangesArray(useCustomMaxSlopeSuccess, "useCustomMaxSlope");
            }
            if (useFixedClayElevation)
                useFixedClayElevationBytecode();
        } catch (NotFoundException | BadBytecode | CannotCompileException e) {
            logger.log(Level.WARNING, e.toString(), e);
        }
    }

    private static void useFixedClayElevationBytecode() {
        int[] successes = new int[]{0};
        try {
            CtClass terraformingCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Terraforming");
            CtClass[] paramTypes = {
                    HookManager.getInstance().getClassPool().get("com/wurmonline/server/creatures/Creature"),
                    HookManager.getInstance().getClassPool().get("com/wurmonline/server/items/Item"),
                    CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.floatType,
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

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int getMaxDepth(Creature performer, Action action) {
        int raiseDeeperThanMine = 0;
        if (action.getNumber() == Actions.MINE )
            raiseDeeperThanMine = 0;
        if (action.getNumber() == Actions.RAISE_GROUND)
            raiseDeeperThanMine = -2;
        return  (int)(raiseDeeperThanMine + (-1 * performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge(0.0d) *
                (Servers.localServer.PVPSERVER ? pvpDepthMultiplier : pveDepthMultiplier)));
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static int getMaxSlope(Creature performer, Action action) {
        int mineSteepThenRaise = 0;
        if (action.getNumber() == Actions.MINE )
            mineSteepThenRaise = 0;
        if (action.getNumber() == Actions.RAISE_GROUND)
            mineSteepThenRaise = (int)(Servers.localServer.PVPSERVER ? pvpSlopeMultiplier : pveSlopeMultiplier);
        return  (int)(mineSteepThenRaise + (-1 * performer.getSkills().getSkillOrLearn(SkillList.MINING).getKnowledge(0.0d) *
                (Servers.localServer.PVPSERVER ? pvpSlopeMultiplier : pveSlopeMultiplier)));
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static double getLargestSkillSlopeMultiplier(){
        return (double) Math.max(pvpSlopeMultiplier, pveSlopeMultiplier);
    }

    /**
     * Change 1802+ in action of tileRockBehaviour to allow custom max water depth for surface mine.
     * was-
     *      if (h2 > -25) {
     * becomes-
     *      if (h >= getMaxDepth) {...}
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static int[] actionBytecodeAlter1() throws NotFoundException, CannotCompileException, BadBytecode {
        int[] success = {0,0};
        CtClass tileRockBehaviourCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.TileRockBehaviour");
        ConstPool constPool = tileRockBehaviourCt.getClassFile().getConstPool();
        CtMethod actionCt = tileRockBehaviourCt.getMethod("action",Descriptor.ofMethod(CtPrimitiveType.booleanType,
                new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/behaviours/Action"),
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/creatures/Creature"),
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/items/Item"),
                        CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.booleanType, CtPrimitiveType.intType,
                        CtPrimitiveType.intType, CtPrimitiveType.shortType,CtPrimitiveType.floatType
                }));

        BytecodeTools find = new BytecodeTools(constPool);
        find.localVariableIndex(Opcode.ILOAD, 14);
        find.integerIndex(Opcode.BIPUSH, -25);
        find.codeBranching(Opcode.IF_ICMPLE, 2018);

        BytecodeTools replace = new BytecodeTools(constPool);
        replace.localVariableIndex(Opcode.ILOAD, 14);
        int performerSlot = BytecodeTools.findSlotInLocalVariableTable(actionCt.getMethodInfo().getCodeAttribute(), "performer");
        int actSlot = BytecodeTools.findSlotInLocalVariableTable(actionCt.getMethodInfo().getCodeAttribute(), "act");
        if (performerSlot == -1 || actSlot == -1)
            success[0] = 0;
        else
            success[0] = 1;
        replace.localVariableIndex(Opcode.ALOAD, performerSlot);
        replace.localVariableIndex(Opcode.ALOAD, actSlot);
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getMaxDepth", Descriptor.ofMethod(CtPrimitiveType.intType,
                new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/creatures/Creature"),
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/behaviours/Action")}),
                "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.codeBranching(Opcode.IF_ICMPLE, 2018);
        if (success[0] == 1) {
            CodeReplacer codeReplacer = new CodeReplacer(actionCt.getMethodInfo().getCodeAttribute());
            try {
                codeReplacer.replaceCode(find.get(), replace.get());
                success[1] = 1;
                actionCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), tileRockBehaviourCt.getClassFile());
            } catch (NotFoundException e) {
                logger.info(e.getMessage());
                success[1] = 0;
            }
        }
        return success;
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
    private static int[] actionBytecodeAlter2() throws NotFoundException, CannotCompileException, BadBytecode {
        int[] success = {0,0,0};
        CtClass tileRockBehaviourCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.TileRockBehaviour");
        CtMethod actionCt = tileRockBehaviourCt.getMethod("action",Descriptor.ofMethod(CtPrimitiveType.booleanType,
                new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/behaviours/Action"),
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/creatures/Creature"),
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/items/Item"),
                        CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.booleanType, CtPrimitiveType.intType,
                        CtPrimitiveType.intType, CtPrimitiveType.shortType,CtPrimitiveType.floatType
                }));

            //<editor-fold desc="** 1 **">
        BytecodeTools find = new BytecodeTools(tileRockBehaviourCt.getClassFile().getConstPool());
        find.addFieldIndex(Opcode.GETSTATIC, "localServer", "Lcom/wurmonline/server/ServerEntry;",
                "com.wurmonline.server.Servers");
        find.addFieldIndex(Opcode.GETFIELD, "PVPSERVER", "Z",
                "com.wurmonline.server.ServerEntry");
        find.codeBranching(Opcode.IFEQ, 7);
        find.addOpcode(Opcode.ICONST_1);
        find.codeBranching(Opcode.GOTO, 4);
        find.addOpcode(Opcode.ICONST_3);
        find.addOpcode(Opcode.I2D);
        find.addOpcode(Opcode.DMUL);
        find.addOpcode(Opcode.D2I);
        find.localVariableIndex(Opcode.ISTORE, 23);


        BytecodeTools replace = new BytecodeTools(tileRockBehaviourCt.getClassFile().getConstPool());
        replace.addFieldIndex(Opcode.GETSTATIC, "localServer", "Lcom/wurmonline/server/ServerEntry;",
                "com.wurmonline.server.Servers");
        replace.addFieldIndex(Opcode.GETFIELD, "PVPSERVER", "Z",
                "com.wurmonline.server.ServerEntry");
        replace.codeBranching(Opcode.IFEQ, 9);
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getPvpSlopeMultiplier", Descriptor.ofMethod(CtPrimitiveType.floatType,
                new CtClass[]{}), "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.codeBranching(Opcode.GOTO, 6);
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getPveSlopeMultiplier", Descriptor.ofMethod(CtPrimitiveType.floatType,
                new CtClass[]{}), "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.addOpcode(Opcode.F2D);
        replace.addOpcode(Opcode.DMUL);
        replace.addOpcode(Opcode.D2I);
        replace.localVariableIndex(Opcode.ISTORE, 23);

        CodeReplacer codeReplacer = new CodeReplacer(actionCt.getMethodInfo().getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            success[0] = 1;
            actionCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), tileRockBehaviourCt.getClassFile());
        } catch (NotFoundException e){
            success[0] = 0;
        }
        //</editor-fold>

        //<editor-fold desc="** 2 **">
        find = new BytecodeTools(tileRockBehaviourCt.getClassFile().getConstPool());
        find.localVariableIndex(Opcode.ILOAD, 22);
        find.addOpcode(Opcode.I2D);
        find.localVariableIndex(Opcode.ALOAD, 16);
        find.addOpcode(Opcode.DCONST_0);
        find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getKnowledge", "(D)D", "com.wurmonline.server.skills.Skill");
        find.findFieldIndex(Opcode.GETSTATIC, "localServer", "Lcom/wurmonline/server/ServerEntry;", "com.wurmonline.server.Servers");
        find.findFieldIndex(Opcode.GETFIELD, "PVPSERVER", "Z", "com.wurmonline.server.ServerEntry");
        find.codeBranching(Opcode.IFEQ, 7);
        find.addOpcode(Opcode.ICONST_1);
        find.codeBranching(Opcode.GOTO, 4);
        find.addOpcode(Opcode.ICONST_3);
        find.addOpcode(Opcode.I2D);
        find.addOpcode(Opcode.DMUL);
        find.addOpcode(Opcode.DCMPL);
        find.codeBranching(Opcode.IFLE, 16);


        replace = new BytecodeTools(tileRockBehaviourCt.getClassFile().getConstPool());
        replace.localVariableIndex(Opcode.ILOAD, 22);
        replace.addOpcode(Opcode.I2D);
        replace.localVariableIndex(Opcode.ALOAD, 16);
        replace.addOpcode(Opcode.DCONST_0);
        replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getKnowledge", "(D)D", "com.wurmonline.server.skills.Skill");
        replace.findFieldIndex(Opcode.GETSTATIC, "localServer", "Lcom/wurmonline/server/ServerEntry;", "com.wurmonline.server.Servers");
        replace.findFieldIndex(Opcode.GETFIELD, "PVPSERVER", "Z", "com.wurmonline.server.ServerEntry");
        replace.codeBranching(Opcode.IFEQ, 9);
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getPvpSlopeMultiplier", Descriptor.ofMethod(CtPrimitiveType.floatType,
                new CtClass[]{}), "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.codeBranching(Opcode.GOTO, 6);
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getPveSlopeMultiplier", Descriptor.ofMethod(CtPrimitiveType.floatType,
                new CtClass[]{}), "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.addOpcode(Opcode.F2D);
        replace.addOpcode(Opcode.DMUL);
        replace.addOpcode(Opcode.DCMPL);
        replace.codeBranching(Opcode.IFLE, 16);

        codeReplacer = new CodeReplacer(actionCt.getMethodInfo().getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            success[1] = 1;
            actionCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), tileRockBehaviourCt.getClassFile());
        } catch (NotFoundException e){
            success[1] = 0;
        }
        //</editor-fold>

        //<editor-fold desc="** 3 **">
        find = new BytecodeTools(tileRockBehaviourCt.getClassFile().getConstPool());
        find.findDoubleIndex(10.0d);
        find.localVariableIndex(Opcode.ALOAD, 16);
        find.addOpcode(Opcode.DCONST_0);
        find.findMethodIndex(Opcode.INVOKEVIRTUAL, "getKnowledge", "(D)D", "com.wurmonline.server.skills.Skill");
        find.findDoubleIndex(3.0d);

        replace = new BytecodeTools(tileRockBehaviourCt.getClassFile().getConstPool());
        replace.findDoubleIndex(10.0d);
        replace.localVariableIndex(Opcode.ALOAD, 16);
        replace.addOpcode(Opcode.DCONST_0);
        replace.findMethodIndex(Opcode.INVOKEVIRTUAL, "getKnowledge", "(D)D", "com.wurmonline.server.skills.Skill");
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getLargestSkillSlopeMultiplier", "()D",
                "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        codeReplacer = new CodeReplacer(actionCt.getMethodInfo().getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            success[2] = 1;
            actionCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), tileRockBehaviourCt.getClassFile());
        } catch (NotFoundException e){
            success[2] = 0;
        }
        //</editor-fold>
        return success;
    }


    /**
     * Change 766+ in raiseRockLevel of CaveTileBehaviour to allow custom max water depth for concrete.
     * was-
     *      if (h >= -25 || source.getTemplateId() == 176) {
     * becomes-
     *      if (h >= getMaxDepth(performer, action) || source.getTemplateId() == 176) {
     *
     * @throws BadBytecode JA related, forwarded
     * @throws NotFoundException JA related, forwarded
     * @throws CannotCompileException JA related, forwarded
     */
    private static int[] raiseRockLevelBytecodeAlter1() throws BadBytecode, NotFoundException, CannotCompileException {
        int[] success = {0,0};
        CtClass caveTileBehaviourCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.CaveTileBehaviour");
        CtMethod raiseRockLevelCt = caveTileBehaviourCt.getMethod("raiseRockLevel", Descriptor.ofMethod(CtPrimitiveType.booleanType,
                new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                        CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.floatType,
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Action")
                }));

        BytecodeTools find = new BytecodeTools(caveTileBehaviourCt.getClassFile().getConstPool());
        find.localVariableIndex(Opcode.ILOAD, 8);
        find.integerIndex(Opcode.BIPUSH, -25);
        find.codeBranching(Opcode.IF_ICMPGE, 13);

        BytecodeTools replace = new BytecodeTools(caveTileBehaviourCt.getClassFile().getConstPool());
        replace.localVariableIndex(Opcode.ILOAD, 8);
        int performerSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevelCt.getMethodInfo().getCodeAttribute(), "performer");
        int actSlot = BytecodeTools.findSlotInLocalVariableTable(raiseRockLevelCt.getMethodInfo().getCodeAttribute(), "act");
        if (performerSlot == -1 || actSlot == -1)
            success[0] = 0;
        else
            success[0] = 1;
        replace.localVariableIndex(Opcode.ALOAD, performerSlot);
        replace.localVariableIndex(Opcode.ALOAD, actSlot);
        replace.addMethodIndex(Opcode.INVOKESTATIC, "getMaxDepth",
                Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/creatures/Creature"),
                        HookManager.getInstance().getClassPool().get("com/wurmonline/server/behaviours/Action")}),
                "com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.codeBranching(Opcode.IF_ICMPGE, 13);

        if (success[0] == 1) {
            CodeReplacer codeReplacer = new CodeReplacer(raiseRockLevelCt.getMethodInfo().getCodeAttribute());
            try {
                codeReplacer.replaceCode(find.get(), replace.get());
                success[1] = 1;
                raiseRockLevelCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), caveTileBehaviourCt.getClassFile());
            } catch (NotFoundException e) {
                success[1] = 0;
            }
        }
        return success;
    }

    /**
     * Change raiseRockLevel in CaveTileBehaviour to allow a configurable max slope when applying concrete.
     * bytecode lines 630+
     *
     * was-
     *      final int maxSlope = Servers.localServer.PVPSERVER ? -25 : -40;
     * becomes-
     *      int maxSlope = com.joedobo27.rocksurfaceterraforming.SimpleConcreteCreationMod.getMaxSlope(Creature performer, Action act)
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
    private static int[] raiseRockLevelBytecodeAlter2() throws BadBytecode, NotFoundException, CannotCompileException {
        int[] success = {0,0,0,0};
        CtClass caveTileBehaviourCt = HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.CaveTileBehaviour");
        ConstPool constPool = caveTileBehaviourCt.getClassFile().getConstPool();
        CtMethod raiseRockLevelCt = caveTileBehaviourCt.getMethod("raiseRockLevel", Descriptor.ofMethod(CtPrimitiveType.booleanType,
                new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.items.Item"),
                        CtPrimitiveType.intType, CtPrimitiveType.intType, CtPrimitiveType.floatType,
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Action")
                }));
        CodeAttribute codeAttribute = raiseRockLevelCt.getMethodInfo().getCodeAttribute();

        //<editor-fold desc="** 1 **">
        BytecodeTools find = new BytecodeTools(constPool);
        find.findFieldIndex(Opcode.GETSTATIC, "localServer", "Lcom/wurmonline/server/ServerEntry;",
                "com.wurmonline.server.Servers");
        find.findFieldIndex(Opcode.GETFIELD, "PVPSERVER", "Z", "com.wurmonline.server.ServerEntry");
        find.codeBranching(Opcode.IFEQ, 8);
        find.integerIndex(Opcode.BIPUSH, -25);
        find.codeBranching(Opcode.GOTO, 5);
        find.integerIndex(Opcode.BIPUSH, -40);
        find.localVariableIndex(Opcode.ISTORE, 8);

        BytecodeTools replace = new BytecodeTools(constPool);
        int performerSlot = BytecodeTools.findSlotInLocalVariableTable(codeAttribute, "performer");
        int actSlot = BytecodeTools.findSlotInLocalVariableTable(codeAttribute, "act");
        if (performerSlot == -1 || actSlot == -1)
            success[0] = 0;
        else
            success[0] = 1;
        replace.localVariableIndex(Opcode.ALOAD, performerSlot);
        replace.localVariableIndex(Opcode.ALOAD, actSlot);
        replace.addMethodIndex(Opcode.INVOKESTATIC,"getMaxSlope", Descriptor.ofMethod( CtPrimitiveType.intType,
                new CtClass[]{
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.creatures.Creature"),
                        HookManager.getInstance().getClassPool().get("com.wurmonline.server.behaviours.Action")
                }),"com.joedobo27.rocksurfaceterraforming.RockSurfaceTerraformingMod");
        replace.localVariableIndex(Opcode.ISTORE, 8);
        if (success[0] == 1) {
            CodeReplacer codeReplacer = new CodeReplacer(codeAttribute);
            try {
                codeReplacer.replaceCode(find.get(), replace.get());
                success[1] = 1;
                raiseRockLevelCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), caveTileBehaviourCt.getClassFile());
            } catch (NotFoundException e) {
                success[1] = 0;
            }
        }
        //</editor-fold>

        //<editor-fold desc="** 2 **">
        find = new BytecodeTools(constPool);
        find.localVariableIndex(Opcode.ILOAD, 7);
        find.integerIndex(Opcode.SIPUSH, -300);
        find.codeBranching(Opcode.IF_ICMPGE, 91);

        replace = new BytecodeTools(constPool);
        replace.localVariableIndex(Opcode.ILOAD, 7);

        int maxSlopeSlot = BytecodeTools.findSlotInLocalVariableTable(codeAttribute, "maxSlope");
        if (maxSlopeSlot == -1)
            success[2] = 0;
        else
            success[2] = 1;
        replace.localVariableIndex(Opcode.ILOAD, maxSlopeSlot);
        replace.codeBranching(Opcode.IF_ICMPGE, 92);
        // replace code is one shorter then find so a nop will be added to end, add one to branch value.

        if (success[2] == 1) {
            CodeReplacer codeReplacer1 = new CodeReplacer(codeAttribute);
            try {
                codeReplacer1.replaceCode(find.get(), replace.get());
                success[3] = 1;
                raiseRockLevelCt.getMethodInfo().rebuildStackMapIf6(HookManager.getInstance().getClassPool(), caveTileBehaviourCt.getClassFile());
            } catch (NotFoundException e) {
                success[3] = 0;
            }
        }
        //</editor-fold>

        return success;
    }

    @SuppressWarnings("unused")
    public static float getPveSlopeMultiplier() {
        return pveSlopeMultiplier;
    }

    @SuppressWarnings("unused")
    public static float getPvpSlopeMultiplier() {
        return pvpSlopeMultiplier;
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
