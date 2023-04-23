package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

@ModuleInfo(name = "Scaffold",category = ModuleCategory.WORLD)
public class Scaffold extends Module {
    public final DoubleProperty delay = new DoubleProperty("Reach", new DoubleValue(0, 100, 25, 1));
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        BlockPos placePos = new BlockPos((int) Math.floor(mc.thePlayer.getPositionVector().xCoord),(int) Math.floor(mc.thePlayer.getPositionVector().yCoord) - 1,(int) Math.floor(mc.thePlayer.getPositionVector().zCoord));
        if (mc.thePlayer.getHeldItem() != null) {
            ItemStack heldItem = mc.thePlayer.getHeldItem();
            if (PlayerUtils.playerOverAir() && heldItem.getItem() instanceof ItemBlock) {
                C08PacketPlayerBlockPlacement packet = new C08PacketPlayerBlockPlacement(
                        placePos, EnumFacing.UP.getIndex(), null, 0, 0, 0);
                Minecraft.getMinecraft().getNetHandler().addToSendQueue(packet);
            }
        }
    };
}
