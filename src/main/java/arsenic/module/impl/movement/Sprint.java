package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.KeyMapping;

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
