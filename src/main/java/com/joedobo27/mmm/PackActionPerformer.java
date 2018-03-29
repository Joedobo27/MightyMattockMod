package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
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

class PackActionPerformer implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    PackActionPerformer(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final PackActionPerformer _performer;
        static {
            _performer = new PackActionPerformer(Actions.ROAD_PACK, Actions.actionEntrys[Actions.ROAD_PACK]);
        }
    }

    static PackActionPerformer getPackActionPerformer(){
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
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (!TerraformBehaviours.isMattock(source) || !TileUtilities.isPackable(TilePos.fromXY(tileX, tileY))) {
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);
        }

        PackAction packTerraformer;
        if (!PackAction.hashMapHasInstance(action)) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();

            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));

            ConfigureActionOptions options = ConfigureOptions.getInstance().getPackActionOptions();
            packTerraformer = new PackAction(action, performer, source, SkillList.DIGGING, options.getMinSkill(),
                                                  options.getMaxSkill(), options.getLongestTime(),
                                                  options.getShortestTime(), options.getMinimumStamina(),
                                                  failureTestFunctions, TilePos.fromXY(tileX, tileY));
        }
        else
            packTerraformer = PackAction.actionDataWeakHashMap.get(action);

        if(packTerraformer.isActionStartTime(counter) && packTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (packTerraformer.isActionStartTime(counter)) {
            packTerraformer.doActionStartMessages();
            packTerraformer.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!packTerraformer.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (packTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        packTerraformer.packTile();
        packTerraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        packTerraformer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
