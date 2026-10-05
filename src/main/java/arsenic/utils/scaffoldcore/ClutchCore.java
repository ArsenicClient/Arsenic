package arsenic.utils.scaffoldcore;

import arsenic.utils.botcore.Box;
import arsenic.utils.botcore.Dir;

import java.util.Random;

public final class ClutchCore {

    public static final class Tuning {

        public boolean enabled = true;
        public int knockWindow = 10;
        public boolean clutchRearms = true;
        public int voidMemory = 4;
        public int placeTimeout = 4;
        public int voidDepth = 20;
        public boolean classify = false;
        public double knockPush = 0.01;
        public boolean plan = false;
        public boolean steer = false;
        public boolean steerAgainst = false;
        public boolean trail = false;

        public static Tuning legacy() {
            return new Tuning();
        }

        public static Tuning best() {
            Tuning t = new Tuning();
            t.classify = true;
            t.plan = true;
            t.steer = true;
            t.steerAgainst = true;
            t.trail = true;
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

    public static final class Rotation {
        public boolean set;
        public float yaw, pitch, speed;
        public boolean preventDuplicateLook;
    }

    private static final double REACH = 4.5;

    public final Tuning tun;
    private final Random rnd;
    private final Rotation rotation = new Rotation();
    private final ScaffoldCore.Action action = new ScaffoldCore.Action();
    private final ScaffoldWorld.Hit hit = new ScaffoldWorld.Hit();

    private int tick;
    private int lastKnock = -1000, lastClutch = -1000, lastVoid = -1000, lastPlace;
    private boolean falling, placing, hasRots;
    private float lastYaw, lastPitch;
    private ScaffoldCore.Target target;

    public ClutchCore(Tuning tun, Random rnd) {
        this.tun = tun;
        this.rnd = rnd;
    }

    public void reset() {
        lastKnock = lastClutch = lastVoid = -1000;
        falling = placing = hasRots = false;
        target = null;
    }

    public ScaffoldCore.Target target() {
        return falling ? target : null;
    }

    public boolean active() {
        return falling;
    }

    public void onVelocity(double motionX, double motionY, double motionZ) {
        if (motionX == 0 && motionY == 0 && motionZ == 0) return;
        lastKnock = tick + 1;
        knocked = true;
        knockX = motionX;
        knockZ = motionZ;
        knockAt = tick + 1;
    }

    private boolean knocked, knockOff;
    private double knockX, knockZ;
    private int knockAt;
    private int supportX, supportZ;
    private boolean hasSupport, wasSupported;

    public boolean knockedOff() {
        return knockOff;
    }

    private double groundY = Double.NaN;

    private void track(ScaffoldCore.Input in, ScaffoldWorld w) {
        if (in.onGround || Double.isNaN(groundY)) groundY = in.posY;
        double floorY = Math.min(groundY, in.posY);
        boolean supported = !w.boxFree(new Box(in.posX - 0.3, floorY - 0.5, in.posZ - 0.3, in.posX + 0.3, Math.max(in.posY, floorY - 0.4), in.posZ + 0.3));
        if (supported) {
            int y = ScaffoldCore.floor(floorY - 0.01);
            int cx = ScaffoldCore.floor(in.posX), cz = ScaffoldCore.floor(in.posZ);
            double best = Double.MAX_VALUE;
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    if (w.isAir(x, y, z)) continue;
                    if (x + 1 <= in.posX - 0.3 || x >= in.posX + 0.3 || z + 1 <= in.posZ - 0.3 || z >= in.posZ + 0.3) continue;
                    double dx = x + 0.5 - in.posX, dz = z + 0.5 - in.posZ, d = dx * dx + dz * dz;
                    if (d < best) {
                        best = d;
                        supportX = x;
                        supportZ = z;
                        hasSupport = true;
                    }
                }
            }
        }
        if (tick > knockAt) {
            double drag = in.onGround ? in.slipperiness * 0.91 : 0.91;
            knockX *= drag;
            knockZ *= drag;
        }
        if (Math.hypot(knockX, knockZ) < tun.knockPush) knocked = false;
        if (in.onGround && !knocked) knockOff = false;
        boolean held = supported || in.onGround;
        if (wasSupported && !held) knockOff = knocked && pushedOff(in);
        if (!wasSupported && !held && knocked && !knockOff && tick == knockAt) knockOff = pushedOff(in);
        wasSupported = held;
    }

    private boolean pushedOff(ScaffoldCore.Input in) {
        if (!hasSupport) return true;
        double nx = in.posX - (supportX + 0.5), nz = in.posZ - (supportZ + 0.5);
        if (Math.abs(nx) > 1.5 * Math.abs(nz)) nz = 0;
        else if (Math.abs(nz) > 1.5 * Math.abs(nx)) nx = 0;
        nx = Math.signum(nx);
        nz = Math.signum(nz);
        double len = Math.hypot(nx, nz);
        if (len < 1e-6) return true;
        return (knockX * nx + knockZ * nz) / len >= tun.knockPush;
    }

    public Rotation rotate(ScaffoldCore.Input in, ScaffoldWorld w, double speed) {
        tick++;
        target = null;
        placing = false;
        rotation.set = false;
        track(in, w);
        if (tun.plan) return planRotate(in, w, speed);
        if (!tun.enabled || !fallingIntoVoid(in, w) || !armed()) {
            falling = false;
            hasRots = false;
            return rotation;
        }
        if (!falling) lastPlace = tick;
        falling = true;
        if (tick - lastPlace > tun.placeTimeout) {
            hasRots = false;
            return rotation;
        }
        rotation.speed = (float) speed;
        rotation.preventDuplicateLook = true;
        if (in.hasBlock) {
            float lockedYaw = in.cameraYaw;
            ScaffoldCore.Target found = findCatch(in, w, lockedYaw);
            if (found != null) {
                target = found;
                placing = true;
                float[] solved = ScaffoldCore.rotationsForFace(in, found.x, found.y, found.z, found.face, lockedYaw);
                if (solved != null) {
                    lastYaw = lockedYaw;
                    lastPitch = solved[1];
                } else {
                    float[] free = ScaffoldCore.freeRotationsForFace(in, found.x, found.y, found.z, found.face);
                    lastYaw = free[0];
                    lastPitch = free[1];
                }
                hasRots = true;
            }
        }
        if (hasRots) {
            rotation.set = true;
            rotation.yaw = lastYaw;
            rotation.pitch = lastPitch;
        }
        return rotation;
    }

    private static final int HORIZON = 30;
    private final double[] pathX = new double[HORIZON], pathY = new double[HORIZON], pathZ = new double[HORIZON];
    private int pathLen, landAt, crossAt, catchLayer;
    private boolean lethal;
    private final java.util.HashMap<Long, Integer> pathCells = new java.util.HashMap<>();
    private final java.util.HashSet<Long> trail = new java.util.HashSet<>();
    private float[] keys;

    public float[] keys() {
        return keys;
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    private void predict(ScaffoldCore.Input in, ScaffoldWorld w) {
        double x = in.posX, y = in.posY, z = in.posZ, mx = in.motionX, my = in.motionY, mz = in.motionZ;
        boolean ground = in.onGround && my <= 0;
        float fwd = in.moveForward * 0.98f, str = in.moveStrafe * 0.98f;
        float yawRad = in.silentYaw * (float) Math.PI / 180f;
        float sn = ScaffoldCore.sin(yawRad), cs = ScaffoldCore.cos(yawRad);
        pathLen = 0;
        landAt = -1;
        for (int t = 0; t < HORIZON; t++) {
            double accel = ground ? in.aiMoveSpeed * 0.16277136 / Math.pow(in.slipperiness * 0.91, 3) : 0.02;
            double len = Math.sqrt(fwd * fwd + str * str);
            if (len >= 1e-4) {
                double f = accel / Math.max(1.0, len);
                mx += str * f * cs - fwd * f * sn;
                mz += fwd * f * cs + str * f * sn;
            }
            double nx = x + mx, ny = y + my, nz = z + mz;
            if (!w.boxFree(new Box(nx - 0.3, ny, nz - 0.3, nx + 0.3, ny + 1.8, nz + 0.3))) {
                if (w.boxFree(new Box(nx - 0.3, y, nz - 0.3, nx + 0.3, y + 1.8, nz + 0.3))) {
                    if (my < 0) {
                        landAt = t;
                        break;
                    }
                    my = 0;
                    ny = y;
                } else {
                    mx = mz = 0;
                    nx = x;
                    nz = z;
                    ny = y + my;
                    if (!w.boxFree(new Box(nx - 0.3, ny, nz - 0.3, nx + 0.3, ny + 1.8, nz + 0.3))) {
                        if (my < 0) {
                            landAt = t;
                            break;
                        }
                        ny = y;
                        my = 0;
                    }
                }
            }
            x = nx;
            y = ny;
            z = nz;
            pathX[t] = x;
            pathY[t] = y;
            pathZ[t] = z;
            pathLen = t + 1;
            if (ground) {
                ground = !w.boxFree(new Box(x - 0.3, y - 0.01, z - 0.3, x + 0.3, y, z + 0.3));
                if (ground) {
                    my = -0.0784;
                    mx *= in.slipperiness * 0.91;
                    mz *= in.slipperiness * 0.91;
                    continue;
                }
            }
            my = (my - 0.08) * 0.98;
            mx *= 0.91;
            mz *= 0.91;
        }
        double base = Double.isNaN(groundY) ? in.posY : groundY;
        lethal = landAt < 0 && pathLen > 0 && pathY[pathLen - 1] < base - 4 && overVoid(pathX[pathLen - 1], pathY[pathLen - 1], pathZ[pathLen - 1], w);
        catchLayer = ScaffoldCore.floor(base - 0.01);
        crossAt = pathLen - 1;
        for (int t = 0; t < pathLen; t++) {
            if (pathY[t] < catchLayer + 1) {
                crossAt = t - 1;
                break;
            }
        }
        pathCells.clear();
        for (int t = 0; t <= crossAt; t++) {
            int x0 = ScaffoldCore.floor(pathX[t] - 0.3), x1 = ScaffoldCore.floor(pathX[t] + 0.3);
            int z0 = ScaffoldCore.floor(pathZ[t] - 0.3), z1 = ScaffoldCore.floor(pathZ[t] + 0.3);
            for (int cx = x0; cx <= x1; cx++)
                for (int cz = z0; cz <= z1; cz++)
                    pathCells.put(cellKey(cx, catchLayer, cz), t);
        }
        if (tun.trail) {
            if (in.posY >= catchLayer + 1) {
                int x0 = ScaffoldCore.floor(in.posX - 0.3), x1 = ScaffoldCore.floor(in.posX + 0.3);
                int z0 = ScaffoldCore.floor(in.posZ - 0.3), z1 = ScaffoldCore.floor(in.posZ + 0.3);
                for (int cx = x0; cx <= x1; cx++)
                    for (int cz = z0; cz <= z1; cz++)
                        trail.add(cellKey(cx, catchLayer, cz));
            }
            for (long k : trail) pathCells.putIfAbsent(k, -1);
        }
    }

    private boolean predictedKnockOff(ScaffoldCore.Input in, ScaffoldWorld w) {
        if (!knocked || !lethal) return false;
        for (int t = 0; t < pathLen; t++) {
            if (!w.boxFree(new Box(pathX[t] - 0.3, catchLayer + 0.5, pathZ[t] - 0.3, pathX[t] + 0.3, catchLayer + 1, pathZ[t] + 0.3))) continue;
            if (!hasSupport) return true;
            double nx = pathX[t] - (supportX + 0.5), nz = pathZ[t] - (supportZ + 0.5);
            if (Math.abs(nx) > 1.5 * Math.abs(nz)) nz = 0;
            else if (Math.abs(nz) > 1.5 * Math.abs(nx)) nx = 0;
            double len = Math.hypot(Math.signum(nx), Math.signum(nz));
            if (len < 1e-6) return true;
            double decay = Math.pow(0.91, t);
            return (knockX * Math.signum(nx) + knockZ * Math.signum(nz)) * decay / len >= tun.knockPush;
        }
        return false;
    }

    private static final float[] YAW_STEPS = {0, 10, -10, 20, -20, 35, -35, 55, -55, 80, -80, 110, -110, 145, -145, 180};

    private Rotation planRotate(ScaffoldCore.Input in, ScaffoldWorld w, double speed) {
        keys = null;
        if (!tun.enabled) {
            falling = false;
            return rotation;
        }
        predict(in, w);
        boolean go = knockOff ? lethal || !in.onGround : predictedKnockOff(in, w);
        if (!go) {
            falling = false;
            hasRots = false;
            trail.clear();
            return rotation;
        }
        falling = true;
        rotation.speed = (float) speed;
        rotation.preventDuplicateLook = true;
        if (!in.hasBlock) {
            if (tun.steer) keys = steer(in);
            return rotation;
        }
        double ex = in.posX, ey = in.posY + in.eyeHeight, ez = in.posZ;
        double bestCost = Double.MAX_VALUE;
        int px = ScaffoldCore.floor(in.posX), pz = ScaffoldCore.floor(in.posZ);
        for (java.util.Map.Entry<Long, Integer> e : pathCells.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k << 38 >> 38), cz = (int) (k << 12 >> 38), cy = catchLayer;
            if (Math.abs(cx - px) > 5 || Math.abs(cz - pz) > 5) continue;
            if (!w.isAir(cx, cy, cz)) continue;
            int progress = e.getValue();
            for (int face = 0; face < 6; face++) {
                int bx = cx - Dir.DX[face], by = cy - Dir.DY[face], bz = cz - Dir.DZ[face];
                if (w.isAir(bx, by, bz) || !w.isFullCube(bx, by, bz)) continue;
                if (!w.canPlaceOnSide(bx, by, bz, face)) continue;
                float[] r = aimFace(in, w, bx, by, bz, face);
                if (r == null) continue;
                double turn = Math.abs(wrap180(r[0] - in.silentYaw)) + Math.abs(r[1] - in.silentPitch);
                double cost = -progress * 10 + turn / 30.0;
                if (cost < bestCost) {
                    bestCost = cost;
                    target = new ScaffoldCore.Target(bx, by, bz, face);
                    lastYaw = r[0];
                    lastPitch = r[1];
                }
            }
        }
        if (target != null) {
            placing = true;
            hasRots = true;
        }
        if (tun.steer) keys = steer(in);
        if (hasRots) {
            rotation.set = true;
            rotation.yaw = lastYaw;
            rotation.pitch = lastPitch;
        }
        return rotation;
    }

    private float[] aimFace(ScaffoldCore.Input in, ScaffoldWorld w, int bx, int by, int bz, int face) {
        double ex = in.posX, ey = in.posY + in.eyeHeight, ez = in.posZ;
        double fx = bx + 0.5 + Dir.DX[face] * 0.5, fy = by + 0.5 + Dir.DY[face] * 0.5, fz = bz + 0.5 + Dir.DZ[face] * 0.5;
        double dx = fx - ex, dy = fy - ey, dz = fz - ez;
        if (dx * dx + dy * dy + dz * dz > REACH * REACH) return null;
        float toward = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float best = Float.NaN, bestP = 0;
        double bestTurn = Double.MAX_VALUE;
        for (float base : new float[]{in.silentYaw, toward}) {
            for (float off : YAW_STEPS) {
                float yaw = base + off;
                double[] band = ScaffoldCore.pitchBand(ex, ey, ez, yaw, bx, by, bz, face, 0.1);
                if (band == null) continue;
                float p = (float) Math.max(band[0], Math.min(band[1], in.silentPitch));
                p = in.silentPitch + Math.round((p - in.silentPitch) / in.gcd) * in.gcd;
                if (p < band[0]) p += in.gcd;
                if (p > band[1]) p -= in.gcd;
                p = Math.max(-90f, Math.min(90f, p));
                if (!ray(in, w, yaw, p, hit) || hit.x != bx || hit.y != by || hit.z != bz || hit.face != face) continue;
                double turn = Math.abs(wrap180(yaw - in.silentYaw)) + Math.abs(p - in.silentPitch);
                if (turn < bestTurn) {
                    bestTurn = turn;
                    best = yaw;
                    bestP = p;
                }
            }
        }
        return Float.isNaN(best) ? null : new float[]{best, bestP};
    }

    private float[] steer(ScaffoldCore.Input in) {
        double tx, tz;
        if (!tun.steerAgainst && hasSupport) {
            tx = supportX + 0.5 - in.posX;
            tz = supportZ + 0.5 - in.posZ;
        } else {
            tx = -in.motionX;
            tz = -in.motionZ;
        }
        double want = Math.atan2(tz, tx);
        double yr = Math.toRadians(in.cameraYaw), sn = Math.sin(yr), cs = Math.cos(yr);
        float bestF = 0, bestS = 0;
        double bestDot = -2;
        for (int f = -1; f <= 1; f++) {
            for (int s = -1; s <= 1; s++) {
                if (f == 0 && s == 0) continue;
                double vx = f * -sn + s * cs, vz = f * cs + s * sn;
                double dot = (vx * Math.cos(want) + vz * Math.sin(want)) / Math.hypot(vx, vz);
                if (dot > bestDot) {
                    bestDot = dot;
                    bestF = f;
                    bestS = s;
                }
            }
        }
        return new float[]{bestF, bestS};
    }

    private static float wrap180(float v) {
        v %= 360f;
        if (v >= 180f) v -= 360f;
        if (v < -180f) v += 360f;
        return v;
    }

    private ScaffoldCore.Action planPost(ScaffoldCore.Input in, ScaffoldWorld w, float yaw, float pitch) {
        if (!falling || !in.hasBlock || !ray(in, w, yaw, pitch, hit)) return action;
        int cx = hit.x + Dir.DX[hit.face], cy = hit.y + Dir.DY[hit.face], cz = hit.z + Dir.DZ[hit.face];
        if (cy != catchLayer || !pathCells.containsKey(cellKey(cx, cy, cz))) return action;
        if (w.isAir(hit.x, hit.y, hit.z) || !w.canPlaceOnSide(hit.x, hit.y, hit.z, hit.face)) return action;
        target = new ScaffoldCore.Target(hit.x, hit.y, hit.z, hit.face);
        action.place = true;
        action.x = hit.x;
        action.y = hit.y;
        action.z = hit.z;
        action.face = hit.face;
        action.hitX = hit.hx;
        action.hitY = hit.hy;
        action.hitZ = hit.hz;
        return action;
    }

    public ScaffoldCore.Action post(ScaffoldCore.Input in, ScaffoldWorld w, float yaw, float pitch) {
        action.place = false;
        if (tun.plan) return planPost(in, w, yaw, pitch);
        if (!placing || target == null || !in.hasBlock) return action;
        if (!ray(in, w, yaw, pitch, hit)) return action;
        if (hit.face == Dir.DOWN) return action;
        if (hit.face == Dir.UP && !canPlaceUpOn(in, hit.y)) return action;
        if (w.isAir(hit.x, hit.y, hit.z)) return action;
        if (!w.canPlaceOnSide(hit.x, hit.y, hit.z, hit.face)) return action;
        target = new ScaffoldCore.Target(hit.x, hit.y, hit.z, hit.face);
        action.place = true;
        action.x = hit.x;
        action.y = hit.y;
        action.z = hit.z;
        action.face = hit.face;
        faceVector(hit.x, hit.y, hit.z, hit.face);
        lastPlace = tick;
        lastClutch = tick;
        return action;
    }

    private boolean armed() {
        if (tun.classify) return knockOff;
        return tick - lastKnock <= tun.knockWindow || (tun.clutchRearms && tick - lastClutch <= tun.knockWindow);
    }

    private boolean fallingIntoVoid(ScaffoldCore.Input in, ScaffoldWorld w) {
        if (bigFallNow(in, w)) {
            lastVoid = tick;
            return true;
        }
        return tick - lastVoid <= tun.voidMemory;
    }

    private boolean bigFallNow(ScaffoldCore.Input in, ScaffoldWorld w) {
        if (in.onGround) return false;
        if (!ScaffoldCore.willFallNextTick(in, w, 1.0)) return false;
        return overVoid(in.posX, in.posY, in.posZ, w);
    }

    private boolean overVoid(double x, double y, double z, ScaffoldWorld w) {
        double half = 0.3;
        double bottom = Math.max(0, y - tun.voidDepth);
        return w.boxFree(new Box(x - half, bottom, z - half, x + half, y, z + half));
    }

    private ScaffoldCore.Target findCatch(ScaffoldCore.Input in, ScaffoldWorld w, float lockedYaw) {
        int px = ScaffoldCore.floor(in.posX), pz = ScaffoldCore.floor(in.posZ), top = ScaffoldCore.floor(in.posY) - 1;
        Box predicted = ScaffoldCore.predictedBox(in, 1.0);
        double targetX = (predicted.minX + predicted.maxX) * 0.5, targetZ = (predicted.minZ + predicted.maxZ) * 0.5, targetY = top + 0.5;
        double ex = in.posX, ey = in.posY + in.eyeHeight, ez = in.posZ;
        ScaffoldCore.Target best = null;
        double bestScore = Double.MAX_VALUE, existingScore = Double.MAX_VALUE;
        for (int down = 0; down <= 5; down++) {
            int y = top - down;
            if (y < 0) break;
            for (int x = px - 4; x <= px + 4; x++) {
                for (int z = pz - 4; z <= pz + 4; z++) {
                    if (w.isAir(x, y, z) || !w.isFullCube(x, y, z)) continue;
                    if (overlaps(x, z, predicted) && w.isAir(x, y + 1, z)) existingScore = Math.min(existingScore, score(x, y, z, targetX, targetY, targetZ));
                    for (int face = 1; face < 6; face++) {
                        if (face == Dir.UP && !canPlaceUpOn(in, y)) continue;
                        if (!w.canPlaceOnSide(x, y, z, face)) continue;
                        int nx = x + Dir.DX[face], ny = y + Dir.DY[face], nz = z + Dir.DZ[face];
                        if (!w.isAir(nx, ny, nz)) continue;
                        if (!overlaps(nx, nz, predicted)) continue;
                        double fx = x + 0.5 + Dir.DX[face] * 0.5 - ex, fy = y + 0.5 + Dir.DY[face] * 0.5 - ey, fz = z + 0.5 + Dir.DZ[face] * 0.5 - ez;
                        if (fx * fx + fy * fy + fz * fz > REACH * REACH) continue;
                        double s = score(nx, ny, nz, targetX, targetY, targetZ);
                        if (s >= bestScore) continue;
                        float[] r = ScaffoldCore.rotationsForFace(in, x, y, z, face, lockedYaw);
                        if (r == null) r = ScaffoldCore.freeRotationsForFace(in, x, y, z, face);
                        if (!ray(in, w, r[0], r[1], hit) || hit.x != x || hit.y != y || hit.z != z || hit.face != face) continue;
                        bestScore = s;
                        best = new ScaffoldCore.Target(x, y, z, face);
                    }
                }
            }
        }
        if (best != null && existingScore <= bestScore) return null;
        return best;
    }

    private static boolean canPlaceUpOn(ScaffoldCore.Input in, int y) {
        return in.posY - (y + 1) > 0.5;
    }

    private static boolean overlaps(int x, int z, Box b) {
        return x < b.maxX && x + 1 > b.minX && z < b.maxZ && z + 1 > b.minZ;
    }

    private static double score(int x, int y, int z, double tx, double ty, double tz) {
        double dx = x + 0.5 - tx, dy = y + 0.5 - ty, dz = z + 0.5 - tz;
        return dx * dx + dz * dz + dy * dy * 0.25;
    }

    private boolean ray(ScaffoldCore.Input in, ScaffoldWorld w, float yaw, float pitch, ScaffoldWorld.Hit out) {
        double ex = in.posX, ey = in.posY + in.eyeHeight, ez = in.posZ;
        float f = ScaffoldCore.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = ScaffoldCore.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -ScaffoldCore.cos(-pitch * 0.017453292F);
        float f3 = ScaffoldCore.sin(-pitch * 0.017453292F);
        return w.rayTrace(ex, ey, ez, ex + f1 * f2 * REACH, ey + f3 * REACH, ez + f * f2 * REACH, out);
    }

    private void faceVector(int x, int y, int z, int face) {
        double a1 = 0.45 + rnd.nextDouble() * 0.1, a2 = 0.45 + rnd.nextDouble() * 0.1;
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
}
