package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.function.Function;

import static com.joedobo27.libs.action.ActionFailureFunction.*;
import static org.gotti.wurmunlimited.modsupport.actions.ActionPropagation.*;

public class CollectResourceAction implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    CollectResourceAction(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;

    }

    @Override
    public short getActionId() {return (short)this.actionId;}

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface,
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (!TerraformBehaviours.isMattock(source) || !TileUtilities.isResourceTile(TilePos.fromXY(tileX, tileY))) {
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }
        CollectResourceTerraformer collectResourceTerraformer;
        if (!CollectResourceTerraformer.hashMapHasInstance(action)){
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_HOUSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_IS_OCCUPIED_BY_BRIDGE_SUPPORT));

            ConfigureActionOptions options = ConfigureOptions.getInstance().getCollectResourceActionOptions();
            collectResourceTerraformer = new CollectResourceTerraformer(action, performer, source, SkillList.DIGGING,
                    options.getMinSkill(), options.getMaxSkill(), options.getLongestTime(), options.getShortestTime()
                    , options.getMinimumStamina(), failureTestFunctions, TilePos.fromXY(tileX, tileY));
        }
        else
            collectResourceTerraformer = CollectResourceTerraformer.actionDataWeakHashMap.get(action);

        if(collectResourceTerraformer.isActionStartTime(counter) && collectResourceTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (collectResourceTerraformer.isActionStartTime(counter)) {
            collectResourceTerraformer.doActionStartMessages();
            collectResourceTerraformer.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!collectResourceTerraformer.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (collectResourceTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        double power = collectResourceTerraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        collectResourceTerraformer.makeDugItemOnGround(power);
        collectResourceTerraformer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }

}
