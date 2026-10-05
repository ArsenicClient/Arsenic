package arsenic.utils.botcore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Bot {
    public final Tuning tun;
    public boolean sprintJump = false;
    private final boolean async;

    private Goal goal;
    private volatile boolean active;
    private boolean arrived;
    private boolean failed;

    private volatile List<Step> path;
    private volatile boolean pathComplete;
    private int idx;
    private volatile List<Step> nextPath;
    private double[] airAim;
    private int centringTicks;
    private long airAimTick;
    private volatile boolean nextComplete;

    private volatile boolean searching;
    private volatile int generation;
    private volatile Planner.Result lastResult;
    public volatile List<Step> searchPreview;

    private final Map<Long, Long> avoid = new ConcurrentHashMap<>();
    private long tickCount;

    private double anchorX, anchorY, anchorZ;
    private int watchTicks, stuckLevel, nudgeTicks, escapeTicks, offPathTicks;
    private float escapeYaw;
    private final Random random = new Random(1234);

    public Bot(Tuning tun, boolean async) {
        this.tun = tun;
        this.async = async;
    }


    public void setGoal(Goal g) {
        generation++;
        goal = g;
        active = g != null;
        arrived = false;
        failed = false;
        path = null;
        nextPath = null;
        searching = false;
        idx = 0;
        centringTicks = 0;
        stuckLevel = 0;
        nudgeTicks = 0;
        escapeTicks = 0;
        watchTicks = 0;
        offPathTicks = 0;
    }

    public void stop() {
        setGoal(null);
    }

    public boolean isActive() {
        return active;
    }

    public boolean arrived() {
        return arrived;
    }

    public boolean failed() {
        return failed;
    }

    public Goal goal() {
        return goal;
    }

    public List<Step> path() {
        return path;
    }

    public int pathIndex() {
        return idx;
    }

    public List<Step> nextPath() {
        return nextPath;
    }

    public boolean searching() {
        return searching;
    }

    public Planner.Result lastResult() {
        return lastResult;
    }


    public Controls tick(PlayerView p, BlockView view) {
        Controls c = new Controls();
        tickCount++;
        if (delayed != null && tickCount >= delayedAt) {
            Runnable d = delayed;
            delayed = null;
            d.run();
        }
        if (!active || goal == null) {
            return c;
        }
        viewForPlan = view;
        caps = capsOf(p);
        Terrain t = new Terrain(view, p.pickaxe);
        int fx = Terrain.floor(p.x), fy = Terrain.floor(p.y + 1e-3), fz = Terrain.floor(p.z);
        long now = tickCount;
        avoid.values().removeIf(exp -> exp < now);

        if ((p.onGround || onClimbable(t, p)) && goal.reached(t, fx, fy, fz, p.y)) {
            if (goal.reachedFrom(t, p.x, p.eyeY(), p.z) || ++centringTicks > 20) {
                active = false;
                arrived = true;
                return c;
            }
            walkTo(p, c, fx + 0.5, fz + 0.5, true);
            return c;
        }

        if (escapeTicks > 0) {
            escapeTicks--;
            c.setYaw = true;
            c.yaw = escapeYaw;
            c.forward = true;
            c.sprint = true;
            c.jump = p.onGround && p.collidedHorizontally;
            if (escapeTicks == 0) {
                replan(p, t);
            }
            return c;
        }

        List<Step> cur = path;
        if (cur == null) {
            if (!searching) {
                replan(p, t);
                cur = path;
            }
            if (cur == null) {
                return c;
            }
        }

        advance(p, t, cur);
        if (idx >= cur.size()) {
            List<Step> next = nextPath;
            Step last = cur.get(cur.size() - 1);
            if (next != null && next.get(0).same(last.x, last.y, last.z)) {
                path = next;
                pathComplete = nextComplete;
                nextPath = null;
                idx = 1;
                cur = next;
            } else {
                path = null;
                if (!searching || nextPath == null) {
                    replan(p, t);
                }
                cur = path;
                if (cur == null) {
                    return c;
                }
            }
            if (idx >= cur.size()) {
                return c;
            }
        }

        if (!pathComplete && !searching && nextPath == null && cur.size() - idx <= tun.planAheadSteps) {
            Step last = cur.get(cur.size() - 1);
            requestPlan(last.x, last.y, last.z, last.feet, true);
        }

        if (offPath(p, cur)) {
            if (++offPathTicks > 10) {
                offPathTicks = 0;
                replan(p, t);
                return c;
            }
        } else {
            offPathTicks = 0;
        }

        execute(p, t, cur, c);

        if (nudgeTicks > 0) {
            nudgeTicks--;
            c.forward = true;
            c.jump = true;
        }
        watchdog(p, t, cur);
        return c;
    }


    private void replan(PlayerView p, Terrain t) {
        int[] s = startNode(p, t);
        generation++;
        searching = false;
        path = null;
        nextPath = null;
        idx = 0;
        requestPlan(s[0], s[1], s[2], p.onGround ? standOr(t, s, p.y) : p.y, false);
        anchor(p);
    }

    private Planner.Caps caps = new Planner.Caps();

    private Planner.Caps capsOf(PlayerView p) {
        Planner.Caps c = new Planner.Caps();
        c.speed = p.speed;
        c.blocks = p.blocks;
        c.pickaxe = p.pickaxe;
        c.hop = sprintJump;
        return c;
    }

    private BlockView viewForPlan;

    private double standOr(Terrain t, int[] s, double y) {
        double h = t.standHeight(s[0], s[1], s[2]);
        return Double.isNaN(h) ? y : h;
    }

    private int[] startNode(PlayerView p, Terrain t) {
        int fx = Terrain.floor(p.x), fy = Terrain.floor(p.y + 1e-3), fz = Terrain.floor(p.z);
        if (!p.onGround || !Double.isNaN(t.standHeight(fx, fy, fz))) {
            return new int[]{fx, fy, fz};
        }
        int[] best = {fx, fy, fz};
        double bestD = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = Terrain.floor(p.x + dx * Terrain.HW), z = Terrain.floor(p.z + dz * Terrain.HW);
                double h = t.standHeight(x, fy, z);
                if (Double.isNaN(h) || Math.abs(h - p.y) > 0.01) continue;
                double ex = x + 0.5 - p.x, ez = z + 0.5 - p.z, d = ex * ex + ez * ez;
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, fy, z};
                }
            }
        }
        return best;
    }

    private void requestPlan(final int sx, final int sy, final int sz, final double feet, final boolean ahead) {
        final int gen = generation;
        final Goal g = goal;
        final Planner.Caps cp = caps;
        final BlockView view = viewForPlan;
        final Set<Long> av = new HashSet<>(avoid.keySet());
        searching = true;
        Runnable job = () -> {
            Planner.Result r;
            try {
                r = Planner.plan(view, sx, sy, sz, feet, g, tun, cp, av, () -> gen != generation, steps -> searchPreview = steps);
            } catch (RuntimeException e) {
                e.printStackTrace();
                r = null;
            }
            deliver(r, gen, ahead);
        };
        if (async) {
            Thread th = new Thread(job, "bot-planner");
            th.setDaemon(true);
            th.start();
        } else {
            job.run();
        }
    }

    public int simLatency = 0;
    public double simTimeScale = 3;
    private Runnable delayed;
    private long delayedAt;

    private void deliver(final Planner.Result r, final int gen, final boolean ahead) {
        if (!async && simLatency > 0) {
            delayed = () -> apply(r, gen, ahead);
            delayedAt = tickCount + simLatency + (r == null ? 0 : (long) Math.ceil(r.millis * simTimeScale / 50.0));
            return;
        }
        apply(r, gen, ahead);
    }

    private void apply(Planner.Result r, int gen, boolean ahead) {
        searchPreview = null;
        if (gen != generation) {
            return;
        }
        searching = false;
        lastResult = r;
        if (r == null) {
            return;
        }
        if (ahead) {
            if (r.steps.size() > 1) {
                nextPath = r.steps;
                nextComplete = r.complete;
            }
            return;
        }
        if (r.steps.size() <= 1 && !r.complete) {
            failed = true;
            active = false;
            return;
        }
        path = r.steps;
        pathComplete = r.complete;
        idx = 1;
    }


    private static boolean onClimbable(Terrain t, PlayerView p) {
        return t.climbable(Terrain.floor(p.x), Terrain.floor(p.y + 1e-3), Terrain.floor(p.z));
    }

    private void advance(PlayerView p, Terrain t, List<Step> cur) {
        int fx = Terrain.floor(p.x), fy = Terrain.floor(p.y + 1e-3), fz = Terrain.floor(p.z);
        boolean settled = p.onGround || onClimbable(t, p);
        int hi = Math.min(cur.size() - 1, idx + tun.smoothLookahead + 8);
        if (settled) {
            for (int j = hi; j >= Math.max(0, idx - 1); j--) {
                if (cur.get(j).same(fx, fy, fz)) {
                    if (j + 1 > idx) {
                        idx = j + 1;
                    }
                    return;
                }
            }
            for (int j = Math.min(cur.size() - 1, idx + 2); j >= idx; j--) {
                Step s = cur.get(j);
                if (Math.abs(p.x - (s.x + 0.5)) < 0.5 + Terrain.HW && Math.abs(p.z - (s.z + 0.5)) < 0.5 + Terrain.HW
                        && Math.abs(p.y - s.feet) < 0.05) {
                    idx = j + 1;
                    return;
                }
            }
        } else {
            for (int j = idx; j <= hi; j++) {
                Step s = cur.get(j);
                if (!s.flat()) {
                    return;
                }
                if (s.x == fx && s.z == fz && fy >= s.y && fy <= s.y + 1) {
                    idx = j + 1;
                }
            }
        }
    }

    private boolean offPath(PlayerView p, List<Step> cur) {
        if (!p.onGround) {
            return false;
        }
        double best = Double.MAX_VALUE;
        for (int j = Math.max(0, idx - 2); j <= Math.min(cur.size() - 1, idx + 2); j++) {
            Step s = cur.get(j);
            double dx = s.x + 0.5 - p.x, dz = s.z + 0.5 - p.z, dy = s.feet - p.y;
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz) + Math.max(0, Math.abs(dy) - 1));
        }
        return best > tun.offPathDistance;
    }


    private void execute(PlayerView p, Terrain t, List<Step> cur, Controls c) {
        Step s = cur.get(idx);
        Step prev = cur.get(idx - 1);
        if (s.type != Step.MINE_DOWN && mineInTheWay(p, t, prev, s, c)) {
            return;
        }
        boolean grounded = p.onGround || onClimbable(t, p);
        if (!p.onGround && onClimbable(t, p) && (s.x != prev.x || s.z != prev.z)
                && (t.climbable(s.x, s.y, s.z) || t.climbable(s.x, s.y - 1, s.z))) {
            int wall = t.pushSide(s.x, t.climbable(s.x, s.y, s.z) ? s.y : s.y - 1, s.z);
            if (wall >= 0) {
                lookYaw(c, p, s.x + 0.5 + Dir.DX[wall] * 0.9, s.z + 0.5 + Dir.DZ[wall] * 0.9);
                c.forward = true;
                return;
            }
        }
        switch (s.type) {
            case Step.WALK:
            case Step.DIAGONAL: {
                double[] aim = smoothTarget(p, t, cur);
                if (p.onGround || tun.airControl) {
                    airAim = aim;
                    airAimTick = tickCount;
                } else if (airAim != null && tickCount - airAimTick <= 20) {
                    aim = airAim;
                }
                walkTo(p, c, aim[0], aim[1], grounded);
                c.sprint = true;
                if (sprintJump && p.onGround && hopSafe(p, t, cur, aim, c.yaw)) {
                    c.jump = true;
                }
                break;
            }
            case Step.ASCEND: {
                walkTo(p, c, s.x + 0.5, s.z + 0.5, grounded);
                c.sprint = true;
                double dist = distToEdge(p, prev, s);
                if (p.onGround && (dist < tun.ascendJumpDist || p.collidedHorizontally) && s.feet - p.y > Terrain.STEP) {
                    c.jump = true;
                }
                break;
            }
            case Step.DROP: {
                if (p.onGround) {
                    walkTo(p, c, s.x + 0.5, s.z + 0.5, true);
                    c.sprint = s.feet > p.y - 2;
                } else {
                    airSteer(p, c, s);
                }
                break;
            }
            case Step.PARKOUR: {
                double jx = s.x - prev.x, jz = s.z - prev.z, jl = Math.sqrt(jx * jx + jz * jz);
                double dx = jx / jl, dz = jz / jl;
                double edge = 0.5 / Math.max(Math.abs(dx), Math.abs(dz));
                if (!p.onGround) {
                    airSteer(p, c, s);
                    break;
                }
                walkTo(p, c, s.x + 0.5, s.z + 0.5, true);
                c.sprint = true;
                double along = (p.x - (prev.x + 0.5)) * dx + (p.z - (prev.z + 0.5)) * dz;
                int fx = Terrain.floor(p.x), fz = Terrain.floor(p.z);
                boolean onTakeoff = fx == prev.x && fz == prev.z;
                double tx = s.x + 0.5, tz = s.z + 0.5;
                float base = c.yaw, bestYaw = base;
                double bestErr = Double.MAX_VALUE, bestNear = 0;
                for (int k = -tun.parkourYawSteps; k <= tun.parkourYawSteps; k++) {
                    float yaw = base + k * 2.0f;
                    PlayerView after = withYaw(p, yaw);
                    double[] far = predictLanding(after, true, 99, s.feet, t);
                    double[] near = predictLanding(after, true, 1, s.feet, t);
                    if (far == null || near == null) {
                        continue;
                    }
                    double err = segmentDistance(tx, tz, near[0], near[1], far[0], far[1]);
                    if (err < bestErr - 1e-9 || (Math.abs(err - bestErr) < 1e-9 && Math.abs(k) < Math.abs((bestYaw - base) / 2))) {
                        bestErr = err;
                        bestYaw = yaw;
                        bestNear = (near[0] - tx) * dx + (near[1] - tz) * dz;
                    }
                }
                boolean mustGo = along >= edge + tun.parkourTakeoff - 0.5 || !onTakeoff;
                if ((bestErr <= tun.parkourWindow && along >= tun.parkourEarliest) || mustGo) {
                    c.yaw = bestYaw;
                    c.jump = true;
                } else if (bestErr == Double.MAX_VALUE) {
                    walkTo(p, c, prev.x + 0.5 + dx * 0.45, prev.z + 0.5 + dz * 0.45, true);
                    c.sprint = false;
                } else if (bestNear > tun.parkourWindow) {
                    c.forward = false;
                    c.sprint = false;
                }
                break;
            }
            case Step.CLIMB_UP: {
                int wall = wallDir(t, prev.x, prev.y, prev.z);
                if (wall >= 0) {
                    lookYaw(c, p, prev.x + 0.5 + Dir.DX[wall] * 0.9, prev.z + 0.5 + Dir.DZ[wall] * 0.9);
                } else {
                    walkTo(p, c, s.x + 0.5, s.z + 0.5, true);
                }
                c.forward = true;
                if (p.onGround) {
                    c.jump = true;
                }
                break;
            }
            case Step.MINE_DOWN: {
                int mx = prev.x, my = prev.y - 1, mz = prev.z;
                if (t.boxes(mx, my, mz).length > 0 && t.is(mx, my, mz, BlockView.OWN_BLOCK)) {
                    double cx = prev.x + 0.5 - p.x, cz = prev.z + 0.5 - p.z;
                    if (p.onGround && cx * cx + cz * cz > 0.04) {
                        walkTo(p, c, prev.x + 0.5, prev.z + 0.5, true);
                        c.sneak = true;
                        break;
                    }
                    c.mine = true;
                    c.mineX = mx;
                    c.mineY = my;
                    c.mineZ = mz;
                    c.mineFace = Dir.UP;
                    c.setPitch = true;
                    c.pitch = 90;
                } else if (!p.onGround) {
                    airSteer(p, c, s);
                }
                break;
            }
            case Step.CLIMB_DOWN: {
                break;
            }
            case Step.PILLAR: {
                c.setPitch = true;
                c.pitch = 90;
                if (p.onGround) {
                    c.jump = true;
                }
                if (p.y >= s.py + 1 - 1e-3 && t.empty(s.px, s.py, s.pz)) {
                    placeAgainst(c, p, t, s.px, s.py, s.pz);
                }
                break;
            }
            case Step.BRIDGE: {
                if (!t.empty(s.px, s.py, s.pz)) {
                    walkTo(p, c, s.x + 0.5, s.z + 0.5, grounded);
                    break;
                }
                c.sneak = true;
                walkTo(p, c, s.x + 0.5, s.z + 0.5, grounded);
                c.sprint = false;
                placeAgainst(c, p, t, s.px, s.py, s.pz);
                break;
            }
            default:
                break;
        }
    }


    private static PlayerView withYaw(PlayerView p, float yaw) {
        PlayerView v = new PlayerView();
        v.x = p.x;
        v.y = p.y;
        v.z = p.z;
        v.motionX = p.motionX;
        v.motionY = p.motionY;
        v.motionZ = p.motionZ;
        v.yaw = yaw;
        v.onGround = p.onGround;
        v.sprinting = true;
        v.speed = p.speed;
        return v;
    }

    public static double[] predictLanding(PlayerView p, boolean jump, int holdTicks, double landY, Terrain terrain) {
        double x = p.x, y = p.y, z = p.z, vx = p.motionX, vy = p.motionY, vz = p.motionZ;
        double rad = Math.toRadians(p.yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        boolean sprint = p.sprinting;
        boolean ground = p.onGround;
        double base = 0.1 * p.speed;
        if (jump && ground) {
            vy = 0.42;
            if (sprint) {
                vx -= sin * 0.2;
                vz += cos * 0.2;
            }
        }
        double px = x, pz = z, py = y;
        for (int t = 0; t < 100; t++) {
            boolean w = t < holdTicks;
            double accel;
            if (ground) {
                accel = base * (sprint ? 1.3 : 1.0);
            } else {
                accel = sprint ? 0.026 : 0.02;
            }
            if (!w) sprint = false;
            if (w) {
                vx -= sin * accel * 0.98;
                vz += cos * accel * 0.98;
            }
            px = x;
            pz = z;
            py = y;
            if (terrain != null && vy > 0 && blocked(terrain, x, y + vy, z, landY)) {
                double lo = y, hi = y + vy;
                for (int k = 0; k < 6; k++) {
                    double mid = (lo + hi) / 2;
                    if (blocked(terrain, x, mid, z, landY)) hi = mid;
                    else lo = mid;
                }
                vy = lo - y;
                y = lo;
                x += vx;
                z += vz;
                vy = 0;
            } else {
                x += vx;
                y += vy;
                z += vz;
            }
            if (terrain != null && blocked(terrain, x, y, z, landY)) {
                return null;
            }
            double fr = ground ? 0.546 : 0.91;
            ground = false;
            vx *= fr;
            vz *= fr;
            vy = (vy - 0.08) * 0.98;
            if (vy < 0 && y <= landY) {
                double f = py - y > 1e-9 ? (py - landY) / (py - y) : 1;
                f = Math.max(0, Math.min(1, f));
                return new double[]{px + (x - px) * f, pz + (z - pz) * f};
            }
        }
        return new double[]{x, z};
    }

    private static boolean blocked(Terrain t, double x, double y, double z, double landY) {
        double b = Math.max(y, landY) + 0.01;
        return t.fitsBox(new Box(x - Terrain.HW, b, z - Terrain.HW, x + Terrain.HW, b + Terrain.HEIGHT - 0.01, z + Terrain.HW)) != 0;
    }

    static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 < 1e-12 ? 0 : ((px - ax) * vx + (pz - az) * vz) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + vx * t - px, cz = az + vz * t - pz;
        return Math.sqrt(cx * cx + cz * cz);
    }

    private void airSteer(PlayerView p, Controls c, Step s) {
        double tx = s.x + 0.5, tz = s.z + 0.5;
        double[] coast = predictLanding(p, false, 0, s.feet, null);
        double dc = (coast[0] - tx) * (coast[0] - tx) + (coast[1] - tz) * (coast[1] - tz);
        if (!tun.airControl) {
            double[] push = predictLanding(p, false, 1, s.feet, null);
            double dp = (push[0] - tx) * (push[0] - tx) + (push[1] - tz) * (push[1] - tz);
            c.forward = dp < dc;
            c.sprint = c.forward;
            return;
        }
        double best = dc;
        float bestYaw = Float.NaN;
        PlayerView v = withYaw(p, p.yaw);
        v.sprinting = p.sprinting;
        for (int k = 0; k < tun.airSteerYaws; k++) {
            v.yaw = k * (360f / tun.airSteerYaws);
            double[] l = predictLanding(v, false, 99, s.feet, null);
            double d = (l[0] - tx) * (l[0] - tx) + (l[1] - tz) * (l[1] - tz);
            if (d < best) {
                best = d;
                bestYaw = v.yaw;
            }
        }
        if (!Float.isNaN(bestYaw)) {
            c.setYaw = true;
            c.yaw = bestYaw;
            c.forward = true;
            c.sprint = true;
        }
    }

    private void walkTo(PlayerView p, Controls c, double x, double z, boolean grounded) {
        if (grounded || tun.airControl) {
            lookYaw(c, p, x, z);
        }
        c.forward = grounded || tun.airControl || facing(p, x, z, 60);
    }

    private static void lookYaw(Controls c, PlayerView p, double x, double z) {
        c.setYaw = true;
        c.yaw = yawTo(p.x, p.z, x, z);
    }

    public static float yawTo(double fromX, double fromZ, double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-(x - fromX), z - fromZ));
    }

    private static boolean facing(PlayerView p, double x, double z, double within) {
        float want = yawTo(p.x, p.z, x, z);
        double d = ((want - p.yaw) % 360 + 540) % 360 - 180;
        return Math.abs(d) < within;
    }

    private static double distToEdge(PlayerView p, Step prev, Step s) {
        int dx = Integer.signum(s.x - prev.x), dz = Integer.signum(s.z - prev.z);
        double along = (p.x - (prev.x + 0.5)) * dx + (p.z - (prev.z + 0.5)) * dz;
        return 0.5 - along;
    }

    private static int wallDir(Terrain t, int x, int y, int z) {
        return t.pushSide(x, y, z);
    }


    private static void placeAgainst(Controls c, PlayerView p, Terrain t, int x, int y, int z) {
        double ex = p.x, ey = p.eyeY(), ez = p.z;
        double best = Double.MAX_VALUE;
        for (int f = 0; f < 6; f++) {
            int ax = x + Dir.DX[f], ay = y + Dir.DY[f], az = z + Dir.DZ[f];
            if (!t.is(ax, ay, az, BlockView.PLACE_AGAINST)) continue;
            int face = Dir.opposite(f);
            double hx = ax + 0.5 + Dir.DX[face] * 0.5, hy = ay + 0.5 + Dir.DY[face] * 0.5, hz = az + 0.5 + Dir.DZ[face] * 0.5;
            double d = (hx - ex) * (hx - ex) + (hy - ey) * (hy - ey) + (hz - ez) * (hz - ez);
            if (d < best) {
                best = d;
                c.place = true;
                c.placeX = x;
                c.placeY = y;
                c.placeZ = z;
                c.againstX = ax;
                c.againstY = ay;
                c.againstZ = az;
                c.againstFace = face;
                c.hitX = hx;
                c.hitY = hy;
                c.hitZ = hz;
            }
        }
        if (c.place) {
            lookAt(c, p, c.hitX, c.hitY, c.hitZ);
        }
    }

    private static void lookAt(Controls c, PlayerView p, double x, double y, double z) {
        double dx = x - p.x, dy = y - p.eyeY(), dz = z - p.z;
        c.setYaw = true;
        c.yaw = yawTo(p.x, p.z, x, z);
        c.setPitch = true;
        c.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    private static boolean mineInTheWay(PlayerView p, Terrain t, Step prev, Step s, Controls c) {
        if (!p.pickaxe) return false;
        double top = Math.max(prev.feet, s.feet);
        Box[] check = {
                Box.player(s.x + 0.5, s.feet, s.z + 0.5),
                Box.player((prev.x + s.x) * 0.5 + 0.5, top, (prev.z + s.z) * 0.5 + 0.5)
        };
        for (Box b : check) {
            for (int x = Terrain.floor(b.minX); x <= Terrain.floor(b.maxX - 1e-6); x++) {
                for (int y = Terrain.floor(b.minY + 1e-6); y <= Terrain.floor(b.maxY - 1e-6); y++) {
                    for (int z = Terrain.floor(b.minZ); z <= Terrain.floor(b.maxZ - 1e-6); z++) {
                        if (!t.is(x, y, z, BlockView.OWN_BLOCK)) continue;
                        for (Box cb : t.boxes(x, y, z)) {
                            if (cb.intersects(b)) {
                                c.mine = true;
                                c.mineX = x;
                                c.mineY = y;
                                c.mineZ = z;
                                int[] face = new int[1];
                                double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
                                t.rayTrace(p.x, p.eyeY(), p.z, cx, cy, cz, face);
                                c.mineFace = face[0] < 0 ? Dir.UP : face[0];
                                lookAt(c, p, cx, cy, cz);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }


    private double[] smoothTarget(PlayerView p, Terrain t, List<Step> cur) {
        Step s = cur.get(idx);
        double[] fallback = {s.x + 0.5, s.z + 0.5};
        if (!p.onGround && !tun.airControl) {
            return fallback;
        }
        int last = idx;
        for (int j = idx + 1; j < cur.size() && j <= idx + tun.smoothLookahead; j++) {
            Step a = cur.get(j);
            if (!a.flat() || Math.abs(a.feet - cur.get(j - 1).feet) > Terrain.STEP) break;
            last = j;
        }
        for (int j = last; j > idx; j--) {
            Step a = cur.get(j);
            if (corridor(t, p.x, p.z, a.x + 0.5, a.z + 0.5, p.onGround ? p.y : s.feet, tun.smoothMargin)) {
                return new double[]{a.x + 0.5, a.z + 0.5};
            }
        }
        return fallback;
    }

    static boolean corridor(Terrain t, double ax, double az, double bx, double bz, double feet, double margin) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        int n = Math.max(1, (int) Math.ceil(len / 0.2));
        double h = feet;
        double w = Terrain.HW + margin;
        for (int i = 0; i <= n; i++) {
            double cx = ax + dx * i / n, cz = az + dz * i / n;
            int x = Terrain.floor(cx), z = Terrain.floor(cz), y = Terrain.floor(h + 1e-3);
            double fh = Double.NaN;
            for (int yy = y + 1; yy >= y - 1 && Double.isNaN(fh); yy--) {
                double f = t.floorAt(x, yy, z);
                if (!Double.isNaN(f) && Math.abs(f - h) <= Terrain.STEP) fh = f;
            }
            if (Double.isNaN(fh) || t.dangerBelow(x, Terrain.floor(fh + 1e-3), z)) {
                return false;
            }
            h = fh;
            Box b = new Box(cx - w, h + 0.01, cz - w, cx + w, h + Terrain.HEIGHT, cz + w);
            if (t.fitsBox(b) != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hopSafe(PlayerView p, Terrain t, List<Step> cur, double[] aim, float yaw) {
        for (int j = idx; j < cur.size() && j < idx + tun.hopLookSteps; j++) {
            Step s = cur.get(j);
            if (!(s.flat() || s.type == Step.ASCEND) || s.places()) return false;
        }
        double ax = aim[0] - p.x, az = aim[1] - p.z;
        double aimDist = Math.sqrt(ax * ax + az * az);
        if (aimDist < tun.hopMinRun) return false;
        PlayerView v = withYaw(p, yaw);
        v.sprinting = p.sprinting;
        double[] land = predictLanding(v, true, 99, p.y, t);
        if (land == null) return false;
        double lx = land[0] - p.x, lz = land[1] - p.z;
        double along = (lx * ax + lz * az) / aimDist;
        double side = Math.abs(lx * az - lz * ax) / aimDist;
        if (along > aimDist + tun.hopOvershoot || side > tun.hopSideways) return false;
        int x = Terrain.floor(land[0]), z = Terrain.floor(land[1]), y = Terrain.floor(p.y + 1e-3);
        for (double d = -tun.hopLandSlack; d <= tun.hopLandSlack + 1e-9; d += tun.hopLandSlack) {
            double px = land[0] + ax / aimDist * d, pz = land[1] + az / aimDist * d;
            if (!t.supportAt(px, p.y, pz)) return false;
        }
        return !t.dangerBelow(x, y, z);
    }


    private void anchor(PlayerView p) {
        anchorX = p.x;
        anchorY = p.y;
        anchorZ = p.z;
        watchTicks = 0;
    }

    private void watchdog(PlayerView p, Terrain t, List<Step> cur) {
        if (++watchTicks < tun.stuckTicks) {
            return;
        }
        double dx = p.x - anchorX, dy = p.y - anchorY, dz = p.z - anchorZ;
        boolean moved = dx * dx + dy * dy + dz * dz >= 0.75 * 0.75;
        anchor(p);
        if (moved) {
            stuckLevel = 0;
            return;
        }
        Step s = cur.get(Math.min(idx, cur.size() - 1));
        long key = Terrain.key(s.x, s.y, s.z);
        stuckLevel++;
        if (stuckLevel == 1) {
            nudgeTicks = 10;
        } else if (stuckLevel == 2) {
            avoid.put(key, tickCount + 1200);
            replan(p, t);
        } else {
            avoid.put(key, tickCount + 1200);
            escapeYaw = random.nextFloat() * 360f;
            escapeTicks = 25;
            stuckLevel = 1;
        }
    }

    public void setView(BlockView v) {
        viewForPlan = v;
    }

    public List<Step> allSteps() {
        List<Step> out = new ArrayList<>();
        List<Step> a = path, b = nextPath;
        if (a != null) out.addAll(a);
        if (b != null) out.addAll(b);
        return out.isEmpty() ? Collections.<Step>emptyList() : out;
    }
}
