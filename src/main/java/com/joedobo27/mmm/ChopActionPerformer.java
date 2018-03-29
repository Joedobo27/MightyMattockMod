package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.joedobo27.libs.action.ActionMaster;
import com.wurmonline.math.TilePos;
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

public class ChopActionPerformer implements ModAction, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    ChopActionPerformer(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    private static class SingletonHelper {
        private static final ChopActionPerformer _performer;
        static {
            _performer = new ChopActionPerformer(Actions.CHOP, Actions.actionEntrys[Actions.CHOP]);
        }
    }

    static ChopActionPerformer getChopActionPerformer(){
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
    public boolean action(Action action, Creature performer, Item activeItem, int tileX, int tileY, boolean onSurface,
                          int heightOffset, int encodedTile, short actionId, float counter) {
        if (actionId != this.actionId || !TerraformBehaviours.isMattock(activeItem)) {
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);
        }
        TilePos targetTile = TilePos.fromXY(tileX, tileY);
        if (!Tiles.isTree(TileUtilities.getSurfaceTypeId(targetTile)) &&
                !Tiles.isBush(TileUtilities.getSurfaceTypeId(targetTile))) {
            return propagate(action, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);
        }

        ChopAction chopTerraformer;
        if (!ChopAction.hashMapHasInstance(action)) {
            ArrayList<Function<ActionMaster, Boolean>> failureTestFunctions = new ArrayList<>();
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_INSUFFICIENT_STAMINA));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_SERVER_BOARDER_TOO_CLOSE));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_TILE_GOD_PROTECTED));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVE_VILLAGE_ENEMY_TILE_ACTION));
            failureTestFunctions.add(getFunction(FAILURE_FUNCTION_PVP_VILLAGE_ENEMY_TILE_ACTION));

            ConfigureActionOptions options = ConfigureOptions.getInstance()
                    .getChopActionOptions();
            chopTerraformer = new ChopAction(action, performer, activeItem, SkillList.WOODCUTTING,
                                                  options.getMinSkill(), options.getMaxSkill(),
                                                  options.getLongestTime(), options.getShortestTime(),
                                                  options.getMinimumStamina(), failureTestFunctions, targetTile);
        }
        else
            chopTerraformer = ChopAction.actionDataWeakHashMap.get(action);

        if(chopTerraformer.isActionStartTime(counter) && chopTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, SERVER_PROPAGATION, ACTION_PERFORMER_PROPAGATION);

        if (chopTerraformer.isActionStartTime(counter)) {
            chopTerraformer.doActionStartMessages();
            chopTerraformer.setInitialTime(this.actionEntry);
            activeItem.setDamage(activeItem.getDamage() + 0.0015f * activeItem.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
        }

        if (!chopTerraformer.isActionTimedOut(action, counter))
            return propagate(action, CONTINUE_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        if (chopTerraformer.hasAFailureCondition())
            return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);

        double power = chopTerraformer.doSkillCheckAndGetPower(counter);
        chopTerraformer.makeFelledTreeOnGround(power);
        chopTerraformer.alterTileState();
        activeItem.setDamage(activeItem.getDamage() + 0.0015f * activeItem.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        chopTerraformer.doActionEndMessages();
        return propagate(action, FINISH_ACTION, NO_SERVER_PROPAGATION, NO_ACTION_PERFORMER_PROPAGATION);
    }
}
