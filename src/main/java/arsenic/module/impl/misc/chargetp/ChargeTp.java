package arsenic.module.impl.misc.chargetp;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.util.MovementInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@ModuleInfo(name = "ChargeTp", category = ModuleCategory.MOVEMENT)
public class ChargeTp extends Module {

    private final CustomNetworkHandler customNetworkManager = new CustomNetworkHandler(EnumPacketDirection.SERVERBOUND);

    private final MovementInput customMovementInput = new MovementInput() {
        @Override
        public void updatePlayerMoveState() {
            moveForward = 1;
            jump = true;
        }
    };
    private int ticks;

    private final List<PacketTick> packets = new ArrayList<>();

    public void addToPacketList(Packet<?> p, double ticks) {
        packets.add(new PacketTick(p, ticks));
    }

    @Override
    protected void postApplyConfig() {
        setEnabled(false);
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        packets.clear();
    }

    @EventLink
    public final Listener<EventPacket.OutGoing> eventPacket = event -> {
        if(mc.theWorld == null)
            return;
        Packet<?> packet = event.getPacket();
        if(packet instanceof C00PacketKeepAlive || packet instanceof C0FPacketConfirmTransaction)
            addToPacketList(packet, ticks + ((IMixinMinecraft) mc).getTimer().renderPartialTicks );
        event.cancel();
    };

    @EventLink
    public final Listener<EventUpdate.Post> eventUpdate = event -> ticks++;

    @EventLink
    public final Listener<EventMove> eventMove = event -> {
        event.setForward(0);
        event.setStrafe(0);
    };

    @Override
    protected void onDisable() {
        NetHandlerPlayClient customNetHandler = new NetHandlerPlayClient(mc, mc.currentScreen, customNetworkManager, mc.thePlayer.getGameProfile());
        customNetworkManager.setNetHandler(customNetHandler);
        CustomPlayer customPlayer = new CustomPlayer(customNetHandler);
        customNetworkManager.setCustomPlayer(customPlayer);
        customPlayer.movementInput = customMovementInput;

        mc.theWorld.addEntityToWorld(9999, customPlayer);
        customPlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, 0);
        customPlayer.setSprinting(mc.thePlayer.isSprinting());
        for(int i = 0; i < ticks; i++) {
            customPlayer.update();
        }
        customPlayer.setSprinting(mc.thePlayer.isSprinting());
        mc.thePlayer.setPosition(customPlayer.posX, customPlayer.posY, customPlayer.posZ);
        mc.theWorld.removeEntity(customPlayer);
        packets.sort(Comparator.comparingDouble(PacketTick::getTick));
        packets.forEach(packet -> {
            mc.getNetHandler().addToSendQueue(packet.getPacket());
            PlayerUtils.addWaterMarkedMessageToChat(packet.getPacket().getClass().getName());
        });
    }

    private static class PacketTick {
        public PacketTick(Packet<?> packet, double tick) {
            this.packet = packet;
            this.tick = tick;
        }

        public Packet<?> getPacket() {
            return packet;
        }

        public double getTick() {
            return tick;
        }

        private final Packet<?> packet;
        private final double tick;
    }
}
