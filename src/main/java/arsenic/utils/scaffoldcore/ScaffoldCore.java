package arsenic.utils.scaffoldcore;

import arsenic.utils.botcore.Box;
import arsenic.utils.botcore.Dir;

import java.util.Random;

public final class ScaffoldCore {

    public static final class Tuning {

        public boolean realHitVec = false;
        public boolean predictive = false;
        public double needLead = 2.0;
        public double aimLead = 1.0;
        public float bandInset = 0.12f;
        public float maxYawOffset = 50f;
        public float yawCost = 0.3f;
        public float yawMoveCost = 0.1f;
        public boolean needWorst = false;
        public boolean predictSneak = false;
        public boolean noGroundUp = false;
        public boolean noAirSneak = false;
        public boolean worstCaseMove = false;
        public float fallMargin = 0f;
        public double hardLead = 1.0;
        public boolean pressCycle = false;
        public double eagleLeadCap = 3;
        public double eagleHoldScale = 0.5;
        public int minHold = 2;
        public int releaseTicks = 2;
        public boolean keepYaw = false;
        public boolean autoSide = false;
        public float sideSwitchCost = 0.15f;
        public boolean laneNudge = false;
        public float laneDead = 0.12f;
        public float laneOffset = 0f;
        public boolean laneDiagonals = false;
        public float laneWindow = 4f;
        public float sideWindow = 4f;
        public boolean laneSnap = false;
        public boolean yawSteer = false;
        public double steerGain = 30;
        public double steerMax = 10;
        public float steerLimit = 25;
        public boolean steerOnly = false;
        public double steerDead = 0;

        public static Tuning legacy() {
            return new Tuning();
        }

        public static Tuning best() {
            Tuning t = new Tuning();
            t.realHitVec = true;
            t.predictive = true;
            t.needLead = 3.0;
            t.bandInset = 0.09f;
            t.noAirSneak = true;
            t.worstCaseMove = true;
            t.fallMargin = 0.03f;
            t.keepYaw = true;
            t.laneDiagonals = true;
            t.laneOffset = 0.15f;
            t.laneWindow = 15f;
            t.sideWindow = 22.5f;
            t.laneSnap = true;
            t.pressCycle = true;
            t.needWorst = true;
            t.predictSneak = true;
            t.noGroundUp = true;
            t.autoSide = true;
            t.laneNudge = true;
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
        public double posX, posY, posZ;
        public double motionX, motionY, motionZ;
        public boolean onGround;
        public float eyeHeight = 1.62f;
        public float cameraYaw;
        public float silentYaw, silentPitch;
        public boolean keyForward, keyBack, keyLeft, keyRight, keyJump;
        public float moveForward, moveStrafe;
        public float moveScale = 1f;
        public float aiMoveSpeed = 0.1f;
        public float slipperiness = 0.6f;
        public float gcd = 0.15f;
        public boolean hasBlock = true;
        public float yawBias;
        public double speedMin = 180, speedMax = 360;
        public boolean eagle = true;
        public double safety = 10;
    }

    public static final class Target {
        public final int x, y, z, face;

        public Target(int x, int y, int z, int face) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
        }
    }

    public static final class Rotation {
        public float yaw, pitch, speed;
        public boolean preventDuplicateLook;
    }

    public static final class Action {
        public boolean place;
        public int x, y, z, face;
        public double hitX, hitY, hitZ;
    }

    public final Tuning tun;
    private final Random rnd;
    private final Rotation rotation = new Rotation();
    private final Action action = new Action();
    private final ScaffoldWorld.Hit hit = new ScaffoldWorld.Hit();
    private final ScaffoldWorld.Hit scratch = new ScaffoldWorld.Hit();

    private Target target;
    private float[] rots = new float[2];
    private boolean solvedRots;
    private boolean aimed;

    public ScaffoldCore(Tuning tun, Random rnd) {
        this.tun = tun;
        this.rnd = rnd;
    }

    public void reset() {
        lane.reset();
        nudge = null;
        target = null;
        rots = new float[2];
        solvedRots = false;
        aimed = false;
        presses.reset();
    }

    public Target target() {
        return target;
    }


    private static final float[] SIN_TABLE = new float[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
        }
    }

    static float sin(float v) {
        return SIN_TABLE[(int) (v * 10430.378F) & 65535];
    }

    static float cos(float v) {
        return SIN_TABLE[(int) (v * 10430.378F + 16384.0F) & 65535];
    }

    static int floor(double d) {
        int i = (int) d;
        return d < (double) i ? i - 1 : i;
    }

    private static final double HALF_W = (double) (0.6F / 2.0F);
    private static final double HEIGHT = (double) 1.8F;


    public Rotation rotate(Input in, ScaffoldWorld w) {
        Rotation r = rotateAim(in, w);
        steer(in, r);
        return r;
    }

    private void steer(Input in, Rotation r) {
        if (!tun.yawSteer || !lane.tracking || !in.onGround) return;
        float f = (in.keyForward ? 1 : 0) - (in.keyBack ? 1 : 0), s = (in.keyLeft ? 1 : 0) - (in.keyRight ? 1 : 0);
        if (f == 0 && s == 0) return;
        float yaw = lane.steerYaw(f, s, in.cameraYaw, r.yaw, tun.steerGain, tun.steerMax, tun.steerDead, tun.steerLimit);
        if (Float.isNaN(yaw)) return;
        if (aimed && target != null) {
            double[] band = pitchBand(in.posX, in.posY + in.eyeHeight, in.posZ, yaw, target.x, target.y, target.z, target.face, 0.02);
            if (band == null || r.pitch < band[0] || r.pitch > band[1]) return;
        }
        r.yaw = yaw;
        if (tun.steerOnly) nudge = null;
    }

    private Rotation rotateAim(Input in, ScaffoldWorld w) {
        sided = tun.autoSide && gridHeading(in);
        if (sided) in.yawBias = side * 45f;
        computeNudge(in);
        aimed = false;
        if (tun.predictive && in.onGround) return rotatePredictive(in, w);
        Input eye = in;
        boolean willFall = willFallNextTick(in, w, 1.0) && in.motionY < 0.3;
        target = findBestPlacement(in, eye, w);
        rotation.speed = (float) (in.speedMin + rnd.nextDouble() * (in.speedMax - in.speedMin));
        rotation.preventDuplicateLook = true;

        float baseYaw = baseYaw(in);
        rotation.yaw = baseYaw;
        rotation.pitch = rots[1];

        if (!in.hasBlock) return rotation;

        if (willFall) {
            if (target != null) {
                float[] solved = rotationsForFace(eye, target.x, target.y, target.z, target.face, baseYaw);
                if (solved != null) {
                    rots = solved;
                    solvedRots = true;
                } else {
                    solvedRots = false;
                    rots = freeRotationsForFace(eye, target.x, target.y, target.z, target.face);
                }
            }
            rotation.yaw = solvedRots ? baseYaw : rots[0];
            rotation.pitch = rots[1];
        } else {
            rotation.yaw = baseYaw;
            rotation.pitch = rots[1];
        }
        return rotation;
    }

    public static float baseYaw(Input in) {
        int forward = 0, strafe = 0;
        if (in.keyForward) forward++;
        if (in.keyBack) forward--;
        if (in.keyLeft) strafe++;
        if (in.keyRight) strafe--;

        float offset;
        if (forward > 0) {
            offset = strafe == 0 ? 180f : (strafe > 0 ? 135f : -135f);
        } else if (forward < 0) {
            offset = strafe == 0 ? 0f : (strafe > 0 ? 45f : -45f);
        } else {
            offset = strafe == 0 ? 180f : (strafe > 0 ? 90f : -90f);
        }
        return in.cameraYaw + offset + in.yawBias;
    }


    private static final float[] YAW_OFFSETS = {0, 4, -4, 8, -8, 12, -12, 18, -18, 25, -25, 35, -35, 50, -50, 70, -70};

    private static final class Cand implements Comparable<Cand> {
        final int x, z, face;
        final double score;

        Cand(int x, int z, int face, double score) {
            this.x = x;
            this.z = z;
            this.face = face;
            this.score = score;
        }

        @Override
        public int compareTo(Cand o) {
            return Double.compare(score, o.score);
        }
    }

    private final java.util.ArrayList<Cand> cands = new java.util.ArrayList<>();

    private static final float WAIT = Float.POSITIVE_INFINITY;

    private Rotation rotatePredictive(Input in, ScaffoldWorld w) {
        rotation.speed = (float) (in.speedMin + rnd.nextDouble() * (in.speedMax - in.speedMin));
        rotation.preventDuplicateLook = true;
        float baseYaw = baseYaw(in);
        rotation.yaw = baseYaw;
        rotation.pitch = in.silentPitch;
        target = null;
        if (!in.hasBlock) return rotation;
        if (!needBlock(in, w)) return rotation;

        if (searchAim(in, w, tun.aimLead, baseYaw)) {
            target = new Target(aimCell.x, floor(in.posY) - 1, aimCell.z, aimCell.face);
            rotation.yaw = aimYaw;
            rotation.pitch = aimPitch;
            rots[0] = aimYaw;
            rots[1] = aimPitch;
            aimed = true;
            solvedRots = true;
            return rotation;
        }
        if (aimWait) return rotation;

        Input eye = in;
        target = findBestPlacement(in, eye, w);
        if (target != null) {
            float[] solved = rotationsForFace(eye, target.x, target.y, target.z, target.face, baseYaw);
            if (solved != null) {
                rots = solved;
                solvedRots = true;
            } else {
                solvedRots = false;
                rots = freeRotationsForFace(eye, target.x, target.y, target.z, target.face);
            }
            rotation.yaw = solvedRots ? baseYaw : rots[0];
            rotation.pitch = rots[1];
        }
        return rotation;
    }

    private boolean needBlock(Input in, ScaffoldWorld w) {
        if (!tun.needWorst || in.moveScale == 1f) return willFallNextTick(in, w, tun.needLead);
        float f = in.moveForward, s = in.moveStrafe;
        in.moveForward /= in.moveScale;
        in.moveStrafe /= in.moveScale;
        try {
            return willFallNextTick(in, w, tun.needLead);
        } finally {
            in.moveForward = f;
            in.moveStrafe = s;
        }
    }

    private Cand aimCell;
    private float aimYaw, aimPitch;
    private boolean aimWait;
    private int side = 1;
    private boolean sided;

    private boolean gridHeading(Input in) {
        double off = SneakPresses.offGrid((in.keyForward ? 1 : 0) - (in.keyBack ? 1 : 0), (in.keyLeft ? 1 : 0) - (in.keyRight ? 1 : 0), in.cameraYaw);
        return off >= 0 && off <= tun.sideWindow;
    }

    private float[] nudge;

    public float[] nudge() {
        return nudge;
    }

    private final LaneNudge lane = new LaneNudge();

    private void computeNudge(Input in) {
        nudge = null;
        if (!tun.laneNudge) return;
        float f = (in.keyForward ? 1 : 0) - (in.keyBack ? 1 : 0), s = (in.keyLeft ? 1 : 0) - (in.keyRight ? 1 : 0);
        lane.diagonalOffset = tun.laneOffset;
        lane.diagonals = tun.laneDiagonals;
        lane.window = tun.laneWindow;
        lane.snap = tun.laneSnap;
        nudge = lane.compute(f, s, in.cameraYaw, in.posX, in.posZ, in.onGround, tun.laneDead);
    }

    private boolean searchAim(Input in, ScaffoldWorld w, double lead, float baseYaw) {
        aimWait = false;
        Input ahead = advance(in, lead);
        if (tun.predictSneak && in.onGround) {
            boolean sneaking = in.eyeHeight < 1.6f, will = willSneak(in, w);
            if (will != sneaking) {
                float f = in.moveForward, s = in.moveStrafe, e = in.eyeHeight;
                float k = sneaking ? (in.moveScale != 1f ? 1f / in.moveScale : 1f) : 0.3f;
                in.moveForward *= k;
                in.moveStrafe *= k;
                in.eyeHeight = sneaking ? e + 0.08f : e - 0.08f;
                ahead = advance(in, lead);
                in.moveForward = f;
                in.moveStrafe = s;
                in.eyeHeight = e;
            }
        }
        Box predicted = predictedBox(in, lead);
        double targetX = (predicted.minX + predicted.maxX) * 0.5;
        double targetZ = (predicted.minZ + predicted.maxZ) * 0.5;
        int py = floor(in.posY) - 1;
        int px = floor(in.posX), pz = floor(in.posZ);

        cands.clear();
        double slack = 0.1;
        for (int x = px - 4; x <= px + 4; x++) {
            for (int z = pz - 4; z <= pz + 4; z++) {
                if (w.isAir(x, py, z) || !w.isFullCube(x, py, z)) continue;
                for (int i = 0; i < 4; i++) {
                    int face = HORIZONTALS[i];
                    int nx = x + Dir.DX[face], nz = z + Dir.DZ[face];
                    if (!w.isAir(nx, py, nz)) continue;
                    if (nx + 1 <= predicted.minX - slack || nx >= predicted.maxX + slack
                            || nz + 1 <= predicted.minZ - slack || nz >= predicted.maxZ + slack) continue;
                    if (!w.canPlaceOnSide(x, py, z, face)) continue;
                    double dx = nx + 0.5 - targetX, dz = nz + 0.5 - targetZ;
                    cands.add(new Cand(x, z, face, dx * dx + dz * dz));
                }
            }
        }
        java.util.Collections.sort(cands);

        double eyeAX = ahead.posX, eyeAY = ahead.posY + ahead.eyeHeight, eyeAZ = ahead.posZ;
        Cand bestC = null;
        float bestYaw = 0, bestPitch = 0;
        double bestCost = Double.MAX_VALUE;
        float pure = baseYaw - in.yawBias;
        int[] sides = sided ? new int[]{side, -side} : new int[]{0};
        int bestSide = side;
        for (Cand c : cands) {
            for (int sd : sides) {
                float by = sided ? pure + sd * 45f : baseYaw;
                double switchCost = sided && sd != side ? tun.sideSwitchCost : 0;
                int firstPass = tun.keepYaw && pitchBand(eyeAX, eyeAY, eyeAZ, by, c.x, py, c.z, c.face, tun.bandInset) == null
                        && pitchBand(eyeAX, eyeAY, eyeAZ, by, c.x, py, c.z, c.face, tun.bandInset * 0.25) != null ? 1 : 0;
                for (int pass = firstPass; pass < 2; pass++) {
                    double inset = pass == 0 ? tun.bandInset : tun.bandInset * 0.25;
                    boolean visible = false;
                    for (float off : YAW_OFFSETS) {
                        if (Math.abs(off) > tun.maxYawOffset) break;
                        float yaw = by + off;
                        double[] band = pitchBand(eyeAX, eyeAY, eyeAZ, yaw, c.x, py, c.z, c.face, inset);
                        if (band == null) continue;
                        visible = true;
                        float p = chooseInBand(in, w, ahead, yaw, c.x, py, c.z, c.face, band, in.silentPitch, inset);
                        if (p == WAIT) {
                            aimWait = true;
                            break;
                        }
                        if (Float.isNaN(p)) continue;
                        double turn = wrap180(yaw - in.silentYaw) / 45.0;
                        double cost = c.score + switchCost + tun.yawCost * (off / 45.0) * (off / 45.0) + tun.yawMoveCost * turn * turn;
                        if (cost < bestCost) {
                            bestCost = cost;
                            bestC = c;
                            bestYaw = yaw;
                            bestPitch = p;
                            bestSide = sd;
                        }
                    }
                    if (visible) break;
                }
            }
        }
        if (bestC != null && sided) side = bestSide;
        if (bestC == null) return false;
        aimCell = bestC;
        aimYaw = bestYaw;
        aimPitch = bestPitch;
        return true;
    }

    private float chooseInBand(Input in, ScaffoldWorld w, Input ahead, float yaw, int x, int y, int z, int face, double[] bandAhead, float current, double inset) {
        double lo = bandAhead[0], hi = bandAhead[1];
        double[] bandNow = pitchBand(in.posX, in.posY + in.eyeHeight, in.posZ, yaw, x, y, z, face, inset);
        if (bandNow != null) {
            double l2 = Math.max(lo, bandNow[0]), h2 = Math.min(hi, bandNow[1]);
            if (l2 <= h2) {
                lo = l2;
                hi = h2;
            } else {
                lo = bandNow[0];
                hi = bandNow[1];
            }
        }
        float want = (float) Math.max(lo, Math.min(hi, current));
        float gcd = in.gcd;
        float p = prevPitchGrid(current, want, gcd);
        if (p < lo) p += gcd;
        if (p > hi) p -= gcd;
        if (p < lo - 1e-3 || p > hi + 1e-3) return hi >= 89.0 ? WAIT : Float.NaN;
        p = Math.min(90f, p);
        if (!rayFrom(bandNow != null ? in : ahead, w, yaw, p, scratch)) return Float.NaN;
        if (scratch.x != x || scratch.y != y || scratch.z != z || scratch.face != face) return Float.NaN;
        return p;
    }

    private static float prevPitchGrid(float current, float want, float gcd) {
        return current + Math.round((want - current) / gcd) * gcd;
    }

    private static Input advance(Input in, double ticks) {
        Input a = new Input();
        Box p = predictedBox(in, ticks);
        a.posX = (p.minX + p.maxX) * 0.5;
        a.posZ = (p.minZ + p.maxZ) * 0.5;
        double my = in.motionY;
        boolean jumping = in.keyJump && in.onGround;
        if (jumping) my = 0.42;
        a.posY = in.posY + (in.onGround && !jumping ? 0 : my * ticks);
        a.onGround = in.onGround && !jumping;
        a.motionY = jumping ? (my - 0.08) * 0.98 : (in.onGround ? 0 : (my - 0.08) * 0.98);
        a.eyeHeight = in.eyeHeight;
        a.gcd = in.gcd;
        a.silentYaw = in.silentYaw;
        a.silentPitch = in.silentPitch;
        a.cameraYaw = in.cameraYaw;
        a.keyForward = in.keyForward;
        a.keyBack = in.keyBack;
        a.keyLeft = in.keyLeft;
        a.keyRight = in.keyRight;
        a.keyJump = in.keyJump;
        a.hasBlock = in.hasBlock;
        return a;
    }

    static double[] pitchBand(double eyeX, double eyeY, double eyeZ, float lockedYaw, int bx, int by, int bz, int face, double inset) {
        float yawRad = (float) Math.toRadians(lockedYaw);
        double hx = -Math.sin(yawRad), hz = Math.cos(yawRad);
        double x0 = bx + inset, x1 = bx + 1.0 - inset, y0 = by + inset, y1 = by + 1.0 - inset, z0 = bz + inset, z1 = bz + 1.0 - inset;
        double t;
        switch (face) {
            case Dir.NORTH:
            case Dir.SOUTH: {
                if (Math.abs(hz) < 1e-6) return null;
                t = ((face == Dir.NORTH ? bz : bz + 1.0) - eyeZ) / hz;
                if (t <= 0) return null;
                double hitX = eyeX + hx * t;
                if (hitX < x0 || hitX > x1) return null;
                return new double[]{Math.toDegrees(Math.atan((eyeY - y1) / t)), Math.toDegrees(Math.atan((eyeY - y0) / t))};
            }
            case Dir.WEST:
            case Dir.EAST: {
                if (Math.abs(hx) < 1e-6) return null;
                t = ((face == Dir.WEST ? bx : bx + 1.0) - eyeX) / hx;
                if (t <= 0) return null;
                double hitZ = eyeZ + hz * t;
                if (hitZ < z0 || hitZ > z1) return null;
                return new double[]{Math.toDegrees(Math.atan((eyeY - y1) / t)), Math.toDegrees(Math.atan((eyeY - y0) / t))};
            }
            case Dir.UP: {
                double h = eyeY - (by + 1.0);
                if (h <= 0) return null;
                double d1 = 0, d2 = Double.MAX_VALUE;
                if (Math.abs(hx) < 1e-9) {
                    if (eyeX < x0 || eyeX > x1) return null;
                } else {
                    double a = (x0 - eyeX) / hx, b = (x1 - eyeX) / hx;
                    d1 = Math.max(d1, Math.min(a, b));
                    d2 = Math.min(d2, Math.max(a, b));
                }
                if (Math.abs(hz) < 1e-9) {
                    if (eyeZ < z0 || eyeZ > z1) return null;
                } else {
                    double a = (z0 - eyeZ) / hz, b = (z1 - eyeZ) / hz;
                    d1 = Math.max(d1, Math.min(a, b));
                    d2 = Math.min(d2, Math.max(a, b));
                }
                if (d2 <= d1 || d2 == Double.MAX_VALUE) return null;
                return new double[]{Math.toDegrees(Math.atan(h / d2)), d1 <= 1e-9 ? 90.0 : Math.toDegrees(Math.atan(h / d1))};
            }
            default:
                return null;
        }
    }


    public Action post(Input in, ScaffoldWorld w, float yaw, float pitch) {
        action.place = false;
        tryPlace(in, w, yaw, pitch);
        return action;
    }

    private void tryPlace(Input in, ScaffoldWorld w, float yaw, float pitch) {
        if (in.hasBlock && target != null && rayFrom(in, w, yaw, pitch, hit)
                && hit.face != Dir.DOWN && !(tun.noGroundUp && in.onGround && hit.face == Dir.UP)
                && w.canPlaceOnSide(hit.x, hit.y, hit.z, hit.face)) {
            target = new Target(hit.x, hit.y, hit.z, hit.face);
            action.place = true;
            action.x = hit.x;
            action.y = hit.y;
            action.z = hit.z;
            action.face = hit.face;
            if (tun.realHitVec) {
                action.hitX = hit.hx;
                action.hitY = hit.hy;
                action.hitZ = hit.hz;
            } else {
                faceVector(hit.x, hit.y, hit.z, hit.face);
            }
        }
    }

    private void faceVector(int x, int y, int z, int face) {
        double a1 = 0.45 + rnd.nextDouble() * 0.1;
        double a2 = 0.45 + rnd.nextDouble() * 0.1;
        double hx = x, hy = y, hz = z;
        switch (face) {
            case Dir.UP: hx += a1; hy += 1; hz += a2; break;
            case Dir.DOWN: hx += a1; hz += a2; break;
            case Dir.EAST: hx += 1; hy += a1; hz += a2; break;
            case Dir.WEST: hy += a1; hz += a2; break;
            case Dir.NORTH: hx += a1; hy += a2; break;
            default: hx += a1; hy += a2; hz += 1; break;
        }
        action.hitX = hx;
        action.hitY = hy;
        action.hitZ = hz;
    }


    private final SneakPresses presses = new SneakPresses();

    public boolean sneak(Input in, ScaffoldWorld w, boolean placed) {
        float f = in.moveForward, s = in.moveStrafe;
        if (tun.worstCaseMove && in.moveScale != 1f) {
            in.moveForward /= in.moveScale;
            in.moveStrafe /= in.moveScale;
        }
        try {
            if (tun.pressCycle) {
                if (!in.onGround) {
                    presses.airborne();
                    return false;
                }
                syncPresses(in.safety);
                return presses.next(ticks -> willFallWithMargin(in, w, ticks), in.eagle, in.safety, diagonal(in), placed);
            }
            if (tun.noAirSneak && !in.onGround) return false;
            boolean shift = in.onGround && !placed && willFallWithMargin(in, w, tun.hardLead);
            if (in.eagle && willFallWithMargin(in, w, in.safety)) shift = true;
            return shift;
        } finally {
            in.moveForward = f;
            in.moveStrafe = s;
        }
    }

    private boolean willSneak(Input in, ScaffoldWorld w) {
        if (!tun.pressCycle) return false;
        float f = in.moveForward, s = in.moveStrafe;
        if (tun.worstCaseMove && in.moveScale != 1f) {
            in.moveForward /= in.moveScale;
            in.moveStrafe /= in.moveScale;
        }
        try {
            syncPresses(in.safety);
            return presses.peek(ticks -> willFallWithMargin(in, w, ticks), in.eagle, in.safety, diagonal(in));
        } finally {
            in.moveForward = f;
            in.moveStrafe = s;
        }
    }

    // Safety is the lead in ticks (fractions included): shift when the fall is within that many ticks of motion.
    // No minimum lead is forced above 1.0. tun.needLead still applies to the worst-case movement check.
    private void syncPresses(double safety) {
        presses.hardLead = safety;
        presses.leadCap = tun.eagleLeadCap;
        presses.needLead = 0;
        presses.holdScale = tun.eagleHoldScale;
        presses.minHold = tun.minHold;
        presses.releaseTicks = tun.releaseTicks;
    }

    private static boolean diagonal(Input in) {
        return SneakPresses.diagonal((in.keyForward ? 1 : 0) - (in.keyBack ? 1 : 0), (in.keyLeft ? 1 : 0) - (in.keyRight ? 1 : 0), in.cameraYaw);
    }


    public static Box predictedBox(Input in, double precision) {
        double motionX = in.motionX;
        double motionZ = in.motionZ;
        float moveForward = in.moveForward;
        float moveStrafe = in.moveStrafe;

        motionX *= 0.98;
        motionZ *= 0.98;
        if (Math.abs(motionX) < 0.005) motionX = 0.0;
        if (Math.abs(motionZ) < 0.005) motionZ = 0.0;

        moveStrafe *= 0.98F;
        moveForward *= 0.98F;

        float f4 = 0.91F;
        if (in.onGround) {
            f4 = in.slipperiness * 0.91F;
        }

        float f = 0.16277136F / (f4 * f4 * f4);
        float f5 = in.aiMoveSpeed * f;

        f = moveStrafe * moveStrafe + moveForward * moveForward;
        if (f >= 1.0E-4F) {
            f = (float) Math.sqrt(f);
            if (f < 1.0F) {
                f = 1.0F;
            }

            f = f5 / f;
            moveStrafe *= f;
            moveForward *= f;
            float f1 = sin(in.silentYaw * (float) Math.PI / 180.0F);
            float f2 = cos(in.silentYaw * (float) Math.PI / 180.0F);
            motionX += (double) (moveStrafe * f2 - moveForward * f1);
            motionZ += (double) (moveForward * f2 + moveStrafe * f1);
        }

        double minX = in.posX - HALF_W, minZ = in.posZ - HALF_W;
        return new Box(minX + motionX * precision, in.posY, minZ + motionZ * precision,
                minX + 2 * HALF_W + motionX * precision, in.posY + HEIGHT, minZ + 2 * HALF_W + motionZ * precision);
    }

    public static boolean willFallNextTick(Input in, ScaffoldWorld w, double precision) {
        return w.boxFree(predictedBox(in, precision).offset(0, -0.5, 0));
    }

    private boolean willFallWithMargin(Input in, ScaffoldWorld w, double precision) {
        return willFall(in, w, precision, tun.fallMargin);
    }

    public static boolean willFall(Input in, ScaffoldWorld w, double precision, double margin) {
        Box b = predictedBox(in, precision).offset(0, -0.5, 0);
        return w.boxFree(margin == 0 ? b : new Box(b.minX + margin, b.minY, b.minZ + margin, b.maxX - margin, b.maxY, b.maxZ - margin));
    }


    private static final int[] HORIZONTALS = {Dir.SOUTH, Dir.WEST, Dir.NORTH, Dir.EAST};

    private boolean rayFrom(Input in, ScaffoldWorld w, float yaw, float pitch, ScaffoldWorld.Hit out) {
        double ex = in.posX, ey = in.posY + in.eyeHeight, ez = in.posZ;
        float f = cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -cos(-pitch * 0.017453292F);
        float f3 = sin(-pitch * 0.017453292F);
        double lx = (double) (f1 * f2), ly = (double) f3, lz = (double) (f * f2);
        return w.rayTrace(ex, ey, ez, ex + lx * 4.5, ey + ly * 4.5, ez + lz * 4.5, out);
    }

    public Target findBestPlacement(Input in, ScaffoldWorld w) {
        return findBestPlacement(in, in, w);
    }

    public Target findBestPlacement(Input in, Input eye, ScaffoldWorld w) {
        float baseYaw = baseYaw(in);
        int px = floor(in.posX), py = floor(in.posY) - 1, pz = floor(in.posZ);

        Target best = null;
        double bestScore = Double.MAX_VALUE;

        Box predicted = predictedBox(in, 1.0);
        double targetX = (predicted.minX + predicted.maxX) * 0.5;
        double targetZ = (predicted.minZ + predicted.maxZ) * 0.5;
        double targetY = py + 0.5;

        double existingScore = Double.MAX_VALUE;

        boolean tower = !in.onGround;
        int lowestLayer = tower ? -1 : 0;

        for (int layer = 0; layer >= lowestLayer; layer--) {
            int y = py + layer;
            for (int x = px - 4; x <= px + 4; x++) {
                for (int z = pz - 4; z <= pz + 4; z++) {
                    if (w.isAir(x, y, z)) continue;
                    if (!w.isFullCube(x, y, z)) continue;

                    double exDx = (x + 0.5) - targetX;
                    double exDz = (z + 0.5) - targetZ;
                    double exDy = (y + 0.5) - targetY;
                    double exScore = exDx * exDx + exDz * exDz + exDy * exDy * 0.25;
                    if (exScore < existingScore) existingScore = exScore;

                    for (int i = 0; i < (tower ? 5 : 4); i++) {
                        int face = i < 4 ? HORIZONTALS[i] : Dir.UP;
                        if (!w.canPlaceOnSide(x, y, z, face)) continue;

                        int nx = x + Dir.DX[face], ny = y + Dir.DY[face], nz = z + Dir.DZ[face];
                        if (!w.isAir(nx, ny, nz)) continue;

                        double dx = nx + 0.5 - targetX;
                        double dz = nz + 0.5 - targetZ;
                        double dy = ny + 0.5 - targetY;
                        double score = dx * dx + dz * dz + dy * dy * 0.25;
                        if (score >= bestScore) continue;

                        float[] r = rotationsForFace(eye, x, y, z, face, baseYaw);
                        if (r == null) {
                            r = freeRotationsForFace(eye, x, y, z, face);
                        }
                        if (!rayFrom(eye, w, r[0], r[1], scratch)) continue;
                        if (scratch.x != x || scratch.y != y || scratch.z != z) continue;
                        if (scratch.face != face) continue;

                        bestScore = score;
                        best = new Target(x, y, z, face);
                    }
                }
            }
        }

        if (best != null && existingScore <= bestScore) {
            return null;
        }
        return best;
    }


    static float[] patchGCD(float prevYaw, float prevPitch, float yaw, float pitch, float gcd) {
        final float deltaYaw = yaw - prevYaw, deltaPitch = pitch - prevPitch;
        return new float[]{prevYaw + Math.round(deltaYaw / gcd) * gcd, prevPitch + Math.round(deltaPitch / gcd) * gcd};
    }

    private static float wrap180(float v) {
        v = v % 360.0F;
        if (v >= 180.0F) v -= 360.0F;
        if (v < -180.0F) v += 360.0F;
        return v;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static float[] rotationsForFace(Input in, int bx, int by, int bz, int face, float lockedYaw) {
        double eyeX = in.posX;
        double eyeY = in.posY + in.eyeHeight;
        double eyeZ = in.posZ;

        float yawRad = (float) Math.toRadians(lockedYaw);

        double hx = -Math.sin(yawRad);
        double hz = Math.cos(yawRad);

        double bx0 = bx, bx1 = bx0 + 1.0;
        double by0 = by, by1 = by0 + 1.0;
        double bz0 = bz, bz1 = bz0 + 1.0;

        float currentPitch = in.silentPitch;

        float bestPitch = Float.MAX_VALUE;
        float bestDiff = Float.MAX_VALUE;

        switch (face) {
            case Dir.UP: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by1, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(wrap180(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                for (double cx : new double[]{bx0 + 0.1, bx1 - 0.1}) {
                    for (double cz : new double[]{bz0 + 0.1, bz1 - 0.1}) {
                        float p = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, cx, by1, cz);
                        if (!Float.isNaN(p)) {
                            float diff = Math.abs(wrap180(p - currentPitch));
                            if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                        }
                    }
                }
                break;
            }
            case Dir.DOWN: {
                float pitch = pitchToHitPoint(eyeX, eyeY, eyeZ, hx, hz, bx0 + 0.5, by0, bz0 + 0.5);
                if (!Float.isNaN(pitch)) {
                    float diff = Math.abs(wrap180(pitch - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = pitch; }
                }
                break;
            }
            case Dir.NORTH:
            case Dir.SOUTH: {
                float[] candidates = pitchesToHitZPlane(eyeX, eyeY, eyeZ, hx, hz,
                        face == Dir.NORTH ? bz0 : bz1, bx0, bx1, by0, by1);
                for (float p : candidates) {
                    float diff = Math.abs(wrap180(p - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                }
                break;
            }
            default: {
                float[] candidates = pitchesToHitXPlane(eyeX, eyeY, eyeZ, hx, hz,
                        face == Dir.WEST ? bx0 : bx1, by0, by1, bz0, bz1);
                for (float p : candidates) {
                    float diff = Math.abs(wrap180(p - currentPitch));
                    if (diff < bestDiff) { bestDiff = diff; bestPitch = p; }
                }
                break;
            }
        }

        if (bestPitch == Float.MAX_VALUE) {
            return null;
        }

        bestPitch = clamp(bestPitch, -90f, 90f);

        float[] fixed = patchGCD(lockedYaw, currentPitch, lockedYaw, bestPitch, in.gcd);
        fixed[0] = lockedYaw;
        return fixed;
    }

    public static float[] freeRotationsForFace(Input in, int bx, int by, int bz, int face) {
        double eyeX = in.posX;
        double eyeY = in.posY + in.eyeHeight;
        double eyeZ = in.posZ;

        double faceCX = bx + 0.5 + Dir.DX[face] * 0.5;
        double faceCY = by + 0.5 + Dir.DY[face] * 0.5;
        double faceCZ = bz + 0.5 + Dir.DZ[face] * 0.5;

        double dx = faceCX - eyeX;
        double dy = faceCY - eyeY;
        double dz = faceCZ - eyeZ;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        pitch = clamp(pitch, -90f, 90f);

        return patchGCD(in.silentYaw, in.silentPitch, yaw, pitch, in.gcd);
    }

    private static float pitchToHitPoint(double eyeX, double eyeY, double eyeZ,
                                         double hx, double hz,
                                         double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;

        double tCosp;
        if (Math.abs(hx) > Math.abs(hz)) {
            if (Math.abs(hx) < 1e-6) return Float.NaN;
            tCosp = dx / hx;
        } else {
            if (Math.abs(hz) < 1e-6) return Float.NaN;
            tCosp = dz / hz;
        }

        if (tCosp <= 0) return Float.NaN;

        double tanPitch = -dy / tCosp;
        return (float) Math.toDegrees(Math.atan(tanPitch));
    }

    private static final double[] SAMPLES = {0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1};

    private static double sampleY(int i, double yMin, double yMax) {
        switch (i) {
            case 0: return yMin + 0.1;
            case 4: return (yMin + yMax) * 0.5;
            case 9: return yMax - 0.1;
            default: return yMin + (yMax - yMin) * SAMPLES[i];
        }
    }

    private static float[] pitchesToHitZPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceZ,
                                              double xMin, double xMax,
                                              double yMin, double yMax) {
        if (Math.abs(hz) < 1e-6) return new float[0];

        double tCosp = (faceZ - eyeZ) / hz;
        if (tCosp <= 0) return new float[0];

        double hitX = eyeX + hx * tCosp;
        if (hitX < xMin || hitX > xMax) return new float[0];

        float[] results = new float[10];
        for (int i = 0; i < 10; i++) {
            double dy = sampleY(i, yMin, yMax) - eyeY;
            results[i] = (float) Math.toDegrees(Math.atan(-dy / tCosp));
        }
        return results;
    }

    private static float[] pitchesToHitXPlane(double eyeX, double eyeY, double eyeZ,
                                              double hx, double hz,
                                              double faceX,
                                              double yMin, double yMax,
                                              double zMin, double zMax) {
        if (Math.abs(hx) < 1e-6) return new float[0];

        double tCosp = (faceX - eyeX) / hx;
        if (tCosp <= 0) return new float[0];

        double hitZ = eyeZ + hz * tCosp;
        if (hitZ < zMin || hitZ > zMax) return new float[0];

        float[] results = new float[10];
        for (int i = 0; i < 10; i++) {
            double dy = sampleY(i, yMin, yMax) - eyeY;
            results[i] = (float) Math.toDegrees(Math.atan(-dy / tCosp));
        }
        return results;
    }
}
