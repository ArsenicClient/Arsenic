package arsenic.module.impl.world;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import static arsenic.utils.minecraft.ScaffoldUtil.willFallNextTick;

/**
 * Sneaks for you at the edge of a bridge, so walking backwards off the block you are placing on is
 * no longer possible.
 * <p>
 * This was SafeWalk, with seven properties describing when it was allowed to act. The combination
 * that people actually used - hold S, look down, sneak at the edge - is now simply how it behaves:
 * it engages while you are walking backwards with your pitch below the horizon, and does nothing
 * otherwise. Nothing here is a preference, so nothing here is a setting.
 */
@ModuleInfo(name = "BridgeAssist", category = ModuleCategory.MOVEMENT)
public class BridgeAssist extends Module {

    /**
     * Pitch below which the player is considered to be bridging. 65 degrees is steep enough that
     * ordinary walking and fighting never trip it, and shallow enough for a normal bridging angle.
     */
    private static final double BRIDGE_PITCH = 65.0;

    /**
     * Look-ahead margin for the fall check, and the one thing worth tuning here: larger sneaks
     * earlier and more often, which never lets you fall but does mean visibly sneaking further from
     * the edge than a person would.
     */
    public final DoubleProperty safety = new DoubleProperty("Safety", new DoubleValue(1, 3, 1, 0.1));

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> tickEvent = tickEvent -> {
        if (mc.currentScreen != null || !mc.thePlayer.onGround) {
            setSneak(false);
            return;
        }

        // Backwards, and looking down far enough to be placing blocks.
        boolean bridging = mc.gameSettings.keyBindBack.isKeyDown()
                && mc.thePlayer.rotationPitch >= BRIDGE_PITCH;

        setSneak(bridging && willFallNextTick(safety.getValue().getInput()));
    };

    /**
     * Drives the sneak keybind without ever fighting the player for it.
     * <p>
     * The old version called {@code setKeyBindState(sneak, false)} outright on every frame it did
     * not want to sneak, which cancelled the player's own shift the moment they pressed it - you
     * could not manually sneak while the module was enabled. Folding the physical key state into the
     * result means the module can only ever <em>add</em> sneaking.
     */
    private void setSneak(boolean wanted) {
        int key = mc.gameSettings.keyBindSneak.getKeyCode();
        KeyBinding.setKeyBindState(key, wanted || Keyboard.isKeyDown(key));
    }

    @Override
    public void onDisable() {
        setSneak(false);
    }
}
