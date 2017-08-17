package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.skills.SkillList;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.List;


public class MineAction implements ModAction, BehaviourProvider, ActionPerformer {
    private final ActionEntry actionEntry;
    private final short actionId;

    MineAction(short actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, int tileX, int tileY, boolean onSurface,
                                              Tiles.TileBorderDirection borderDirection, boolean border, int heightOffset) {
        if (subject.getTemplateId() != MightyMattockMod.getPickMattockTemplateID() || !border)
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection, border, heightOffset);
        TilePos opposingCorner = Terraformer.getOpposingCorner(performer, tileX, tileY, borderDirection);
        if (opposingCorner.x == -1 || opposingCorner.y == -1)
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection, border, heightOffset);
        if (TileUtilities.getDirtDepth(opposingCorner) > 0)
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection, border, heightOffset);

        ArrayList<ActionEntry> toReturn = new ArrayList<>();
        toReturn.add(new ActionEntry((short)(-4), "Terraform", ""));
        toReturn.add(Actions.actionEntrys[Actions.MINE]);
        toReturn.add(Actions.actionEntrys[MightyMattockMod.getRaiseRockEntryId()]);
        //toReturn.add(Actions.actionEntrys[Actions.LEVEL]);
        toReturn.add(Actions.actionEntrys[MightyMattockMod.getRaiseDirtEntryId()]);
        return toReturn;
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset,
                          Tiles.TileBorderDirection borderDirection, long borderId, short actionId, float counter) {

        if (actionId != this.actionId || source.getTemplateId() != MightyMattockMod.getPickMattockTemplateID())
            return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, borderDirection, borderId, actionId, counter);

        Terraformer terraformer;
        if (!Terraformer.hashMapHasInstance(action))
            terraformer = new Terraformer(action, performer, source, SkillList.MINING, 10, 95,
                    200, 10, TilePos.fromXY(tileX, tileY), onSurface, heightOffset, borderDirection,
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

        terraformer.modifyHeight(-1);
        terraformer.shouldMutableBeDirt();
        double power = terraformer.doSkillCheckAndGetPower(counter);
        source.setDamage(source.getDamage() + 0.0015f * source.getDamageModifier());
        performer.getStatus().modifyStamina(-5000.0f);
        terraformer.makeMinedItemOnGround(power);
        terraformer.doActionEndMessages();
        return true;
    }

}
