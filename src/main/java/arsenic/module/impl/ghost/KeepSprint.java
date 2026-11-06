package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.io.Keys;

/**
 * After you hit something while sprinting forward, briefly lets go of W and presses it again a random number of ticks
 * later, so sprint is re-applied server side and the next hit keeps its sprint knockback.
 */
@ModuleInfo(name = "KeepSprint", description = "Re-presses W after a sprint hit to restore sprint", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class KeepSprint extends Module {

    /** Ticks between releasing W and pressing it again; randomised so the cadence is not a fixed fingerprint. */
    public final RangeProperty restoreDelay = new RangeProperty("Restore Delay", new RangeValue(0, 3, 1, 2, 1));

    /** An attack landed while sprinting forward; handle it on the next tick. */
    private boolean pending;
    /** Ticks left until W is pressed again; -1 when W is not being held off. */
    private int restoreTicks = -1;

    @Override
    public String getHudInfo() {
        return (int) restoreDelay.getValue().getMin() + "-" + (int) restoreDelay.getValue().getMax();
    }

    @Override
    protected void onDisable() {
        if (restoreTicks >= 0)
            restoreForward();
        pending = false;
        restoreTicks = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() != null && mc.player.isSprinting() && forwardHeld())
            pending = true;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        // a WTap module, when present and on, does this job itself
        Module wTap = Arsenic.getArsenic().getModuleManager().getModuleByName("WTap");
        if (wTap != null && wTap != this && wTap.isEnabled()) {
            if (restoreTicks >= 0)
                restoreForward();
            pending = false;
            restoreTicks = -1;
            return;
        }

        if (restoreTicks > 0) {
            restoreTicks--;
            return;
        }
        if (restoreTicks == 0) {
            restoreTicks = -1;
            restoreForward();
        }

        if (!pending)
            return;
        pending = false;

        if (mc.options.keyShift.isDown() || mc.player.isShiftKeyDown() || !forwardHeld()
                || mc.player.horizontalCollision || mc.player.isUsingItem()
                || mc.player.getFoodData().getFoodLevel() <= 6)
            return;

        Keys.setDown(mc.options.keyUp, false);
        restoreTicks = (int) Math.round(restoreDelay.getValue().getRandomInRange());
    };

    private boolean forwardHeld() {
        return Keys.isPhysicallyDown(mc.options.keyUp);
    }

    /** Presses W again if the player is still holding it, and resumes sprinting. */
    private void restoreForward() {
        if (mc.player == null || !forwardHeld())
            return;
        Keys.setDown(mc.options.keyUp, true);
        mc.player.setSprinting(true);
    }
}
