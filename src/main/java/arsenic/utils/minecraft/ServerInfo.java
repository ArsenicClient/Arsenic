package arsenic.utils.minecraft;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

public class ServerInfo {
    private final Minecraft mc = Minecraft.getInstance();
    public int onGroundTicks,offGroundTicks;
    public float yaw,pitch;
    public boolean blocking,sprinting;

    private volatile boolean windowOpen;
    private volatile long chatUntil;
    private static final long CHAT_GRACE_MS = 100;

    public boolean isInGuiServerSide() {
        return windowOpen || System.currentTimeMillis() < chatUntil;
    }

    private volatile float sentYaw, sentPitch;
    private volatile boolean hasSentRotation;
    private volatile boolean serverSetRotation;

    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventUpdate.Pre> freezeRotationListener = e -> {
        if (hasSentRotation && isInGuiServerSide()) {
            e.setYaw(sentYaw);
            e.setPitch(sentPitch);
        }
    };

    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> windowOutListener = e -> {
        if (e.isCancelled())
            return;
        Packet<?> p = e.getPacket();
        if (p instanceof ServerboundMovePlayerPacket move && move.hasRotation()) {
            float packetYaw = move.getYRot(0), packetPitch = move.getXRot(0);
            if (serverSetRotation) {
                serverSetRotation = false;
                sentYaw = packetYaw;
                sentPitch = packetPitch;
                hasSentRotation = true;
            } else if (hasSentRotation && isInGuiServerSide()
                    && (packetYaw != sentYaw || packetPitch != sentPitch)) {
                e.setPacket(move.hasPosition()
                        ? new ServerboundMovePlayerPacket.Pos(move.getX(0), move.getY(0), move.getZ(0), move.isOnGround(), move.horizontalCollision())
                        : new ServerboundMovePlayerPacket.StatusOnly(move.isOnGround(), move.horizontalCollision()));
            } else {
                sentYaw = packetYaw;
                sentPitch = packetPitch;
                hasSentRotation = true;
            }
            return;
        }
        // 1.8 also sent an "inventory opened" status here; modern servers are never told
        if (p instanceof ServerboundContainerClickPacket) {
            windowOpen = true;
        } else if (p instanceof ServerboundContainerClosePacket) {
            windowOpen = false;
        } else if (p instanceof ServerboundChatPacket || p instanceof ServerboundChatCommandPacket) {
            chatUntil = System.currentTimeMillis() + CHAT_GRACE_MS;
        }
    };

    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.Incoming.Pre> windowInListener = e -> {
        Packet<?> p = e.getPacket();
        if (p instanceof ClientboundPlayerPositionPacket) {
            serverSetRotation = true;
        } else if (p instanceof ClientboundOpenScreenPacket) {
            windowOpen = true;
        } else if (p instanceof ClientboundContainerClosePacket || p instanceof ClientboundRespawnPacket || p instanceof ClientboundLoginPacket) {
            windowOpen = false;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> preListener = pre -> {
        pitch = pre.getPitch();
        yaw = pre.getYaw();
        if (mc.player.onGround()){
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            onGroundTicks = 0;
            offGroundTicks++;
        }
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> outGoingListener = e -> {
        if (e.getPacket() instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                sprinting = true;
            }
            if (command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING) {
                sprinting = false;
            }
        }
        if (e.getPacket() instanceof ServerboundUseItemPacket && mc.player != null && PlayerUtils.isPlayerHoldingSword()) {
            blocking = true;
        }
        if (e.getPacket() instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            blocking = false;
        }
    };
}
