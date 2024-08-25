package arsenic.module.impl.player;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.injection.accessor.C03PacketPlayerAccessor;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.network.play.client.C03PacketPlayer;

@ModuleInfo(name = "NoFall", category = ModuleCategory.PLAYER)
public class NoFall extends Module {

    public final EnumProperty<nMode> nofallMode = new EnumProperty<>("Mode: ", nMode.NoGround);
    private boolean spoof = false;
    private boolean stopShit = false;
    private int waitTicks = 0;
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (mc.thePlayer != null && mc.theWorld != null) {
            switch (nofallMode.getValue()) {
                case NoGround:
                    if (event.getPacket() instanceof C03PacketPlayer) {
                        if (spoof) {
                            ((C03PacketPlayerAccessor) event.getPacket()).setOnGround(false);
                            PlayerUtils.addWaterMarkedMessageToChat("spoof");
                        }
                    }
                    break;
            }
        }
    };

    @EventLink
    public final Listener<EventUpdate.Pre> preListener = event -> {
        if (mc.thePlayer != null && mc.theWorld != null) {
            switch (nofallMode.getValue()) {
                case NoGround:
                    if (mc.thePlayer.fallDistance >= 4) {
                        spoof = true;
                    }
                    if (spoof && mc.thePlayer.onGround) {
                        stopShit = true;
                    }
                    if (stopShit) {
                        waitTicks++;
                    }
                    if (waitTicks >= 20) {
                        spoof = false;
                        stopShit = false;
                        waitTicks = 0;
                    }
                    break;
            }
        }
    };

    public enum nMode {
        NoGround
    }
}
