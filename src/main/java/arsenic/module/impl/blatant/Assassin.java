package arsenic.module.impl.blatant;

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
import arsenic.injection.accessor.IMixinEntityPlayerSP;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.List;

/**
 * When a target is about ten blocks away, the player is frozen in place client side and every outgoing packet is held.
 * While frozen, a copy of the player is simulated sprint jumping (1.8.9 movement physics against the world's collision
 * boxes) around to the point behind the target, the opposite way to where the target is looking. Each simulated tick
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
 */
@ModuleInfo(name = "Assassin", description = "Blinks a simulated sprint jump to behind the nearest player", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class Assassin extends Module {

    public final RangeProperty distance = new RangeProperty("Trigger Distance", new RangeValue(4, 20, 9, 11, 0.5));
    public final DoubleProperty behind = new DoubleProperty("Behind Distance", new DoubleValue(1, 4, 2, 0.1));
    public final DoubleProperty maxHold = new DoubleProperty("Max Hold (ticks)", new DoubleValue(10, 80, 50, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ms)", new DoubleValue(0, 10000, 3000, 100));
    public final BooleanProperty faceTarget = new BooleanProperty("Face Target", true);
    public final BooleanProperty showGhost = new BooleanProperty("Show Ghost", true);

    private static final int JOIN_GRACE_TICKS = 100;
    private static final float MAX_TURN = 60f;
    private static final double REACHED = 1.0;
    private static final double MOTION_CUTOFF = 0.005;
    /** EntityPlayer.jumpMovementFactor while sprinting, rounded as the game does. */
    private static final float AIR_ACCEL_SPRINT = (float) ((double) 0.02F + (double) 0.02F * 0.3D);

    private boolean active;
    private int heldTicks;
    private EntityPlayer target;
    private final MSTimer lastRun = MSTimer.expired();

    // simulated player
    private AxisAlignedBB box;
    private double motionX, motionY, motionZ;
    private boolean ground;
    private int jumpTicks;
    private float moveSpeed;
    private double jumpMotion;
    private float sentYaw, sentPitch;

    private volatile double[] pendingVelocity;

    @Override
    protected void onDisable() {
        if (active)
            finish(mc.thePlayer == null || mc.getNetHandler() == null);
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        if (active && event.getEntity() == mc.thePlayer)
            finish(true);
    };

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (!active || mc.thePlayer == null || !(event.getPacket() instanceof S12PacketEntityVelocity))
            return;
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() == mc.thePlayer.getEntityId())
            pendingVelocity = new double[]{packet.getMotionX() / 8000.0, packet.getMotionY() / 8000.0, packet.getMotionZ() / 8000.0};
    };

    /** Knockback ends the run before this tick's movement, so the client takes it from where the server has us. */
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!active)
            return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
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

        boolean done = !targetStillValid() || !simulateTick() || isBehind();
        if (done || heldTicks >= maxHold.getValue().getInput())
            finish(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onPostUpdate = event -> {
        if (active || !canStart())
            return;
        EntityPlayer candidate = findTarget();
        if (candidate != null)
            start(candidate);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        if (!active)
            return;
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
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
        if (!active || box == null || !showGhost.getValue() || mc.thePlayer == null)
            return;
        RenderUtils.drawBoundingBox(new Vec3(posX(), box.minY, posZ()), new Color(255, 60, 60));
    };

    @Override
    public String getHudInfo() {
        return active ? heldTicks + "t" : null;
    }

    private boolean canStart() {
        EntityPlayerSP p = mc.thePlayer;
        return !mc.isSingleplayer() && mc.getNetHandler() != null
                && p.ticksExisted >= JOIN_GRACE_TICKS
                && lastRun.finished((long) cooldown.getValue().getInput())
                && !LagManager.isLagging()
                && p.onGround && p.isSprinting() && ((IMixinEntityPlayerSP) p).getServerSprintState() && !p.isSneaking() && !p.isRiding() && !p.isInWater() && !p.isInLava()
                && !p.isOnLadder() && !p.capabilities.isFlying
                && (p.getFoodStats().getFoodLevel() > 6 || p.capabilities.allowFlying);
    }

    private EntityPlayer findTarget() {
        EntityPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        double min = distance.getValue().getMin(), max = distance.getValue().getMax();
        for (EntityPlayer player : PlayerUtils.getPlayersWithin(max + 1)) {
            if (player.isDead || player.getHealth() <= 0 || !TargetManager.isValidTarget(player))
                continue;
            double d = horizontalDistance(mc.thePlayer.posX, mc.thePlayer.posZ, player);
            if (d >= min && d <= max && d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    /** Called after this tick's movement packet went out: the simulation continues from exactly that state. */
    private void start(EntityPlayer candidate) {
        EntityPlayerSP p = mc.thePlayer;
        target = candidate;
        box = p.getEntityBoundingBox();
        motionX = p.motionX;
        motionY = p.motionY;
        motionZ = p.motionZ;
        ground = p.onGround;
        jumpTicks = 0;

        // already sprinting (canStart), so this is the sprint ground speed the next tick uses
        moveSpeed = p.getAIMoveSpeed();
        jumpMotion = (double) 0.42F;
        if (p.isPotionActive(Potion.jump))
            jumpMotion += (double) ((float) (p.getActivePotionEffect(Potion.jump).getAmplifier() + 1) * 0.1F);

        IMixinEntityPlayerSP accessor = (IMixinEntityPlayerSP) p;
        sentYaw = accessor.getLastReportedYaw();
        sentPitch = accessor.getLastReportedPitch();

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
        EntityPlayerSP p = mc.thePlayer;

        if (discard || p == null || box == null) {
            LagManager.releaseAndDiscard(getClass());
            reset();
            return;
        }

        double x = posX(), y = box.minY, z = posZ();
        p.setPosition(x, y, z);
        p.prevPosX = p.lastTickPosX = x;
        p.prevPosY = p.lastTickPosY = y;
        p.prevPosZ = p.lastTickPosZ = z;
        double[] velocity = pendingVelocity;
        if (velocity != null) {
            p.motionX = velocity[0];
            p.motionY = velocity[1];
            p.motionZ = velocity[2];
        } else {
            p.motionX = motionX;
            p.motionY = motionY;
            p.motionZ = motionZ;
        }
        p.onGround = ground;
        // frozen with no input, the client stopped sprinting (only client side, its packet was cancelled) and its
        // ground speed and air control fell to walking values; the server still has us sprinting at full speed
        p.setSprinting(true);
        p.setAIMoveSpeed((float) p.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue());
        p.jumpMovementFactor = AIR_ACCEL_SPRINT;

        IMixinEntityPlayerSP accessor = (IMixinEntityPlayerSP) p;
        accessor.setLastReportedYaw(sentYaw);
        accessor.setLastReportedPitch(sentPitch);

        if (faceTarget.getValue() && target != null && !target.isDead) {
            float[] look = RotationUtils.getRotationsToEntity(target);
            float[] patched = RotationUtils.patchGCD(new float[]{p.rotationYaw, p.rotationPitch},
                    new float[]{p.rotationYaw + MathHelper.wrapAngleTo180_float(look[0] - p.rotationYaw), look[1]});
            p.rotationYaw = p.prevRotationYaw = patched[0];
            p.rotationPitch = p.prevRotationPitch = patched[1];
        }

        LagManager.release(getClass());
        reset();
    }

    private void reset() {
        target = null;
        box = null;
        heldTicks = 0;
        pendingVelocity = null;
    }

    private boolean targetStillValid() {
        return target != null && !target.isDead && target.getHealth() > 0
                && mc.theWorld.playerEntities.contains(target)
                && horizontalDistance(posX(), posZ(), target) <= distance.getValue().getMax() * 2;
    }

    /** Behind the target: at the point opposite its look, or anywhere in the cone behind it at that distance. */
    private boolean isBehind() {
        double[] back = backVector();
        double bx = target.posX + back[0] * behind.getValue().getInput();
        double bz = target.posZ + back[1] * behind.getValue().getInput();
        double toPointX = posX() - bx, toPointZ = posZ() - bz;
        if (toPointX * toPointX + toPointZ * toPointZ <= REACHED * REACHED)
            return true;

        double offX = posX() - target.posX, offZ = posZ() - target.posZ;
        double len = Math.sqrt(offX * offX + offZ * offZ);
        return len > 1.0E-4 && len <= behind.getValue().getInput() + 0.75
                && (offX * back[0] + offZ * back[1]) / len >= 0.5;
    }

    /** Unit horizontal vector pointing away from where the target looks. */
    private double[] backVector() {
        float rad = target.rotationYawHead * (float) Math.PI / 180f;
        return new double[]{MathHelper.sin(rad), -MathHelper.cos(rad)};
    }

    /** Where to steer this tick: straight for the back point, or past the target's side first when in front of it. */
    private double[] steerPoint() {
        double[] back = backVector();
        double dist = behind.getValue().getInput();
        double offX = posX() - target.posX, offZ = posZ() - target.posZ;
        if (offX * back[0] + offZ * back[1] >= 0)
            return new double[]{target.posX + back[0] * dist, target.posZ + back[1] * dist};

        // perpendicular to the look, on the side we are already on
        double sideX = -back[1], sideZ = back[0];
        if (offX * sideX + offZ * sideZ < 0) {
            sideX = -sideX;
            sideZ = -sideZ;
        }
        return new double[]{target.posX + sideX * dist + back[0] * dist * 0.5,
                target.posZ + sideZ * dist + back[1] * dist * 0.5};
    }

    /**
     * One tick of EntityLivingBase.onLivingUpdate / moveEntityWithHeading for a player holding forward, sprint and jump,
     * then the movement packet for it. @return false when the run cannot continue (blocked, liquid, unloaded chunk)
     */
    private boolean simulateTick() {
        double[] goal = steerPoint();
        float wanted = (float) (Math.toDegrees(Math.atan2(goal[1] - posZ(), goal[0] - posX())) - 90.0);
        float turn = MathHelper.clamp_float(MathHelper.wrapAngleTo180_float(wanted - sentYaw), -MAX_TURN, MAX_TURN);
        float[] rotation = RotationUtils.patchGCD(new float[]{sentYaw, sentPitch}, new float[]{sentYaw + turn, sentPitch});
        float yaw = rotation[0];
        float rad = yaw * 0.017453292F;
        float sin = MathHelper.sin(rad), cos = MathHelper.cos(rad);

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
        if (!mc.theWorld.isBlockLoaded(below))
            return false;
        float drag = ground ? slipperiness(below) * 0.91F : 0.91F;
        float accel = ground ? moveSpeed * (0.16277136F / (drag * drag * drag)) : AIR_ACCEL_SPRINT;

        // moveFlying with forward 0.98 (its length is below 1, so the friction is not divided)
        float forward = 0.98F * accel;
        float flyRad = yaw * (float) Math.PI / 180.0F;
        motionX += (double) (-forward * MathHelper.sin(flyRad));
        motionZ += (double) (forward * MathHelper.cos(flyRad));

        if (!move())
            return false;

        motionY -= 0.08D;
        motionY *= (double) 0.98F;
        motionX *= (double) drag;
        motionZ *= (double) drag;

        if (mc.theWorld.isAnyLiquid(box))
            return false;

        Packet<?> packet = yaw != sentYaw
                ? new C03PacketPlayer.C06PacketPlayerPosLook(posX(), box.minY, posZ(), yaw, sentPitch, ground)
                : new C03PacketPlayer.C04PacketPlayerPosition(posX(), box.minY, posZ(), ground);
        sentYaw = yaw;
        send(packet);
        return true;
    }

    /** Entity.moveEntity without sneaking or stepping. @return false when it hit a wall (sprinting would stop) */
    private boolean move() {
        double dx = motionX, dy = motionY, dz = motionZ;
        List<AxisAlignedBB> boxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.addCoord(dx, dy, dz));

        AxisAlignedBB moved = box;
        for (AxisAlignedBB b : boxes)
            dy = b.calculateYOffset(moved, dy);
        moved = moved.offset(0, dy, 0);
        for (AxisAlignedBB b : boxes)
            dx = b.calculateXOffset(moved, dx);
        moved = moved.offset(dx, 0, 0);
        for (AxisAlignedBB b : boxes)
            dz = b.calculateZOffset(moved, dz);
        moved = moved.offset(0, 0, dz);
        box = moved;

        boolean collidedY = dy != motionY;
        ground = collidedY && motionY < 0;
        if (collidedY)
            motionY = 0;
        return dx == motionX && dz == motionZ;
    }

    private BlockPos belowPos() {
        return new BlockPos(MathHelper.floor_double(posX()), MathHelper.floor_double(box.minY) - 1, MathHelper.floor_double(posZ()));
    }

    private float slipperiness(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock().slipperiness;
    }

    private double posX() {
        return (box.minX + box.maxX) / 2.0;
    }

    private double posZ() {
        return (box.minZ + box.maxZ) / 2.0;
    }

    private static double horizontalDistance(double x, double z, EntityPlayer player) {
        double dx = player.posX - x, dz = player.posZ - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Goes through the network manager, so LagManager holds it with everything else. */
    private static void send(Packet<?> packet) {
        mc.getNetHandler().addToSendQueue(packet);
    }
}
