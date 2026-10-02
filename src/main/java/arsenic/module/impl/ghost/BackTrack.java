package arsenic.module.impl.ghost;

import arsenic.gui.themes.ThemeManager;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinMoveEntityPacket;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.PositionMoveRotation;
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
            p -> p instanceof ClientboundMoveEntityPacket || p instanceof ClientboundTeleportEntityPacket
                    || p instanceof ClientboundEntityPositionSyncPacket;

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
        /** Our own copy of the entity's position codec - move packets are deltas against it. */
        final VecDeltaCodec codec = new VecDeltaCodec();
        final int latency;
        final Player player;
        final long trackStart;

        TrackEntry(Player player, Vec3 vec3, int latency) {
            this.player = player;
            this.vec3 = vec3;
            this.codec.setBase(player.getPositionCodec().getBase());
            this.latency = latency;
            trackStart = System.currentTimeMillis();
        }

    }


    @Override
    public void onEnable() {
        tracked.clear();
        // Pos, PosRot and Rot are all ClientboundMoveEntityPacket, so one selector covers them.
        LagManager.delay(BackTrack.class, ALL_TRACKED, this::onDelayPacket);
    }

    @Override
    public void onDisable() {
        LagManager.releaseDelayedFor(BackTrack.class);
        LagManager.undelay(BackTrack.class);
        tracked.clear();
    }

    private long onDelayPacket(Packet<?> raw) {
        if (raw instanceof ClientboundTeleportEntityPacket teleport)
            return onEntityTeleport(teleport);
        if (raw instanceof ClientboundEntityPositionSyncPacket sync)
            return onPositionSync(sync);
        if (raw instanceof ClientboundMoveEntityPacket move)
            return onEntityMove(move);
        return 0L;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        // Re-initialise (re-bind the packet delays, clear stale tracks) whenever the local player
        // joins a new world — the previous world's entity tracks no longer mean anything.
        if (event.getEntity() == mc.player)
            onEnable();
    };


    private long onEntityMove(ClientboundMoveEntityPacket packet) {
        TrackEntry entry = tracked.get(((IMixinMoveEntityPacket) packet).getEntityId());
        if (entry == null) return 0L;

        if (packet.hasPosition()) {
            Vec3 end = packet.getPositionDelta().decode(entry.codec).endPosition();
            entry.codec.setBase(end);
            entry.vec3 = end;
        }
        return entry.latency;
    }

    private long onEntityTeleport(ClientboundTeleportEntityPacket packet) {
        TrackEntry entry = tracked.get(packet.id());
        if (entry == null) return 0L;

        PositionMoveRotation current = new PositionMoveRotation(entry.vec3, Vec3.ZERO, 0, 0);
        Vec3 end = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives()).position();
        entry.codec.setBase(end);
        entry.vec3 = end;
        return entry.latency;
    }

    private long onPositionSync(ClientboundEntityPositionSyncPacket packet) {
        TrackEntry entry = tracked.get(packet.id());
        if (entry == null) return 0L;

        Vec3 end = packet.position().endPosition();
        entry.codec.setBase(end);
        entry.vec3 = end;
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
        // a damage event is what 1.8 sent as entity status 2, the hurt animation
        if (!(event.getPacket() instanceof ClientboundDamageEventPacket packet)) return;

        Entity entity = mc.level.getEntity(packet.entityId());
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

            // Build a bounding box at the backtracked XZ but at our own Y,
            // matching the player's real hitbox dimensions.
            double rx = vec3.x;
            double ry = vec3.y;
            double rz = vec3.z;

            AABB playerBB = target.getBoundingBox();
            double w = playerBB.maxX - playerBB.minX;
            double h = playerBB.maxY - playerBB.minY;

            AABB bb = new AABB(
                    rx - w / 2, ry, rz - w / 2,
                    rx + w / 2, ry + h, rz + w / 2
            );

            switch (mode) {
                case BOX:
                case MODEL: // drawing a second copy of the model is not possible on the new renderer
                    RenderUtils.drawBoundingBox(bb, color.getRGB());
                    break;
                case FILLED:
                    RenderUtils.drawShadedBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 63);
                    break;
                case WIREFRAME:
                    RenderUtils.drawBoundingBox(bb, color.getRGB());
                    break;
            }
        }

    };

    private static Predicate<Packet<?>> filterFor(int entityId) {
        return p -> {
            if (p instanceof ClientboundMoveEntityPacket)
                return ((IMixinMoveEntityPacket) p).getEntityId() == entityId;
            if (p instanceof ClientboundTeleportEntityPacket teleport)
                return teleport.id() == entityId;
            if (p instanceof ClientboundEntityPositionSyncPacket sync)
                return sync.id() == entityId;
            return false;
        };
    }


    public enum EspMode {
        NONE, BOX, FILLED, WIREFRAME, MODEL
    }
}