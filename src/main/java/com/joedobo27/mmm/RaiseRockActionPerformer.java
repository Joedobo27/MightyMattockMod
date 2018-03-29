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

class RaiseRockActionPerformer implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    RaiseRockActionPerformer(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final RaiseRockActionPerformer _performer;
        static {
            int transferActionId = ModActions.getNextActionId();
            _performer = new RaiseRockActionPerformer(transferActionId,
                    ActionEntry.createEntry( (short)transferActionId,"Raise rock","raising rock",
                    new int[]{ACTION_FATIGUE.getId(), ACTION_POLICED.getId(), ACTION_SHOW_ON_SELECT_BAR.getId()}));
        }
    }

    static RaiseRockActionPerformer getRaiseRockActionPerformer(){
        return SingletonHelper._performer;
    }

    ActionEntry getActionEntry() {
        return actionEntry;
    }

    @Override
    public short getActionId() {
        return (short) this.actionId;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset,
                          Tiles.TileBorderDirection borderDirection, long borderId, short actionId, float counter) {
        if (actionId != this.actionId || !TerraformBehaviours.isMattock(source))
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        RaiseRockAction raiseRockTerraformer;
        if (!RaiseRockAction.hashMapHasInstance(action)) {
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
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_NO_CONCRETE_NEARBY));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_CAVE_FLOOR_AND_CEILING_PROXIMITY));

            ConfigureActionOptions options = ConfigureOptions.getInstance().getRaiseRockActionOptions();
            raiseRockTerraformer = new RaiseRockAction(action, performer, source, SkillList.MINING, options.getMinSkill(),
                                                            options.getMaxSkill(), options.getLongestTime(),
                                                            options.getShortestTime(), options.getMinimumStamina(),
                                                            failureTestFunctions,
                                                            TerraformBehaviours.getOpposingCorner(
                                                                    performer, tileX, tileY, borderDirection), onSurface);
        }
        else
            raiseRockTerraformer = RaiseRockAction.actionDataWeakHashMap.get(action);

        if(raiseRockTerraformer.isActionStartTime(counter) && raiseRockTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (raiseRockTerraformer.isActionStartTime(counter)) {
            raiseRockTerraformer.doActionStartMessages();
            raiseRockTerraformer.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!raiseRockTerraformer.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (raiseRockTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        raiseRockTerraformer.modifyHeight(1);
        raiseRockTerraformer.destroyRaiseResource();
        raiseRockTerraformer.shouldMutableBeDirt();
        raiseRockTerraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        raiseRockTerraformer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
