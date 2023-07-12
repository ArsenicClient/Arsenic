package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;

@ModuleInfo(name = "Criticals",category = ModuleCategory.BLATANT)
public class Criticals extends Module {
    public final EnumProperty<CritMode> critMode = new EnumProperty<>("Mode: ",CritMode.Offset);

    private boolean attack = false;


    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (mc.thePlayer != null && mc.theWorld != null) {
            switch (critMode.getValue()) {
                case Offset: {
                    if (event.getPacket() instanceof C02PacketUseEntity && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) {
                        mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                                mc.thePlayer.posX, mc.thePlayer.posY + 0.0625, mc.thePlayer.posZ, false
                        ));
                        mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(
                                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, false
                        ));
                    }
                }
                case Jump: {
                    if (event.getPacket() instanceof C02PacketUseEntity && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) {
                     attack = true;
                    }
                }
            }
        }
    };

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdateListener = event -> {
        if (mc.thePlayer != null && mc.theWorld != null) {
            if (mc.thePlayer.onGround && critMode.getValue() == CritMode.Jump && attack) {
                mc.thePlayer.jump();
                attack = false;
            }
        }
    };

    public enum CritMode {
        Offset,
        Jump,
    }
}
