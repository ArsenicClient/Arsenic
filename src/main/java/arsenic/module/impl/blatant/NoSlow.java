package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.MoveUtil;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.MOVEMENT)
public class NoSlow extends Module {
    public final EnumProperty<nMode> mode = new EnumProperty<nMode>("Mode", nMode.VANILLA) {
        @Override
        public void onValueUpdate() {
            switch (getValue()) {
                case HYPIXEL:
                    // wtf kv why does this not work
                    PlayerUtils.addWaterMarkedMessageToChat("NoSlow only works with Food and Bows!");
                    break;
            }
        }
    };

    public final BooleanProperty swordNoSlow = new BooleanProperty("Sword", true);
    public final BooleanProperty foodNoSlow = new BooleanProperty("Food", true);
    public final BooleanProperty potionNoSlow = new BooleanProperty("Potion", true);
    public final BooleanProperty bowNoSlow = new BooleanProperty("Bow", true);

    @EventLink
    public final Listener<EventUpdate.Pre> preListener = event -> {
        if (nullCheck()) {
            return;
        }
        if (mode.getValue() == nMode.HYPIXEL && mc.thePlayer.isUsingItem() && MoveUtil.isMoving()) {
            if (mc.thePlayer.getCurrentEquippedItem() != null && (mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemFood || mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemBow)) {
                mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 0, null, 0, 0, 0));
            }
        }
    };


    public enum nMode {
        VANILLA,
        HYPIXEL
    }
}