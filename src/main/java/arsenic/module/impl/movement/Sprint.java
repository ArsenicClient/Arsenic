package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.KeyMapping;

/**
 * Holds the sprint key for you.
 * <p>
 * It had three settings - a mode, all-directions and ignore-blindness - and only one combination is
 * sensible: hold the key, forwards only, and stop when blinded like the game intends. The other two
 * set {@code setSprinting(true)} directly, which is server-visible sprinting in states the vanilla
 * client would never sprint in, and is the difference between this module being invisible and being
 * the most obvious thing you are running. So there is nothing left to configure.
 */
@ModuleInfo(name = "Sprint", category = ModuleCategory.MOVEMENT, hidden = true)
public class Sprint extends Module {

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event ->
            mc.options.keySprint.setDown(true);

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
        mc.player.setSprinting(false);
    }
}
