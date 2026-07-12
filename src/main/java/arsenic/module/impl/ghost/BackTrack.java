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

    public enum BacktrackMode {NORMAL, PULSE}
    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(10, 1000, 50, 100, 10));
    public final EnumProperty<BacktrackMode> backtrackMode = new EnumProperty<>("Mode", BacktrackMode.NORMAL);
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);
    public final ColourProperty espColor = new ColourProperty("Color", 0xFFFFFFFF);

    private final Map<Integer, TrackEntry> tracked = new ConcurrentHashMap<>();

    private static class TrackEntry {
        volatile Vec3 vec3;
        final int latency;
        final EntityPlayer player;
        final long trackStart;

        TrackEntry(EntityPlayer player, Vec3 vec3, int latency) {
            this.player = player;
            this.vec3 = vec3;
            this.latency = latency;
            trackStart = System.currentTimeMillis();
        }

    }


    @Override
    public void onEnable() {
        PlayerUtils.addMessageToChat("Enabled");
        tracked.clear();
        LagManager.delay(S14PacketEntity.class, this::onEntityMove);
        LagManager.delay(S14PacketEntity.S15PacketEntityRelMove.class, this::onEntityMove);
        LagManager.delay(S18PacketEntityTeleport.class, this::onEntityTeleport);
    }

    @Override
    public void onDisable() {
        LagManager.releaseDelayed(ALL_TRACKED);
        LagManager.undelay(S14PacketEntity.class);
        LagManager.undelay(S14PacketEntity.S15PacketEntityRelMove.class);
        LagManager.undelay(S18PacketEntityTeleport.class);
        tracked.clear();
    }


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
        if(backtrackMode.getValue() != BacktrackMode.NORMAL) return;
        if (!(event.getTarget() instanceof EntityPlayer)) return;

        EntityPlayer target = (EntityPlayer) event.getTarget();

        tracked.computeIfAbsent(target.getEntityId(), id -> new TrackEntry(
                target,
                target.getPositionVector(),
                (int) latencyRange.getValue().getRandomInRange()
        ));
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> listener = event -> {
        if(backtrackMode.getValue() != BacktrackMode.PULSE) return;
        if (!(event.getPacket() instanceof S19PacketEntityStatus)) return;

        S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
        if (packet.getOpCode() != 2) return; //hurt animation

        Entity entity = packet.getEntity(mc.theWorld);
        if (!(entity instanceof EntityPlayer)) return;

        EntityPlayer target = (EntityPlayer) entity;
        if (entity == mc.thePlayer) return;
        if (RotationUtils.getDistanceToEntityBox(target) >= 3.0) return;

        tracked.computeIfAbsent(target.getEntityId(), id -> new TrackEntry(
                target,
                target.getPositionVector(),
                (int) latencyRange.getValue().getRandomInRange()
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


            boolean shouldRemove = false;
            if (backtrackMode.getValue() == BacktrackMode.NORMAL) {
                shouldRemove = RotationUtils.getDistanceToEntityBox(target) > 3.0;
            } else if (backtrackMode.getValue() == BacktrackMode.PULSE) {
                shouldRemove = System.currentTimeMillis() - entry.trackStart > entry.latency;
            }

            if (shouldRemove) {
                LagManager.releaseDelayed(filterFor(entityId));
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