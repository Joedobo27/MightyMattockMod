package com.joedobo27.mmm;

import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.ArrayList;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static com.joedobo27.libs.action.ActionTypes.ACTION_FATIGUE;
import static com.joedobo27.libs.action.ActionTypes.ACTION_POLICED;
import static com.joedobo27.libs.action.ActionTypes.ACTION_SHOW_ON_SELECT_BAR;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

public class RaiseDirtActionPerformer implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    RaiseDirtActionPerformer(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final RaiseDirtActionPerformer _performer;
        static {
            int transferActionId = ModActions.getNextActionId();
            _performer = new RaiseDirtActionPerformer(transferActionId,
                    ActionEntry.createEntry((short)transferActionId, "Raise dirt","raising dirt",
                            new int[]{ACTION_FATIGUE.getId(), ACTION_POLICED.getId(), ACTION_SHOW_ON_SELECT_BAR.getId()}));
        }
    }

    static RaiseDirtActionPerformer getRaiseDirtActionPerformer(){
        return SingletonHelper._performer;
    }

    @Override
    public short getActionId() {
        return (short)this.actionId;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface,
                          int heightOffset, Tiles.TileBorderDirection borderDirection, long borderId, short actionId,
                          float counter) {
        if (actionId != this.actionId || !TerraformBehaviours.isMattock(source))
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        RaiseDirtAction raiseDirtTerraformer;
        if (!RaiseDirtAction.hashMapHasInstance(action)) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CORNER_OCCUPIED_BY_FENCE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_HOUSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_BRIDGE_SUPPORT));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_BRIDGE_EXIT));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_OCCUPIED_BY_CAVE_ENTRANCE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_NO_DIRT_NEARBY));

            ConfigureActionOptions options = ConfigureOptions.getInstance().getRaiseDirtActionOptions();
            raiseDirtTerraformer = new RaiseDirtAction(action, performer, source, SkillList.DIGGING, options.getMinSkill(),
                                                            options.getMaxSkill(), options.getLongestTime(),
                                                            options.getShortestTime(), options.getMinimumStamina(),
                                                            failureTestFunctions,
                                                            TerraformBehaviours.getOpposingCorner(
                                                                    performer, tileX, tileY, borderDirection), actionId);
        }
        else
            raiseDirtTerraformer = RaiseDirtAction.actionDataWeakHashMap.get(action);

        if(raiseDirtTerraformer.isActionStartTime(counter) && raiseDirtTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (raiseDirtTerraformer.isActionStartTime(counter)) {
            raiseDirtTerraformer.doActionStartMessages();
            raiseDirtTerraformer.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!raiseDirtTerraformer.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (raiseDirtTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        raiseDirtTerraformer.modifyHeight(1);
        raiseDirtTerraformer.shouldRockBeDirt();
        raiseDirtTerraformer.destroyRaiseResource();
        raiseDirtTerraformer.shouldMutableBeDirt();
        raiseDirtTerraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        raiseDirtTerraformer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
