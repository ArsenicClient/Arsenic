package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.injection.accessor.C03PacketPlayerAccessor;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;

@ModuleInfo(name = "Criticals", category = ModuleCategory.BLATANT)
public class Criticals extends Module {
    public final EnumProperty<CritMode> critMode = new EnumProperty<>("Mode: ", CritMode.Jump);

    private boolean attack = false;


    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (mc.thePlayer != null && mc.theWorld != null) {
            if (critMode.getValue() == CritMode.Jump) {
                if (event.getPacket() instanceof C02PacketUseEntity && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK) {
                    attack = true;
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
        Jump,
    }
}
