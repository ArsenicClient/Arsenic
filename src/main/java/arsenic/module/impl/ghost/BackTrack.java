package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinS14PacketEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(name = "Backtrack", category = ModuleCategory.GHOST)
public class BackTrack extends Module {

    private static final Predicate<Packet<?>> ALL_TRACKED =
            p -> p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport;

    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(10, 1000, 50, 100, 10));
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);
    public final ColourProperty espColor = new ColourProperty("Color", 0xFFFFFFFF);

    // entityId → per-target backtrack state
    private final Map<Integer, TrackEntry> tracked = new ConcurrentHashMap<>();

    private static class TrackEntry {
        // Server-ahead position, updated by each delayed movement packet before it's released.
        // Volatile because the packet thread writes and the render/main thread reads.
        volatile Vec3 vec3;
        final int latency;
        final EntityPlayer player;

        TrackEntry(EntityPlayer player, Vec3 vec3, int latency) {
            this.player = player;
            this.vec3 = vec3;
            this.latency = latency;
        }
    }

    // ---- Lifecycle ----

    @Override
    public void onEnable() {
        tracked.clear();
        LagManager.delay(S14PacketEntity.class, this::onEntityMove);
        LagManager.delay(S14PacketEntity.S15PacketEntityRelMove.class, this::onEntityMove);
        LagManager.delay(S14PacketEntity.S16PacketEntityLook.class, this::onEntityMove);
        LagManager.delay(S14PacketEntity.S17PacketEntityLookMove.class, this::onEntityMove);
        LagManager.delay(S18PacketEntityTeleport.class, this::onEntityTeleport);
    }

    @Override
    public void onDisable() {
        LagManager.undelay(S14PacketEntity.class);
        LagManager.undelay(S14PacketEntity.S15PacketEntityRelMove.class);
        LagManager.undelay(S14PacketEntity.S16PacketEntityLook.class);
        LagManager.undelay(S14PacketEntity.S17PacketEntityLookMove.class);
        LagManager.undelay(S18PacketEntityTeleport.class);
        LagManager.releaseDelayed(ALL_TRACKED); // instant flush on disable
        tracked.clear();
    }

    // ---- Packet delay handlers ----

    private long onEntityMove(Packet<?> raw) {
        TrackEntry entry = tracked.get(((IMixinS14PacketEntity) raw).getEntityId());
        if (entry == null) return 0L;

        S14PacketEntity packet = (S14PacketEntity) raw;
        // addVector creates a new Vec3, so the volatile write is safe
        entry.vec3 = entry.vec3.addVector(
                packet.func_149062_c() / 32.0D,
                packet.func_149061_d() / 32.0D,
                packet.func_149064_e() / 32.0D
        );
        return (long) entry.latency;
    }

    private long onEntityTeleport(Packet<?> raw) {
        S18PacketEntityTeleport packet = (S18PacketEntityTeleport) raw;
        TrackEntry entry = tracked.get(packet.getEntityId());
        if (entry == null) return 0L;

        entry.vec3 = new Vec3(packet.getX() / 32.0D, packet.getY() / 32.0D, packet.getZ() / 32.0D);
        return (long) entry.latency;
    }

    // ---- Attack: start / refresh tracking ----

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        if (!(event.getTarget() instanceof EntityPlayer)) return;

        EntityPlayer target = (EntityPlayer) event.getTarget();

        if (!tracked.containsKey(target.getEntityId())) {
            tracked.put(target.getEntityId(), new TrackEntry(
                    target,
                    target.getPositionVector(),
                    (int) latencyRange.getValue().getRandomInRange()
            ));
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (tracked.isEmpty()) return;
        int pingTicks = Math.max(1, LagManager.getPingAsTicks());

        Iterator<Map.Entry<Integer, TrackEntry>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrackEntry> mapEntry = it.next();
            TrackEntry entry = mapEntry.getValue();
            EntityPlayer target = entry.player;
            Vec3 vec3 = entry.vec3;

            if (target.isDead || mc.theWorld.getEntityByID(target.getEntityId()) == null) {
                LagManager.releaseDelayed(filterFor(target.getEntityId()));
                it.remove();
                continue;
            }

            if (RotationUtils.getDistanceToEntityBox(target) <= 3.0)
                continue;

            double px = mc.thePlayer.posX;
            double pz = mc.thePlayer.posZ;

            double bdx = vec3.xCoord - px;
            double bdz = vec3.zCoord - pz;
            double backtrackDist = Math.sqrt(bdx * bdx + bdz * bdz);

            double rdx = target.posX - px;
            double rdz = target.posZ - pz;
            double realDist = Math.sqrt(rdx * rdx + rdz * rdz);

            double futureX = target.posX + target.motionX * pingTicks;
            double futureZ = target.posZ + target.motionZ * pingTicks;
            double fdx = futureX - px;
            double fdz = futureZ - pz;
            double futureDist = Math.sqrt(fdx * fdx + fdz * fdz);

            if (realDist < backtrackDist || futureDist < backtrackDist) {
                releaseSmooth(target.getEntityId());
                it.remove();
            }
        }
    };

    // ---- ESP ----
    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> eventRenderWorldLast = event -> {
        if (tracked.isEmpty()) return;

        EspMode mode = espMode.getValue();
        if (mode == EspMode.NONE) return;

        Color color = new Color(espColor.getValue());

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
                // Translate by the XZ delta only; Y is anchored to the player so it stays grounded
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

            // Build a bounding box at the backtracked XZ but at our own Y,
            // matching the player's real hitbox dimensions.
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
                    GL11.glLineWidth(1.5F);
                    RenderGlobal.drawOutlinedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
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

    // ---- Helpers ----
    /** Per-entity packet predicate so releases only drain one target's queue. */
    private static Predicate<Packet<?>> filterFor(int entityId) {
        return p -> {
            if (p instanceof S14PacketEntity)
                return ((IMixinS14PacketEntity) p).getEntityId() == entityId;
            if (p instanceof S18PacketEntityTeleport)
                return ((S18PacketEntityTeleport) p).getEntityId() == entityId;
            return false;
        };
    }

    /** Smooth (chunked) release for a single target when backtrack is no longer beneficial. */
    private void releaseSmooth(int entityId) {
        LagManager.releaseDelayedChunked(filterFor(entityId), 3);
    }

    public enum EspMode {
        NONE, BOX, FILLED, WIREFRAME, MODEL
    }
}