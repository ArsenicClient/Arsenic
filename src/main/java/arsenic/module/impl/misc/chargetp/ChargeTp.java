package arsenic.module.impl.misc.chargetp;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventUpdate;
import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.java.PlayerInfo;
import arsenic.utils.minecraft.PlayerUtils;
<<<<<<< Updated upstream
import net.minecraft.client.entity.EntityPlayerSP;
=======
import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.EntityOtherPlayerMP;
>>>>>>> Stashed changes
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.util.MovementInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static arsenic.utils.mixin.UtilMixinEntityPlayerSP.*;


@ModuleInfo(name = "ChargeTp", category = ModuleCategory.OTHER)
public class ChargeTp extends Module {

    private final CustomNetworkHandler customNetworkManager = new CustomNetworkHandler(EnumPacketDirection.SERVERBOUND);
    private final CustomNetworkHandler voidNetworkManager = new CustomNetworkHandler(EnumPacketDirection.SERVERBOUND) {
        @Override
        public void sendPacket(Packet packetIn) {
            
        }
    };

    private final MovementInput customMovementInput = new MovementInput() {
        @Override
        public void updatePlayerMoveState() {
            moveForward = 1;
            jump = true;
        }
    };

    EntityOtherPlayerMP fakePlayer;

    private int ticks;

    private final List<PacketTick> packets = new ArrayList<>();

    public void addToPacketList(Packet<?> p, double ticks) {
        packets.add(new PacketTick(p, ticks));
    }

    private PlayerInfo chachePlayerInfo;

    @Override
    protected void postApplyConfig() {
        setEnabled(false);
    }

    @Override
    protected void onEnable() {
        if(mc.thePlayer == null) {
            setEnabled(false);
            return;
        }
        ticks = 0;
        packets.clear();
        chachePlayerInfo = getPlayerInfo(mc.thePlayer);
        fakePlayer = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), "$Charge"));
        mc.theWorld.addEntityToWorld(fakePlayer.getEntityId(), fakePlayer);
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
    public final Listener<EventRenderWorldLast> eventRenderWorldLastListener = eventRenderWorldLast -> {
        Arsenic.getArsenic().getEventManager().getBus().setFlag(true);
        NetHandlerPlayClient customNetHandler = new NetHandlerPlayClient(mc, mc.currentScreen, voidNetworkManager, mc.thePlayer.getGameProfile());
        CustomPlayer customPlayer = new CustomPlayer(customNetHandler);
        customPlayer.movementInput = customMovementInput;
        mc.theWorld.addEntityToWorld(customPlayer.getEntityId(), customPlayer);
        customPlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, 0);
        customPlayer.setSprinting(true);
        setPlayerInfo(customPlayer, chachePlayerInfo);
        for(int i = 0; i < ticks; i++) {
            customPlayer.update();
        }
        fakePlayer.setPosition(customPlayer.posX, customPlayer.posY, customPlayer.posZ);
        fakePlayer.rotationYawHead = mc.thePlayer.rotationYawHead;
        fakePlayer.rotationYaw = mc.thePlayer.rotationYaw;
        fakePlayer.rotationPitch = mc.thePlayer.rotationPitch;
        mc.theWorld.removeEntity(customPlayer);
        Arsenic.getArsenic().getEventManager().getBus().setFlag(false);
    };

    @EventLink
    public final Listener<EventMove> eventMove = event -> {
        event.setForward(0);
        event.setStrafe(0);
    };

    @Override
    protected void onDisable() {
        mc.theWorld.removeEntity(fakePlayer);
        NetHandlerPlayClient customNetHandler = new NetHandlerPlayClient(mc, mc.currentScreen, customNetworkManager, mc.thePlayer.getGameProfile());
        customNetworkManager.setNetHandler(customNetHandler);
        CustomPlayer customPlayer = new CustomPlayer(customNetHandler);
        customNetworkManager.setCustomPlayer(customPlayer);
        customPlayer.movementInput = customMovementInput;

        mc.theWorld.addEntityToWorld(customPlayer.getEntityId(), customPlayer);
        customPlayer.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, 0);
        customPlayer.setSprinting(true);
        setPlayerInfo(customPlayer, chachePlayerInfo);
        EntityPlayerSP realPlayer = mc.thePlayer;
        mc.thePlayer = customPlayer;
        for(int i = 0; i < ticks; i++) {
            customPlayer.update();
        }
        mc.thePlayer = realPlayer;
        customPlayer.setSprinting(mc.thePlayer.isSprinting());
        mc.thePlayer.setPosition(customPlayer.posX, customPlayer.posY, customPlayer.posZ);
        mc.theWorld.removeEntity(customPlayer);
        setPlayerInfo(mc.thePlayer, getPlayerInfo(customPlayer));
        packets.sort(Comparator.comparingDouble(PacketTick::getTick));
        packets.forEach(packet -> mc.getNetHandler().addToSendQueue(packet.getPacket()));
        mc.theWorld.removeEntityFromWorld(fakePlayer.getEntityId());
        fakePlayer = null;
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