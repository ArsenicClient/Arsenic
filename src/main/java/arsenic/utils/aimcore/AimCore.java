package arsenic.utils.aimcore;

import java.util.Random;

/**
 * The aim maths shared by KillAura/AimAssist (through AimController), kept free of Minecraft
 * classes.
 *
 * Per tick: where on the target to look (a point inside its box, led by its predicted movement,
 * drifting around the chest while we move) and how far to turn towards it this tick (Lazy: a
 * minimum-jerk flick for big errors, a loose follow for small ones). Every behaviour change sits
 * behind a {@link Tuning} switch; legacy() is the original.
 */
public final class AimCore {

    /** Knobs. best() is the tuned aim; legacy() is the original aim. */
    public static final class Tuning {
        /** Ticks of target movement to lead the aim by. */
        public float predictionTicks = 3f;
        /**
         * Lead by a smoothed estimate of the target's velocity instead of its last tick's movement
         * (0 = raw last tick). Other players' positions arrive every other tick in 1/32-block steps,
         * only once they've moved 1/8 of a block, and are eased in over 3 ticks, so the per-tick
         * movement is lumpy, and leading by 3x that lump throws the aim around.
         */
        public float velocitySmoothing = 0f;
        /** Use our own motion for the relative movement (pos - lastTickPos is always 0 at aim time). */
        public boolean selfFromMotion = false;
        /**
         * Aim height: 0 = the box point nearest our eyes (original), 1 = the drift point on the body.
         * Blended in by this much on top of the moving/standing blend.
         */
        public float bodyHeight = 0f;
        /**
         * Horizontal aim: pull the nearest point this much towards the box's centre line. Up close
         * the nearest point is a few cm away, so the pitch to it swings wildly with every step.
         */
        public float centrePull = 0f;
        /** Ease the aim point towards its new position each tick (0 = none). */
        public float aimPointEasing = 0f;
        /** Follow gain changes slowly instead of being re-rolled every tick. */
        public boolean smoothGain = false;
        /** Error (degrees) past which a turn is a flick rather than tracking. */
        public float flickThreshold = 12f;
        /** Pitch follow gain relative to yaw. */
        public float pitchGainScale = 1f;
        /** Pitch errors under this many degrees are left alone. */
        public float pitchDeadzone = 0f;
        /** Yaw errors under this many degrees are left alone. */
        public float yawDeadzone = 0f;
        /** Never pitch further than this from level towards a target (degrees); 90 = no limit. */
        public float pitchLimit = 90f;
        /**
         * Pitch from the horizontal distance to the box's centre line (at least this far, 0 = off)
         * instead of to the aim point. The aim point can be centimetres away up close, and the pitch
         * to it then swings towards straight down with every step either player takes.
         */
        public float pitchCentreDist = 0f;
        /** Keep the aim height this far inside the box's top and bottom (blocks). */
        public float aimYMargin = 0f;
        /** Low-pass our own eye height for picking the aim height (0 = off): jumps and knockback don't drag the aim around. */
        public float eyeSmoothing = 0f;
        /** Decide flicks on the yaw error only; pitch errors (mostly from jumping) are tracked, not flicked. */
        public boolean flickOnYawOnly = false;
        /** Cap on pitch change per tick while tracking (degrees, 0 = only the speed cap). */
        public float maxPitchStep = 0f;
        /** Cap on pitch change per tick, flicks included (degrees, 0 = only the speed cap). */
        public float pitchSpeedCap = 0f;
        /**
         * Turn speed carries over between ticks while tracking (0 = none): the step eases towards
         * the wanted one instead of jumping to it, so a sudden change (a jump starting, landing,
         * knockback) bends the aim instead of kinking it.
         */
        public float pitchInertia = 0f;
        public float yawInertia = 0f;
        /**
         * Keep-on-box aiming: instead of a point on the target, aim wherever the crosshair already
         * is, clamped into the (shrunk) box as seen from our eyes, so the view only moves when the
         * box would slide out from under it - like a player keeping their crosshair on someone.
         */
        public boolean keepOnBox = false;
        /** How far inside the box's sides / top and bottom the crosshair is kept (blocks). */
        public float keepMarginH = 0.1f, keepMarginV = 0.3f;
        /** Pull towards the middle of the box each tick (0 = none, 1 = always the middle). */
        public float keepCentreBias = 0f;
        /** Pull towards the middle when the box is far off the crosshair (a flick onto it), so it lands well inside. */
        public float keepFarBias = 0f;
        /** Never lead the target by more than this many blocks (0 = no cap). */
        public float maxLead = 0f;

        public static Tuning legacy() {
            return new Tuning();
        }

        /** The tuned aim: smoothest without losing hits. */
        public static Tuning best() {
            Tuning t = new Tuning();
            t.keepOnBox = true;
            t.keepMarginH = 0.25f;
            t.keepMarginV = 0.2f;
            t.predictionTicks = 1f;
            t.smoothGain = true;
            t.flickOnYawOnly = true;
            t.flickThreshold = 18f;
            t.pitchSpeedCap = 8f;
            return t;
        }

        public Tuning copy() {
            Tuning t = new Tuning();
            try {
                for (java.lang.reflect.Field f : Tuning.class.getFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) f.set(t, f.get(this));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            return t;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            try {
                for (java.lang.reflect.Field f : Tuning.class.getFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) sb.append(f.getName()).append('=').append(f.get(this)).append(' ');
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            return sb.toString().trim();
        }
    }

    /** One tick's view of us and the target. */
    public static final class Input {
        /** Our eye position; our movement this tick (pos - lastTickPos) and our motion. */
        public double eyeX, eyeY, eyeZ, selfDX, selfDZ, selfMotionX, selfMotionZ;
        /** The target's box, and how far it moved this tick on our client (pos - lastTickPos). */
        public double minX, minY, minZ, maxX, maxY, maxZ;
        public double targetDX, targetDZ;
        /** Identifies the target, to notice switches. */
        public int targetId;
        /** The rotation currently being sent. */
        public float curYaw, curPitch;
        /** Hard cap on degrees per tick, and Lazy's flick deadline in ticks (0 = speed cap decides). */
        public float maxSpeed, budgetTicks;
    }

    public final Tuning tun;
    private final Random rnd;

    // aim point drift, as fractions of the box (0 = min edge, 1 = max edge)
    private float driftX = 0.5f, driftY = 0.65f, driftZ = 0.5f;
    private float driftGoalX = 0.5f, driftGoalY = 0.65f, driftGoalZ = 0.5f;
    private int driftTicksLeft = 0;
    /** 0 = aim at the nearest point of the box, 1 = aim at the drift point. Eased, never snapped. */
    private float driftBlend = 0f;

    // flick state
    private boolean flicking = false;
    private int flickTick, flickDuration;
    private float overshootYaw, overshootPitch;
    private int flickTarget = Integer.MIN_VALUE;

    // smoothing state
    private int velTarget = Integer.MIN_VALUE;
    private double velX, velZ;
    private int aimTarget = Integer.MIN_VALUE;
    private double aimX, aimY, aimZ;
    private float gain = 0.625f, gainGoal = 0.625f;
    private double eyeAvg = Double.NaN;
    private float lastStepYaw, lastStepPitch;

    public AimCore(Tuning tun, Random rnd) {
        this.tun = tun;
        this.rnd = rnd;
    }

    public void reset() {
        flicking = false;
        flickTarget = Integer.MIN_VALUE;
        driftBlend = 0f;
        aimTarget = Integer.MIN_VALUE;
        velTarget = Integer.MIN_VALUE;
    }

    /** Whether a flick is in progress. */
    public boolean isFlicking() {
        return flicking;
    }

    /** Drops any flick in progress, e.g. when the target is lost. */
    public void cancelFlick() {
        flicking = false;
        flickTarget = Integer.MIN_VALUE;
    }

    private float random(float min, float max) {
        return min + rnd.nextFloat() * (max - min);
    }

    /**
     * Moves the drift point once per tick. While we're moving, the aim eases onto a point that
     * wanders slowly around the target's chest; standing still, it eases back to the nearest point
     * of the box. The blend is eased both ways, so switching never snaps the aim.
     */
    public void updateDrift(Input self) {
        double mx = tun.selfFromMotion ? self.selfMotionX : self.selfDX;
        double mz = tun.selfFromMotion ? self.selfMotionZ : self.selfDZ;
        boolean moving = mx * mx + mz * mz > 1.0E-4;
        driftBlend += ((moving ? 1f : 0f) - driftBlend) * 0.2f;
        if (--driftTicksLeft <= 0) {
            driftGoalX = random(0.25f, 0.75f);
            driftGoalY = random(0.5f, 0.85f);
            driftGoalZ = random(0.25f, 0.75f);
            driftTicksLeft = 8 + rnd.nextInt(18);
        }
        driftX += (driftGoalX - driftX) * 0.15f;
        driftY += (driftGoalY - driftY) * 0.15f;
        driftZ += (driftGoalZ - driftZ) * 0.15f;
        if (Double.isNaN(eyeAvg) || tun.eyeSmoothing <= 0) eyeAvg = self.eyeY;
        else eyeAvg += (self.eyeY - eyeAvg) * (1 - tun.eyeSmoothing);
    }

    /** Feeds this tick's target movement into the velocity estimate. Once per tick, before aiming. */
    public void observe(Input in) {
        if (in.targetId != velTarget || tun.velocitySmoothing <= 0) {
            velTarget = in.targetId;
            velX = in.targetDX;
            velZ = in.targetDZ;
        } else {
            double a = 1 - tun.velocitySmoothing;
            velX += (in.targetDX - velX) * a;
            velZ += (in.targetDZ - velZ) * a;
        }
    }

    /** Horizontal offset of the target's box {@code ticks} ahead, relative to our own movement. */
    public double[] predictOffset(Input in, float ticks) {
        if (ticks <= 0) return new double[]{0, 0};
        double sx = tun.selfFromMotion ? in.selfMotionX : in.selfDX;
        double sz = tun.selfFromMotion ? in.selfMotionZ : in.selfDZ;
        double ox = (velX - sx) * ticks, oz = (velZ - sz) * ticks;
        if (tun.maxLead > 0) {
            double l = Math.sqrt(ox * ox + oz * oz);
            if (l > tun.maxLead) {
                ox *= tun.maxLead / l;
                oz *= tun.maxLead / l;
            }
        }
        return new double[]{ox, oz};
    }

    /** The aim point on the target's box led by {@code ticks}, before easing. */
    private double[] aimPoint(Input in, float ticks) {
        double[] o = predictOffset(in, ticks);
        double minX = in.minX + o[0], maxX = in.maxX + o[0], minZ = in.minZ + o[1], maxZ = in.maxZ + o[1];
        double x = aimCoord(in.eyeX, minX, maxX, driftX, driftBlend, tun.centrePull);
        double z = aimCoord(in.eyeZ, minZ, maxZ, driftZ, driftBlend, tun.centrePull);
        float yBlend = driftBlend + (1 - driftBlend) * tun.bodyHeight;
        double eye = Double.isNaN(eyeAvg) ? in.eyeY : eyeAvg;
        double m = Math.min(tun.aimYMargin, (in.maxY - in.minY) * 0.45);
        double y = aimCoord(eye, in.minY + m, in.maxY - m, driftY, yBlend, 0);
        return new double[]{x, y, z, minX, maxX, minZ, maxZ};
    }

    /** Yaw/pitch to aim at this tick (stateful: eases the aim point). Call once per tick. */
    public float[] aimRotations(Input in) {
        return aimRotations(in, tun.predictionTicks);
    }

    /** {@link #aimRotations(Input)} leading the target by {@code ticks} instead of the tuned amount. */
    public float[] aimRotations(Input in, float ticks) {
        if (tun.keepOnBox) {
            aimTarget = in.targetId;
            float[] r = keepOnBox(in, ticks, in.curYaw, in.curPitch, tun.keepCentreBias);
            float turn = Math.max(Math.abs(wrap(r[0] - in.curYaw)), Math.abs(r[1] - in.curPitch));
            if (tun.keepFarBias > 0 && (flicking || turn > tun.flickThreshold)) {
                r = keepOnBox(in, ticks, in.curYaw, in.curPitch, tun.keepFarBias);
            }
            return r;
        }
        double[] p = aimPoint(in, ticks);
        if (tun.aimPointEasing > 0 && in.targetId == aimTarget) {
            double k = 1 - tun.aimPointEasing;
            aimX = clamp(aimX + (p[0] - aimX) * k, p[3], p[4]);
            aimY = clamp(aimY + (p[1] - aimY) * k, in.minY, in.maxY);
            aimZ = clamp(aimZ + (p[2] - aimZ) * k, p[5], p[6]);
        } else {
            aimX = p[0];
            aimY = p[1];
            aimZ = p[2];
        }
        aimTarget = in.targetId;
        return rotationsTo(in, aimX, aimY, aimZ, (p[3] + p[4]) * 0.5, (p[5] + p[6]) * 0.5);
    }

    /** Yaw/pitch to the aim point led by {@code ticks}, without touching any state. */
    public float[] peekRotations(Input in, float ticks) {
        if (tun.keepOnBox) return keepOnBox(in, ticks, in.curYaw, in.curPitch, tun.keepCentreBias);
        double[] p = aimPoint(in, ticks);
        return rotationsTo(in, p[0], p[1], p[2], (p[3] + p[4]) * 0.5, (p[5] + p[6]) * 0.5);
    }

    private float[] rotationsTo(Input in, double x, double y, double z, double cx, double cz) {
        double dx = x - in.eyeX, dy = y - in.eyeY, dz = z - in.eyeZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (tun.pitchCentreDist > 0) {
            double ax = cx - in.eyeX, az = cz - in.eyeZ;
            dist = Math.max(tun.pitchCentreDist, Math.sqrt(ax * ax + az * az));
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        pitch = clamp(pitch, -tun.pitchLimit, tun.pitchLimit);
        return new float[]{yaw, pitch};
    }

    /** The rotation nearest (fromYaw, fromPitch) that still points into the shrunk, led box. */
    private float[] keepOnBox(Input in, float ticks, float fromYaw, float fromPitch, float bias) {
        double[] o = predictOffset(in, ticks);
        double minX = in.minX + o[0], maxX = in.maxX + o[0], minZ = in.minZ + o[1], maxZ = in.maxZ + o[1];
        double cx = (minX + maxX) * 0.5, cz = (minZ + maxZ) * 0.5;
        double hx = Math.max(0, (maxX - minX) * 0.5 - tun.keepMarginH), hz = Math.max(0, (maxZ - minZ) * 0.5 - tun.keepMarginH);
        double ex = in.eyeX, ey = in.eyeY, ez = in.eyeZ;
        float yc = yawTo(cx - ex, cz - ez);
        float lo = 0, hi = 0;
        boolean above = Math.abs(ex - cx) <= hx && Math.abs(ez - cz) <= hz;
        if (!above) {
            lo = Float.MAX_VALUE;
            hi = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                double px = cx + ((i & 1) == 0 ? -hx : hx), pz = cz + ((i & 2) == 0 ? -hz : hz);
                float d = wrap(yawTo(px - ex, pz - ez) - yc);
                lo = Math.min(lo, d);
                hi = Math.max(hi, d);
            }
        }
        float off = above ? wrap(fromYaw - yc) : clamp(wrap(fromYaw - yc), lo, hi);
        if (!above) off *= 1 - bias;
        float yaw = fromYaw + wrap(yc + off - fromYaw);

        double dc = Math.max(0.3, Math.sqrt((cx - ex) * (cx - ex) + (cz - ez) * (cz - ez)));
        double mv = Math.min(tun.keepMarginV, (in.maxY - in.minY) * 0.45);
        float top = (float) -Math.toDegrees(Math.atan2(in.maxY - mv - ey, dc));
        float bottom = (float) -Math.toDegrees(Math.atan2(in.minY + mv - ey, dc));
        float mid = (top + bottom) * 0.5f;
        float pitch = clamp(fromPitch, top, bottom);
        pitch += (mid - pitch) * bias;
        pitch = clamp(pitch, -tun.pitchLimit, tun.pitchLimit);
        return new float[]{yaw, pitch};
    }

    private static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    }

    private static double aimCoord(double eye, double min, double max, float drift, float blend, float centrePull) {
        double nearest = clamp(eye, min, max);
        if (centrePull > 0) nearest += ((min + max) * 0.5 - nearest) * centrePull;
        double wander = min + (max - min) * drift;
        return nearest + (wander - nearest) * blend;
    }

    /**
     * Lazy: the rotation to send this tick, turning from (in.curYaw, in.curPitch) towards {@code rots}.
     * A big error starts a flick along a minimum-jerk curve timed to land by the budget (larger ones
     * sometimes overshoot a little); otherwise the aim follows loosely, which also corrects overshoot.
     * The caller applies the result as-is (no extra smoothing).
     */
    public float[] lazyStep(Input in, float[] rots) {
        float yawErr = wrap(rots[0] - in.curYaw);
        float pitchErr = rots[1] - in.curPitch;
        float err = tun.flickOnYawOnly ? Math.abs(yawErr) : Math.max(Math.abs(yawErr), Math.abs(pitchErr));

        if (in.targetId != flickTarget) {
            flicking = false;
            flickTarget = in.targetId;
            lastStepYaw = lastStepPitch = 0;
        }
        if (!flicking && err > tun.flickThreshold) {
            startFlick(yawErr, pitchErr, err, in.maxSpeed, in.budgetTicks);
        }

        float stepYaw, stepPitch;
        if (flicking) {
            float done = minJerk(flickTick / (float) flickDuration);
            float next = minJerk(Math.min(1f, (flickTick + 1) / (float) flickDuration));
            // share of what's left to cover this tick, re-applied to the live error every tick so
            // the flick bends to follow a moving target
            float frac = (next - done) / (1f - done);
            stepYaw = (yawErr + overshootYaw) * frac;
            stepPitch = (pitchErr + overshootPitch) * frac;
            if (++flickTick >= flickDuration) flicking = false;
        } else {
            float g;
            if (tun.smoothGain) {
                if (rnd.nextFloat() < 0.08f) gainGoal = random(0.5f, 0.75f);
                gain += (gainGoal - gain) * 0.15f;
                g = gain;
            } else {
                g = random(0.5f, 0.75f);
            }
            stepYaw = dead(yawErr, tun.yawDeadzone) * g;
            stepPitch = dead(pitchErr, tun.pitchDeadzone) * g * tun.pitchGainScale;
            stepYaw = lastStepYaw + (stepYaw - lastStepYaw) * (1 - tun.yawInertia);
            stepPitch = lastStepPitch + (stepPitch - lastStepPitch) * (1 - tun.pitchInertia);
            if (tun.maxPitchStep > 0) stepPitch = clamp(stepPitch, -tun.maxPitchStep, tun.maxPitchStep);
        }
        stepYaw = clamp(stepYaw, -in.maxSpeed, in.maxSpeed);
        stepPitch = clamp(stepPitch, -in.maxSpeed, in.maxSpeed);
        if (tun.pitchSpeedCap > 0) stepPitch = clamp(stepPitch, -tun.pitchSpeedCap, tun.pitchSpeedCap);
        lastStepYaw = stepYaw;
        lastStepPitch = stepPitch;
        return new float[]{in.curYaw + stepYaw, clamp(in.curPitch + stepPitch, -90f, 90f)};
    }

    private static float dead(float err, float zone) {
        if (zone <= 0) return err;
        return Math.abs(err) <= zone ? 0 : err - Math.signum(err) * zone;
    }

    private void startFlick(float yawErr, float pitchErr, float err, float maxSpeed, float budgetTicks) {
        // minimum-jerk peaks at 1.875x its average speed: never plan a flick the speed cap would cut
        float minBySpeed = 1.875f * err / Math.max(1f, maxSpeed);
        float ticks = Math.max(budgetTicks, minBySpeed) * random(0.9f, 1.15f);
        flickDuration = Math.max(2, Math.min(12, Math.round(ticks)));
        flickTick = 0;
        flicking = true;
        if (err > 25f && rnd.nextFloat() < 0.35f) {
            float k = random(0.04f, 0.12f);
            overshootYaw = yawErr * k;
            overshootPitch = pitchErr * k * 0.5f;
        } else {
            overshootYaw = overshootPitch = 0f;
        }
    }

    /** Minimum-jerk position profile: 0 at s=0, 1 at s=1, zero velocity and acceleration at both ends. */
    private static float minJerk(float s) {
        return s * s * s * (10f + s * (-15f + 6f * s));
    }

    public static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
