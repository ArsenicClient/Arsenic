package arsenic.module.impl.ghost;

import arsenic.module.property.impl.SliderScale;
import arsenic.gui.themes.ThemeManager;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinS14PacketEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(name = "Backtrack", category = ModuleCategory.COMBAT)
public class BackTrack extends Module {

    private static final Predicate<Packet<?>> ALL_TRACKED =
            p -> p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport;

    // Grim interpolates living entities over 3 ticks and only accepts hits on positions inside that window. 100 ms
    // (2 ticks) leaves one tick of margin for jitter. The slider and the clamp both use this, so settings cannot go past it.
    private static final long GRIM_MAX_LAG_MS = 100L;
    private static final long GRIM_MIN_LAG_MS = 10L;
    // Grim's reach limit is 3.0; stay just inside it against the server position
    private static final double REACH_LIMIT = 2.95;

    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(GRIM_MIN_LAG_MS, GRIM_MAX_LAG_MS, 40, 80, 10), SliderScale.LOG);
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);

    private final Map<Integer, TrackEntry> tracked = new ConcurrentHashMap<>();

    private static class TrackEntry {
        volatile Vec3 vec3;
        final int latency;
        final EntityPlayer player;

        TrackEntry(EntityPlayer player, Vec3 vec3, int latency) {
            this.player = player;
            this.vec3 = vec3;
            this.latency = latency;
        }

    }


    private int pickLatency() {
        long picked = (long) latencyRange.getValue().getRandomInRange();
        return (int) Math.max(GRIM_MIN_LAG_MS, Math.min(picked, GRIM_MAX_LAG_MS));
    }

    // Distance from our eyes to where the server has the target, which is where Grim measures reach
    private double serverDistance(TrackEntry entry) {
        EntityPlayer target = entry.player;
        AxisAlignedBB serverBox = target.getEntityBoundingBox().offset(
                entry.vec3.xCoord - target.posX,
                entry.vec3.yCoord - target.posY,
                entry.vec3.zCoord - target.posZ);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        double x = MathHelper.clamp_double(eyes.xCoord, serverBox.minX, serverBox.maxX);
        double y = MathHelper.clamp_double(eyes.yCoord, serverBox.minY, serverBox.maxY);
        double z = MathHelper.clamp_double(eyes.zCoord, serverBox.minZ, serverBox.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    @Override
    public void onEnable() {
        tracked.clear();
        LagManager.delay(BackTrack.class, ALL_TRACKED, this::onDelayPacket);
    }

    @Override
    public void onDisable() {
        LagManager.releaseDelayedFor(BackTrack.class);
        LagManager.undelay(BackTrack.class);
        tracked.clear();
    }

    private long onDelayPacket(Packet<?> raw) {
        if (raw instanceof S18PacketEntityTeleport)
            return onEntityTeleport(raw);
        if (raw instanceof S14PacketEntity)
            return onEntityMove(raw);
        return 0L;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        if (event.getEntity() == mc.thePlayer)
            onEnable();
    };


    private long onEntityMove(Packet<?> raw) {
        TrackEntry entry = tracked.get(((IMixinS14PacketEntity) raw).getEntityId());
        if (entry == null) return 0L;

        S14PacketEntity packet = (S14PacketEntity) raw;
        entry.vec3 = entry.vec3.addVector(
                packet.func_149062_c() / 32.0D,
                packet.func_149061_d() / 32.0D,
                packet.func_149064_e() / 32.0D
        );
        return entry.latency;
    }

    private long onEntityTeleport(Packet<?> raw) {
        S18PacketEntityTeleport packet = (S18PacketEntityTeleport) raw;
        TrackEntry entry = tracked.get(packet.getEntityId());
        if (entry == null) return 0L;

        entry.vec3 = new Vec3(packet.getX() / 32.0D, packet.getY() / 32.0D, packet.getZ() / 32.0D);
        return entry.latency;
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        if (!(event.getTarget() instanceof EntityPlayer)) return;

        EntityPlayer target = (EntityPlayer) event.getTarget();

        tracked.computeIfAbsent(target.getEntityId(), id -> new TrackEntry(
                target,
                target.getPositionVector(),
                pickLatency()
        ));
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (tracked.isEmpty())
            return;

        tracked.entrySet().removeIf(mapEntry -> {
            TrackEntry entry = mapEntry.getValue();
            EntityPlayer target = entry.player;
            int entityId = target.getEntityId();


            // Grim checks reach against the server position, so stop once that is out of reach
            boolean shouldRemove = serverDistance(entry) > REACH_LIMIT;

            if (shouldRemove) {
                LagManager.releaseDelayedFor(BackTrack.class, filterFor(entityId));
            }

            return shouldRemove;
        });
    };



    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> eventRenderWorldLast = event -> {
        if (tracked.isEmpty()) return;

        EspMode mode = espMode.getValue();
        if (mode == EspMode.NONE) return;

        Color color = new Color(ThemeManager.getMainColor(), true);

        GlStateManager.pushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);

        for (TrackEntry entry : tracked.values()) {
            Vec3 vec3 = entry.vec3;
            EntityPlayer target = entry.player;
            if (vec3 == null || target.isDead) continue;

            if (mode == EspMode.MODEL) {
                double dx = vec3.xCoord - target.posX;
                double dz = vec3.zCoord - target.posZ;
                GlStateManager.pushMatrix();
                GlStateManager.translate(dx, 0, dz);
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                mc.getRenderManager().renderEntityStatic(target, event.partialTicks, false);
                GlStateManager.enableDepth();
                GlStateManager.disableBlend();
                GlStateManager.popMatrix();
                continue;
            }

            double rx = vec3.xCoord - mc.getRenderManager().viewerPosX;
            double ry = vec3.yCoord - mc.getRenderManager().viewerPosY;
            double rz = vec3.zCoord - mc.getRenderManager().viewerPosZ;

            AxisAlignedBB playerBB = target.getEntityBoundingBox();
            double w = playerBB.maxX - playerBB.minX;
            double h = playerBB.maxY - playerBB.minY;

            AxisAlignedBB bb = new AxisAlignedBB(
                    rx - w / 2, ry, rz - w / 2,
                    rx + w / 2, ry + h, rz + w / 2
            );

            switch (mode) {
                case BOX:
                    GL11.glLineWidth(2.0F);
                    RenderGlobal.drawOutlinedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                    break;
                case FILLED:
                    RenderUtils.drawShadedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 63);
                    break;
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glLineWidth(1.0F);
        GlStateManager.popMatrix();
    };

    private static Predicate<Packet<?>> filterFor(int entityId) {
        return p -> {
            if (p instanceof S14PacketEntity)
                return ((IMixinS14PacketEntity) p).getEntityId() == entityId;
            if (p instanceof S18PacketEntityTeleport)
                return ((S18PacketEntityTeleport) p).getEntityId() == entityId;
            return false;
        };
    }


    public enum EspMode {
        NONE, BOX, FILLED, MODEL
    }
}