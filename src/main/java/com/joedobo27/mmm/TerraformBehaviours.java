package com.joedobo27.mmm;

import com.joedobo27.libs.TileUtilities;
import com.wurmonline.math.TilePos;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Actions;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;

import java.util.ArrayList;
import java.util.List;

import static com.joedobo27.libs.TileUtilities.getPerformerNearestTile;

public class TerraformBehaviours implements ModAction, BehaviourProvider {

    TerraformBehaviours(){}

    /**
     * Chop; Cut down trees and bushes.
     */
    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item activeItem, int tileX, int tileY,
                                              boolean onSurface, int encodedTile) {
        if (!isMattock(activeItem))
            return BehaviourProvider.super.getBehavioursFor(performer, activeItem, tileX, tileY, onSurface, encodedTile);
        ArrayList<ActionEntry> toReturn = new ArrayList<>();
        TilePos actionTile = TilePos.fromXY(tileX, tileY);
        if (TileUtilities.isPackable(actionTile))
            toReturn.add(Actions.actionEntrys[Actions.ROAD_PACK]);
        Tiles.Tile tileType = Tiles.getTile(TileUtilities.getSurfaceTypeId(actionTile));
        if (tileType != null && (tileType.isTree() || tileType.isBush()))
            toReturn.add(Actions.actionEntrys[Actions.CHOP]);
        if (TileUtilities.isResourceTile(actionTile))
            toReturn.add(Actions.actionEntrys[MightyMattockMod.getCollectResourceEntryId()]);
        return toReturn;
    }

    /**
     * Mine, Raise rock, Dig, Raise dirt; Change tile borders by raising or lowering the opposing corner.
     */
    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, int tileX, int tileY, boolean onSurface,
                                              Tiles.TileBorderDirection borderDirection, boolean border,
                                              int heightOffset) {
        if (!isMattock(subject) || !border)
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection,
                    border, heightOffset);
        TilePos opposingCorner = getOpposingCorner(performer, tileX, tileY, borderDirection);
        if (opposingCorner.x == -1 || opposingCorner.y == -1)
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection,
                    true, heightOffset);
        if (!performerWithinOneTile(performer, opposingCorner))
            return BehaviourProvider.super.getBehavioursFor(performer, subject, tileX, tileY, onSurface, borderDirection,
                    true, heightOffset);
        if (TileUtilities.getDirtDepth(opposingCorner) <= 0) {
            ArrayList<ActionEntry> toReturn = new ArrayList<>();
            if (onSurface)
                toReturn.add(new ActionEntry((short) (-3), "Terraform", ""));
            else
                toReturn.add(new ActionEntry((short) (-2), "Terraform", ""));
            toReturn.add(Actions.actionEntrys[Actions.MINE]);
            toReturn.add(Actions.actionEntrys[MightyMattockMod.getRaiseRockEntryId()]);
            //toReturn.add(Actions.actionEntrys[Actions.LEVEL]);
            if (onSurface)
                toReturn.add(Actions.actionEntrys[MightyMattockMod.getRaiseDirtEntryId()]);
            return toReturn;
        }
        if (TileUtilities.getDirtDepth(opposingCorner) > 0) {
            ArrayList<ActionEntry> toReturn = new ArrayList<>();
            toReturn.add(new ActionEntry((short) (-2), "Terraform", ""));
            toReturn.add(Actions.actionEntrys[Actions.DIG]);
            toReturn.add(Actions.actionEntrys[MightyMattockMod.getRaiseDirtEntryId()]);
            //toReturn.add(Actions.actionEntrys[Actions.LEVEL]);
            return toReturn;
        }
        return null;
    }

    static TilePos getOpposingCorner(Creature performer, int tileX, int tileY, Tiles.TileBorderDirection borderDirection) {
        if (borderDirection == null)
            return null;
        TilePos tilePosPerformer = getPerformerNearestTile(performer);
        TilePos tilePosTarget = TilePos.fromXY(tileX, tileY);
        TilePos tilePosOpposing = TilePos.fromXY(-1, -1);
        if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_HORIZ.getCode() &&
                (int)Math.signum(tilePosPerformer.x - tilePosTarget.x) == 0)
            tilePosOpposing = tilePosTarget.East();
        else if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_HORIZ.getCode() &&
                (int)Math.signum(tilePosPerformer.x - tilePosTarget.x) == 1)
            tilePosOpposing = tilePosTarget;
        else if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_DOWN.getCode() &&
                (int)Math.signum(tilePosPerformer.y - tilePosTarget.y) == 0)
            tilePosOpposing = tilePosTarget.South();
        else if (borderDirection.getCode() == Tiles.TileBorderDirection.DIR_DOWN.getCode() &&
                (int)Math.signum(tilePosPerformer.y - tilePosTarget.y) == 1)
            tilePosOpposing = tilePosTarget;
        return tilePosOpposing;
    }

    static boolean isMattock(Item source) {
        return source != null && (source.getTemplateId() == MightyMattockMod.getPickMattockTemplateID() ||
                source.isWand());
    }

    private static boolean performerWithinOneTile(Creature performer, TilePos opposingCorner) {
        TilePos performerTile = TileUtilities.getPerformerNearestTile(performer);
        return Math.abs(performerTile.x - opposingCorner.x) <= 1 && Math.abs(performerTile.y - opposingCorner.y) <= 1;
    }
}
