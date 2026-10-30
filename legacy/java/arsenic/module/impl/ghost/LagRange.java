package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventDropTick;
import arsenic.event.impl.EventMovementInput;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventRunTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.SliderScale;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.lag.PingTracker;
import arsenic.utils.lag.TimerBalance;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.injection.accessor.IMixinEntityPlayerSP;
import arsenic.runtime.hooks.MinecraftHooks;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C18PacketSpectate;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Wins the race into hit range: the server and the target see us further back than we are until our first hit.
 *
 * Lag: from five blocks out, every outgoing packet is held for a delay that builds up over 600 ms (Delay), so the
 * server keeps us where we were and the target cannot reach us yet. Everything is let go at once when we attack: the
 * held movement arrives first and the attack right after it, from the position we really swung from, so we land the
 * first hit. It also lets go when holding stops helping (the held position is closer to the target than we are, i.e. we
 * are backing off) or the target is gone. The delay only grows while holding, so no packet overtakes another.
 *
 * Speed Up, closing in on a target that can be hit: on the first tick the timer balance {@link TimerBalance} estimates
 * Grim allows covers the whole way to reach (3 blocks from the eyes to its hitbox, at the ground covered toward it last
 * tick), extra player ticks are run at the start of the game tick (normal movement from your own input) until we are
 * just inside reach, and no more. The target stands still meanwhile, as far as the server is concerned: they all arrive
 * at once. The crosshair target is then worked out again, so the click that follows in the same tick aims from where we
 * now are. Held movement packets have not reached Grim's balance yet, so they count as already spent.
 *
 * Dodge, on those extra ticks only: the strafe input (A/D) is replaced to move away from the side of us the target's
 * aim ray is on, so we arrive beside where they are swinging. Their aim is led by their turn over our ping, since what
 * we see of it is a one way trip old. Forward input is left alone, and with the movement fix the strafe stays relative
 * to the camera. Every other tick the input is the player's own.
 *
 * Slow Down, in reach while there is no point swinging (the target is still invulnerable from its last hit by the time
 * anything sent now would arrive, its hurt time against our ping): such ticks are dropped (no movement and no movement
 * packet), which refills the balance for the next approach; not when a swing, attack, dig or similar went out this tick
 * (Grim's Post check wants this tick's movement packet after it), only with room left to bank, and at most a few ticks
 * in a row.
 */
@ModuleInfo(name = "LagRange", description = "Holds your packets while closing in and lets them go with your first hit", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class LagRange extends Module {

    /** Ping plus this must stay under Grim's 1000 ms TimerLimit threshold, or the bank stops growing. */
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 1000, 300, 500, 10), SliderScale.LOG);
    public final BooleanProperty speedUp = new BooleanProperty("Speed Up", true);
    public final BooleanProperty slowDown = new BooleanProperty("Slow Down", true);
    public final BooleanProperty dodge = new BooleanProperty("Dodge", true);
    /** Spare kept against ping jitter; raise it if Timer still flags. */
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty margin = new DoubleProperty("Safety Margin (ms)", new DoubleValue(0, 200, 50, 5));

    /** Vanilla reach: the server checks hits against it. */
    private static final double HIT_RANGE = 3.0;
    private static final double SEARCH_RANGE = 20;
    /** Holding only starts further out than this, as LagRange always did. */
    private static final double LAG_START_DISTANCE = 5.0;
    /** The bank is the real limit. */
    private static final int MAX_SPEED_TICKS = 10;
    /** Ground covered toward the target per tick below which we are not closing in. */
    private static final double MIN_CLOSING = 0.05;
    /** A frozen player cannot hit back, so not longer than this. */
    private static final int MAX_SLOW_TICKS = 3;
    private static final long LAG_COOLDOWN_MS = 500;
    private static final int JOIN_GRACE_TICKS = 100;
    /** Ticks of invulnerability the target must still have when our packets arrive for a tick to be dropped. */
    private static final int HURT_SLACK = 1;
    /** Speed Up only while the target is within this many degrees of where we look. */
    private static final float SPEED_FOV = 60f;
    private static final double LAG_BUILD_UP_MS = 600;
    private static final int MAX_POSITION_HISTORY = 60;
    /** Their aim within this many blocks of our middle counts as on us: keep strafing the way we were going. */
    private static final double DODGE_DEADZONE = 0.15;
    /** Their turn is carried forward at most this many ticks. */
    private static final float MAX_AIM_LEAD_TICKS = 6;

    private EntityPlayer target;
    private double distance;
    private boolean extraTicking;
    private volatile boolean actionSinceMovement;
    private int droppedInARow;
    /** +1 left, -1 right. */
    private float dodgeSide = 1;

    private boolean lagging;
    private double lagMs, lagTargetMs;
    private final MSTimer lastRelease = MSTimer.expired();
    /** Our position at the start of each game tick, newest first. */
    private final ArrayDeque<Vec3> positions = new ArrayDeque<>();

    @Override
    protected void onDisable() {
        stopLag();
        target = null;
        positions.clear();
        droppedInARow = 0;
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        if (event.getEntity() == mc.thePlayer) {
            stopLag();
            positions.clear();
        }
    };

    /** Start of the game tick, before clicks and before the world ticks the player. */
    @EventLink
    public final Listener<EventRunTick> onRunTick = event -> {
        if (mc.thePlayer == null || mc.theWorld == null)
            return;
        positions.addFirst(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        while (positions.size() > MAX_POSITION_HISTORY)
            positions.removeLast();

        target = mc.isSingleplayer() || mc.getNetHandler() == null ? null : findTarget();
        distance = target == null ? Double.MAX_VALUE : RotationUtils.getDistanceToEntityBox(target);
        updateLag();
        if (droppedInARow > 0)
            guardBlockClick();

        if (!shouldSpeedUp())
            return;
        // the normal tick still has to go out after these
        int extra = Math.min(MAX_SPEED_TICKS, spareTicks() - 1);
        // not before the bank covers the whole way in, so we arrive just inside reach rather than spend it short
        int needed = ticksToReach();
        if (needed < 1 || needed > extra)
            return;
        int ran = 0;
        while (ran < extra && RotationUtils.getDistanceToEntityBox(target) > HIT_RANGE) {
            runExtraTick();
            ran++;
        }
        if (ran > 0) {
            // this tick's click aims from where we now are, as the server checks it
            mc.entityRenderer.getMouseOver(1.0F);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventDropTick> onDropTick = event -> {
        if (extraTicking)
            return;
        if (shouldSlowDown()) {
            event.cancel();
            droppedInARow++;
        } else {
            droppedInARow = 0;
        }
    };

    /** Dodge, on Speed Up's extra ticks: strafe input away from the side of us the target's aim is on. */
    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        if (!dodge.getValue() || !extraTicking || target == null || !updateDodgeSide())
            return;
        event.setStrafe(dodgeSide * (mc.thePlayer.movementInput.sneak ? 0.3f : 1f));
    };

    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof EntityPlayer)
            stopLag();
    };

    /** Packets that went out since our last movement packet that Grim's Post check wants a movement packet after. */
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (event.isCancelled())
            return;
        Packet<?> p = event.getPacket();
        if (p instanceof C03PacketPlayer)
            actionSinceMovement = false;
        else if (p instanceof C0APacketAnimation || p instanceof C02PacketUseEntity || p instanceof C07PacketPlayerDigging
                || p instanceof C08PacketPlayerBlockPlacement || p instanceof C09PacketHeldItemChange
                || p instanceof C0BPacketEntityAction || p instanceof C0EPacketClickWindow
                || p instanceof C13PacketPlayerAbilities || p instanceof C18PacketSpectate)
            actionSinceMovement = true;
    };

    private boolean shouldSpeedUp() {
        if (!speedUp.getValue() || target == null)
            return false;
        EntityPlayerSP p = mc.thePlayer;
        return distance > HIT_RANGE
                && p.movementInput.moveForward > 0 && !p.isRiding() && mc.currentScreen == null
                && Math.abs(RotationUtils.fovFromEntity(target)) <= SPEED_FOV
                // only worth arriving early if the hit can land
                && !noPointSwinging();
    }

    /**
     * Extra ticks it takes to come within reach, at the ground we covered toward the target last tick. The target does
     * not move during them: they reach the server together. @return less than 1 when we are not closing in
     */
    private int ticksToReach() {
        EntityPlayerSP p = mc.thePlayer;
        double dx = target.posX - p.posX, dz = target.posZ - p.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4)
            return 0;
        double closing = ((p.posX - p.lastTickPosX) * dx + (p.posZ - p.lastTickPosZ) * dz) / len;
        if (closing < MIN_CLOSING)
            return 0;
        return (int) Math.ceil((distance - HIT_RANGE) / closing);
    }

    private boolean shouldSlowDown() {
        if (!slowDown.getValue() || target == null || mc.thePlayer.ticksExisted < JOIN_GRACE_TICKS)
            return false;
        if (distance > HIT_RANGE || droppedInARow >= MAX_SLOW_TICKS)
            return false;
        if (actionSinceMovement || !noPointSwinging())
            return false;
        return TimerBalance.getMaxBalanceMs() - TimerBalance.getBalanceMs() >= 50 + margin.getValue().getInput();
    }

    /**
     * After a dropped tick the server still has the look from before it, while the crosshair kept following the camera.
     * A dig started now (a click, or holding attack over a block) is checked against that old look by Grim's
     * RotationBreak, so when the crosshair is on a block the old look does not hit, the attack key is ignored this tick.
     */
    private void guardBlockClick() {
        mc.entityRenderer.getMouseOver(1.0F);
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;
        EntityPlayerSP p = mc.thePlayer;
        IMixinEntityPlayerSP reported = (IMixinEntityPlayerSP) p;
        Vec3 eyes = p.getPositionEyes(1f);
        Vec3 look = ((IMixinEntity) p).invokeGetVectorForRotation(reported.getLastReportedPitch(), reported.getLastReportedYaw());
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition old = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        if (old == null || old.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !over.getBlockPos().equals(old.getBlockPos()))
            MinecraftHooks.blockAttackThisTick();
    }

    /** The server still has the target invulnerable from its last hit when anything sent now arrives. */
    private boolean noPointSwinging() {
        return target.hurtTime - latencyTicks() >= HURT_SLACK;
    }

    /**
     * Ticks between a hurt animation reaching us and our answer reaching the server: the target was hit one way trip
     * before we saw it, and what we send takes the other way, plus whatever Lag is holding.
     */
    private int latencyTicks() {
        return (int) Math.ceil((PingTracker.getPing() + (lagging ? lagMs : 0)) / 50.0);
    }

    /**
     * Picks the strafe side away from the target's aim ray. Their look as we see it is a one way trip old and our
     * movement takes the other way, so their turn is carried forward by our ping.
     * @return false when they are not looking our way (leave the input alone)
     */
    private boolean updateDodgeSide() {
        EntityPlayerSP p = mc.thePlayer;
        float turn = MathHelper.wrapAngleTo180_float(target.rotationYawHead - target.prevRotationYawHead);
        float lead = Math.min(MAX_AIM_LEAD_TICKS, PingTracker.getPing() / 50f);
        float rad = (target.rotationYawHead + turn * lead) * 0.017453292F;
        double aimX = -MathHelper.sin(rad), aimZ = MathHelper.cos(rad);

        double toX = p.posX - target.posX, toZ = p.posZ - target.posZ;
        double along = toX * aimX + toZ * aimZ;
        if (along <= 0)
            return false;
        // our offset from their aim ray, sideways
        double offX = toX - aimX * along, offZ = toZ - aimZ * along;
        if (offX * offX + offZ * offZ < DODGE_DEADZONE * DODGE_DEADZONE)
            return true;

        // strafe left (+1) moves along (cos yaw, sin yaw), as in moveFlying
        float ourRad = p.rotationYaw * 0.017453292F;
        dodgeSide = offX * MathHelper.cos(ourRad) + offZ * MathHelper.sin(ourRad) >= 0 ? 1 : -1;
        return true;
    }

    /** Movement packets the balance allows now, counting held ones as spent: they may be released at any moment. */
    private int spareTicks() {
        int spare = TimerBalance.getSpareTicks(margin.getValue().getInput());
        if (lagging)
            spare -= LagManager.countDelayedOutgoing(p -> p instanceof C03PacketPlayer);
        return spare;
    }

    /** One more player tick inside this game tick, the way World.updateEntity runs it. */
    private void runExtraTick() {
        EntityPlayerSP p = mc.thePlayer;
        extraTicking = true;
        try {
            p.lastTickPosX = p.posX;
            p.lastTickPosY = p.posY;
            p.lastTickPosZ = p.posZ;
            p.ticksExisted++;
            p.onUpdate();
        } finally {
            extraTicking = false;
        }
    }

    /** Holds on the way in; reaching the target does not let go, the first attack does (see onAttack). */
    private void updateLag() {
        if (target == null) {
            stopLag();
            return;
        }
        if (lagging) {
            if (heldPositionIsCloser())
                stopLag();
            else
                lagMs = Math.min(lagTargetMs, lagMs + lagTargetMs * 50.0 / LAG_BUILD_UP_MS);
            return;
        }
        if (distance > LAG_START_DISTANCE && lastRelease.finished(LAG_COOLDOWN_MS))
            startLag();
    }

    /** The server has us where we were Lag's delay ago; holding only helps while that is further away than now. */
    private boolean heldPositionIsCloser() {
        int ticksAgo = (int) Math.round(lagMs / 50.0);
        if (ticksAgo <= 0 || positions.size() <= ticksAgo)
            return false;
        Iterator<Vec3> it = positions.iterator();
        Vec3 held = null;
        for (int i = 0; i <= ticksAgo && it.hasNext(); i++)
            held = it.next();
        Vec3 now = positions.peekFirst();
        Vec3 enemy = new Vec3(target.posX, target.posY, target.posZ);
        return held != null && held.distanceTo(enemy) < now.distanceTo(enemy);
    }

    private void startLag() {
        lagging = true;
        lagMs = 0;
        lagTargetMs = delay.getValue().getRandomInRange();
        // read per packet, and it only grows while holding, so later packets never leave before earlier ones
        LagManager.delayOutgoing(LagRange.class, LagManager.ALL_PACKETS, p -> (long) lagMs);
    }

    private void stopLag() {
        if (!lagging)
            return;
        lagging = false;
        lagMs = 0;
        lastRelease.reset();
        // held ones first, then stop holding; the second release catches one queued meanwhile (keep-alive answers are
        // sent from the network thread)
        LagManager.releaseDelayedOutgoingFor(LagRange.class);
        LagManager.undelayOutgoing(LagRange.class);
        LagManager.releaseDelayedOutgoingFor(LagRange.class);
    }

    private EntityPlayer findTarget() {
        EntityPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        double max = SEARCH_RANGE;
        for (EntityPlayer player : PlayerUtils.getPlayersWithin(max + 1)) {
            if (player.isDead || player.getHealth() <= 0 || !TargetManager.isValidTarget(player))
                continue;
            double d = RotationUtils.getDistanceToEntityBox(player);
            if (d <= max && d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }
}
