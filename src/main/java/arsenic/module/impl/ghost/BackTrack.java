package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinS14PacketEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.ghost.backtrack.TimedPacket;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static arsenic.utils.lag.LagManager.receivePacket;

@ModuleInfo(name = "Backtrack", category = ModuleCategory.GHOST)
public class BackTrack extends Module {

    private static final int MAX_PACKET_QUEUE_SIZE = 50;

    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(10, 1000, 50, 100, 10));
    public final RangeProperty distanceRange = new RangeProperty("Distance", new RangeValue(0, 6, 0, 4.0, 0.1));
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);
    public final ColourProperty espColor = new ColourProperty("Color", 0xFFFFFFFF);
    public final DoubleProperty wireframeWidth = new DoubleProperty("Wireframe Width", new DoubleValue(0.5, 5.0, 2.0, 0.1));
    public final EnumProperty<ReleaseStyle> releaseStyle = new EnumProperty<>("Style", ReleaseStyle.PULSE);
    public final BooleanProperty smart = new BooleanProperty("Smart", true);

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>> skipPackets = new ArrayList<>();
    private final MSTimer cycleTimer = new MSTimer();
    private Vec3 vec3;
    private EntityPlayer target;
    private int currentLatency = 0;

    @Override
    public void onEnable() {
        packetQueue.clear();
        skipPackets.clear();
        vec3 = null;
        target = null;
        currentLatency = 0;
    }

    @Override
    public void onDisable() {
        releaseAll();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdate = event -> {
        if (target == null || vec3 == null)
            return;

        if (smart.getValue() && target.hurtTime <= 2) {
            double realDist = mc.thePlayer.getDistanceToEntity(target);
            double backtrackDist = mc.thePlayer.getDistance(vec3.xCoord, vec3.yCoord, vec3.zCoord);
            if (realDist + 0.5 < backtrackDist) {
                currentLatency = 0;
                releaseAll();
                target = null;
                vec3 = null;
                return;
            }
        }

        final double distance = RotationUtils.getDistanceToEntityBox(target);
        if (!distanceRange.hasInRange(distance)) {
            currentLatency = 0;
            releaseAll();
            target = null;
            vec3 = null;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketIncoming = event -> {
        Packet<?> packet = event.getPacket();
        if (skipPackets.contains(packet)) {
            skipPackets.remove(packet);
            return;
        }

        try {
            if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 20) {
                packetQueue.clear();
                return;
            }

            if (target == null) {
                releaseAll();
                return;
            }

            if (event.isCancelled())
                return;

            if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
                releaseAll();
                target = null;
                vec3 = null;
                return;
            } else if (packet instanceof S13PacketDestroyEntities) {
                S13PacketDestroyEntities wrapper = (S13PacketDestroyEntities) packet;
                for (int id : wrapper.getEntityIDs()) {
                    if (id == target.getEntityId()) {
                        target = null;
                        vec3 = null;
                        releaseAll();
                        return;
                    }
                }
            }

            if (packet instanceof S14PacketEntity) {
                S14PacketEntity s14PacketEntity = (S14PacketEntity) packet;
                IMixinS14PacketEntity wrapper = (IMixinS14PacketEntity) packet;
                if (wrapper.getEntityId() == target.getEntityId()) {
                    if (packetQueue.size() >= MAX_PACKET_QUEUE_SIZE)
                        return;
                    vec3 = vec3.addVector(s14PacketEntity.func_149062_c() / 32.0D, s14PacketEntity.func_149061_d() / 32.0D,
                            s14PacketEntity.func_149064_e() / 32.0D);
                    TimedPacket timedPacket = new TimedPacket(packet, currentLatency);
                    timedPacket.getTimer().start();
                    packetQueue.add(timedPacket);
                    event.cancel();
                }

            } else if (packet instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) packet;
                if (wrapper.getEntityId() == target.getEntityId()) {
                    if (packetQueue.size() >= MAX_PACKET_QUEUE_SIZE)
                        return;
                    vec3 = new Vec3(wrapper.getX() / 32.0D, wrapper.getY() / 32.0D, wrapper.getZ() / 32.0D);
                    TimedPacket timedPacket = new TimedPacket(packet, currentLatency);
                    timedPacket.getTimer().start();
                    packetQueue.add(timedPacket);
                    event.cancel();
                }
            }
        } catch (NullPointerException ignored) {
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (releaseStyle.getValue() == ReleaseStyle.PULSE) {
            if (!cycleTimer.hasTimeElapsed(currentLatency))
                return;
            while (!packetQueue.isEmpty()) {
                try {
                    Packet<?> packet = packetQueue.remove().getPacket();
                    skipPackets.add(packet);
                    receivePacket(packet);
                } catch (NullPointerException ignored) {
                }
            }
            cycleTimer.reset();
            if (packetQueue.isEmpty() && target != null) {
                vec3 = target.getPositionVector();
            }
            return;
        }

        while (!packetQueue.isEmpty()) {
            try {
                if (packetQueue.element().getTimer().hasFinished()) {
                    Packet<?> packet = packetQueue.remove().getPacket();
                    skipPackets.add(packet);
                    receivePacket(packet);
                } else {
                    break;
                }
            } catch (NullPointerException ignored) {
            }
        }

        if (packetQueue.isEmpty() && target != null) {
            vec3 = target.getPositionVector();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> eventRenderWorldLast = event -> {
        if (target == null || vec3 == null || target.isDead || currentLatency == 0)
            return;

        EspMode mode = espMode.getValue();
        if (mode == EspMode.NONE)
            return;

        Color color = new Color(espColor.getValue());

        double x = vec3.xCoord - mc.getRenderManager().viewerPosX;
        double y = vec3.yCoord - mc.getRenderManager().viewerPosY;
        double z = vec3.zCoord - mc.getRenderManager().viewerPosZ;

        if (mode == EspMode.MODEL) {
            double dx = vec3.xCoord - target.posX;
            double dy = vec3.yCoord - target.posY;
            double dz = vec3.zCoord - target.posZ;
            GlStateManager.pushMatrix();
            GlStateManager.translate(dx, dy, dz);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            mc.getRenderManager().renderEntityStatic(target, event.partialTicks, false);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            return;
        }

        AxisAlignedBB playerBB = target.getEntityBoundingBox();
        double w = playerBB.maxX - playerBB.minX;
        double h = playerBB.maxY - playerBB.minY;

        AxisAlignedBB bb = new AxisAlignedBB(
                x - w / 2, y, z - w / 2,
                x + w / 2, y + h, z + w / 2
        );

        GlStateManager.pushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);

        switch (mode) {
            case BOX:
                GL11.glLineWidth(2.0F);
                RenderGlobal.drawOutlinedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                break;

            case FILLED:
                RenderUtils.drawShadedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 63);
                break;

            case WIREFRAME:
                GL11.glLineWidth((float) wireframeWidth.getValue().getInput());
                RenderGlobal.drawOutlinedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                break;
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glLineWidth(1.0F);
        GlStateManager.popMatrix();
    };

    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        final Vec3 targetPos = event.getTarget().getPositionVector();
        if (event.getTarget() instanceof EntityPlayer) {
            if (target == null || event.getTarget() != target) {
                vec3 = targetPos;
            }
            target = (EntityPlayer) event.getTarget();

            try {
                final double distance = RotationUtils.getDistanceToEntityBox(target);
                if (!distanceRange.hasInRange(distance))
                    return;

            } catch (NullPointerException ignored) {
            }

            currentLatency = (int) latencyRange.getValue().getRandomInRange();
            cycleTimer.reset();
        }
    };

    public void releaseAll() {
        if (!packetQueue.isEmpty()) {
            for (TimedPacket timedPacket : packetQueue) {
                Packet<?> packet = timedPacket.getPacket();
                skipPackets.add(packet);
                receivePacket(packet);
            }
            packetQueue.clear();
        }
    }

    public enum EspMode {
        NONE, BOX, FILLED, MODEL, WIREFRAME
    }

    public enum ReleaseStyle {
        PULSE, SMOOTH
    }
}
