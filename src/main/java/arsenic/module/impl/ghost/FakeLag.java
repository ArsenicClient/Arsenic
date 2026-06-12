package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.C03PacketPlayerAccessor;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.ghost.fakelag.QueueData;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "FakeLag", category = ModuleCategory.GHOST)
public class FakeLag extends Module {

    public enum LagStyle {
        PULSE, SMOOTH
    }

    public enum DistanceMode {
        ALLOW, FORBID, IGNORE
    }

    public enum HurtTimeMode {
        ALLOW, FORBID, IGNORE
    }

    public final EnumProperty<LagStyle> style = new EnumProperty<>("Style", LagStyle.SMOOTH);
    public final DoubleProperty delay = new DoubleProperty("Delay", new DoubleValue(0, 1000, 550, 10));
    public final DoubleProperty recoilTime = new DoubleProperty("RecoilTime", new DoubleValue(0, 2000, 750, 10));

    public final EnumProperty<DistanceMode> clientDistanceHandling = new EnumProperty<>("ClientDistance", DistanceMode.FORBID);
    public final RangeProperty clientDistance = new RangeProperty("ClientDistRange", new RangeValue(0, 6, 1.5, 3.5, 0.1));
    public final EnumProperty<DistanceMode> serverDistanceHandling = new EnumProperty<>("ServerDistance", DistanceMode.FORBID);
    public final RangeProperty serverDistance = new RangeProperty("ServerDistRange", new RangeValue(0, 6, 1.5, 3.5, 0.1));

    public final BooleanProperty smart = new BooleanProperty("Smart", true);
    public final DoubleProperty advantageThreshold = new DoubleProperty("Advantage", new DoubleValue(0, 1, 0, 0.05));

    public final EnumProperty<HurtTimeMode> hurtTimeHandling = new EnumProperty<>("HurtTime", HurtTimeMode.ALLOW);
    public final RangeProperty hurtTime = new RangeProperty("HurtTimeRange", new RangeValue(0, 10, 0, 0, 1));

    public final BooleanProperty blinkOnAction = new BooleanProperty("BlinkOnAction", true);
    public final BooleanProperty pauseOnNoMove = new BooleanProperty("PauseOnNoMove", true);
    public final BooleanProperty pauseOnChest = new BooleanProperty("PauseOnChest", false);

    public final BooleanProperty line = new BooleanProperty("Line", true);
    public final ColourProperty lineColor = new ColourProperty("LineColor", 0xFF00FF00);

    private final Queue<QueueData> packetQueue = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Vec3> positions = new ArrayDeque<>();
    private final MSTimer pulseTimer = new MSTimer();
    private final MSTimer resetTimer = new MSTimer();
    private boolean ignoreWholeTick = false;

    @Override
    public void onDisable() {
        if (mc.thePlayer == null) return;
        blink();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (event.isCancelled()) return;

        if (checkShouldFlush()) return;

        EntityPlayerSP player = mc.thePlayer;
        Packet<?> packet = event.getPacket();

        if (packet instanceof C00Handshake || packet instanceof C00PacketServerQuery
                || packet instanceof C01PacketPing || packet instanceof C01PacketChatMessage
                || packet instanceof S01PacketPong) {
            return;
        }

        if (packet instanceof C0EPacketClickWindow || packet instanceof C0DPacketCloseWindow) {
            blink();
            return;
        }

        if (packet instanceof C08PacketPlayerBlockPlacement || packet instanceof C07PacketPlayerDigging
                || packet instanceof C12PacketUpdateSign || packet instanceof C19PacketResourcePackStatus) {
            blink();
            return;
        }

        if (blinkOnAction.getValue() && packet instanceof C02PacketUseEntity) {
            blink();
            return;
        }

        if (style.getValue() == LagStyle.PULSE && pulseTimer.hasTimeElapsed((long) delay.getValue().getInput())) {
            pulseTimer.reset();
            blink();
            return;
        }

        if (!resetTimer.hasTimeElapsed((long) recoilTime.getValue().getInput())) return;

        if (mc.isSingleplayer() || mc.getCurrentServerData() == null) {
            blink();
            return;
        }

        event.cancel();

        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayerAccessor c03 = (C03PacketPlayerAccessor) packet;
            if (c03.isMoving()) {
                synchronized (positions) {
                    positions.add(new Vec3(c03.getX(), c03.getY(), c03.getZ()));
                }
            }
        }

        packetQueue.add(new QueueData(packet, System.currentTimeMillis()));
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncomingPacket = event -> {
        if (event.isCancelled()) return;

        if (checkShouldFlush()) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            blink();
            return;
        }

        if (packet instanceof S12PacketEntityVelocity) {
            if (mc.thePlayer.getEntityId() == ((S12PacketEntityVelocity) packet).getEntityID()) {
                blink();
            }
            return;
        }

        if (packet instanceof S27PacketExplosion) {
            blink();
        }
    };

    private boolean checkShouldFlush() {
        if (mc.thePlayer == null) return false;
        if (mc.thePlayer.isDead || ignoreWholeTick) {
            if (mc.thePlayer.isDead) blink();
            return true;
        }

        if (pauseOnNoMove.getValue() && !isMoving(mc.thePlayer)) {
            blink();
            return true;
        }

        if (!onAllowedHurtTime()) {
            blink();
            return true;
        }

        if (pauseOnChest.getValue() && mc.currentScreen instanceof GuiContainer) {
            blink();
            return true;
        }

        return false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventPlayerJoinWorld> onWorld = event -> {
        if (event.getWorld() == null) blink(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventGameLoop> onGameLoop = event -> {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        if (mc.theWorld == null) return;

        if (player.isDead || player.isUsingItem()) {
            blink();
            return;
        }

        if (style.getValue() == LagStyle.PULSE && pulseTimer.hasTimeElapsed((long) delay.getValue().getInput())) {
            pulseTimer.reset();
            blink();
            return;
        }

        if (!resetTimer.hasTimeElapsed((long) recoilTime.getValue().getInput())) return;

        handlePackets();
        ignoreWholeTick = false;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender3D = event -> {
        if (positions.isEmpty()) return;

        if (line.getValue()) {
            IMixinRenderManager renderManager = (IMixinRenderManager) mc.getRenderManager();

            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            mc.entityRenderer.disableLightmap();
            GL11.glBegin(GL11.GL_LINE_STRIP);

            Color c = new Color(lineColor.getValue());
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

            for (Vec3 pos : positions) {
                GL11.glVertex3d(
                        pos.xCoord - renderManager.getRenderPosX(),
                        pos.yCoord - renderManager.getRenderPosY(),
                        pos.zCoord - renderManager.getRenderPosZ()
                );
            }

            GL11.glColor4d(1.0, 1.0, 1.0, 1.0);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glPopMatrix();
        }
    };

    private void blink() {
        blink(true);
    }

    private void blink(boolean handlePackets) {
        mc.addScheduledTask(() -> {
            if (handlePackets) {
                resetTimer.reset();
            }
            handlePackets(true);
            ignoreWholeTick = true;
        });
    }

    private void handlePackets() {
        handlePackets(false);
    }

    private void handlePackets(boolean clear) {
        long now = System.currentTimeMillis();
        long delayMs = (long) delay.getValue().getInput();

        QueueData data;
        while ((data = packetQueue.peek()) != null) {
            if (data.getTimestamp() <= now - delayMs || clear) {
                packetQueue.poll();
                sendPacket(data.getPacket());
            } else {
                break;
            }
        }

        synchronized (positions) {
            positions.clear();
        }
    }

    private void sendPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null) {
            mc.getNetHandler().addToSendQueue(packet);
        }
    }

    private boolean onAllowedHurtTime() {
        HurtTimeMode mode = hurtTimeHandling.getValue();
        if (mode == HurtTimeMode.IGNORE) return true;
        int playerHurtTime = mc.thePlayer.hurtTime;
        double min = this.hurtTime.getValue().getMin();
        double max = this.hurtTime.getValue().getMax();
        boolean inRange = playerHurtTime >= min && playerHurtTime <= max;
        return mode == HurtTimeMode.ALLOW ? inRange : !inRange;
    }

    private boolean isMoving(EntityPlayerSP player) {
        return player.motionX != 0 || player.motionY != 0 || player.motionZ != 0;
    }
}
