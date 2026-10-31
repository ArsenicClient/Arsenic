package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.world.BridgeAssist;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.impl.world.TellyScaffold;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ScaffoldUtil;
import arsenic.utils.scaffoldcore.ScaffoldCore;

/**
 * Sneaks for you on the tick before a step that would walk you off an edge, and lets go the tick after. It presses the
 * real sneak key, only when the fall is certain, so it never holds shift or taps it for nothing.
 */
@ModuleInfo(name = "SafeWalk", description = "Shifts at an edge so you never walk off it", category = ModuleCategory.MOVEMENT, tier = ModuleTier.LEGIT)
public class SafeWalk extends Module {

    private static final double FALL_MARGIN = 0.03;

    private boolean holding;

    @Override
    protected void onDisable() {
        if (holding)
            release();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onTick = event -> {
        if (otherSneaker() || mc.gui.screen() != null || !mc.player.onGround()
                || !(mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown())) {
            if (holding)
                release();
            return;
        }
        ScaffoldCore.Input in = ScaffoldUtil.coreInput(true);
        if (in.moveScale != 1f) {
            in.moveForward /= in.moveScale;
            in.moveStrafe /= in.moveScale;
        }
        boolean fall = ScaffoldCore.willFall(in, ScaffoldUtil.WORLD, 1.0, FALL_MARGIN);
        if (fall) {
            mc.options.keyShift.setDown(true);
            holding = true;
        } else if (holding) {
            release();
        }
    };

    /** Scaffold and BridgeAssist press sneak themselves. */
    private static boolean otherSneaker() {
        return isOn(Scaffold.class) || isOn(TellyScaffold.class) || isOn(BridgeAssist.class);
    }

    private static boolean isOn(Class<? extends Module> type) {
        Module m = Arsenic.getArsenic().getModuleManager().getModuleByClass(type);
        return m != null && m.isEnabled();
    }

    /** Hands the sneak key back to the player. */
    private void release() {
        holding = false;
        mc.options.keyShift.setDown(mc.gui.screen() == null && Keys.isPhysicallyDown(mc.options.keyShift));
    }
}
