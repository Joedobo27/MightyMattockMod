package com.Joedobo27.WUmod;

import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.Servers;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.*;
import javassist.*;
import javassist.bytecode.*;
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
    private float pvpSlopeMultiplier = 1f;
    private float pveSlopeMultiplier = 3f;

    private MethodInfo raiseRockLevelMInfo;
    private CodeAttribute raiseRockLevelAttribute;
    private CodeIterator raiseRockLevelIterator;

    private Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    public void configure(Properties properties) {
        shardNClayToConcrete = Boolean.valueOf(properties.getProperty("shardNClayToConcrete", Boolean.toString(shardNClayToConcrete)));
        shardWeight = Short.valueOf(properties.getProperty("loadProximityRequired", Short.toString(shardWeight)));
        clayWeight = Short.valueOf(properties.getProperty("loadProximityRequired", Short.toString(clayWeight)));
        concreteSpecs.clear();
        for (String a :  properties.getProperty("concreteSpecs", concreteSpecs.toString().replaceAll("\\[|\\]", "")).split(",")){
            concreteSpecs.add(Integer.valueOf(a));
        }
        useCustomSlopeMax = Boolean.valueOf(properties.getProperty("useCustomSlopeMax", Boolean.toString(useCustomSlopeMax)));
        pvpSlopeMultiplier = Float.valueOf(properties.getProperty("pvpSlopeMultiplier", Float.toString(pvpSlopeMultiplier)));
        pveSlopeMultiplier = Float.valueOf(properties.getProperty("pveSlopeMultiplier", Float.toString(pveSlopeMultiplier)));

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
        /*
        if (shardNClayToConcrete) {
            HookManager.getInstance().registerHook("com.wurmonline.server.items.CreationEntryCreator", "createCreationEntries", "()V",
                    new InvocationHandlerFactory() {
                        @Override
                        public InvocationHandler createInvocationHandler() {
                            return new InvocationHandler() {

                                @Override
                                public Object invoke(Object object, Method method, Object[] args) throws Throwable {
                                    Object toReturn = method.invoke(object, args);


                                    return toReturn;
                                }
                            };
                        }
                    });
        }
        */
        if (useCustomSlopeMax) {
            try {
                ClassPool pool = HookManager.getInstance().getClassPool();
                CtClass ctcCaveTileBehaviour = pool.get("com.wurmonline.server.behaviours.CaveTileBehaviour");
                ClassFile cfCaveTileBehaviour = ctcCaveTileBehaviour.getClassFile();
                CtClass ctcSelf = pool.get(this.getClass().getName());

                /*
                String s1 = "";
                s1 = s1.concat("public static boolean calcMaxMiningSlope(com.wurmonline.server.creatures.Creature performer, float pvp, float pve, int tileX, int tileY)");
                s1 = s1.concat("{return true;}");
                CtMethod m = CtNewMethod.make(s1, ctcCaveTileBehaviour);
                m.setBody(ctcSelf.getMethod("calcMaxMiningSlope", "(Lcom/wurmonline/server/creatures/Creature;FFII)Z"), null);
                logger.log(Level.INFO, "long name " + m.getLongName());
                logger.log(Level.INFO, "declaring class " + m.getDeclaringClass().getName());
                logger.log(Level.INFO, "signature " + m.getSignature());
                */

                CtMethod ctmRaiseRockLevel = ctcCaveTileBehaviour.getMethod("raiseRockLevel",
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z");
                /*
                ConstPool cp = ctcCaveTileBehaviour.getClassFile().getConstPool();
                for (int i=0; i < cp.getSize(); i++){
                    try {
                        logger.log(Level.INFO, "method names " + cp.getMethodrefName(i));
                    }catch (ClassCastException e){}
                }
                */
                String s = "";
                s = s.concat("com.wurmonline.server.skills.Skills skills2 = $1.getSkills();com.wurmonline.server.skills.Skill mining2 = null;");
                s = s.concat("try {mining2 = skills2.getSkill(1008);} catch (com.wurmonline.server.skills.NoSuchSkillException e) {mining2 = skills2.learn(1008, 1.0f);}");
                s = s.concat("int slopeDown1 = com.wurmonline.server.behaviours.Terraforming#getMaxSurfaceDownSlope($3, $4);");
                s = s.concat("int maxSlope = (int) (mining2.getKnowledge(0.0) * (com.wurmonline.server.Servers.localServer.PVPSERVER ? "+pvpSlopeMultiplier+" : "+pveSlopeMultiplier+"));");
                s = s.concat("if (Math.abs(slopeDown1) > maxSlope)");
                s = s.concat("{$1.getCommunicator().sendNormalServerMessage(\"The \" + source.getName() + \" would only flow away.\");return true;}");
                ctmRaiseRockLevel.insertAt( 1272, "{ " + s + " }");

                setRaiseRockLevel(cfCaveTileBehaviour,
                        "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIFLcom/wurmonline/server/behaviours/Action;)Z",
                        "raiseRockLevel");

                String replaceResult = jaseBT.byteCodeFindReplace(
                        "ac,1c,1d,b803be,3607,1507,b203c1,b403c7,990008,10e7,a70005,10d8,a20032,2a,b60092,bb010e,59,1303b4,b70117,2b,b601a9,b6011b,1303cc,b6011b,b6011f,b60098,04,ac,2a",
                        "1c,1d,b803be,3607,1507,b203c1,b403c7,990008,10e7,a70005,10d8,a20032,2a,b60092,bb010e,59,1303b4,b70117,2b,b601a9,b6011b,1303cc,b6011b,b6011f,b60098,04,ac",
                        "00,00,000000,0000,0000,000000,000000,000000,0000,000000,0000,000000,00,000000,000000,00,000000,000000,00,000000,000000,000000,000000,000000,000000,00,00",
                        getRaiseRockLevelIterator(), "raiseRockLevel");
                //<editor-fold desc="Code replaced">
                /*
                removing -
                    final int slopeDown = Terraforming.getMaxSurfaceDownSlope(tilex, tiley);
                    if (slopeDown < (Servers.localServer.PVPSERVER ? -25 : -40)) {
                        performer.getCommunicator().sendNormalServerMessage("The " + source.getName() + " would only flow away.");
                        return true;
                    }
                Code to replace the removed section's function was insert above it.
                */
                //</editor-fold>
                getRaiseRockLevelAttribute().computeMaxStack();
                getRaiseRockLevelMInfo().rebuildStackMapIf6(pool, cfCaveTileBehaviour);


                //logger.log(Level.INFO, replaceResult);
                String printResult = jaseBT.byteCodePrint(getRaiseRockLevelIterator(), "raiseRockLevel",
                        "C:\\Program Files (x86)\\Steam\\SteamApps\\common\\Wurm Unlimited Dedicated Server\\byte code prints");
                logger.log(Level.INFO, printResult);

            } catch (NotFoundException | CannotCompileException | BadBytecode e) {
                logger.log(Level.WARNING, e.toString());
            }
        }
    }

    private static String makeRaiseRockInsertString(float pvp, float pve){
        String s = "";
        s = s.concat("int slopeDown1 = com.wurmonline.server.behaviours.Terraforming#getMaxSurfaceDownSlope($3, $4);");
        s = s.concat("int maxSlope = calcMaxMiningSlope($1, " + Float.toString(pvp) + ", " + Float.toString(pve) + ");");
        s = s.concat("if (Math.abs(slopeDown1) > maxSlope)");
        s = s.concat("{performer.getCommunicator().sendNormalServerMessage(\"The \" + source.getName() + \" would only flow away.\");return true;}");
        return s;
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
}
