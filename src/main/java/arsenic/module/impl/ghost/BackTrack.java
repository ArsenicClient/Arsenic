package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinS14PacketEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
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
import java.util.function.Predicate;

@ModuleInfo(name = "Backtrack", category = ModuleCategory.GHOST)
public class BackTrack extends Module {

    // packet types this module ever asks LagManager to delay - used to target releases/discards.
    private static final Predicate<Packet<?>> TRACKED_PACKETS = p -> p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport;

    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(10, 1000, 50, 100, 10));
    public final RangeProperty distanceRange = new RangeProperty("Distance", new RangeValue(0, 6, 0, 4.0, 0.1));
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);
    public final ColourProperty espColor = new ColourProperty("Color", 0xFFFFFFFF);
    public final DoubleProperty wireframeWidth = new DoubleProperty("Wireframe Width", new DoubleValue(0.5, 5.0, 2.0, 0.1));
    public final EnumProperty<ReleaseStyle> releaseStyle = new EnumProperty<>("Style", ReleaseStyle.PULSE);
    public final BooleanProperty smart = new BooleanProperty("Smart", true);

    private final MSTimer cycleTimer = new MSTimer();
    private Vec3 vec3;
    private EntityPlayer target;
    private int currentLatency = 0;

    @Override
    public void onEnable() {
        vec3 = null;
        target = null;
        currentLatency = 0;
        LagManager.delay(S14PacketEntity.S15PacketEntityRelMove.class, this::onEntityMove);
        LagManager.delay(S14PacketEntity.S16PacketEntityLook.class, this::onEntityMove);
        LagManager.delay(S14PacketEntity.S17PacketEntityLookMove.class, this::onEntityMove);
        LagManager.delay(S18PacketEntityTeleport.class, this::onEntityTeleport);
    }

    @Override
    public void onDisable() {
        LagManager.undelay(S14PacketEntity.S15PacketEntityRelMove.class);
        LagManager.undelay(S14PacketEntity.S16PacketEntityLook.class);
        LagManager.undelay(S14PacketEntity.S17PacketEntityLookMove.class);
        LagManager.undelay(S18PacketEntityTeleport.class);
        releaseAll();
    }

    private long onEntityMove(Packet<?> raw) {
        if (target == null || vec3 == null)
            return 0L;

        IMixinS14PacketEntity wrapper = (IMixinS14PacketEntity) raw;
        if (wrapper.getEntityId() != target.getEntityId())
            return 0L;


        S14PacketEntity packet = (S14PacketEntity) raw;
        vec3 = vec3.addVector(packet.func_149062_c() / 32.0D, packet.func_149061_d() / 32.0D, packet.func_149064_e() / 32.0D);
        return (long) currentLatency;
    }

    private long onEntityTeleport(Packet<?> raw) {
        if (target == null)
            return 0L;

        S18PacketEntityTeleport packet = (S18PacketEntityTeleport) raw;
        if (packet.getEntityId() != target.getEntityId())
            return 0L;

        vec3 = new Vec3(packet.getX() / 32.0D, packet.getY() / 32.0D, packet.getZ() / 32.0D);
        return (long) currentLatency;
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
        try {
            if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 20) {
                LagManager.discardDelayed(TRACKED_PACKETS);
                return;
            }

            if (target == null) {
                releaseAll();
                return;
            }

            if (event.isCancelled())
                return;

            Packet<?> packet = event.getPacket();

            if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
                releaseAll();
                target = null;
                vec3 = null;
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
        } catch (NullPointerException ignored) {
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (target == null)
            return;

        if (releaseStyle.getValue() == ReleaseStyle.PULSE) {
            if (!cycleTimer.hasTimeElapsed(currentLatency))
                return;
            releaseAll();
            cycleTimer.reset();
        }

        // SMOOTH packets are auto-released by LagManager as their individual timers finish;
        // either way, once nothing's left queued, snap back in sync with the real position.
        if (LagManager.countDelayed(TRACKED_PACKETS) == 0) {
            vec3 = target.getPositionVector();
        }
    };


    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        final Vec3 targetPos = event.getTarget().getPositionVector();
        if (currentLatency == 0 && event.getTarget() instanceof EntityPlayer && target == null) {
            vec3 = targetPos;
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

    public void releaseAll() {
        LagManager.releaseDelayed(TRACKED_PACKETS);
    }

    public enum EspMode {
        NONE, BOX, FILLED, MODEL, WIREFRAME
    }

    public enum ReleaseStyle {
        PULSE, SMOOTH
    }
}