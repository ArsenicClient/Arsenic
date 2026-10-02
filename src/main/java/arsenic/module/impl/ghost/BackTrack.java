package arsenic.module.impl.ghost;

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
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@ModuleInfo(name = "Backtrack", category = ModuleCategory.COMBAT)
public class BackTrack extends Module {

    private static final Predicate<Packet<?>> ALL_TRACKED =
            p -> p instanceof S14PacketEntity || p instanceof S18PacketEntityTeleport;

    public enum BacktrackMode {NORMAL, PULSE}
    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(10, 1000, 50, 100, 10));
    public final EnumProperty<BacktrackMode> backtrackMode = new EnumProperty<>("Mode", BacktrackMode.NORMAL);
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);

    @Override
    public String getHudInfo() {
        return backtrackMode.getValue().name().toLowerCase();
    }

    private final Map<Integer, TrackEntry> tracked = new ConcurrentHashMap<>();

    private static class TrackEntry {
        volatile Vec3 vec3;
        final int latency;
        final Player player;
        final long trackStart;

        TrackEntry(Player player, Vec3 vec3, int latency) {
            this.player = player;
            this.vec3 = vec3;
            this.latency = latency;
            trackStart = System.currentTimeMillis();
        }

    }


    @Override
    public void onEnable() {
        tracked.clear();
        // All S15/S16/S17 move packets are subclasses of S14PacketEntity, so one selector covers them.
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
        // Re-initialise (re-bind the packet delays, clear stale tracks) whenever the local player
        // joins a new world — the previous world's entity tracks no longer mean anything.
        if (event.getEntity() == mc.player)
            onEnable();
    };


    private long onEntityMove(Packet<?> raw) {
        TrackEntry entry = tracked.get(((IMixinS14PacketEntity) raw).getId());
        if (entry == null) return 0L;

        S14PacketEntity packet = (S14PacketEntity) raw;
        entry.vec3 = entry.vec3.add(
                packet.func_149062_c() / 32.0D,
                packet.func_149061_d() / 32.0D,
                packet.func_149064_e() / 32.0D
        );
        return entry.latency;
    }

    private long onEntityTeleport(Packet<?> raw) {
        S18PacketEntityTeleport packet = (S18PacketEntityTeleport) raw;
        TrackEntry entry = tracked.get(packet.getId());
        if (entry == null) return 0L;

        entry.vec3 = new Vec3(packet.getX() / 32.0D, packet.getY() / 32.0D, packet.getZ() / 32.0D);
        return entry.latency;
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        if(backtrackMode.getValue() != BacktrackMode.NORMAL) return;
        if (!(event.getTarget() instanceof Player)) return;

        Player target = (Player) event.getTarget();

        tracked.computeIfAbsent(target.getId(), id -> new TrackEntry(
                target,
                target.position(),
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

        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player)) return;

        Player target = (Player) entity;
        if (entity == mc.player) return;
        if (RotationUtils.getDistanceToEntityBox(target) >= 3.0) return;

        tracked.computeIfAbsent(target.getId(), id -> new TrackEntry(
                target,
                target.position(),
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
            Player target = entry.player;
            int entityId = target.getId();


            boolean shouldRemove = false;
            if (backtrackMode.getValue() == BacktrackMode.NORMAL) {
                shouldRemove = RotationUtils.getDistanceToEntityBox(target) > 3.0;
            } else if (backtrackMode.getValue() == BacktrackMode.PULSE) {
                shouldRemove = System.currentTimeMillis() - entry.trackStart > entry.latency;
            }

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

        // Colour follows the client theme rather than a per-module picker.
        Color color = new Color(ThemeManager.getMainColor(), true);


        for (TrackEntry entry : tracked.values()) {
            Vec3 vec3 = entry.vec3;
            Player target = entry.player;
            if (vec3 == null || target.isRemoved()) continue;

            if (mode == EspMode.MODEL) {
                // Translate by the XZ delta only; Y is anchored to the player so it stays grounded
                double dx = vec3.x - target.getX();
                double dz = vec3.z - target.getZ();
                GlStateManager.translate(dx, 0, dz);
                mc.getRenderManager().renderEntityStatic(target, event.partialTicks, false);
                continue;
            }

            // Build a bounding box at the backtracked XZ but at our own Y,
            // matching the player's real hitbox dimensions.
            double rx = vec3.x - mc.getRenderManager().viewerPosX;
            double ry = vec3.y - mc.getRenderManager().viewerPosY;
            double rz = vec3.z - mc.getRenderManager().viewerPosZ;

            AABB playerBB = target.getBoundingBox();
            double w = playerBB.maxX - playerBB.minX;
            double h = playerBB.maxY - playerBB.minY;

            AABB bb = new AABB(
                    rx - w / 2, ry, rz - w / 2,
                    rx + w / 2, ry + h, rz + w / 2
            );

            switch (mode) {
                case BOX:
                    RenderGlobal.drawOutlinedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                    break;
                case FILLED:
                    RenderUtils.drawShadedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 63);
                    break;
                case WIREFRAME:
                    RenderGlobal.drawOutlinedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
                    break;
            }
        }

    };

    private static Predicate<Packet<?>> filterFor(int entityId) {
        return p -> {
            if (p instanceof S14PacketEntity)
                return ((IMixinS14PacketEntity) p).getId() == entityId;
            if (p instanceof S18PacketEntityTeleport)
                return ((S18PacketEntityTeleport) p).getId() == entityId;
            return false;
        };
    }


    public enum EspMode {
        NONE, BOX, FILLED, WIREFRAME, MODEL
    }
}