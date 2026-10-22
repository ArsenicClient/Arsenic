package arsenic.utils.scaffoldcore;

import arsenic.utils.aimcore.AimCore;
import arsenic.utils.botcore.Box;

import java.util.Random;

/**
 * Telly bridging decisions: when to turn to the bridge, how fast, and when to turn back. Placement and aim at the
 * block are {@link ScaffoldCore}'s, held to one layer: the one the jump took off from, so the bridge stays flat, or
 * one higher while the jump key is held, so each jump climbs a block.
 *
 * A jump goes through four phases. FORWARD looks where the camera looks. OUT starts a few ticks into the jump and
 * turns to the bridging yaw: not straight back but 135 degrees off the camera, where the movement keys come out as S
 * plus A or D. BRIDGE aims and places. BACK starts once the spot we will land on is held up, so the turn back to the
 * camera gets the rest of the jump instead of a snap, and we land facing forward to sprint-jump again. The turns out
 * and back are KillAura's Heuristics flicks ({@link AimCore}): min-jerk, a little longer or shorter each time, now and
 * then overshooting, and no tick much faster than {@link Tuning#maxStep}. If the landing block went down late, the
 * jump off loses its sprint rather than the rotation snapping.
 */
public final class TellyCore {

    public static final class Tuning {
        /** The original telly: turn when the jump is {@link #startTick} ticks old, snap back on landing. */
        public boolean legacy = false;
        public int startTick = 1;
        public float outTicks = 5;
        /** How far the bridging yaw sits off straight back; 45 makes the keys S plus A or D. */
        public float sideAngle = 45;
        /** 0 picks the side each jump, the one that looks back across the bridge; 1 or -1 always that side. */
        public int sideMode = 0;
        public float minBackTicks = 2;
        public float maxBackTicks = 5;
        /** Fastest tick of a turn out or back, in degrees. */
        public float maxStep = 70;
        /** Fastest re-aim while bridging, in degrees per tick. */
        public float bridgeSpeed = 90;
        /** Ticks before touching down without a block below when the re-aim is no longer capped (a fall is worse). */
        public int urgentTicks = 2;
        /** Share of the landing tick's horizontal move the block below has to cover too. */
        public double landLead = 0.5;
        /** How far the block has to reach in under the feet, so a near miss of the prediction still lands. */
        public double landInset = 0;
        /** Climb a block per jump while the jump key is held. */
        public boolean climb = true;
        /** The bridging yaw's angle off straight back while climbing. */
        public float climbSideAngle = 45;
        /** Ticks the turn out is given while climbing: a climbing jump comes down sooner. */
        public float climbOutTicks = 3;

        public static Tuning legacy() {
            Tuning t = new Tuning();
            t.legacy = true;
            return t;
        }

        public static Tuning best() {
            return new Tuning();
        }
    }

    public static final class Output {
        /** False: leave the rotation alone (it follows the camera). */
        public boolean modify;
        public float yaw, pitch, speed;
        public boolean smoothing;
        public boolean preventDuplicateLook;
    }

    private enum Phase { FORWARD, OUT, BRIDGE, BACK }

    /** Fastest tick of a min-jerk turn over its average. */
    private static final float TURN_PEAK = 1.875f;
    /** Target ids for the heuristic aim, so each turn starts its own flick. */
    private static final int TURN_OUT = 1, TURN_BACK = 2;

    public final Tuning tun;
    private final ScaffoldCore core;
    /** KillAura's Heuristics aim, for the turns out and back. */
    private final AimCore aim;
    private final Output out = new Output();

    private Phase phase = Phase.FORWARD;
    private int groundY = Integer.MIN_VALUE;
    private boolean climbing;
    private float turnTicks;
    /** Which way the bridging yaw sits off straight back this jump. */
    private int side = 1;
    /** The way the turn back goes, the short way as it was when it started. */
    private int backDir = 1;
    private boolean legacyBridging;

    public TellyCore(Tuning tun, ScaffoldCore.Tuning coreTuning, Random rnd) {
        this.tun = tun;
        ScaffoldCore.Tuning ct = coreTuning.copy();
        // the side is ours to choose: the core's own auto side only applies on grid headings
        if (!tun.legacy) ct.autoSide = false;
        this.core = new ScaffoldCore(ct, rnd);
        AimCore.Tuning at = AimCore.Tuning.best().copy();
        // its pitch cap is for tracking a player; a turn to the bridge goes from looking ahead to looking down
        at.pitchSpeedCap = 0;
        this.aim = new AimCore(at, rnd);
    }

    public void reset() {
        core.reset();
        aim.reset();
        phase = Phase.FORWARD;
        groundY = Integer.MIN_VALUE;
        climbing = false;
        legacyBridging = false;
    }

    public String phase() {
        return phase.name();
    }

    /** True while the module aims at the bridge (and may place). */
    public boolean bridging() {
        return tun.legacy ? legacyBridging : phase == Phase.OUT || phase == Phase.BRIDGE;
    }

    public ScaffoldCore.Target target() {
        return bridging() ? core.target() : null;
    }

    public float[] nudge() {
        return bridging() ? core.nudge() : null;
    }

    /**
     * The layer blocks go in: the bridge's own while flat. A climb is left free, since its first block of each step
     * sits on top of the step below and the next ones beside it, the way the original telly climbed.
     */
    private int layer() {
        return climbing ? Integer.MIN_VALUE : landY() - 1;
    }

    /** The height the feet come down to at the end of this jump. */
    private int landY() {
        return groundY + (climbing ? 1 : 0);
    }

    public Output rotate(ScaffoldCore.Input in, ScaffoldWorld w, int airTicks, float cameraPitch) {
        if (tun.legacy) return legacyRotate(in, w, airTicks);
        if (in.onGround) {
            groundY = ScaffoldCore.floor(in.posY);
            // holding jump climbs: this jump comes down a block up, on blocks placed at the height of our feet
            climbing = tun.climb && in.keyJump;
        }
        out.modify = false;
        out.smoothing = false;
        out.preventDuplicateLook = false;
        out.speed = 360;

        if (phase == Phase.FORWARD) {
            core.reset();
            if (in.onGround || airTicks < tun.startTick || !in.hasBlock || groundY == Integer.MIN_VALUE) return out;
            // this jump comes down on blocks that are already there
            if (landingSupported(in, w)) return out;
            side = tun.sideMode != 0 ? tun.sideMode : acrossSide(in);
            startOut(in);
        } else if (phase == Phase.BACK && !in.onGround && airTicks >= tun.startTick && !landingSupported(in, w)) {
            // the last landing block went down late and the turn back ran into this jump, which needs its blocks
            // too: turn out again from here, on the side we are still facing
            float off = wrap180(in.silentYaw - in.cameraYaw);
            side = off < 0 ? 1 : -1;
            startOut(in);
        }
        if (bridging() && (in.onGround || landingSupported(in, w))) {
            float ticks = in.onGround ? tun.minBackTicks : clamp(ticksToLanding(in) + 1, tun.minBackTicks, tun.maxBackTicks);
            float back = wrap180(in.cameraYaw - in.silentYaw);
            backDir = back < 0 ? -1 : 1;
            startTurn(Phase.BACK, ticks, Math.abs(back));
        }

        if (phase == Phase.BACK) {
            float yaw = in.silentYaw + turnDelta(in.cameraYaw - in.silentYaw, backDir);
            if (Math.abs(yaw - in.silentYaw) < 2f && Math.abs(cameraPitch - in.silentPitch) < 2f && !aim.isFlicking()) {
                phase = Phase.FORWARD;
                return out;
            }
            turn(in, yaw, cameraPitch, TURN_BACK);
            return out;
        }

        in.yawBias = side * (climbing ? tun.climbSideAngle : tun.sideAngle);
        in.layerY = layer();
        ScaffoldCore.Rotation aimAt = core.rotate(in, w);
        out.preventDuplicateLook = aimAt.preventDuplicateLook;
        if (phase == Phase.OUT) {
            // one way round even when the aim sits near straight back, where the short way flips sides
            float yaw = in.silentYaw + turnDelta(aimAt.yaw - in.silentYaw, -side);
            turn(in, yaw, aimAt.pitch, TURN_OUT);
            if (!aim.isFlicking()) phase = Phase.BRIDGE;
        } else {
            // placing wants the exact angles that meet the face, which move every tick as we fly over it
            out.modify = true;
            out.yaw = aimAt.yaw;
            out.pitch = aimAt.pitch;
            out.speed = ticksToLanding(in) <= tun.urgentTicks ? 360 : tun.bridgeSpeed;
        }
        return out;
    }

    /** One tick of a heuristic flick towards the given angles, given the ticks the current turn was allowed. */
    private void turn(ScaffoldCore.Input in, float yaw, float pitch, int target) {
        AimCore.Input a = new AimCore.Input();
        a.curYaw = in.silentYaw;
        a.curPitch = in.silentPitch;
        a.targetId = target;
        a.maxSpeed = tun.maxStep;
        a.budgetTicks = turnTicks;
        float[] r = aim.lazyStep(a, new float[]{yaw, pitch});
        out.modify = true;
        out.yaw = r[0];
        out.pitch = r[1];
    }

    /** Placement for this tick's final rotation; only while turned to the bridge. */
    public ScaffoldCore.Action post(ScaffoldCore.Input in, ScaffoldWorld w, float yaw, float pitch) {
        if (!tun.legacy) in.layerY = layer();
        ScaffoldCore.Action action = core.post(in, w, yaw, pitch);
        if (!bridging()) action.place = false;
        return action;
    }

    /**
     * The side whose bridging yaw looks back across the column below rather than away from it: the look-back ray only
     * meets the face of the last block while it crosses the bridge.
     */
    private int acrossSide(ScaffoldCore.Input in) {
        double ox = in.posX - (ScaffoldCore.floor(in.posX) + 0.5), oz = in.posZ - (ScaffoldCore.floor(in.posZ) + 0.5);
        float yaw = (in.cameraYaw + 180f + tun.sideAngle) * (float) Math.PI / 180f;
        double dot = -Math.sin(yaw) * ox + Math.cos(yaw) * oz;
        return dot <= 0 ? 1 : -1;
    }

    private void startOut(ScaffoldCore.Input in) {
        float bridgeOff = wrap180(180f + side * tun.sideAngle);
        float angle = Math.abs(wrap180(bridgeOff - wrap180(in.silentYaw - in.cameraYaw)));
        // a turn that starts part of the way round takes its share of the full turn's ticks
        startTurn(Phase.OUT, (climbing ? tun.climbOutTicks : tun.outTicks) * angle / (180f - tun.sideAngle), angle);
    }

    private void startTurn(Phase next, float ticks, float angle) {
        phase = next;
        turnTicks = Math.max(Math.max(1f, ticks), (float) Math.ceil(angle * TURN_PEAK / tun.maxStep));
    }

    /** The turn from the wrapped difference, taken in the given direction (sign) when it is more than 90 degrees. */
    private static float turnDelta(float diff, int dir) {
        float d = wrap180(diff);
        if (Math.abs(d) > 90f && Math.signum(d) != dir) d += 360f * dir;
        return d;
    }

    /** Whether the block below holds the feet where they come down to the jump's landing height. */
    private boolean landingSupported(ScaffoldCore.Input in, ScaffoldWorld w) {
        double[] land = predictLanding(in);
        // the feet need a block under where they come down, and still under part of that tick's move
        return land != null && !w.boxFree(feet(land[0], land[1]))
                && !w.boxFree(feet(land[0] + land[3] * tun.landLead, land[1] + land[4] * tun.landLead));
    }

    /**
     * Ticks until the feet come down. The jump off is the tick after that, and its rotation is set before it moves,
     * so a turn back has one tick more than this.
     */
    private int ticksToLanding(ScaffoldCore.Input in) {
        double[] land = predictLanding(in);
        return land == null ? (int) tun.maxBackTicks : (int) land[2];
    }

    private Box feet(double x, double z) {
        double r = 0.3 - tun.landInset;
        int y = landY();
        return new Box(x - r, y - 0.5, z - r, x + r, y, z + r);
    }

    /**
     * Air movement until the feet come down to the landing height: {x, z, ticks, dx, dz} where x and z are where the
     * landing tick starts (Minecraft moves along Y first) and dx, dz that tick's horizontal move.
     */
    private double[] predictLanding(ScaffoldCore.Input in) {
        if (groundY == Integer.MIN_VALUE) return null;
        int land = landY();
        double x = in.posX, y = in.posY, z = in.posZ;
        double mx = in.motionX, my = in.motionY, mz = in.motionZ;
        float yawRad = in.cameraYaw * (float) Math.PI / 180f;
        double ax = -ScaffoldCore.sin(yawRad) * 0.02, az = ScaffoldCore.cos(yawRad) * 0.02;
        for (int t = 1; t <= 40; t++) {
            mx += ax;
            mz += az;
            // only coming down counts: a climb's landing height is above where the jump started
            if (my < 0 && y + my <= land) return new double[]{x, z, t, mx, mz};
            x += mx;
            y += my;
            z += mz;
            my = (my - 0.08) * 0.98;
            mx *= 0.91;
            mz *= 0.91;
        }
        return null;
    }

    private Output legacyRotate(ScaffoldCore.Input in, ScaffoldWorld w, int airTicks) {
        legacyBridging = !in.onGround && airTicks >= tun.startTick;
        out.smoothing = true;
        if (!legacyBridging) {
            core.reset();
            out.modify = false;
            out.speed = (float) in.speedMax;
            return out;
        }
        ScaffoldCore.Rotation r = core.rotate(in, w);
        out.modify = true;
        out.yaw = r.yaw;
        out.pitch = r.pitch;
        out.speed = r.speed;
        out.preventDuplicateLook = r.preventDuplicateLook;
        return out;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static float wrap180(float v) {
        v %= 360f;
        if (v >= 180f) v -= 360f;
        if (v < -180f) v += 360f;
        return v;
    }
}
