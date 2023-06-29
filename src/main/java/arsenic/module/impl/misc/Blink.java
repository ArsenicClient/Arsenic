package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.util.ArrayList;

@ModuleInfo(name = "Blink",category = ModuleCategory.OTHER)
public class Blink extends Module {

    private ArrayList<Packet> packets = new ArrayList<>();

    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if(mc.theWorld == null)
            return;
        packets.add(event.getPacket());
    };

    @Override
    protected void onEnable() {
        packets.clear();
    }

    @Override
    protected void onDisable() {
        for(Packet packet : packets) {
            mc.getNetHandler().addToSendQueue(packet);
        }
    }
}