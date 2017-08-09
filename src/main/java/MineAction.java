import com.wurmonline.server.Constants;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.zones.Zones;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;


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


    private static boolean hasAFailureCondition(Creature performer, Item source, int tileX, int tileY, boolean onSurface,
                                                int heightOffset, int encodedTile, short aActionId, float counter){
        int depthMultipliers = 3;
        if (performer.getStatus().getStamina() < 6000) {
            performer.getCommunicator().sendNormalServerMessage("You don't have enough stamina to mine.");
            return true;
        }
        boolean isTooCloseToServerBoarder = tileX < 0 || tileX > 1 << Constants.meshSize || tileY < 0 || tileY > 1 << Constants.meshSize;
        if (isTooCloseToServerBoarder){
            performer.getCommunicator().sendNormalServerMessage("You can't mine this close to the server boarder.", (byte)3);
            return true;
        }
        if (Zones.isTileProtected(tileX, tileY)) {
            performer.getCommunicator().sendNormalServerMessage("This tile is protected by the gods. You can not mine here.", (byte)3);
            return true;
        }
        return false;
    }
}
