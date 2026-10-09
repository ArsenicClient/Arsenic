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
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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
                case WIREFRAME:
                    GL11.glLineWidth(1.0F);
                    drawWireframe(bb, color);
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

    // A lattice over the body: four rings and vertical lines at thirds of each side, so it reads differently from BOX
    private static void drawWireframe(AxisAlignedBB bb, Color color) {
        float r = color.getRed() / 255f, g = color.getGreen() / 255f, b = color.getBlue() / 255f, a = color.getAlpha() / 255f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        double w = bb.maxX - bb.minX;
        double d = bb.maxZ - bb.minZ;
        double h = bb.maxY - bb.minY;
        for (int k = 0; k <= 3; k++) {
            double y = bb.minY + h * k / 3.0;
            line(wr, bb.minX, y, bb.minZ, bb.maxX, y, bb.minZ, r, g, b, a);
            line(wr, bb.maxX, y, bb.minZ, bb.maxX, y, bb.maxZ, r, g, b, a);
            line(wr, bb.maxX, y, bb.maxZ, bb.minX, y, bb.maxZ, r, g, b, a);
            line(wr, bb.minX, y, bb.maxZ, bb.minX, y, bb.minZ, r, g, b, a);
        }
        for (int k = 1; k <= 2; k++) {
            double x = bb.minX + w * k / 3.0;
            line(wr, x, bb.minY, bb.minZ, x, bb.maxY, bb.minZ, r, g, b, a);
            line(wr, x, bb.minY, bb.maxZ, x, bb.maxY, bb.maxZ, r, g, b, a);
            double z = bb.minZ + d * k / 3.0;
            line(wr, bb.minX, bb.minY, z, bb.minX, bb.maxY, z, r, g, b, a);
            line(wr, bb.maxX, bb.minY, z, bb.maxX, bb.maxY, z, r, g, b, a);
        }
        tessellator.draw();
    }

    private static void line(WorldRenderer wr, double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        wr.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

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
        NONE, BOX, FILLED, WIREFRAME, MODEL
    }
}