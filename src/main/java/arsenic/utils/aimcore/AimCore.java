package arsenic.utils.aimcore;

import java.util.Random;

public final class AimCore {

    public static final class Tuning {
        public float predictionTicks = 3f;
        public float velocitySmoothing = 0f;
        public boolean selfFromMotion = false;
        public float bodyHeight = 0f;
        public float centrePull = 0f;
        public float aimPointEasing = 0f;
        public boolean smoothGain = false;
        public float flickThreshold = 12f;
        public float pitchGainScale = 1f;
        public float pitchDeadzone = 0f;
        public float yawDeadzone = 0f;
        public float pitchLimit = 90f;
        public float pitchCentreDist = 0f;
        public float aimYMargin = 0f;
        public float eyeSmoothing = 0f;
        public boolean flickOnYawOnly = false;
        public float maxPitchStep = 0f;
        public float pitchSpeedCap = 0f;
        public float pitchInertia = 0f;
        public float yawInertia = 0f;
        public boolean keepOnBox = false;
        public float keepMarginH = 0.1f, keepMarginV = 0.3f;
        public float keepCentreBias = 0f;
        public float keepFarBias = 0f;
        public float maxLead = 0f;

        public static Tuning legacy() {
            return new Tuning();
        }

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

    public static final class Input {
        public double eyeX, eyeY, eyeZ, selfDX, selfDZ, selfMotionX, selfMotionZ;
        public double minX, minY, minZ, maxX, maxY, maxZ;
        public double targetDX, targetDZ;
        public int targetId;
        public float curYaw, curPitch;
        public float maxSpeed, budgetTicks;
    }

    public final Tuning tun;
    private final Random rnd;

    private float driftX = 0.5f, driftY = 0.65f, driftZ = 0.5f;
    private float driftGoalX = 0.5f, driftGoalY = 0.65f, driftGoalZ = 0.5f;
    private int driftTicksLeft = 0;
    private float driftBlend = 0f;

    private boolean flicking = false;
    private int flickTick, flickDuration;
    private float overshootYaw, overshootPitch;
    private int flickTarget = Integer.MIN_VALUE;

    private int velTarget = Integer.MIN_VALUE;
    private double velX, velZ;
    private int aimTarget = Integer.MIN_VALUE;
    private double aimX, aimY, aimZ;
    private float gain = 0.625f, gainGoal = 0.625f;
    private double eyeAvg = Double.NaN;
    private float lastStepYaw, lastStepPitch;

    private static final int H_LEN = 8;
    private final double[] hX = new double[H_LEN], hY = new double[H_LEN], hZ = new double[H_LEN];
    private int hHead, hCount, hReaction = 1, hReactionLeft, hExcursionLeft;
    private float hExcursionGoal;
    private int hTarget = Integer.MIN_VALUE;
    private float hOffYaw, hOffPitch, hVelYaw, hVelPitch;
    private float hGain = 0.55f, hGoalGain = 0.55f;
    private float hPursuit = 0.9f, hGoalPursuit = 0.9f;

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
        hTarget = Integer.MIN_VALUE;
    }

    public boolean isFlicking() {
        return flicking;
    }

    public void cancelFlick() {
        flicking = false;
        flickTarget = Integer.MIN_VALUE;
    }

    private float random(float min, float max) {
        return min + rnd.nextFloat() * (max - min);
    }

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

    public float[] aimRotations(Input in) {
        return aimRotations(in, tun.predictionTicks);
    }

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

    /**
     * Human-like tracking meant to stay out of rotation-accuracy heuristics. Small corrections chase where the target
     * was a couple of ticks ago (reaction time) through a lagging spring, with an aim offset that wanders around and
     * sometimes past the hitbox edge, so yaw error is neither consistently tiny nor locked to the target's movement.
     * Large turns still use the min-jerk flick from {@link #lazyStep}.
     */
    public float[] heuristicStep(Input in) {
        double cx = (in.minX + in.maxX) * 0.5, cz = (in.minZ + in.maxZ) * 0.5;
        double cy = in.minY + (in.maxY - in.minY) * driftY;
        if (in.targetId != hTarget) {
            hTarget = in.targetId;
            hCount = 0;
            hVelYaw = hVelPitch = 0;
            hOffYaw = random(-0.5f, 0.5f);
            hOffPitch = random(-0.3f, 0.3f);
            hExcursionLeft = 0;
        }
        hX[hHead] = cx;
        hY[hHead] = cy;
        hZ[hHead] = cz;
        hHead = (hHead + 1) % H_LEN;
        hCount = Math.min(H_LEN, hCount + 1);
        if (--hReactionLeft <= 0) {
            hReaction = rnd.nextInt(3);
            hReactionLeft = 20 + rnd.nextInt(40);
        }
        int back = Math.min(hReaction, hCount - 1);
        int idx = ((hHead - 1 - back) % H_LEN + H_LEN) % H_LEN;
        double dx = hX[idx] - in.eyeX, dz = hZ[idx] - in.eyeZ;
        double dist = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
        float halfYaw = (float) Math.toDegrees(Math.atan2((in.maxX - in.minX) * 0.5, dist));
        float halfPitch = (float) Math.toDegrees(Math.atan2((in.maxY - in.minY) * 0.5, dist));

        // Ornstein-Uhlenbeck wander in hitbox half-widths around the body centre rather than its corners. Now and
        // then the aim slips clean off one side for a few ticks, so attacks miss about as often as a player's do.
        if (hExcursionLeft > 0) {
            hExcursionLeft--;
            hOffYaw += (hExcursionGoal - hOffYaw) * 0.35f;
        } else {
            hOffYaw += -hOffYaw * 0.08f + (float) rnd.nextGaussian() * 0.11f;
            if (rnd.nextFloat() < 0.01f) {
                hExcursionLeft = 4 + rnd.nextInt(7);
                hExcursionGoal = (rnd.nextBoolean() ? 1 : -1) * random(1.3f, 2.0f);
            }
            hOffYaw = clamp(hOffYaw, -0.8f, 0.8f);
        }
        hOffPitch += -hOffPitch * 0.05f + (float) rnd.nextGaussian() * 0.06f;
        hOffPitch = clamp(hOffPitch, -0.5f, 0.5f);

        float goalYaw = yawTo(dx, dz) + hOffYaw * halfYaw;
        float goalPitch = (float) -Math.toDegrees(Math.atan2(hY[idx] - in.eyeY, dist)) + hOffPitch * halfPitch;
        goalPitch = clamp(goalPitch, -tun.pitchLimit, tun.pitchLimit);
        float yawErr = wrap(goalYaw - in.curYaw);
        float pitchErr = goalPitch - in.curPitch;

        if (flicking || Math.abs(yawErr) > tun.flickThreshold) {
            float[] out = lazyStep(in, new float[]{goalYaw, goalPitch});
            hVelYaw = lastStepYaw;
            hVelPitch = lastStepPitch;
            return out;
        }
        flickTarget = in.targetId;

        if (rnd.nextFloat() < 0.06f) hGoalGain = random(0.4f, 0.7f);
        hGain += (hGoalGain - hGain) * 0.2f;
        float resp = random(0.6f, 0.9f);
        // Smooth pursuit: match the target's angular speed as seen through the same delay, with an imperfect gain
        if (rnd.nextFloat() < 0.05f) hGoalPursuit = random(0.7f, 1.05f);
        hPursuit += (hGoalPursuit - hPursuit) * 0.2f;
        float angVel = 0;
        if (hCount > back + 1) {
            int prev = (idx - 1 + H_LEN) % H_LEN;
            angVel = wrap(yawTo(dx, dz) - yawTo(hX[prev] - in.eyeX, hZ[prev] - in.eyeZ));
        }
        float wantYaw = yawErr * hGain + angVel * hPursuit;
        // Inside the box a hand barely corrects pitch and now and then rests entirely
        float wantPitch = Math.abs(pitchErr) < halfPitch * 0.8f ? pitchErr * 0.05f : pitchErr * hGain * 0.6f;
        if (Math.abs(yawErr) < halfYaw && rnd.nextFloat() < 0.07f) wantYaw = angVel * hPursuit * 0.5f;
        hVelYaw += (wantYaw - hVelYaw) * resp;
        hVelPitch += (wantPitch - hVelPitch) * resp;
        float stepYaw = hVelYaw * random(0.85f, 1.15f) + (float) rnd.nextGaussian() * 0.04f * halfYaw;
        float stepPitch = hVelPitch * random(0.85f, 1.15f) + (float) rnd.nextGaussian() * 0.03f * halfPitch;
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
