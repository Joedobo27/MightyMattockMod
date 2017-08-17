package com.joedobo27.mmm;

import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

public class LevelAction implements ModAction, BehaviourProvider, ActionPerformer {

    private final ActionEntry actionEntry;
    private final short actionId;

    LevelAction(short actionId, ActionEntry actionEntry) {
        this.actionId = actionId;
        this.actionEntry = actionEntry;
    }

    @Override
    public short getActionId() {
        return this.actionId;
    }

    @Override
    public boolean action(Action act, Creature performer, int tileX, int tileY, boolean onSurface, Tiles.TileBorderDirection dir,
                          long borderId, short action, float counter) {
        return ActionPerformer.super.action(act, performer, tileX, tileY, onSurface, dir, borderId, action, counter);
    }

    @Override
    public boolean action(Action action, Creature performer, Item source, int tileX, int tileY, boolean onSurface, int heightOffset,
                          Tiles.TileBorderDirection borderDirection, long borderId, short actionId, float counter) {
        if (actionId != this.actionId || source.getTemplateId() != MightyMattockMod.getPickMattockTemplateID())
            return ActionPerformer.super.action(action, performer, source, tileX, tileY, onSurface, heightOffset, borderDirection, borderId, actionId, counter);
        return true;
    }
}
