package arsenic.module.impl.ghost;

import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.SliderScale;
import arsenic.gui.themes.ThemeManager;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinMoveEntityPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Holds back a hit target's movement packets for a moment so you can keep hitting where they just were. */
@ModuleInfo(name = "Backtrack", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class BackTrack extends Module {

    private static final Predicate<Packet<?>> ALL_TRACKED =
            p -> p instanceof ClientboundMoveEntityPacket || p instanceof ClientboundTeleportEntityPacket
                    || p instanceof ClientboundEntityPositionSyncPacket;

    // Grim interpolates living entities over 3 ticks and only accepts hits on positions inside that window. 100 ms
    // (2 ticks) leaves one tick of margin for jitter. The slider and the clamp both use this, so settings cannot go past it.
    private static final long GRIM_MAX_LAG_MS = 100L;
    private static final long GRIM_MIN_LAG_MS = 10L;
    // Grim's reach limit is 3.0; stay just inside it against the server position
    private static final double REACH_LIMIT = 2.95;

    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(GRIM_MIN_LAG_MS, GRIM_MAX_LAG_MS, 40, 80, 10), SliderScale.LOG);
    public final RangeProperty range = new RangeProperty("Range", new RangeValue(0, 10, 0, 6, 0.1));
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", false);
    public final BooleanProperty onlyRetreating = new BooleanProperty("Only Retreating", false);
    public final BooleanProperty flushOnKnockback = new BooleanProperty("Flush On Knockback", false);
    @PropertyGroup(Group.VISUAL)
    public final EnumProperty<EspMode> espMode = new EnumProperty<>("ESP", EspMode.BOX);

    private final Map<Integer, TrackEntry> tracked = new ConcurrentHashMap<>();

    private static class TrackEntry {
        volatile Vec3 vec3;
        /** Our own copy of the entity's position codec - move packets are deltas against it. */
        final VecDeltaCodec codec = new VecDeltaCodec();
        final int latency;
        final Player player;
        /** Whether this entry's move packets are currently being held (Only Retreating decides per packet). */
        volatile boolean holding = true;
        // Render-thread smoothing: the box glides to each new server position over three ticks
        Vec3 renderPrev, renderPos, renderGoal;
        int renderSteps;

        TrackEntry(Player player, Vec3 vec3, int latency) {
            this.player = player;
            this.vec3 = vec3;
            this.codec.setBase(player.getPositionCodec().getBase());
            this.latency = latency;
        }

    }

    private int pickLatency() {
        long picked = (long) latencyRange.getValue().getRandomInRange();
        return (int) Math.max(GRIM_MIN_LAG_MS, Math.min(picked, GRIM_MAX_LAG_MS));
    }

    // Distance from our eyes to where the server has the target, which is where Grim measures reach
    private double serverDistance(TrackEntry entry) {
        Player target = entry.player;
        AABB serverBox = target.getBoundingBox().move(
                entry.vec3.x - target.getX(),
                entry.vec3.y - target.getY(),
                entry.vec3.z - target.getZ());
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = Mth.clamp(eyes.x, serverBox.minX, serverBox.maxX);
        double y = Mth.clamp(eyes.y, serverBox.minY, serverBox.maxY);
        double z = Mth.clamp(eyes.z, serverBox.minZ, serverBox.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }


    /** Holding packets is allowed right now (Weapon Only). Updated on the tick, read from the netty thread. */
    private volatile boolean allowed = true;

    private boolean holdingWeapon() {
        return !weaponOnly.getValue() || ItemUtils.isWeapon(mc.player.getMainHandItem());
    }

    /**
     * Decides whether the packet carrying {@code end} is held. With Only Retreating, packets that bring the target
     * closer are let through, after releasing that target's held packets first so they still apply in order.
     */
    private long decide(TrackEntry entry, Vec3 end) {
        if (!allowed) {
            LagManager.releaseDelayedFor(BackTrack.class, filterFor(entry.player.getId()));
            entry.holding = false;
        } else if (onlyRetreating.getValue() && end != null && mc.player != null) {
            boolean retreating = eyeDistance(end, entry.player) > eyeDistance(entry.vec3, entry.player) + 0.001;
            if (!retreating && entry.holding)
                LagManager.releaseDelayedFor(BackTrack.class, filterFor(entry.player.getId()));
            entry.holding = retreating;
        } else if (end != null) {
            entry.holding = true;
        }
        if (end != null) {
            entry.codec.setBase(end);
            entry.vec3 = end;
        }
        return entry.holding ? entry.latency : 0L;
    }

    private double eyeDistance(Vec3 feet, Player target) {
        AABB box = target.getBoundingBox();
        double hw = (box.maxX - box.minX) / 2, h = box.maxY - box.minY;
        Vec3 eyes = mc.player.getEyePosition(1f);
        double x = Mth.clamp(eyes.x, feet.x - hw, feet.x + hw);
        double y = Mth.clamp(eyes.y, feet.y, feet.y + h);
        double z = Mth.clamp(eyes.z, feet.z - hw, feet.z + hw);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    @Override
    public void onEnable() {
        tracked.clear();
        allowed = true;
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
        if (event.getEntity() == mc.player)
            onEnable();
    };


    private long onEntityMove(ClientboundMoveEntityPacket packet) {
        TrackEntry entry = tracked.get(((IMixinMoveEntityPacket) packet).getEntityId());
        if (entry == null) return 0L;

        Vec3 end = packet.hasPosition() ? packet.getPositionDelta().decode(entry.codec).endPosition() : null;
        return decide(entry, end);
    }

    private long onEntityTeleport(ClientboundTeleportEntityPacket packet) {
        TrackEntry entry = tracked.get(packet.id());
        if (entry == null) return 0L;

        PositionMoveRotation current = new PositionMoveRotation(entry.vec3, Vec3.ZERO, 0, 0);
        Vec3 end = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives()).position();
        return decide(entry, end);
    }

    private long onPositionSync(ClientboundEntityPositionSyncPacket packet) {
        TrackEntry entry = tracked.get(packet.id());
        if (entry == null) return 0L;

        Vec3 end = packet.position().endPosition();
        return decide(entry, end);
    }


    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        if (!(event.getTarget() instanceof Player)) return;

        Player target = (Player) event.getTarget();
        if (!allowed || !range.hasInRange(mc.player.distanceTo(target))) return;

        tracked.computeIfAbsent(target.getId(), id -> new TrackEntry(
                target,
                target.position(),
                pickLatency()
        ));
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        boolean nowAllowed = holdingWeapon();
        if (allowed && !nowAllowed)
            LagManager.releaseDelayedFor(BackTrack.class);
        allowed = nowAllowed;

        if (tracked.isEmpty())
            return;

        for (TrackEntry entry : tracked.values()) {
            Vec3 goal = entry.vec3;
            if (entry.renderPos == null) {
                entry.renderPos = goal;
                entry.renderPrev = goal;
                entry.renderGoal = goal;
                continue;
            }
            entry.renderPrev = entry.renderPos;
            if (goal != entry.renderGoal) {
                entry.renderGoal = goal;
                entry.renderSteps = 3;
            }
            if (entry.renderSteps > 0) {
                entry.renderPos = entry.renderPos.add(goal.subtract(entry.renderPos).scale(1.0 / entry.renderSteps));
                entry.renderSteps--;
            }
        }

        tracked.entrySet().removeIf(mapEntry -> {
            TrackEntry entry = mapEntry.getValue();
            Player target = entry.player;
            int entityId = target.getId();


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


        for (TrackEntry entry : tracked.values()) {
            Vec3 vec3 = entry.vec3;
            Player target = entry.player;
            if (vec3 == null || target.isRemoved() || !entry.holding) continue;

            double rx, ry, rz;
            if (entry.renderPos != null && entry.renderPrev != null) {
                float pt = event.partialTicks;
                rx = Mth.lerp(pt, entry.renderPrev.x, entry.renderPos.x);
                ry = Mth.lerp(pt, entry.renderPrev.y, entry.renderPos.y);
                rz = Mth.lerp(pt, entry.renderPrev.z, entry.renderPos.z);
            } else {
                rx = vec3.x;
                ry = vec3.y;
                rz = vec3.z;
            }

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
            }
        }

    };

    /** Flush On Knockback: being hit or set back releases everything held, so the fight resyncs. */
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (!flushOnKnockback.getValue() || mc.player == null) return;
        Packet<?> packet = event.getPacket();
        boolean flush = packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == mc.player.getId();
        if (flush)
            LagManager.releaseDelayedFor(BackTrack.class);
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
        NONE, BOX, FILLED, MODEL
    }
}