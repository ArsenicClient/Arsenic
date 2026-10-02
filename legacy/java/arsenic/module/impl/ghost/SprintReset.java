package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;

import static arsenic.utils.lag.LagManager.getPing;

@ModuleInfo(name = "SprintReset",category = ModuleCategory.COMBAT, hidden = true)
public class SprintReset extends Module {
    public int hurtTime = 1;
    public EntityPlayer target;
    public final EnumProperty<wMode> mode = new EnumProperty<>("Mode", wMode.COMBO);
    public boolean hasTapped = false;

    /**
     * Whether the server has actually acknowledged our hit yet, i.e. we have seen the target's
     * hurtTime go above zero since we attacked. COMBO must not judge the hurt window before this
     * is true - see the guard in the movement listener.
     */
    private boolean sawHurt = false;
    /** Guards against a target sticking forever when a hit simply did not land. */
    private final MSTimer attackTimer = new MSTimer();

    @Override
    public String getHudInfo() {
        return mode.getValue().name().toLowerCase();
    }

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof EntityPlayer) {
            target = (EntityPlayer) event.getTarget();
            hasTapped = false;
            sawHurt = false;
            attackTimer.reset();
            hurtTime = Math.max(1, getPing()/20) + 1;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> movementInputListener = event -> {
        if(target == null || target.isDead || target.getHealth() <= 0)
            return;

        switch (mode.getValue()) {
            case COMBO:
                double distToTarget = RotationUtils.getDistanceToEntityBox(target);
                double targetDistToPlayer = RotationUtils.getDistanceToEntityBox(mc.thePlayer, target);

                // hurtTime is set client side only when the server's damage packet arrives, a full
                // round trip after we swing. On the ticks in between it is still 0, so testing
                // "hurtTime < hurtTime" straight away threw the target out before the hit had ever
                // been acknowledged - which is why COMBO only fired when the packet happened to
                // land inside the same tick. Wait until we have actually seen the target flinch
                // before judging the window.
                if (target.hurtTime > 0)
                    sawHurt = true;

                if (distToTarget > 4.5) {
                    target = null;
                    return;
                }
                // Nothing came back at all: give up rather than hold a stale target forever.
                if (!sawHurt && attackTimer.hasTimeElapsed(getPing() + 500L)) {
                    target = null;
                    return;
                }
                if (sawHurt && target.hurtTime < hurtTime) {
                    target = null;
                    return;
                }
                if(!hasTapped || (targetDistToPlayer < 3.05 && distToTarget < 2.95)) {
                    event.setSpeed(0);
                    mc.thePlayer.setSprinting(false);
                    hasTapped = true;
                    return;
                }

                break;
            case NORMAL:
                if (mc.thePlayer.isSprinting() && target.hurtTime == hurtTime) {
                    event.setSpeed(0);
                    mc.thePlayer.setSprinting(false);
                    target = null;
                }
                break;
            case UNSPRINT:
                if (mc.thePlayer.isSprinting() && target.hurtTime == hurtTime) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                    mc.thePlayer.setSprinting(false);
                    target = null;
                }
                break;
        }
    };

    public enum wMode {
        NORMAL,
        COMBO,
        UNSPRINT
    }

}
