package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.network.play.client.C03PacketPlayer;

@ModuleInfo(name = "Blink",category = ModuleCategory.OTHER)
public class Blink extends Module {

    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (event.getPacket() instanceof C03PacketPlayer && mc.theWorld != null) {
            event.setCancelled(true);
        }
    };
}