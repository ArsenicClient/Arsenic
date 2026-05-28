package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.item.EnumAction;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import static arsenic.utils.lag.LagManager.*;

@ModuleInfo(name = "NoSlow2", category = ModuleCategory.MOVEMENT)
public class NoSlow2 extends Module {

    public final DoubleProperty maxTicks = new DoubleProperty("Max Ticks", new DoubleValue(1, 20, 10, 1));
    private boolean fakePacket;
    private boolean pendingRelease = false;
    private int ticksElapsed = 0;

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        if (pendingRelease) {
            pendingRelease = false;
            fakePacket = true;
            sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            fakePacket = false;
        }

        if (itemInUse() && !isHolding(getClass())) {
            acquire(getClass());
            pendingRelease = true;
            ticksElapsed = 0;
        }

        if (isHolding(getClass())) {
            ticksElapsed++;
            if (ticksElapsed >= maxTicks.getValue().getInput()) {
                release(getClass());
                ticksElapsed = 0;
                if (itemInUse()) {
                    sendPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, mc.thePlayer.getHeldItem(), 0.0F, 0.0F, 0.0F));
                }
            }
        }
    };

    @RequiresPlayer
    @EventLink(value = Priorities.LOW)
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (!fakePacket && event.getPacket() instanceof C07PacketPlayerDigging && isHolding(getClass())) {
            C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
            if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                event.cancel();
                release(getClass());
            }
        }
    };

    public boolean itemInUse() {
        if (mc.thePlayer.getHeldItem() == null || !mc.thePlayer.isUsingItem())
            return false;
        return mc.thePlayer.getHeldItem().getItem().getItemUseAction(mc.thePlayer.getHeldItem()) == EnumAction.BLOCK;
    }

    public boolean mixinResult() {
        return itemInUse() && !isHolding(getClass());
    }

    @Override
    protected void onDisable() {
        release(getClass());
        pendingRelease = false;
        ticksElapsed = 0;
    }
}