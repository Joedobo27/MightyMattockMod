package com.Joedobo27.WUmod;

import com.sun.xml.internal.bind.v2.runtime.reflect.opt.Const;
import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.*;
import javassist.*;
import javassist.bytecode.*;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPCode;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("Convert2Lambda")
public class ClayNShards4ConcreteMod implements WurmMod, Initable, Configurable, ServerStartedListener, ItemTemplatesCreatedListener {

    private boolean shardNClayToConcrete = false;
    private short shardWeight = 20;
    private short clayWeight = 5;
    private ArrayList<Integer> concreteSpecs = new ArrayList<>(Arrays.asList(25, 25, 30, 20));
    private boolean useCustomSlopeMax = false;
    private double pvpSlopeMultiplier = 1;
    private double pveSlopeMultiplier = 3;
    private boolean useCustomDepthMax = false;
    private double pvpDepthMultiplier = 1;
    private double pveDepthMultiplier = 3;


    private MethodInfo raiseRockLevelMInfo;
    private CodeAttribute raiseRockLevelAttribute;
    private CodeIterator raiseRockLevelIterator;

    private MethodInfo checkSaneAmountsMInfo;
    private CodeAttribute checkSaneAmountsAttribute;
    private CodeIterator checkSaneAmountsIterator;

    private Logger logger = Logger.getLogger(this.getClass().getName());

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
        useCustomDepthMax = Boolean.valueOf(properties.getProperty("useCustomSlopeMax", Boolean.toString(useCustomSlopeMax)));
        pvpDepthMultiplier = Double.valueOf(properties.getProperty("pvpSlopeMultiplier", Double.toString(pvpSlopeMultiplier)));
        pveDepthMultiplier = Double.valueOf(properties.getProperty("pvpSlopeMultiplier", Double.toString(pveDepthMultiplier)));
    }

    @Override
    public void onItemTemplatesCreated() {
        if (shardNClayToConcrete) {
            try {
                // Remove the existent concrete entry from item templates and add it back in with updated values.
                Map<Integer, ItemTemplate> fieldTemplates = ReflectionUtil.getPrivateField(ItemTemplateFactory.class,
                        ReflectionUtil.getField(ItemTemplateFactory.class, "templates"));
                fieldTemplates.remove(782);
                ItemTemplateFactory.getInstance().createItemTemplate(782, 3, "concrete", "concrete", "excellent", "good", "ok", "poor",
                        "Concrete, for instance used to raise cave floors.", new short[]{25, 146, 46, 112, 113, 158}, (short) 591, (short) 1,
                        0, 18000L, concreteSpecs.get(0), concreteSpecs.get(1), concreteSpecs.get(2), -10, MiscConstants.EMPTY_BYTE_PRIMITIVE_ARRAY,
                        "model.resource.concrete.", 25.0f, concreteSpecs.get(3) * 1000, (byte) 18, 10, false, -1);
            } catch (IOException | NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onServerStarted() {
        if (shardNClayToConcrete) {
            try {
                // Remove the Mortar + Lye entries from simpleEntries and Matrix.
                Map<Integer, List<CreationEntry>> simpleEntries = ReflectionUtil.getPrivateField(CreationMatrix.class,
                        ReflectionUtil.getField(CreationMatrix.class, "simpleEntries"));
                Map<Integer, List<CreationEntry>> matrix = ReflectionUtil.getPrivateField(CreationMatrix.class,
                        ReflectionUtil.getField(CreationMatrix.class, "matrix"));
                List<CreationEntry> entryReference = null;
                boolean result;
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
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.log(Level.SEVERE, e.toString());
            }
        }
    }

    @Override
    public void init() {
        if (useCustomSlopeMax || useCustomDepthMax) {
            try {
                jaseBT jbt;
                jaseBT jbt1;
                ClassPool pool = HookManager.getInstance().getClassPool();

                CtClass ctcCaveTileBehaviour = pool.get("com.wurmonline.server.behaviours.CaveTileBehaviour");
                ClassFile cfCaveTileBehaviour = ctcCaveTileBehaviour.getClassFile();
                ConstPool cpCaveTileBehaviour = cfCaveTileBehaviour.getConstPool();

                setRaiseRockLevel(cfCaveTileBehaviour,
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z",
                        "raiseRockLevel");
                if (useCustomDepthMax){
                    //Change raiseRockLevel in CaveTileBehaviour to allow custom max water depth for concrete.
                    getRaiseRockLevelIterator().insertGap(712,34);
                    jbt = new jaseBT();
                    jbt.setOpCodeStructure(new ArrayList<>(Arrays.asList(Opcode.ALOAD_0, Opcodes.INVOKEVIRTUAL, Opcodes.SIPUSH,
                            Opcodes.INVOKEVIRTUAL, Opcodes.DCONST_0, Opcodes.INVOKEVIRTUAL, Opcodes.GETSTATIC, Opcodes.GETFIELD,
                            Opcodes.IFEQ, Opcode.LDC2_W, Opcode.GOTO, Opcode.LDC2_W, Opcode.DMUL, Opcode.D2I, Opcode.INEG, Opcode.ILOAD,
                            Opcode.SWAP)));
                    jbt.setOperandStructure(new ArrayList<>(Arrays.asList("",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour,ConstPool.CONST_Methodref,"getSkills","()Lcom/wurmonline/server/skills/Skills;",null,"com/wurmonline/server/creatures/Creature",null),
                            "03f0",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour,ConstPool.CONST_Methodref,"getSkillOrLearn","(I)Lcom/wurmonline/server/skills/Skill;",null,"com/wurmonline/server/skills/Skills",null),
                            "",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour,ConstPool.CONST_Methodref,"getKnowledge","(D)D",null,"com/wurmonline/server/skills/Skill",null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour,ConstPool.CONST_Fieldref,"localServer","Lcom/wurmonline/server/ServerEntry;",null,"com/wurmonline/server/Servers",null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour,ConstPool.CONST_Fieldref,"PVPSERVER","Z",null,"com/wurmonline/server/ServerEntry",null),
                            "0009",
                            String.format("%04X", cpCaveTileBehaviour.addDoubleInfo(pvpDepthMultiplier) & 0xffff),
                            "0006",
                            String.format("%04X", cpCaveTileBehaviour.addDoubleInfo(pveDepthMultiplier) & 0xffff),
                            "","","","08","")));
                    jbt.setOpcodeOperand();
                    logger.log(Level.INFO, jbt.getOpcodeOperand());
                    String replaceResult = jaseBT.byteCodeFindReplace(
                            "00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,1508,10e7,a1021e",
                            "00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,00,1508,10e7",
                            jbt.getOpcodeOperand(),getRaiseRockLevelIterator(),"raiseRockLevel");
                    logger.log(Level.INFO, replaceResult);
                    String printResult = jaseBT.byteCodePrint(getRaiseRockLevelIterator(), "raiseRockLevel",
                            "C:\\Users\\Jason\\steamcmd\\red_beta\\byte code prints");
                    logger.log(Level.INFO, printResult);
                    getRaiseRockLevelAttribute().computeMaxStack();
                    getRaiseRockLevelMInfo().rebuildStackMapIf6(pool,cfCaveTileBehaviour);
                }
                if (useCustomSlopeMax) {
                    CtClass ctcCreationEntry = pool.get("com.wurmonline.server.items.CreationEntry");
                    ClassFile cfCreationEntry = ctcCreationEntry.getClassFile();
                    ConstPool cpCreationEntry = cfCreationEntry.getConstPool();

                    //<editor-fold desc="Change information.">
                    /*
                    Change raiseRockLevel in CaveTileBehaviour to allow a configurable max slope when applying concrete.
                    Lines 578 - 640
                    removing -
                        final int slopeDown = Terraforming.getMaxSurfaceDownSlope(tilex, tiley);
                        if (slopeDown < (Servers.localServer.PVPSERVER ? -25 : -40)) {
                            performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " would only flow away.");
                            return true;
                        }
                    Code to replace the removed section's function was inserted below it and consists of the insertAt string.
                    */
                    //</editor-fold>
                    jbt = new jaseBT();
                    jbt.setOpCodeStructure(new ArrayList<>(Arrays.asList(Opcode.ILOAD_2, Opcode.ILOAD_3, Opcode.INVOKESTATIC, Opcode.ISTORE,
                            Opcode.ILOAD, Opcode.GETSTATIC, Opcode.GETFIELD, Opcode.IFEQ, Opcode.BIPUSH, Opcode.GOTO, Opcode.BIPUSH,
                            Opcode.IF_ICMPGE, Opcode.ALOAD_0, Opcode.INVOKEVIRTUAL, Opcode.NEW, Opcode.DUP, Opcode.LDC_W, Opcode.INVOKESPECIAL,
                            Opcode.ALOAD_1, Opcode.INVOKEVIRTUAL, Opcode.INVOKEVIRTUAL, Opcode.LDC_W, Opcode.INVOKEVIRTUAL, Opcode.INVOKEVIRTUAL,
                            Opcode.INVOKEVIRTUAL, Opcode.ICONST_1, Opcode.IRETURN)));
                    jbt.setOperandStructure(new ArrayList<>(Arrays.asList("", "",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "getMaxSurfaceDownSlope", "(II)I", null, "com/wurmonline/server/behaviours/Terraforming", null),
                            "07", "07",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Fieldref, "localServer", "Lcom/wurmonline/server/ServerEntry;", null, "com/wurmonline/server/Servers", null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Fieldref, "PVPSERVER", "Z", null, "com/wurmonline/server/ServerEntry", null),
                            "0008", "e7", "0005", "d8", "0032", "",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "getCommunicator", "()Lcom/wurmonline/server/creatures/Communicator;", null, "com/wurmonline/server/creatures/Creature", null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Class, null, null, "java/lang/StringBuilder", null, null),
                            "",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_String, null, null, "The ", null, null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "<init>", "(Ljava/lang/String;)V", null, "java/lang/StringBuilder", null),
                            "",
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "getName", "()Ljava/lang/String;", null, "com/wurmonline/server/items/Item", null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", null, "java/lang/StringBuilder", null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_String, null, null, " would only flow away.", null, null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", null, "java/lang/StringBuilder", null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "toString", "()Ljava/lang/String;", null, "java/lang/StringBuilder", null),
                            jaseBT.findConstantPoolReference(cpCaveTileBehaviour, ConstPool.CONST_Methodref, "sendNormalServerMessage", "(Ljava/lang/String;)V", null, "com/wurmonline/server/creatures/Communicator", null),
                            "", "")));
                    jbt.setOpcodeOperand();
                    String replaceResult = jaseBT.byteCodeFindReplace(jbt.getOpcodeOperand(), jbt.getOpcodeOperand(),
                            "00,00,000000,0000,0000,000000,000000,000000,0000,000000,0000,000000,00,000000,000000,00,000000,000000,00,000000,000000,000000,000000,000000,a70011,00,00",
                            getRaiseRockLevelIterator(), "raiseRockLevel");
                    getRaiseRockLevelAttribute().computeMaxStack();
                    getRaiseRockLevelMInfo().rebuildStackMapIf6(pool,cfCaveTileBehaviour);

                    CtMethod ctmRaiseRockLevel = ctcCaveTileBehaviour.getMethod("raiseRockLevel",
                            "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z");
                    String s = "";
                    s = s.concat("com.wurmonline.server.skills.Skills skills2 = $1.getSkills();com.wurmonline.server.skills.Skill mining2 = null;");
                    s = s.concat("try {mining2 = skills2.getSkill(1008);} catch (com.wurmonline.server.skills.NoSuchSkillException e) {mining2 = skills2.learn(1008, 1.0f);}");
                    s = s.concat("int slopeDown1 = com.wurmonline.server.behaviours.Terraforming#getMaxSurfaceDownSlope($3, $4);");
                    s = s.concat("int maxSlope = (int) (mining2.getKnowledge(0.0) * (com.wurmonline.server.Servers.localServer.PVPSERVER ? " + pvpSlopeMultiplier + " : " + pveSlopeMultiplier + "));");
                    s = s.concat("if (Math.abs(slopeDown1) > maxSlope)");
                    s = s.concat("{$1.getCommunicator().sendNormalServerMessage(\"The \" + source.getName() + \" would only flow away.\");return true;}");
                    ctmRaiseRockLevel.insertAt(1287, s);
                    logger.log(Level.INFO, replaceResult);


                    //<editor-fold desc="Change information.">
                    /*
                    Change checkSaneAmounts of CreationEntry to exclude concrete from a section that was bugging creation because of
                    1) combine for all 2)large weight difference between finished concrete and clay.
                    Lines 387 - 401
                    added "&& this.objectCreated != 782"
                         if (template.isCombine() && this.objectCreated != 782 && this.objectCreated != 73)
                    */
                    //</editor-fold>
                    setCheckSaneAmounts(cfCreationEntry,
                            "(Lcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/Item;ILcom/wurmonline/server/items/ItemTemplate;Lcom/wurmonline/server/creatures/Creature;Z)V",
                            "checkSaneAmounts");
                    getCheckSaneAmountsIterator().insertGap(395, 10);
                    jbt = new jaseBT();

                    jbt.setOpCodeStructure(new ArrayList<>(Arrays.asList(Opcode.ALOAD, Opcode.INVOKEVIRTUAL, Opcode.IFEQ, Opcode.NOP, Opcode.NOP,
                            Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.ALOAD_0,
                            Opcode.GETFIELD, Opcode.BIPUSH, Opcode.IF_ICMPEQ)));
                    jbt.setOperandStructure(new ArrayList<>(Arrays.asList("05",
                            jaseBT.findConstantPoolReference(cpCreationEntry, ConstPool.CONST_Methodref, "isCombine", "()Z", null, "com/wurmonline/server/items/ItemTemplate", null),
                            "0038", "", "", "", "", "", "", "", "", "", "", "",
                            jaseBT.findConstantPoolReference(cpCreationEntry, ConstPool.CONST_Fieldref, "objectCreated", "I", null, "com/wurmonline/server/items/CreationEntry", null),
                            "49", "0025")));
                    jbt.setOpcodeOperand();
                    jbt1 = new jaseBT();
                    jbt1.setOpCodeStructure(new ArrayList<>(Arrays.asList(Opcode.ALOAD, Opcode.INVOKEVIRTUAL, Opcode.IFEQ, Opcode.ALOAD_0,
                            Opcode.GETFIELD, Opcode.LDC_W, Opcode.IF_ICMPEQ, Opcode.ALOAD_0, Opcode.GETFIELD, Opcode.BIPUSH, Opcode.IF_ICMPEQ)));
                    jbt1.setOperandStructure(new ArrayList<>(Arrays.asList("05",
                            jaseBT.findConstantPoolReference(cpCreationEntry, ConstPool.CONST_Methodref, "isCombine", "()Z", null, "com/wurmonline/server/items/ItemTemplate", null),
                            "0038", "",
                            jaseBT.findConstantPoolReference(cpCreationEntry, ConstPool.CONST_Fieldref, "objectCreated", "I", null, "com/wurmonline/server/items/CreationEntry", null),
                            String.format("%04X", cpCreationEntry.addIntegerInfo(782) & 0xffff), "002e", "",
                            jaseBT.findConstantPoolReference(cpCreationEntry, ConstPool.CONST_Fieldref, "objectCreated", "I", null, "com/wurmonline/server/items/CreationEntry", null),
                            "49", "0025")));
                    jbt1.setOpcodeOperand();
                    replaceResult = jaseBT.byteCodeFindReplace(jbt.getOpcodeOperand(), jbt.getOpcodeOperand(), jbt1.getOpcodeOperand(), getCheckSaneAmountsIterator(),
                            "checkSaneAmounts");
                    getCheckSaneAmountsAttribute().computeMaxStack();
                    getCheckSaneAmountsMInfo().rebuildStackMapIf6(pool, cfCreationEntry);
                    logger.log(Level.INFO, replaceResult);
                }
            } catch (NotFoundException | CannotCompileException | BadBytecode e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This is copy template method used to copy and paste into WU CaveTileBehaviour.class.
     *
     * @param performer is Creature type. It's the toon doing the action.
     * @param pvp is float type. It's a multiplier of skill for max slope.
     * @param pve is float type. It's a multiplier of skill for max slope.
     * @return is int type. This is the maxSlope.
     */
    public static boolean calcMaxMiningSlope(Creature performer, float pvp, float pve, int tileX, int tileY) {
        Skills skills2 = performer.getSkills();
        Skill mining2 = null;
        try {
            mining2 = skills2.getSkill(1008);
        } catch (NoSuchSkillException e) {
            mining2 = skills2.learn(1008, 1.0f);
        }
        int slopeDown1 = com.wurmonline.server.behaviours.Terraforming.getMaxSurfaceDownSlope(tileX, tileY);
        int maxSlope = (int) (mining2.getKnowledge(0.0) * (Servers.localServer.PVPSERVER ? pvp : pve));
        return Math.abs(slopeDown1) > maxSlope;
    }

    public void setRaiseRockLevel(ClassFile cf, String desc, String name) {
        if (this.raiseRockLevelMInfo == null || this.raiseRockLevelIterator == null || this.raiseRockLevelAttribute == null) {
            for (List a : new List[]{cf.getMethods()}){
                for(Object b : a){
                    MethodInfo MInfo = (MethodInfo) b;
                    if (Objects.equals(MInfo.getDescriptor(), desc) && Objects.equals(MInfo.getName(), name)){
                        this.raiseRockLevelMInfo = MInfo;
                        break;
                    }
                }
            }
            if (this.raiseRockLevelMInfo == null){
                throw new NullPointerException();
            }
            this.raiseRockLevelAttribute = this.raiseRockLevelMInfo.getCodeAttribute();
            this.raiseRockLevelIterator = this.raiseRockLevelAttribute.iterator();
        }
    }

    public CodeIterator getRaiseRockLevelIterator(){return this.raiseRockLevelIterator;}

    public CodeAttribute getRaiseRockLevelAttribute(){return this.raiseRockLevelAttribute;}

    public MethodInfo getRaiseRockLevelMInfo(){return this.raiseRockLevelMInfo;}

    public void setCheckSaneAmounts(ClassFile cf, String desc, String name) {
        if (this.checkSaneAmountsMInfo == null || this.checkSaneAmountsIterator == null || this.checkSaneAmountsAttribute == null) {
            for (List a : new List[]{cf.getMethods()}){
                for(Object b : a){
                    MethodInfo MInfo = (MethodInfo) b;
                    if (Objects.equals(MInfo.getDescriptor(), desc) && Objects.equals(MInfo.getName(), name)){
                        this.checkSaneAmountsMInfo = MInfo;
                        break;
                    }
                }
            }
            if (this.checkSaneAmountsMInfo == null){
                throw new NullPointerException();
            }
            this.checkSaneAmountsAttribute = this.checkSaneAmountsMInfo.getCodeAttribute();
            this.checkSaneAmountsIterator = this.checkSaneAmountsAttribute.iterator();
        }
    }

    public CodeIterator getCheckSaneAmountsIterator(){return this.checkSaneAmountsIterator;}

    public CodeAttribute getCheckSaneAmountsAttribute(){return this.checkSaneAmountsAttribute;}

    public MethodInfo getCheckSaneAmountsMInfo(){return this.checkSaneAmountsMInfo;}
}
