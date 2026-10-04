package arsenic.utils.minecraft;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;

/**
 * provides information about player's state(s) on server side
 */
public class ServerInfo {
    private final Minecraft mc = Minecraft.getMinecraft();
    public int onGroundTicks,offGroundTicks;
    public float yaw,pitch;
    public boolean blocking,sprinting;

    /**
     * Whether the server thinks a window is open: the player inventory (opening it sends
     * OPEN_INVENTORY_ACHIEVEMENT, clicking in it a click-window packet) or a container the server
     * opened. A close-window packet either way, a respawn or a new world closes it. Written from
     * the network threads, hence volatile.
     */
    private volatile boolean windowOpen;
    /** Until when a chat packet we sent counts as a screen being open (System.currentTimeMillis). */
    private volatile long chatUntil;
    /**
     * A chat message (or command) can only be typed with the chat screen open, so the tick one
     * goes out the server knows a screen was up. Covers that tick and the next.
     */
    private static final long CHAT_GRACE_MS = 100;

    /**
     * Whether the server thinks the player is in a screen right now - an inventory/container
     * window, or just sent a chat packet. Attacking or turning then is something a real client
     * can't do.
     */
    public boolean isInGuiServerSide() {
        return windowOpen || System.currentTimeMillis() < chatUntil;
    }

    /** The rotation the server last received, and whether there has been one yet. */
    private volatile float sentYaw, sentPitch;
    private volatile boolean hasSentRotation;
    /**
     * The server set our rotation (a teleport/look packet): the client's confirming reply carries
     * the rotation the server chose, so it goes out even in a screen and becomes the frozen one.
     */
    private volatile boolean serverSetRotation;

    /**
     * While the server thinks we're in a screen, the movement packet carries the rotation it
     * already has. Runs after every other listener (the silent rotation manager included), so
     * nothing can turn it back. Vanilla only adds a rotation to the packet when it differs from the
     * last one it reported, which this is exactly, so it sends position-only packets and its own
     * idea of the last reported rotation stays right.
     */
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventUpdate.Pre> freezeRotationListener = e -> {
        if (hasSentRotation && isInGuiServerSide()) {
            e.setYaw(sentYaw);
            e.setPitch(sentPitch);
        }
    };

    /**
     * What actually reaches the server: runs after every other listener, and skips packets they
     * cancelled (held by LagManager, say) - those count when they are finally sent.
     */
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> windowOutListener = e -> {
        if (e.isCancelled())
            return;
        Packet<?> p = e.getPacket();
        if (p instanceof C03PacketPlayer && ((C03PacketPlayer) p).getRotating()) {
            C03PacketPlayer c03 = (C03PacketPlayer) p;
            if (serverSetRotation) {
                serverSetRotation = false;
                sentYaw = c03.getYaw();
                sentPitch = c03.getPitch();
                hasSentRotation = true;
            } else if (hasSentRotation && isInGuiServerSide()
                    && (c03.getYaw() != sentYaw || c03.getPitch() != sentPitch)) {
                // Something sent a turn of its own while in a screen: keep the position, drop the look.
                e.setPacket(c03.isMoving()
                        ? new C03PacketPlayer.C04PacketPlayerPosition(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ(), c03.isOnGround())
                        : new C03PacketPlayer(c03.isOnGround()));
            } else {
                sentYaw = c03.getYaw();
                sentPitch = c03.getPitch();
                hasSentRotation = true;
            }
            return;
        }
        if (p instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) p).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            windowOpen = true;
        } else if (p instanceof C0EPacketClickWindow) {
            windowOpen = true;
        } else if (p instanceof C0DPacketCloseWindow) {
            windowOpen = false;
        } else if (p instanceof C01PacketChatMessage) {
            chatUntil = System.currentTimeMillis() + CHAT_GRACE_MS;
        }
    };

    /**
     * Windows the server opens or closes. Taken as they arrive, cancelled or not: a packet that is
     * being delayed was still sent, so the server already thinks the window is open (or shut).
     */
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.Incoming.Pre> windowInListener = e -> {
        Packet<?> p = e.getPacket();
        if (p instanceof S08PacketPlayerPosLook) {
            serverSetRotation = true;
        } else if (p instanceof S2DPacketOpenWindow) {
            windowOpen = true;
        } else if (p instanceof S2EPacketCloseWindow || p instanceof S07PacketRespawn || p instanceof S01PacketJoinGame) {
            windowOpen = false;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> preListener = pre -> {
        pitch = pre.getPitch();
        yaw = pre.getYaw();
        if (mc.thePlayer.onGround){
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            onGroundTicks = 0;
            offGroundTicks++;
        }
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> outGoingListener = e -> {
        if (e.getPacket() instanceof C0BPacketEntityAction) {
            if (((C0BPacketEntityAction) e.getPacket()).getAction() == C0BPacketEntityAction.Action.START_SPRINTING){
                sprinting = true;
            }
            if (((C0BPacketEntityAction) e.getPacket()).getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING){
                sprinting = false;
            }
        }
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement){
            if (((C08PacketPlayerBlockPlacement) e.getPacket()).getPlacedBlockDirection() == 255){
                if (PlayerUtils.isPlayerHoldingSword()){
                    blocking = true;
                }
            }
        }

        if (e.getPacket() instanceof C07PacketPlayerDigging){
            if (((C07PacketPlayerDigging) e.getPacket()).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM){
                blocking = false;
            }
        }
    };
}
