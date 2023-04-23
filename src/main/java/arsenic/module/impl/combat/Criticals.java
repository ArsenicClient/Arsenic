package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;

@ModuleInfo(name = "Criticals",category = ModuleCategory.BLATANT)
public class Criticals extends Module {

    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (mc.thePlayer != null && mc.theWorld != null && event.getPacket() instanceof C02PacketUseEntity && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) {
           mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                   mc.thePlayer.posX, mc.thePlayer.posY + 0.0625, mc.thePlayer.posZ, false
           ));
            mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, false
            ));
        }
    };
}
