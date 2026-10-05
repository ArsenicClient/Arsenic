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
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;

import static arsenic.utils.lag.LagManager.getPing;

@ModuleInfo(name = "SprintReset",category = ModuleCategory.COMBAT, hidden = true)
public class SprintReset extends Module {
    public int hurtTime = 1;
    public Player target;
    public final EnumProperty<wMode> mode = new EnumProperty<>("Mode", wMode.COMBO);
    public boolean hasTapped = false;

    private boolean sawHurt = false;
    private final MSTimer attackTimer = new MSTimer();

    @Override
    public String getHudInfo() {
        return mode.getValue().name().toLowerCase();
    }

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof Player) {
            target = (Player) event.getTarget();
            hasTapped = false;
            sawHurt = false;
            attackTimer.reset();
            hurtTime = Math.max(1, getPing()/20) + 1;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> movementInputListener = event -> {
        if(target == null || target.isRemoved() || target.getHealth() <= 0)
            return;

        switch (mode.getValue()) {
            case COMBO:
                double distToTarget = RotationUtils.getDistanceToEntityBox(target);
                double targetDistToPlayer = RotationUtils.getDistanceToEntityBox(mc.player, target);

                if (target.hurtTime > 0)
                    sawHurt = true;

                if (distToTarget > 4.5) {
                    target = null;
                    return;
                }
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
                    mc.player.setSprinting(false);
                    hasTapped = true;
                    return;
                }

                break;
            case NORMAL:
                if (mc.player.isSprinting() && target.hurtTime == hurtTime) {
                    event.setSpeed(0);
                    mc.player.setSprinting(false);
                    target = null;
                }
                break;
            case UNSPRINT:
                if (mc.player.isSprinting() && target.hurtTime == hurtTime) {
                    mc.options.keySprint.setDown(false);
                    mc.player.setSprinting(false);
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
