package com.joedobo27.mmm;

import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

public class RaiseDirtAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final ActionEntry actionEntry;
    private final int actionId;

    RaiseDirtAction(int actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId() {
        return (short)this.actionId;
    }


    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset,
                          Tiles.TileBorderDirection borderDirection, long borderId, short actionId, float counter) {
        if (actionId != this.actionId || source.getTemplateId() != MightyMattockMod.getPickMattockTemplateID())
            return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, borderDirection, borderId, actionId, counter);

        Terraformer terraformer;
        if (!Terraformer.hashMapHasInstance(action))
            terraformer = new Terraformer(action, performer, source, SkillList.DIGGING, 10, 95,
                    30, 10, TilePos.fromXY(tileX, tileY), onSurface, heightOffset, borderDirection,
                    borderId, actionId, Terraformer.getOpposingCorner(performer, tileX, tileY, borderDirection));
        else
            terraformer = Terraformer.actionDataWeakHashMap.get(action);

        if(terraformer.isActionStartTime(counter) && terraformer.hasAFailureCondition())
            return true;

        if (terraformer.isActionStartTime(counter)) {
            terraformer.doActionStartMessages();
            terraformer.setInitialTime(this.actionEntry);
            source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
            performer.getStatus().modifyStamina(-1000.0f);
        }

        if (!terraformer.isActionTimedOut(action, counter))
            return false;

        if (terraformer.hasAFailureCondition())
            return true;

        terraformer.modifyHeight(1);
        terraformer.shouldRockBeDirt();
        terraformer.destroyRaiseResource();
        terraformer.shouldMutableBeDirt();
        terraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        terraformer.doActionEndMessages();
        return true;
    }
}
