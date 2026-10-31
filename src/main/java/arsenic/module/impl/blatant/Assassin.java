package arsenic.module.impl.blatant;

import net.minecraft.world.entity.Entity;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.injection.accessor.IMixinLocalPlayer;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;

/**
 * When a target is about ten blocks away, the player is frozen in place client side and every outgoing packet is held.
 * While frozen, a copy of the player is simulated sprint jumping (1.8.9 movement physics against the world's collision
 * shapes) around to the point behind the target, the opposite way to where the target is looking. Each simulated tick
 * is sent as a movement packet into the hold. Once the copy is behind the target, the player is moved to it and the
 * held packets are released in order, so the server sees the whole run arrive at once.
 *
 * The run ends early (and is still released where the copy got to) when the path is blocked, the copy reaches water,
 * the target is lost, knockback arrives, or Max Hold runs out.
 *
 * Exactly one simulated tick is sent per real tick held. Grim's Timer floors its balance at the last acknowledged
 * transaction minus 120 ms, so time cannot be banked by holding: any extra movement packet beyond about two ticks is a
 * Timer flag (and the setback that follows shows up as Simulation). A ten block run therefore holds for about two
 * seconds. The simulation matches SimPlayer in the MinecraftSimulator repo: drag is picked before the move, float
 * rounding as in 1.8.9, and ground speed / air control are the previous tick's, so it only starts while sprinting.
 *
 * Through mode runs straight at the target instead and hits it as soon as the sent look is on its hitbox within reach
 * (swing and attack are held with everything else), then keeps running through it and away until Escape Distance from
 * it, and only then releases (earlier if Max Hold runs out or the path ahead is blocked, liquid or has no ground). The
 * server sees the approach, the hit and the escape all at once, ending out of the target's reach.
 */
@ModuleInfo(name = "Assassin", description = "Blinks a simulated sprint jump to behind the nearest player", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class Assassin extends Module {

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Behind);
    public final RangeProperty distance = new RangeProperty("Trigger Distance", new RangeValue(4, 20, 9, 11, 0.5));
    public final DoubleProperty behind = new DoubleProperty("Behind Distance", new DoubleValue(1, 4, 2, 0.1));
    public final DoubleProperty escapeDistance = new DoubleProperty("Escape Distance", new DoubleValue(3, 50, 6, 0.5));
    public final DoubleProperty maxHold = new DoubleProperty("Max Hold (ticks)", new DoubleValue(10, 1000, 50, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ms)", new DoubleValue(0, 10000, 3000, 100));
    public final BooleanProperty faceTarget = new BooleanProperty("Face Target", true);
    public final BooleanProperty hitSlowdown = new BooleanProperty("Hit Slowdown", true);
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty showGhost = new BooleanProperty("Show Ghost", true);

    private static final int JOIN_GRACE_TICKS = 100;
    private static final float MAX_TURN = 60f;
    private static final double REACHED = 1.0;
    private static final double MOTION_CUTOFF = 0.005;
    /** Below vanilla's 3 so the hit still lands after the target moves a little before the release. */
    private static final double HIT_REACH = 2.9;
    private static final int GROUND_SEARCH = 5;
    /** Player.jumpMovementFactor while sprinting, rounded as the game does. */
    private static final float AIR_ACCEL_SPRINT = (float) ((double) 0.02F + (double) 0.02F * 0.3D);

    private boolean active;
    private int heldTicks;
    private Player target;
    private final MSTimer lastRun = MSTimer.expired();

    // through mode
    private Phase phase;
    private double runX, runZ;
    private double lastTargetX, lastTargetZ;
    private boolean holdLook;

    // simulated player
    private AABB box;
    private double motionX, motionY, motionZ;
    private boolean ground;
    private boolean horizontalCollision;
    private int jumpTicks;
    private float moveSpeed;
    private double jumpMotion;
    private float sentYaw, sentPitch;

    private volatile double[] pendingVelocity;

    @Override
    protected void onDisable() {
        if (active)
            finish(mc.player == null || mc.getConnection() == null);
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        if (active && event.getEntity() == mc.player)
            finish(true);
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (!active || mc.player == null || !(event.getPacket() instanceof ClientboundSetEntityMotionPacket))
            return;
        ClientboundSetEntityMotionPacket packet = (ClientboundSetEntityMotionPacket) event.getPacket();
        if (packet.id() == mc.player.getId())
            pendingVelocity = new double[]{packet.movement().x, packet.movement().y, packet.movement().z};
    };

    /** Knockback ends the run before this tick's movement, so the client takes it from where the server has us. */
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!active)
            return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            finish(true);
            return;
        }
        if (pendingVelocity != null)
            finish(false);
    };

    @RequiresPlayer
    @EventLink(Priorities.VERY_HIGH)
    public final Listener<EventUpdate.Pre> onPreUpdate = event -> {
        if (!active)
            return;
        // the real player is frozen, so its own movement packet (and sprint/sneak updates) never goes out; the
        // simulated tick's packet takes its place, one for one
        event.cancel();
        heldTicks++;

        boolean done = mode.getValue() == Mode.Through
                ? !throughTick()
                : !targetStillValid() || !simulateTick() || isBehind();
        if (done || heldTicks >= maxHold.getValue().getInput())
            finish(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onPostUpdate = event -> {
        if (active || !canStart())
            return;
        Player candidate = findTarget();
        if (candidate != null)
            start(candidate);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        if (!active)
            return;
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        if (!active)
            return;
        event.setSpeed(0f);
        event.setStrafe(0f);
        event.setJump(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventMove> onMove = event -> {
        if (!active)
            return;
        event.setForward(0f);
        event.setStrafe(0f);
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (!active || box == null || !showGhost.getValue() || mc.player == null)
            return;
        RenderUtils.drawBoundingBox(box, new Color(255, 60, 60).getRGB());
    };

    @Override
    public String getHudInfo() {
        return active ? heldTicks + "t" : null;
    }

    private boolean canStart() {
        LocalPlayer p = mc.player;
        return !mc.isLocalServer() && mc.getConnection() != null
                && p.tickCount >= JOIN_GRACE_TICKS
                && lastRun.finished((long) cooldown.getValue().getInput())
                && !LagManager.isLagging()
                && p.onGround() && p.isSprinting() && ((IMixinLocalPlayer) p).getServerSprintState() && !p.isShiftKeyDown() && !p.isPassenger() && !p.isInWater() && !p.isInLava()
                && !p.onClimbable() && !p.getAbilities().flying
                && (p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly);
    }

    private Player findTarget() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        double min = distance.getValue().getMin(), max = distance.getValue().getMax();
        for (Player player : PlayerUtils.getPlayersWithin(max + 1)) {
            if (!player.isAlive() || player.getHealth() <= 0 || !TargetManager.isValidTarget(player))
                continue;
            double d = horizontalDistance(mc.player.getX(), mc.player.getZ(), player);
            if (d >= min && d <= max && d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    /** Called after this tick's movement packet went out: the simulation continues from exactly that state. */
    private void start(Player candidate) {
        LocalPlayer p = mc.player;
        target = candidate;
        box = p.getBoundingBox();
        motionX = p.getDeltaMovement().x;
        motionY = p.getDeltaMovement().y;
        motionZ = p.getDeltaMovement().z;
        ground = p.onGround();
        jumpTicks = 0;

        // already sprinting (canStart), so this is the sprint ground speed the next tick uses
        moveSpeed = p.getSpeed();
        jumpMotion = (double) 0.42F;
        if (p.hasEffect(MobEffects.JUMP_BOOST))
            jumpMotion += (double) ((float) (p.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1F);

        IMixinLocalPlayer accessor = (IMixinLocalPlayer) p;
        sentYaw = accessor.getLastReportedYaw();
        sentPitch = accessor.getLastReportedPitch();

        double dx = target.getX() - p.getX(), dz = target.getZ() - p.getZ();
        double len = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4);
        runX = dx / len;
        runZ = dz / len;
        lastTargetX = target.getX();
        lastTargetZ = target.getZ();
        phase = Phase.RUN;
        holdLook = false;

        pendingVelocity = null;
        heldTicks = 0;
        active = true;
        LagManager.acquire(getClass());
    }

    /**
     * Moves the player to where the simulated copy is and releases every held packet in the order it was sent.
     * @param discard drop the held packets instead (the connection they belong to is gone)
     */
    private void finish(boolean discard) {
        active = false;
        lastRun.reset();
        LocalPlayer p = mc.player;

        if (discard || p == null || box == null) {
            LagManager.releaseAndDiscard(getClass());
            reset();
            return;
        }

        double x = posX(), y = box.minY, z = posZ();
        p.setPos(x, y, z);
        p.xo = p.xOld = x;
        p.yo = p.yOld = y;
        p.zo = p.zOld = z;
        double[] velocity = pendingVelocity;
        if (velocity != null) {
            p.setDeltaMovement(velocity[0], velocity[1], velocity[2]);
        } else {
            p.setDeltaMovement(motionX, motionY, motionZ);
        }
        p.setOnGround(ground);
        // frozen with no input, the client stopped sprinting (only client side, its packet was cancelled) and its
        // ground speed and air control fell to walking values; the server still has us sprinting at full speed
        p.setSprinting(true);
        p.setSpeed((float) p.getAttributeValue(Attributes.MOVEMENT_SPEED));

        IMixinLocalPlayer accessor = (IMixinLocalPlayer) p;
        accessor.setLastReportedYaw(sentYaw);
        accessor.setLastReportedPitch(sentPitch);

        if (faceTarget.getValue() && target != null && target.isAlive()) {
            float[] look = RotationUtils.getRotationsToEntity(target);
            float[] patched = RotationUtils.patchGCD(new float[]{p.getYRot(), p.getXRot()},
                    new float[]{p.getYRot() + Mth.wrapDegrees(look[0] - p.getYRot()), look[1]});
            p.setYRot(p.yRotO = patched[0]);
            p.setXRot(p.xRotO = patched[1]);
        }

        LagManager.release(getClass());
        reset();
    }

    private void reset() {
        target = null;
        box = null;
        heldTicks = 0;
        pendingVelocity = null;
        phase = null;
    }

    /**
     * One held tick of Through mode: run at the target and hit it once in reach, then keep running through it until
     * Escape Distance away. @return false when the run should be released here
     */
    private boolean throughTick() {
        if (target != null && mc.level.players().contains(target)) {
            lastTargetX = target.getX();
            lastTargetZ = target.getZ();
        }

        if (phase == Phase.RUN) {
            if (!targetStillValid())
                return false;
            // the click comes before the tick's movement, using the look the previous packet sent, as in vanilla
            if (facingTarget()) {
                hit();
                phase = Phase.ESCAPE;
            } else if ((posX() - target.getX()) * runX + (posZ() - target.getZ()) * runZ > 1.0) {
                // ran past without lining up a hit
                phase = Phase.ESCAPE;
            }
        }

        if (phase == Phase.ESCAPE) {
            double dx = posX() - lastTargetX, dz = posZ() - lastTargetZ;
            double escape = escapeDistance.getValue().getInput();
            if (dx * dx + dz * dz >= escape * escape || !groundAhead())
                return false;
        }
        return simulateTick();
    }

    /**
     * Swing and attack go into the hold. A 1.8 sprint hit slows the attacker to 0.6 before the tick's movement (the
     * client re-sprints in the same tick while sprint is held), and Grim expects that slowdown from a sprinting 1.8 hit.
     */
    private void hit() {
        send(new ServerboundAttackPacket(target.getId()));
        PlayerUtils.swingItem();
        holdLook = true;
        if (hitSlowdown.getValue()) {
            motionX *= 0.6D;
            motionZ *= 0.6D;
        }
    }

    /** Whether the last sent look from the simulated eyes lands on the target's hitbox within reach. */
    private boolean facingTarget() {
        Vec3 eyes = new Vec3(posX(), box.minY + mc.player.getEyeHeight(), posZ());
        Vec3 look = Entity.calculateViewVector(sentPitch, sentYaw);
        Vec3 end = eyes.add(look.x * HIT_REACH, look.y * HIT_REACH, look.z * HIT_REACH);
        // no collision border: the margin covers the target being somewhere else by the time the server checks
        AABB hitbox = target.getBoundingBox();
        return hitbox.contains(eyes) || hitbox.clip(eyes, end).isPresent();
    }

    /** Ground under the next few blocks the escape heads into, so it never jumps off into the void. */
    private boolean groundAhead() {
        float rad = sentYaw * 0.017453292F;
        double awayX = -Mth.sin(rad), awayZ = Mth.cos(rad);
        int feet = Mth.floor(box.minY);
        for (int step = 1; step <= 3; step++) {
            int x = Mth.floor(posX() + awayX * step), z = Mth.floor(posZ() + awayZ * step);
            boolean found = false;
            for (int y = feet; y >= feet - GROUND_SEARCH && !found; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!mc.level.isLoaded(pos))
                    return false;
                found = mc.level.getBlockState(pos).isSolid();
            }
            if (!found)
                return false;
        }
        return true;
    }

    /** Yaw and pitch to steer toward this tick. */
    private float[] wantedRotation() {
        if (mode.getValue() != Mode.Through) {
            double[] goal = steerPoint();
            return new float[]{yawTo(goal[0], goal[1]), sentPitch};
        }
        if (phase == Phase.RUN)
            return new float[]{yawTo(target.getX(), target.getZ()), pitchToTarget()};
        // keep going the way we came through until past where the target is, then straight away from it
        if ((posX() - lastTargetX) * runX + (posZ() - lastTargetZ) * runZ < 1.0)
            return new float[]{yawTo(lastTargetX + runX * 2, lastTargetZ + runZ * 2), 0f};
        return new float[]{yawTo(2 * posX() - lastTargetX, 2 * posZ() - lastTargetZ), 0f};
    }

    private float yawTo(double x, double z) {
        return (float) (Math.toDegrees(Math.atan2(z - posZ(), x - posX())) - 90.0);
    }

    /** Pitch at the middle of the target's hitbox, as far from its top and bottom edges as possible. */
    private float pitchToTarget() {
        double eyeY = box.minY + mc.player.getEyeHeight();
        AABB hitbox = target.getBoundingBox();
        double aimY = (hitbox.minY + hitbox.maxY) / 2.0;
        double horizontal = Math.max(horizontalDistance(posX(), posZ(), target), 1.0E-4);
        return (float) -Math.toDegrees(Math.atan2(aimY - eyeY, horizontal));
    }

    private boolean targetStillValid() {
        return target != null && target.isAlive() && target.getHealth() > 0
                && mc.level.players().contains(target)
                && horizontalDistance(posX(), posZ(), target) <= distance.getValue().getMax() * 2;
    }

    /** Behind the target: at the point opposite its look, or anywhere in the cone behind it at that distance. */
    private boolean isBehind() {
        double[] back = backVector();
        double bx = target.getX() + back[0] * behind.getValue().getInput();
        double bz = target.getZ() + back[1] * behind.getValue().getInput();
        double toPointX = posX() - bx, toPointZ = posZ() - bz;
        if (toPointX * toPointX + toPointZ * toPointZ <= REACHED * REACHED)
            return true;

        double offX = posX() - target.getX(), offZ = posZ() - target.getZ();
        double len = Math.sqrt(offX * offX + offZ * offZ);
        return len > 1.0E-4 && len <= behind.getValue().getInput() + 0.75
                && (offX * back[0] + offZ * back[1]) / len >= 0.5;
    }

    /** Unit horizontal vector pointing away from where the target looks. */
    private double[] backVector() {
        float rad = target.getYHeadRot() * (float) Math.PI / 180f;
        return new double[]{Mth.sin(rad), -Mth.cos(rad)};
    }

    /** Where to steer this tick: straight for the back point, or past the target's side first when in front of it. */
    private double[] steerPoint() {
        double[] back = backVector();
        double dist = behind.getValue().getInput();
        double offX = posX() - target.getX(), offZ = posZ() - target.getZ();
        if (offX * back[0] + offZ * back[1] >= 0)
            return new double[]{target.getX() + back[0] * dist, target.getZ() + back[1] * dist};

        // perpendicular to the look, on the side we are already on
        double sideX = -back[1], sideZ = back[0];
        if (offX * sideX + offZ * sideZ < 0) {
            sideX = -sideX;
            sideZ = -sideZ;
        }
        return new double[]{target.getX() + sideX * dist + back[0] * dist * 0.5,
                target.getZ() + sideZ * dist + back[1] * dist * 0.5};
    }

    /**
     * One simulated tick and its movement packet. When the tick cannot go ahead, the copy is put back to the last tick
     * that was sent, so the release lands exactly where the server already has us.
     * @return false when the run cannot continue (blocked, liquid, unloaded chunk)
     */
    private boolean simulateTick() {
        AABB lastBox = box;
        double lastX = motionX, lastY = motionY, lastZ = motionZ;
        boolean lastGround = ground;
        int lastJumpTicks = jumpTicks;
        boolean lastHoldLook = holdLook;
        if (step())
            return true;
        box = lastBox;
        motionX = lastX;
        motionY = lastY;
        motionZ = lastZ;
        ground = lastGround;
        jumpTicks = lastJumpTicks;
        holdLook = lastHoldLook;
        return false;
    }

    /**
     * One tick of EntityLivingBase.onLivingUpdate / moveEntityWithHeading for a player holding forward, sprint and jump,
     * then the movement packet for it. @return false when nothing was sent (blocked, liquid, unloaded chunk)
     */
    private boolean step() {
        // the hit tick keeps the look the hit was checked with, so the old and new look both land on the hitbox
        float[] wanted = holdLook ? new float[]{sentYaw, sentPitch} : wantedRotation();
        holdLook = false;
        float turn = Mth.clamp(Mth.wrapDegrees(wanted[0] - sentYaw), -MAX_TURN, MAX_TURN);
        float tilt = Mth.clamp(wanted[1] - sentPitch, -MAX_TURN, MAX_TURN);
        float[] rotation = RotationUtils.patchGCD(new float[]{sentYaw, sentPitch}, new float[]{sentYaw + turn, sentPitch + tilt});
        float yaw = rotation[0], pitch = rotation[1];
        float rad = yaw * 0.017453292F;
        float sin = Mth.sin(rad), cos = Mth.cos(rad);

        if (jumpTicks > 0)
            jumpTicks--;
        if (Math.abs(motionX) < MOTION_CUTOFF) motionX = 0;
        if (Math.abs(motionY) < MOTION_CUTOFF) motionY = 0;
        if (Math.abs(motionZ) < MOTION_CUTOFF) motionZ = 0;

        if (ground && jumpTicks == 0) {
            motionY = jumpMotion;
            motionX -= sin * 0.2F;
            motionZ += cos * 0.2F;
            jumpTicks = 10;
        }

        // moveEntityWithHeading: the drag is picked from the ground state before the move (the jump tick still drags
        // like ground, the landing tick like air)
        BlockPos below = belowPos();
        if (!mc.level.isLoaded(below))
            return false;
        float drag = ground ? slipperiness(below) * 0.91F : 0.91F;
        float accel = ground ? moveSpeed * (0.16277136F / (drag * drag * drag)) : AIR_ACCEL_SPRINT;

        // moveFlying with forward 0.98 (its length is below 1, so the friction is not divided)
        float forward = 0.98F * accel;
        float flyRad = yaw * (float) Math.PI / 180.0F;
        motionX += (double) (-forward * Mth.sin(flyRad));
        motionZ += (double) (forward * Mth.cos(flyRad));

        if (!move())
            return false;

        motionY -= 0.08D;
        motionY *= (double) 0.98F;
        motionX *= (double) drag;
        motionZ *= (double) drag;

        if (mc.level.containsAnyLiquid(box))
            return false;

        Packet<?> packet = yaw != sentYaw || pitch != sentPitch
                ? new ServerboundMovePlayerPacket.PosRot(posX(), box.minY, posZ(), yaw, pitch, ground, horizontalCollision)
                : new ServerboundMovePlayerPacket.Pos(posX(), box.minY, posZ(), ground, horizontalCollision);
        sentYaw = yaw;
        sentPitch = pitch;
        send(packet);
        return true;
    }

    /** Entity.move without sneaking or stepping. @return false when it hit a wall (sprinting would stop) */
    private boolean move() {
        Vec3 wanted = new Vec3(motionX, motionY, motionZ);
        Vec3 allowed = Entity.collideBoundingBox(mc.player, wanted, box, mc.level, List.of());
        double dx = allowed.x, dy = allowed.y, dz = allowed.z;
        box = box.move(dx, dy, dz);
        horizontalCollision = dx != motionX || dz != motionZ;

        boolean collidedY = dy != motionY;
        ground = collidedY && motionY < 0;
        if (collidedY)
            motionY = 0;
        return dx == motionX && dz == motionZ;
    }

    private BlockPos belowPos() {
        return new BlockPos(Mth.floor(posX()), Mth.floor(box.minY) - 1, Mth.floor(posZ()));
    }

    private float slipperiness(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock().getFriction();
    }

    private double posX() {
        return (box.minX + box.maxX) / 2.0;
    }

    private double posZ() {
        return (box.minZ + box.maxZ) / 2.0;
    }

    private static double horizontalDistance(double x, double z, Player player) {
        double dx = player.getX() - x, dz = player.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public enum Mode {
        Behind, Through
    }

    private enum Phase {
        RUN, ESCAPE
    }

    /** Goes through the network manager, so LagManager holds it with everything else. */
    private static void send(Packet<?> packet) {
        mc.getConnection().send(packet);
    }
}
