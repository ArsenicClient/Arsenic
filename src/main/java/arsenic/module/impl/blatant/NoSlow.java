package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import arsenic.utils.timer.Timer;

import java.util.function.Supplier;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.BLATANT)
public class NoSlow extends Module {

    public final EnumProperty<sMode> slowMode = new EnumProperty<sMode>("Mode: ", sMode.VANILLA) {
        @Override
        public void onValueUpdate() {
            switch (getValue()) {
                case ONHIT:
                    slow = () -> !timer.hasFinished();
                    break;
                case HYPIXEL:
                    PlayerUtils.addWaterMarkedMessageToChat("This Does Not Work Do Not Use");
                    PlayerUtils.addWaterMarkedMessageToChat("This Does Not Work Do Not Use");
                    PlayerUtils.addWaterMarkedMessageToChat("This Does Not Work Do Not Use");
                    PlayerUtils.addWaterMarkedMessageToChat("This Does Not Work Do Not Use");
                case VANILLA:
                    slow = () -> true;
                    break;
            }
        }
    };

    @PropertyInfo(reliesOn = "Mode: ", value = "ONHIT")
    public final DoubleProperty time = new DoubleProperty("length", new DoubleValue(0, 1000, 200, 1));

    private Supplier<Boolean> slow;
    private final Timer timer = new Timer();

    @Override
    protected void postApplyConfig() {
        slowMode.onValueUpdate();
    }

    @EventLink
    public final Listener<EventUpdate.Post> eventPacketPre = event -> {
        if(slowMode.getValue() != sMode.HYPIXEL || !mc.thePlayer.isUsingItem() || mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword))
            return;
        mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1));
        mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketListener = event -> {
        if(event.getPacket() instanceof S12PacketEntityVelocity && ((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId() && slowMode.getValue() == sMode.ONHIT) {
            timer.setCooldown((long) time.getValue().getInput());
            timer.start();
        }
    };

    public boolean shouldNotSlow() {
        return slow.get();
    }

    public enum sMode {
        VANILLA,
        HYPIXEL,
        ONHIT
    }
}
