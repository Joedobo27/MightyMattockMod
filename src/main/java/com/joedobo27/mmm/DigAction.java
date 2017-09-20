package com.joedobo27.mmm;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

public class DigAction implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    DigAction(int actionId, ActionEntry actionEntry) {
        this.actionEntry = actionEntry;
        this.actionId = actionId;
    }

    @Override
    public short getActionId() {return (short)this.actionId;}

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset,
                          Tiles.TileBorderDirection borderDirection, long borderId, short actionId, float counter) {
        if (actionId != this.actionId || !TerraformBehaviours.isMattock(source))
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        DigTerraformer terraformerDig;
        if (!DigTerraformer.hashMapHasInstance(action)) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_DIGGING_ROCK));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CORNER_OCCUPIED_BY_FENCE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_EXIT));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_CAVE_ENTRANCE));

            ConfigureActionOptions options = ConfigureOptions.getInstance().getDigActionOptions();
            terraformerDig = new DigTerraformer(action, performer, source, SkillList.DIGGING, options.getMinSkill(),
                                                options.getMaxSkill(),options.getLongestTime(),
                                                options.getShortestTime(), options.getMinimumStamina(),
                                                failureTestFunctions,
                                                TerraformBehaviours.getOpposingCorner(
                                                        performer, tileX, tileY, borderDirection));
        }
        else
            terraformerDig = DigTerraformer.actionDataWeakHashMap.get(action);

        if(terraformerDig.isActionStartTime(counter) && terraformerDig.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (terraformerDig.isActionStartTime(counter)) {
            terraformerDig.doActionStartMessages();
            terraformerDig.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!terraformerDig.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (terraformerDig.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        terraformerDig.modifyHeight(-1);
        terraformerDig.shouldDirtBeRock();
        terraformerDig.shouldMutableBeDirt();
        double power = terraformerDig.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        terraformerDig.makeDugItemOnGround(power);
        terraformerDig.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
}
}
