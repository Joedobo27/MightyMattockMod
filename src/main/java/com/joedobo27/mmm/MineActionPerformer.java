package com.joedobo27.mmm;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;


public class MineActionPerformer implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final short actionId;

    MineActionPerformer(short actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final MineActionPerformer _performer;
        static {
            _performer = new MineActionPerformer(Actions.MINE, Actions.actionEntrys[Actions.MINE]);
        }
    }

    static MineActionPerformer getMineActionPerformer(){
        return SingletonHelper._performer;
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface,
                          int heightOffset, Tiles.TileBorderDirection borderDirection, long borderId, short actionId,
                          float counter) {
        if (actionId != this.actionId || !TerraformBehaviours.isMattock(source))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        MineAction mineTerraformer;
        if (!MineAction.hashMapHasInstance(action)) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_ROCK_MESH_AND_CAVE_CEILING_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CORNER_OCCUPIED_BY_FENCE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_HOUSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_BRIDGE_SUPPORT));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_BRIDGE_EXIT));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_CAVE_ENTRANCE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CAVE_ENTRANCE_BORDER));

            ConfigureActionOptions options = ConfigureOptions.getInstance().getMineActionOptions();
            mineTerraformer = new MineAction(action, performer, source, SkillList.MINING, options.getMinSkill(),
                                                  options.getMaxSkill(), options.getLongestTime(),
                                                  options.getShortestTime(), options.getMinimumStamina(),
                                                  failureTestFunctions, TerraformBehaviours.getOpposingCorner(
                                                          performer, tileX, tileY, borderDirection), onSurface);
        }
        else
            mineTerraformer = MineAction.actionDataWeakHashMap.get(action);

        if(mineTerraformer.isActionStartTime(counter) && mineTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (mineTerraformer.isActionStartTime(counter)) {
            mineTerraformer.doActionStartMessages();
            mineTerraformer.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!mineTerraformer.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (mineTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        mineTerraformer.modifyHeight(-1);
        mineTerraformer.shouldMutableBeDirt();
        double power = mineTerraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        mineTerraformer.makeMinedItemOnGround(power);
        mineTerraformer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

}
