package arsenic.module.impl.ghost;

import arsenic.utils.io.Keys;
import arsenic.module.ModuleTier;
import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;

import static arsenic.utils.lag.LagManager.getPing;

@ModuleInfo(name = "SprintReset",category = ModuleCategory.COMBAT, hidden = true, tier = ModuleTier.LEGIT)
public class SprintReset extends Module {
    public int hurtTime = 1;
    public Player target;
    public final EnumProperty<wMode> mode = new EnumProperty<>("Mode", wMode.NORMAL);

    @Override
    public String getHudInfo() {
        return mode.getValue().name().toLowerCase();
    }

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof Player) {
            target = (Player) event.getTarget();
            hurtTime = Math.max(1, getPing()/20) + 1;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> movementInputListener = event -> {
        if(target == null || !target.isAlive() || target.getHealth() <= 0)
            return;

        switch (mode.getValue()) {
            case NORMAL:
                if (mc.player.isSprinting() && target.hurtTime == hurtTime) {
                    event.setSpeed(0);
                    resetSprint();
                    target = null;
                }
                break;
            case UNSPRINT:
                if (mc.player.isSprinting() && target.hurtTime == hurtTime) {
                    Keys.setDown(mc.options.keySprint, false);
                    resetSprint();
                    target = null;
                }
                break;
        }
    };

    public enum wMode {
        NORMAL,
        UNSPRINT
    }

    private void resetSprint() {
        mc.player.setSprinting(false);
        SyntheticKeys.press(SyntheticKeys.Key.SPRINT);
    }
}
