package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.network.play.server.S06PacketUpdateHealth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.BLATANT)
public class NoSlow extends Module {

    public EnumProperty<sMode> slowMode = new EnumProperty<sMode>("Mode: ", sMode.VANILLA) {
        @Override
        public void onValueUpdate() {
            switch (getValue()) {
                case ONHIT:
                    slow = false;
                    break;
                case VANILLA:
                    slow = true;
                    break;
            }
        }
    };

    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public DoubleProperty time = new DoubleProperty("length", new DoubleValue(0, 1000, 200, 1));

    private boolean slow;

    @Override
    protected void postApplyConfig() {
        slowMode.onValueUpdate();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketListener = event -> {
        if(event.getPacket() instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth) event.getPacket()).getHealth() < mc.thePlayer.getHealth() && slowMode.getValue() == sMode.ONHIT) {
            slow = true;
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    Thread.sleep((long) time.getValue().getInput());
                } catch (InterruptedException e) {}
                if(slowMode.getValue() == sMode.ONHIT)
                    slow = false;
            });
        }
    };

    public boolean shouldNotSlow() {
        return slow;
    }

    public enum sMode {
        VANILLA,
        ONHIT;
    }
}
