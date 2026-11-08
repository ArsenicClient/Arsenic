package arsenic.utils.buildcore;

import arsenic.utils.botcore.BlockView;
import arsenic.utils.botcore.Box;
import arsenic.utils.botcore.Dir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link Blueprint} in creative mode while flying: one block placed or broken per tick, mostly bottom-up.
 * Each tick it looks at every unfinished cell in reach of the eye, picks the lowest one it can click (a solid
 * neighbour, a face it can see, a ray that really lands on that face after the rotation's GCD rounding) and the
 * smallest turn, and turns at most {@code maxTurn} degrees. Between ticks it flies to a hover point picked to keep the
 * most open cells in reach, with sky above it where it can so it never walls itself in. Parts that would float get a
 * scaffold block outside the build joining them to an anchored part (or a column down to something solid), broken
 * again as soon as the cells beside it are in. Cells walled in by what was built get a way in broken open.
 *
 * Timing as the mod calls it: at the start of the player tick, before movement, so the click goes out from the eye
 * before this tick's move and the next movement packet carries the rotation used.
 */
public final class BuildCore {

    public static final class Tuning {
        /** Eye to clicked point, blocks. */
        public double reach = 4.5;
        /** Largest turn in one tick, degrees (yaw and pitch together). */
        public float maxTurn = 60;
        /** Mouse step of the rotation (0 = exact). The mod passes the sensitivity's GCD. */
        public float gcd = 0;
        /** Layers above the lowest open layer that may be placed at all. */
        public int band = 8;
        /**
         * A cell waits while a cell to place lies below it, up to this many layers down and this far to the side, so
         * nothing is covered before it is placed and walls still rise several layers in one pass.
         */
        public int belowDepth = 4, belowRadius = 1;
        /** Look for a new hover point when fewer open cells than this are left in its reach. */
        public int replanAt = 3;
        /** Hover point score lost per block of travel. */
        public double travelCost = 0.2;
        /** Fly speed toward the hover point: wanted motion per block left, and the cap (blocks a tick). */
        public double gain = 0.3, maxSpeed = 1.0;
        public boolean sprint = true;
        /** Score cost of a cell per layer above the lowest open layer, per degree of turn, per hotbar switch. */
        public double layerCost = 30, turnCost = 0.25, switchCost = 4;
        /** Hover points must have nothing built, or about to be built, above them. */
        public boolean skyAccess = true;
        /** Hover points sit this far above a whole block, clear of landing on it. */
        public double hoverGap = 0.12;
        /** With sky access, cells to place overhead block a hover point up to this many layers above the lowest open one. */
        public int skyMargin = 3;
        /** Most cells the flight path search looks at. */
        public int pathNodes = 20000;
        /** Blocks kept clear round the body at body height (no placing there). */
        public double clearance = 0.7;
        /** Ticks without moving while away from the hover point before digging out. */
        public int stuckTicks = 40;
        /** Distance a layer up is worth when picking the next work to fly to. */
        public double seedLayerCost = 3;
        /** Times no spot sees a cell before a way in is broken open. */
        public int blindTries = 3;
        /** Hover choices in a row that see no scaffold to break before the rest are left standing. */
        public int giveUpScaffolds = 8;
        /** Ticks without a click before flying somewhere else. */
        public int idleReplan = 3;
        /** Ticks between hover point choices while work goes on. */
        public int replanEvery = 4;
        /** Ticks a cell waits after its hover point gave nothing to click. */
        public int skipTicks = 100;
        /** Hover points scored with line of sight (the best this many by cells in reach). */
        public int hoverChecks = 10;
        /** Degrees off a heading's quadrant edges kept clear when a stair or facing block must face a way. */
        public double facingMargin = 42;
        /** Fraction of a block kept clear of the middle when a hit height must pick a stair or slab half. */
        public double halfMargin = 0.05;

        public static Tuning best() {
            return new Tuning();
        }
    }

    public static final class Input {
        public double x, y, z, motionX, motionY, motionZ;
        public float yaw, pitch;
        public boolean flying, onGround, sprinting;
        /** Palette id of the block in hand (-1 none). */
        public int held = -1;
    }

    public static final class Output {
        public static final int NONE = 0, PLACE = 1, BREAK = 2;
        public final Flight.Keys keys = new Flight.Keys();
        /** Turn flight back on (it stops on touching the ground). */
        public boolean startFlying;
        public float yaw, pitch;
        public int action;
        /** The cell placed into or broken, and for a place the palette id to hold. */
        public int x, y, z, id;
        /** The block clicked, its face and the hit point (what the use-item packet carries). */
        public int againstX, againstY, againstZ, face;
        public double hitX, hitY, hitZ;
        public boolean done;
    }

    private static final int NEED_NONE = 0, NEED_PLACE = 1, NEED_BREAK = 2;
    private static final double EYE = 1.62, HALF_W = 0.3, HEIGHT = 1.8;

    public final Tuning tun;
    public final Blueprint bp;
    public final int ox, oy, oz;
    private final int[] need;
    /** Blueprint cells left open that air from beyond the build reaches (one cell of margin all round). */
    private final boolean[] outside;
    /** Scaffold cells still to place (world keys) and those standing. */
    private final Set<Long> scaffoldPlan = new HashSet<>(), scaffoldUp = new HashSet<>();
    /** Scaffold blocks given up on: left standing, no longer work. */
    private final Set<Long> scaffoldLeft = new HashSet<>();
    private final List<int[]> scaffoldCells = new ArrayList<>();
    private int scaffoldId = 1;
    private final Ray ray = new Ray();
    private final Flight flight = new Flight();
    private final List<Box> boxes = new ArrayList<>();

    private int lowestY;
    private int openPlace, openBreak;
    private boolean clearing;
    private BuildView view;
    private final Set<Long> risesTo = new HashSet<>();
    private boolean hoverSeesWork;
    private int clearFails;
    /** Scaffold blocks left standing because nothing could reach them. */
    public int leftScaffolds;
    private int ticks, lastAnchorCheck = -1000;
    private double[] hover;
    private int idleTicks, plannedAt;
    private final FlyPath flyPath = new FlyPath();
    private List<int[]> path;
    private int pathHeight = 3;
    private double[] pathFor;
    private int pathAt, pathStep, stillTicks;
    private double[] stuckFor;
    private double bestDist;
    /** Blocks to break to get out of a closed space. */
    private final Set<Long> digs = new HashSet<>();
    /** Times each open cell came up with no spot that sees it. */
    private final java.util.Map<Long, Integer> blind = new java.util.HashMap<>();
    /** Cells that could not be anchored even with a scaffold. */
    public int unbuildable;
    /** What the last pick looked at, for traces. */
    public String debug = "";
    /** Ticks spent clicking, turning toward a click, and with nothing to click. */
    public int clickTicks, turnTicks, emptyTicks;
    /** Cells whose hover point gave nothing to click, and the tick until which they wait. */
    private final java.util.Map<Long, Integer> skip = new java.util.HashMap<>();

    /** How each palette id has to be clicked into place (stairs, slabs, blocks that face the player). */
    private final Placement[] placements;
    /** Cells to place that no click on a neighbour can give the right state, for now (rebuilt every scan). */
    private final Set<Long> impossible = new HashSet<>();
    /** Cells left unplaced at the last scan because no neighbour can be clicked to give their facing or half. */
    public int unplaceable;

    private boolean skipped(int x, int y, int z) {
        long k = key(x, y, z);
        if (impossible.contains(k)) return true;
        Integer until = skip.get(k);
        return until != null && until > ticks;
    }

    public BuildCore(Blueprint bp, int ox, int oy, int oz, Tuning tun) {
        this.bp = bp;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.tun = tun;
        need = new int[bp.sizeX * bp.sizeY * bp.sizeZ];
        placements = new Placement[bp.palette.size()];
        for (int i = 0; i < placements.length; i++) placements[i] = i == 0 ? Placement.NONE : Placement.of(bp.palette.get(i));
        int[] counts = new int[bp.palette.size()];
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    int id = bp.get(x, y, z);
                    if (id > 0) counts[id]++;
                }
        // scaffolds are the commonest block that any click places the same way
        boolean found = false;
        for (int i = 1; i < counts.length; i++) {
            if (placements[i].constrained()) continue;
            if (!found || counts[i] > counts[scaffoldId]) scaffoldId = i;
            found = true;
        }
        outside = new boolean[(bp.sizeX + 2) * (bp.sizeY + 2) * (bp.sizeZ + 2)];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        mark(queue, -1, -1, -1);
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int d = 0; d < 6; d++) mark(queue, c[0] + Dir.DX[d], c[1] + Dir.DY[d], c[2] + Dir.DZ[d]);
        }
    }

    private int outsideIndex(int x, int y, int z) {
        return ((y + 1) * (bp.sizeZ + 2) + z + 1) * (bp.sizeX + 2) + x + 1;
    }

    private void mark(ArrayDeque<int[]> queue, int x, int y, int z) {
        if (x < -1 || y < -1 || z < -1 || x > bp.sizeX || y > bp.sizeY || z > bp.sizeZ) return;
        if (bp.inside(x, y, z) && bp.get(x, y, z) > Blueprint.AIR) return;
        int i = outsideIndex(x, y, z);
        if (outside[i]) return;
        outside[i] = true;
        queue.add(new int[]{x, y, z});
    }

    /** Whether a world cell is open to the outside of the finished build (anywhere beyond the blueprint is). */
    private boolean outside(int wx, int wy, int wz) {
        int x = wx - ox, y = wy - oy, z = wz - oz;
        if (x < -1 || y < -1 || z < -1 || x > bp.sizeX || y > bp.sizeY || z > bp.sizeZ) return true;
        return outside[outsideIndex(x, y, z)];
    }

    public int openPlaces() {
        return openPlace;
    }

    public int scaffolds() {
        return scaffoldUp.size() + scaffoldPlan.size();
    }

    public double[] hover() {
        return hover;
    }

    private int index(int x, int y, int z) {
        return (y * bp.sizeZ + z) * bp.sizeX + x;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    // ---- what is left ------------------------------------------------------------------------------

    private boolean solid(BlockView w, int x, int y, int z) {
        return (w.flags(x, y, z) & (BlockView.PLACE_AGAINST | BlockView.UNLOADED)) != 0;
    }

    private boolean collides(BlockView w, int x, int y, int z) {
        boxes.clear();
        w.collisionBoxes(x, y, z, boxes);
        return !boxes.isEmpty() || (w.flags(x, y, z) & BlockView.UNLOADED) != 0;
    }

    /** Need of a world cell: in the blueprint, or a scaffold. */
    private int needAt(int wx, int wy, int wz) {
        int x = wx - ox, y = wy - oy, z = wz - oz;
        if (bp.inside(x, y, z)) {
            int n = need[index(x, y, z)];
            if (n != NEED_NONE) return n;
        }
        long k = key(wx, wy, wz);
        if (digs.contains(k)) return NEED_BREAK;
        if (scaffoldPlan.contains(k)) return NEED_PLACE;
        if (scaffoldUp.contains(k) && (clearing || spent(wx, wy, wz))) return NEED_BREAK;
        return NEED_NONE;
    }

    /**
     * A standing scaffold that has done its job: the build cells beside it are in place and no scaffold still to place
     * goes on it. Blocks do not fall, so it can come down right away, while the player is still near.
     */
    private boolean spent(int wx, int wy, int wz) {
        for (int d = 0; d < 6; d++) {
            int nx = wx + Dir.DX[d], ny = wy + Dir.DY[d], nz = wz + Dir.DZ[d];
            int bx = nx - ox, by = ny - oy, bz = nz - oz;
            if (bp.inside(bx, by, bz) && need[index(bx, by, bz)] == NEED_PLACE) return false;
            if (scaffoldPlan.contains(key(nx, ny, nz))) return false;
        }
        return true;
    }

    private int targetIdAt(int wx, int wy, int wz) {
        int id = bp.get(wx - ox, wy - oy, wz - oz);
        return id > 0 ? id : scaffoldId;
    }

    private void scan(BuildView w) {
        // scaffold cells: placed ones move to the standing set, the rest stay planned
        for (int[] c : scaffoldCells) {
            long k = key(c[0], c[1], c[2]);
            boolean there = solid(w, c[0], c[1], c[2]);
            if (scaffoldPlan.contains(k) && there) {
                scaffoldPlan.remove(k);
                scaffoldUp.add(k);
            } else if (scaffoldUp.contains(k) && !there) {
                scaffoldUp.remove(k);
            }
        }
        digs.removeIf(k -> {
            int x = (int) (k >> 38), y = (int) (k << 26 >> 52), z = (int) (k << 38 >> 38);
            return !collides(w, x, y, z);
        });
        openPlace = 0;
        openBreak = 0;
        lowestY = Integer.MAX_VALUE;
        impossible.clear();
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    int t = bp.get(x, y, z);
                    int i = index(x, y, z);
                    if (t == Blueprint.ANY) {
                        need[i] = NEED_NONE;
                        continue;
                    }
                    int wx = x + ox, wy = y + oy, wz = z + oz;
                    int have = w.idAt(wx, wy, wz);
                    int n;
                    if (t == Blueprint.AIR) {
                        long k = key(wx, wy, wz);
                        n = have == Blueprint.AIR || scaffoldUp.contains(k) || scaffoldLeft.contains(k) ? NEED_NONE : NEED_BREAK;
                    } else {
                        n = have == t ? NEED_NONE : have == Blueprint.AIR ? NEED_PLACE : NEED_BREAK;
                    }
                    need[i] = n;
                    if (n == NEED_PLACE && placements[t].constrained() && !clickable(w, t, wx, wy, wz)) {
                        // no neighbour gives it the right facing or half yet; a neighbour placed later may
                        impossible.add(key(wx, wy, wz));
                    } else if (n == NEED_PLACE) {
                        openPlace++;
                        if (wy < lowestY && !skipped(wx, wy, wz) && supported(w, wx, wy, wz)) lowestY = wy;
                    } else if (n == NEED_BREAK) {
                        openBreak++;
                    }
                }
        unplaceable = impossible.size();
        for (int[] c : scaffoldCells) {
            long k = key(c[0], c[1], c[2]);
            if (scaffoldPlan.contains(k)) {
                openPlace++;
                if (c[1] < lowestY && !skipped(c[0], c[1], c[2]) && supported(w, c[0], c[1], c[2])) lowestY = c[1];
            }
        }
        // the scaffolds come down once everything else is in place
        clearing = openPlace == 0;
        if (clearing) openBreak += scaffoldUp.size();
        if (lowestY == Integer.MAX_VALUE) lowestY = oy;
        flowUp(w);
    }

    /** Whether some solid neighbour has a face that can be clicked to give palette id t its facing and half. */
    private boolean clickable(BuildView w, int t, int x, int y, int z) {
        Placement pl = placements[t];
        for (int d = 0; d < 6; d++) {
            int nx = x + Dir.DX[d], ny = y + Dir.DY[d], nz = z + Dir.DZ[d];
            if (!solid(w, nx, ny, nz) || needAt(nx, ny, nz) == NEED_BREAK) continue;
            int face = Dir.opposite(d);
            if (pl.allows(face) && !merges(w, t, nx, ny, nz, face)) return true;
        }
        return false;
    }

    /** Whether clicking this face of a slab, holding the same kind of slab, would make a double slab instead. */
    private boolean merges(BuildView w, int t, int nx, int ny, int nz, int face) {
        if (!placements[t].slab || face != Dir.UP && face != Dir.DOWN) return false;
        int id = w.idAt(nx, ny, nz);
        return id > 0 && Blueprint.blockName(bp.palette.get(id)).equals(Blueprint.blockName(bp.palette.get(t)));
    }

    /** Whether a cell to place may go in now: low enough, and nothing still to place under it. */
    private boolean due(int x, int y, int z) {
        if (y > lowestY + tun.band) return false;
        int r = tun.belowRadius;
        for (int dy = 1; dy <= tun.belowDepth; dy++)
            for (int dz = -r; dz <= r; dz++)
                for (int dx = -r; dx <= r; dx++) {
                    int bx = x + dx, by = y - dy, bz = z + dz;
                    if (needAt(bx, by, bz) == NEED_PLACE && !skipped(bx, by, bz) && fromBelow(bx, by, bz)) return false;
                }
        return true;
    }

    /**
     * Whether a cell to place gets held up without anything above it: it is held up already, or a chain of cells to
     * place reaches it from one that is, never stepping down. One that can only hang from what is above it must not
     * hold up the cells above it (they would wait for each other forever).
     */
    private boolean fromBelow(int x, int y, int z) {
        return risesTo.contains(key(x, y, z));
    }

    /** Fills {@link #risesTo}: open cells reached from held-up open cells moving sideways or up. */
    private void flowUp(BuildView w) {
        risesTo.clear();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        List<int[]> openCells = new ArrayList<>();
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++)
                    if (need[index(x, y, z)] == NEED_PLACE) openCells.add(new int[]{x + ox, y + oy, z + oz});
        for (int[] c : scaffoldCells)
            if (scaffoldPlan.contains(key(c[0], c[1], c[2]))) openCells.add(c);
        for (int[] c : openCells) {
            if (supported(w, c[0], c[1], c[2]) && risesTo.add(key(c[0], c[1], c[2]))) queue.add(c);
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int d = 0; d < 6; d++) {
                if (d == Dir.DOWN) continue;
                int x = c[0] + Dir.DX[d], y = c[1] + Dir.DY[d], z = c[2] + Dir.DZ[d];
                if (needAt(x, y, z) != NEED_PLACE || !risesTo.add(key(x, y, z))) continue;
                queue.add(new int[]{x, y, z});
            }
        }
    }

    private boolean supported(BlockView w, int x, int y, int z) {
        for (int d = 0; d < 6; d++) {
            int nx = x + Dir.DX[d], ny = y + Dir.DY[d], nz = z + Dir.DZ[d];
            if (solid(w, nx, ny, nz) && needAt(nx, ny, nz) != NEED_BREAK) return true;
        }
        return false;
    }

    /**
     * Finds blueprint parts that touch nothing solid, not even through other parts still to place, and plans a
     * scaffold column from the lowest cell of each straight down to something solid.
     */
    private void anchor(BlockView w) {
        int n = need.length;
        boolean[] reached = new boolean[n];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    if (bp.get(x, y, z) <= Blueprint.AIR) continue;
                    int wx = x + ox, wy = y + oy, wz = z + oz;
                    boolean seed = need[index(x, y, z)] == NEED_NONE || touchesAnchor(w, wx, wy, wz);
                    if (seed) {
                        reached[index(x, y, z)] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
        flood(queue, reached);
        unbuildable = 0;
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    int i = index(x, y, z);
                    if (bp.get(x, y, z) <= Blueprint.AIR || reached[i]) continue;
                    // one block outside the build joining this part to an anchored one, when there is such a cell
                    int[] bridge = bridge(w, x, y, z, reached);
                    if (bridge != null) {
                        long k = key(bridge[0], bridge[1], bridge[2]);
                        if (!scaffoldUp.contains(k) && scaffoldPlan.add(k)) scaffoldCells.add(bridge);
                        reached[i] = true;
                        queue.add(new int[]{x, y, z});
                        flood(queue, reached);
                        continue;
                    }
                    // the lowest cell of this floating part is the first met scanning upward
                    List<int[]> column = new ArrayList<>();
                    boolean ok = false;
                    int wx = x + ox, wz = z + oz;
                    for (int wy = y + oy - 1; wy >= y + oy - 96; wy--) {
                        int bx = x, by = wy - oy, bz = z;
                        if (bp.inside(bx, by, bz) && bp.get(bx, by, bz) > Blueprint.AIR) {
                            if (reached[index(bx, by, bz)]) {
                                ok = true;
                                break;
                            }
                            continue; // a part still floating itself: it gets placed on the way
                        }
                        if (solid(w, wx, wy, wz) && needAt(wx, wy, wz) != NEED_BREAK) {
                            ok = true;
                            break;
                        }
                        column.add(new int[]{wx, wy, wz});
                    }
                    if (!ok) {
                        unbuildable++;
                        reached[i] = true;
                        continue;
                    }
                    for (int[] c : column) {
                        long k = key(c[0], c[1], c[2]);
                        if (!scaffoldUp.contains(k) && scaffoldPlan.add(k)) scaffoldCells.add(c);
                    }
                    reached[i] = true;
                    queue.add(new int[]{x, y, z});
                    flood(queue, reached);
                }
    }

    /**
     * The lowest open cell outside the build touching both the floating part holding blueprint cell (x, y, z) and
     * something anchored, or null.
     */
    private int[] bridge(BlockView w, int x, int y, int z, boolean[] reached) {
        // the part: blueprint cells joined to this one that nothing anchors yet
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y, z});
        seen.add(key(x, y, z));
        int[] best = null;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int d = 0; d < 6; d++) {
                int nx = c[0] + Dir.DX[d], ny = c[1] + Dir.DY[d], nz = c[2] + Dir.DZ[d];
                if (bp.inside(nx, ny, nz) && bp.get(nx, ny, nz) > Blueprint.AIR) {
                    if (!reached[index(nx, ny, nz)] && seen.add(key(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
                    continue;
                }
                int wx = nx + ox, wy = ny + oy, wz = nz + oz;
                if (best != null && wy >= best[1]) continue;
                if (!outside(wx, wy, wz) || collides(w, wx, wy, wz)) continue;
                for (int e = 0; e < 6; e++) {
                    int ax = nx + Dir.DX[e], ay = ny + Dir.DY[e], az = nz + Dir.DZ[e];
                    if (bp.inside(ax, ay, az) && bp.get(ax, ay, az) > Blueprint.AIR) {
                        if (!reached[index(ax, ay, az)]) continue;
                    } else if (!touchesSolid(w, ax + ox, ay + oy, az + oz)) continue;
                    best = new int[]{wx, wy, wz};
                    break;
                }
            }
        }
        return best;
    }

    /** A world cell outside the blueprint's blocks that holds something to build on, or a scaffold. */
    private boolean touchesSolid(BlockView w, int wx, int wy, int wz) {
        if (solid(w, wx, wy, wz) && needAt(wx, wy, wz) != NEED_BREAK) return true;
        long k = key(wx, wy, wz);
        return scaffoldPlan.contains(k) || scaffoldUp.contains(k);
    }

    private boolean touchesAnchor(BlockView w, int wx, int wy, int wz) {
        for (int d = 0; d < 6; d++) {
            int nx = wx + Dir.DX[d], ny = wy + Dir.DY[d], nz = wz + Dir.DZ[d];
            int bx = nx - ox, by = ny - oy, bz = nz - oz;
            if (bp.inside(bx, by, bz) && bp.get(bx, by, bz) > Blueprint.AIR) continue; // part of the build: flooded
            if (solid(w, nx, ny, nz) && needAt(nx, ny, nz) != NEED_BREAK) return true;
            if (scaffoldPlan.contains(key(nx, ny, nz)) || scaffoldUp.contains(key(nx, ny, nz))) return true;
        }
        return false;
    }

    private void flood(ArrayDeque<int[]> queue, boolean[] reached) {
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int d = 0; d < 6; d++) {
                int x = c[0] + Dir.DX[d], y = c[1] + Dir.DY[d], z = c[2] + Dir.DZ[d];
                if (!bp.inside(x, y, z) || bp.get(x, y, z) <= Blueprint.AIR) continue;
                int i = index(x, y, z);
                if (reached[i]) continue;
                reached[i] = true;
                queue.add(new int[]{x, y, z});
            }
        }
    }

    // ---- the tick ----------------------------------------------------------------------------------

    public Output tick(Input in, BuildView w) {
        ticks++;
        view = w;
        debug = "";
        Output out = new Output();
        scan(w);
        if (ticks - lastAnchorCheck >= 40) {
            anchor(w);
            lastAnchorCheck = ticks;
            scan(w);
        }
        out.yaw = in.yaw;
        out.pitch = in.pitch;
        if (openPlace == 0 && openBreak == 0 && digs.isEmpty()) {
            out.done = true;
            return out;
        }
        if (!in.flying) out.startFlying = true;

        double ex = in.x, ey = in.y + EYE, ez = in.z;
        Candidate best = pick(in, w, ex, ey, ez);
        if (best != null) {
            float[] r = turn(in.yaw, in.pitch, best.yaw, best.pitch);
            out.yaw = r[0];
            out.pitch = r[1];
            if (best.ready) {
                out.action = best.action;
                out.x = best.x;
                out.y = best.y;
                out.z = best.z;
                out.id = best.action == Output.PLACE ? targetIdAt(best.x, best.y, best.z) : -1;
                out.againstX = best.hit.x;
                out.againstY = best.hit.y;
                out.againstZ = best.hit.z;
                out.face = best.hit.face;
                out.hitX = best.hit.hx;
                out.hitY = best.hit.hy;
                out.hitZ = best.hit.hz;
            }
        }
        idleTicks = out.action == Output.NONE ? idleTicks + 1 : 0;
        if (out.action != Output.NONE) clickTicks++;
        else if (best != null) turnTicks++;
        else emptyTicks++;

        if (idleTicks >= tun.idleReplan && hover != null && sq(in.x - hover[0]) + sq(in.y - hover[1]) + sq(in.z - hover[2]) < 1) {
            // got there and still nothing to click: leave that work for later
            skip.put(key(Ray.floor(hover[3]), Ray.floor(hover[4]), Ray.floor(hover[5])), ticks + tun.skipTicks);
        }
        if (hover == null || idleTicks >= tun.idleReplan || ticks - plannedAt >= tun.replanEvery
                || ticks - plannedAt >= 5 && workNear(hover) < Math.min(tun.replanAt, openPlace + openBreak)) {
            hoverSeesWork = false;
            double[] h = chooseHover(in, w);
            // a spot next to the current one is no reason to turn round: keep flying where we were going
            boolean close = h != null && hover != null && sq(h[0] - hover[0]) + sq(h[1] - hover[1]) + sq(h[2] - hover[2]) <= 2.0
                    && freeSpot(w, hover[0], hover[1], hover[2], false) && idleTicks < tun.idleReplan;
            if (h != null && !close) hover = h;
            // only scaffolds nobody can get at are left (shut inside the build): leave them standing
            clearFails = clearing && openBreak == scaffoldUp.size() && !hoverSeesWork ? clearFails + 1 : 0;
            if (clearFails >= tun.giveUpScaffolds) {
                leftScaffolds += scaffoldUp.size();
                scaffoldLeft.addAll(scaffoldUp);
                scaffoldUp.clear();
            }
            plannedAt = ticks;
            idleTicks = 0;
        }
        if (best == null && hover != null) {
            // nothing to click: look where the work is
            float[] r = turn(in.yaw, in.pitch, Ray.yawTo(hover[3] - ex, hover[5] - ez),
                    Ray.pitchTo(hover[3] - ex, hover[4] - ey, hover[5] - ez));
            out.yaw = r[0];
            out.pitch = r[1];
        }
        if (hover != null) {
            // no headway toward the hover point for a while: stuck on something
            double dist = Math.sqrt(sq(in.x - hover[0]) + sq(in.y - hover[1]) + sq(in.z - hover[2]));
            if (dist <= 1 || stuckFor == null || !sameSpot(stuckFor, hover) || dist < bestDist - 0.3) {
                stuckFor = hover;
                bestDist = dist;
                stillTicks = 0;
            } else {
                stillTicks++;
            }
            if (stillTicks >= tun.stuckTicks) {
                dig(in, w, hover);
                debug += " stuck digs " + digs.size();
                path = null;
                stillTicks = 0;
            }
            double[] t = route(in, w, hover);
            debug += String.format(" tgt %.2f %.2f %.2f gap %.2f path %s", t[0], t[1], t[2], floorGap(w, in.x, in.y, in.z), path == null ? "-" : pathStep + "/" + path.size());
            flight.steer(in.x, in.y, in.z, in.motionX, in.motionY, in.motionZ, out.yaw, in.sprinting,
                    t[0], t[1], t[2], tun.gain, tun.maxSpeed, tun.sprint, floorGap(w, in.x, in.y, in.z), out.keys);
            Flight.Keys k = out.keys;
            debug += " keys " + (k.forward ? "W" : "") + (k.back ? "S" : "") + (k.left ? "A" : "") + (k.right ? "D" : "") + (k.up ? "^" : "") + (k.down ? "v" : "");
        }
        return out;
    }

    // ---- clicking ----------------------------------------------------------------------------------

    private static final class Candidate {
        int action, x, y, z;
        /** The block clicked and its face; the face's centre. */
        int bx, by, bz, face;
        double px, py, pz;
        float yaw, pitch;
        double score;
        boolean ready;
        Ray.Hit hit;
        /** What the click must give: facing from the heading, half from the face and the height hit. */
        Placement pl = Placement.NONE;
    }

    /** Where on a face to aim, as fractions across its two other axes: the centre first, then toward the corners. */
    private static final double[][] AIM = {{0.5, 0.5}, {0.2, 0.2}, {0.8, 0.2}, {0.2, 0.8}, {0.8, 0.8}, {0.5, 0.15},
            {0.5, 0.85}, {0.15, 0.5}, {0.85, 0.5}};

    /** Every face that could be clicked from the eye for the open cells in reach, without checking line of sight. */
    private List<Candidate> collect(BuildView w, double ex, double ey, double ez, Box body, int held) {
        int r = (int) Math.ceil(tun.reach) + 1;
        int cx = Ray.floor(ex), cy = Ray.floor(ey), cz = Ray.floor(ez);
        List<Candidate> list = new ArrayList<>();
        // a gap round the body at body height, so the player never walls itself in
        double c = tun.clearance;
        Box guard = new Box(body.minX - c, body.minY, body.minZ - c, body.maxX + c, body.maxY, body.maxZ + c);
        for (int y = cy - r; y <= cy + r; y++)
            for (int z = cz - r; z <= cz + r; z++)
                for (int x = cx - r; x <= cx + r; x++) {
                    int n = needAt(x, y, z);
                    if (n == NEED_NONE) continue;
                    if (n == NEED_PLACE) {
                        if (!due(x, y, z)) continue;
                        if (new Box(x, y, z, x + 1, y + 1, z + 1).intersects(guard)) continue;
                        int t = targetIdAt(x, y, z);
                        for (int d = 0; d < 6; d++) {
                            int nx = x + Dir.DX[d], ny = y + Dir.DY[d], nz = z + Dir.DZ[d];
                            if (!solid(w, nx, ny, nz) || needAt(nx, ny, nz) == NEED_BREAK) continue;
                            if (!placements[t].allows(Dir.opposite(d)) || merges(w, t, nx, ny, nz, Dir.opposite(d))) continue;
                            // the face of the neighbour that touches the cell, seen from the cell's side
                            add(list, Output.PLACE, x, y, z, nx, ny, nz, Dir.opposite(d), ex, ey, ez, held);
                        }
                    } else {
                        for (int f = 0; f < 6; f++) {
                            if (solid(w, x + Dir.DX[f], y + Dir.DY[f], z + Dir.DZ[f])) continue;
                            add(list, Output.BREAK, x, y, z, x, y, z, f, ex, ey, ez, held);
                        }
                    }
                }
        return list;
    }

    private Candidate pick(Input in, BuildView w, double ex, double ey, double ez) {
        List<Candidate> list = collect(w, ex, ey, ez, Box.player(in.x, in.y, in.z), in.held);
        for (Candidate c : list) c.score += angle(in.yaw, in.pitch, c.yaw, c.pitch) * tun.turnCost;
        list.sort((a, b) -> Double.compare(a.score, b.score));
        int tries = 0;
        for (Candidate c : list) {
            if (angle(in.yaw, in.pitch, c.yaw, c.pitch) > tun.maxTurn + 25) continue;
            if (++tries > 40) break;
            if (aim(w, c, in.yaw, in.pitch, ex, ey, ez, true)) return c;
        }
        // nothing clickable this tick: turn toward the best one that can be seen
        tries = 0;
        for (Candidate c : list) {
            if (++tries > 12) break;
            if (aim(w, c, in.yaw, in.pitch, ex, ey, ez, false)) {
                c.ready = false;
                return c;
            }
        }
        return null;
    }

    /**
     * Finds a point on the candidate's face the ray really lands on. With {@code turnLimited} the rotation is the one
     * this tick can reach (capped, on the GCD grid) and the candidate becomes ready; otherwise any rotation counts.
     */
    private boolean aim(BlockView w, Candidate c, float yaw, float pitch, double ex, double ey, double ez, boolean turnLimited) {
        int f = c.face;
        for (double[] a : AIM) {
            double px = c.bx + 0.5 + Dir.DX[f] * 0.5, py = c.by + 0.5 + Dir.DY[f] * 0.5, pz = c.bz + 0.5 + Dir.DZ[f] * 0.5;
            if (Dir.DX[f] != 0) {
                py = c.by + a[0];
                pz = c.bz + a[1];
            } else if (Dir.DY[f] != 0) {
                px = c.bx + a[0];
                pz = c.bz + a[1];
            } else {
                px = c.bx + a[0];
                py = c.by + a[1];
            }
            double dx = px - ex, dy = py - ey, dz = pz - ez;
            if (dx * dx + dy * dy + dz * dz > tun.reach * tun.reach) continue;
            float wy = Ray.yawTo(dx, dz), wp = Ray.pitchTo(dx, dy, dz);
            // a stair or facing block takes its facing from the heading the server has when the click lands: the
            // one before this tick's turn, so it must be right already
            if (c.pl.look >= 0 && (!c.pl.heading(wy, tun.facingMargin) || turnLimited && !c.pl.heading(yaw, tun.facingMargin))) continue;
            float ry = wy, rp = wp;
            if (turnLimited) {
                if (angle(yaw, pitch, wy, wp) > tun.maxTurn) continue;
                float[] rot = turn(yaw, pitch, wy, wp);
                ry = rot[0];
                rp = rot[1];
            }
            Ray.Hit h = ray.trace(w, ex, ey, ez, ry, rp, tun.reach);
            if (h == null || h.x != c.bx || h.y != c.by || h.z != c.bz || h.face != f) continue;
            // on a side face the half comes from the height hit
            if (c.pl.half != 0 && Dir.DY[f] == 0 && !c.pl.hitHeight(h.hy - c.by, tun.halfMargin)) continue;
            c.yaw = wy;
            c.pitch = wp;
            c.hit = h;
            c.ready = turnLimited;
            return true;
        }
        return false;
    }

    /** Whether a point (dx, dy, dz) from a face's centre is in front of the face. */
    private static boolean inFront(int face, double dx, double dy, double dz) {
        return dx * Dir.DX[face] + dy * Dir.DY[face] + dz * Dir.DZ[face] > 0.01;
    }

    private void add(List<Candidate> list, int action, int x, int y, int z, int bx, int by, int bz, int face,
                     double ex, double ey, double ez, int held) {
        double px = bx + 0.5 + Dir.DX[face] * 0.5, py = by + 0.5 + Dir.DY[face] * 0.5, pz = bz + 0.5 + Dir.DZ[face] * 0.5;
        double dx = px - ex, dy = py - ey, dz = pz - ez;
        if (!inFront(face, -dx, -dy, -dz)) return;
        // the face's nearest point must be in reach
        if (dx * dx + dy * dy + dz * dz > (tun.reach + 0.7) * (tun.reach + 0.7)) return;
        Candidate c = new Candidate();
        c.action = action;
        c.x = x;
        c.y = y;
        c.z = z;
        c.bx = bx;
        c.by = by;
        c.bz = bz;
        c.face = face;
        c.px = px;
        c.py = py;
        c.pz = pz;
        c.yaw = Ray.yawTo(dx, dz);
        c.pitch = Ray.pitchTo(dx, dy, dz);
        if (action == Output.PLACE) {
            c.pl = placements[targetIdAt(x, y, z)];
            c.score = (y - lowestY) * tun.layerCost + (targetIdAt(x, y, z) != held ? tun.switchCost : 0);
        } else {
            // clear from the top down, before placing
            c.score = -1000 - y * tun.layerCost;
        }
        list.add(c);
    }

    /** Open cells that could be clicked from feet (x, y, z), each counted once. */
    private int visibleWork(BuildView w, double x, double y, double z) {
        double ex = x, ey = y + EYE, ez = z;
        List<Candidate> list = collect(w, ex, ey, ez, Box.player(x, y, z), -1);
        Set<Long> seen = new HashSet<>();
        int n = 0;
        for (Candidate c : list) {
            long k = key(c.x, c.y, c.z);
            if (seen.contains(k)) continue;
            if (aim(w, c, 0, 0, ex, ey, ez, false)) {
                seen.add(k);
                n++;
            }
        }
        return n;
    }

    private static float wrap(float a) {
        a %= 360.0F;
        if (a >= 180.0F) a -= 360.0F;
        if (a < -180.0F) a += 360.0F;
        return a;
    }

    private static double angle(float y0, float p0, float y1, float p1) {
        double dy = wrap(y1 - y0), dp = p1 - p0;
        return Math.sqrt(dy * dy + dp * dp);
    }

    /** From (yaw, pitch) toward the wanted rotation, at most maxTurn, on the GCD grid. */
    private float[] turn(float yaw, float pitch, float wantYaw, float wantPitch) {
        double dy = wrap(wantYaw - yaw), dp = wantPitch - pitch;
        double len = Math.sqrt(dy * dy + dp * dp);
        if (len > tun.maxTurn) {
            dy *= tun.maxTurn / len;
            dp *= tun.maxTurn / len;
        }
        if (tun.gcd > 0) {
            dy = Math.round(dy / tun.gcd) * tun.gcd;
            dp = Math.round(dp / tun.gcd) * tun.gcd;
        }
        float p = (float) Math.max(-90, Math.min(90, pitch + dp));
        return new float[]{(float) (yaw + dy), p};
    }

    // ---- where to fly ------------------------------------------------------------------------------

    /** Open cells within reach of a hover point (x, y, z = feet). */
    private int workNear(double[] h) {
        double ex = h[0], ey = h[1] + EYE, ez = h[2];
        double rr = (tun.reach - 0.4) * (tun.reach - 0.4);
        int r = (int) Math.ceil(tun.reach);
        int n = 0;
        for (int y = Ray.floor(ey) - r; y <= Ray.floor(ey) + r; y++) {
            for (int z = Ray.floor(ez) - r; z <= Ray.floor(ez) + r; z++)
                for (int x = Ray.floor(ex) - r; x <= Ray.floor(ex) + r; x++) {
                    int need = needAt(x, y, z);
                    if (need == NEED_NONE || need == NEED_PLACE && !due(x, y, z)) continue;
                    double dx = x + 0.5 - ex, dy = y + 0.5 - ey, dz = z + 0.5 - ez;
                    if (dx * dx + dy * dy + dz * dz <= rr) n++;
                }
        }
        return n;
    }

    /** {feet x, y, z, focus x, y, z}: a spot near the closest open work with the most open cells in reach. */
    private double[] chooseHover(Input in, BuildView w) {
        // the open cell to work toward: the nearest on the lowest layers, or anything left
        List<int[]> open = new ArrayList<>();
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    if (need[index(x, y, z)] != NEED_NONE) open.add(new int[]{x + ox, y + oy, z + oz});
                }
        open.addAll(scaffoldCells);
        int[] seed = null;
        double seedCost = Double.MAX_VALUE;
        for (int[] c : open) {
            int n = needAt(c[0], c[1], c[2]);
            if (n == NEED_NONE) continue;
            if (skipped(c[0], c[1], c[2])) continue;
            if (n == NEED_PLACE && (!due(c[0], c[1], c[2]) || !supported(w, c[0], c[1], c[2]))) continue;
            double cost = Math.sqrt(sq(c[0] + 0.5 - in.x) + sq(c[2] + 0.5 - in.z)) + Math.abs(c[1] - in.y) * 0.5
                    + (n == NEED_PLACE ? (c[1] - lowestY) * tun.seedLayerCost : 0);
            if (cost < seedCost) {
                seedCost = cost;
                seed = c;
            }
        }
        if (seed == null && !skip.isEmpty()) {
            // everything left was put off: try it all again
            skip.clear();
            scan(w);
            return chooseHover(in, w);
        }
        if (seed == null) {
            int notDue = 0, unsupported = 0, skippedN = 0, total = 0;
            for (int[] c : open) {
                if (needAt(c[0], c[1], c[2]) != NEED_PLACE) continue;
                total++;
                if (skipped(c[0], c[1], c[2])) skippedN++;
                else if (!due(c[0], c[1], c[2])) notDue++;
                else if (!supported(w, c[0], c[1], c[2])) unsupported++;
            }
            debug = "noseed open " + total + " skipped " + skippedN + " notDue " + notDue + " unsupported " + unsupported
                    + " lowestY " + lowestY + " plan " + scaffoldPlan.size();
            return null;
        }

        // spots with open sky first; walled in under something built, any free spot
        double[] pick = null;
        for (int pass = 0; pass < 2 && pick == null; pass++) {
            boolean sky = tun.skyAccess && pass == 0;
            if (pass == 1 && !tun.skyAccess) break;
            List<double[]> spots = spots(in, w, seed, sky);
            pick = bestSpot(w, spots, seed);
        }
        hoverSeesWork = pick != null;
        if (pick == null && needAt(seed[0], seed[1], seed[2]) == NEED_PLACE
                && blind.merge(key(seed[0], seed[1], seed[2]), 1, Integer::sum) >= tun.blindTries) {
            // walled in by what was built since: open a way to it from outside, put back afterwards
            blind.remove(key(seed[0], seed[1], seed[2]));
            shaft(w, seed[0], seed[1], seed[2]);
        }
        if (pick == null) {
            List<double[]> spots = spots(in, w, seed, tun.skyAccess);
            if (spots.isEmpty()) spots = spots(in, w, seed, false);
            if (!spots.isEmpty()) {
                double[] s = spots.get(0);
                pick = new double[]{s[0], s[1], s[2], seed[0] + 0.5, seed[1] + 0.5, seed[2] + 0.5};
            }
        }
        return pick;
    }

    /** Free spots within reach of the seed, best rough score (work in reach less the trip) first. */
    private List<double[]> spots(Input in, BuildView w, int[] seed, boolean sky) {
        List<double[]> spots = new ArrayList<>();
        for (int fy = -1; fy <= 4; fy++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dx = -3; dx <= 3; dx++) {
                    double hx = seed[0] + 0.5 + dx, hy = seed[1] + fy + tun.hoverGap, hz = seed[2] + 0.5 + dz;
                    double sx = seed[0] + 0.5 - hx, sy = seed[1] + 0.5 - (hy + EYE), sz = seed[2] + 0.5 - hz;
                    if (sx * sx + sy * sy + sz * sz > (tun.reach - 0.4) * (tun.reach - 0.4)) continue;
                    if (!freeSpot(w, hx, hy, hz, sky)) continue;
                    double travel = Math.sqrt(sq(hx - in.x) + sq(hy - in.y) + sq(hz - in.z));
                    spots.add(new double[]{hx, hy, hz, workNear(new double[]{hx, hy, hz}) - tun.travelCost * travel, travel});
                }
        spots.sort((a, b) -> Double.compare(b[3], a[3]));
        return spots;
    }

    /** The spot seeing the most work less the trip: the best few by rough score, or further down until one sees any. */
    private double[] bestSpot(BuildView w, List<double[]> spots, int[] seed) {
        double best = -Double.MAX_VALUE;
        double[] pick = null;
        for (int i = 0; i < Math.min(tun.hoverChecks * 8, spots.size()); i++) {
            if (i >= tun.hoverChecks && pick != null) break;
            double[] s = spots.get(i);
            int seen = visibleWork(w, s[0], s[1], s[2]);
            if (seen == 0) continue;
            double score = seen - tun.travelCost * s[4];
            if (score > best) {
                best = score;
                pick = new double[]{s[0], s[1], s[2], seed[0] + 0.5, seed[1] + 0.5, seed[2] + 0.5};
            }
        }
        return pick;
    }

    /**
     * The player fits at feet (x, y, z): no blocks in the way, none of the cells to be placed soon (they would be
     * blocked), not resting on anything (touching the ground ends the flight), and with sky access nothing built or
     * soon to be built overhead, so the player is out of a room before its ceiling goes on.
     */
    private boolean freeSpot(BlockView w, double x, double y, double z, boolean sky) {
        int x0 = Ray.floor(x - HALF_W), x1 = Ray.floor(x + HALF_W), z0 = Ray.floor(z - HALF_W), z1 = Ray.floor(z + HALF_W);
        int y0 = Ray.floor(y), y1 = Ray.floor(y + HEIGHT);
        int top = sky ? Math.max(y1, oy + bp.sizeY) : y1;
        int soon = lowestY + 1, overhead = lowestY + tun.skyMargin;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                for (int cy = y0 - 1; cy <= top; cy++) {
                    if (cy == y0 - 1) {
                        if (collides(w, cx, cy, cz) && y - (cy + 1) < tun.hoverGap - 0.05) return false;
                        continue;
                    }
                    if (collides(w, cx, cy, cz)) return false;
                    boolean open = needAt(cx, cy, cz) == NEED_PLACE;
                    if (open && (cy <= (cy <= y1 ? soon : overhead) || cy <= y1 && due(cx, cy, cz))) return false;
                }
        return true;
    }

    /**
     * Where to fly this tick: straight to the hover point when nothing is in the way, else along a {@link FlyPath}
     * (to the farthest of the next few cells in plain sight). With no way there at all, the block above the head (or
     * the first one in the way) is broken to get out; it is placed again later.
     */
    private double[] route(Input in, BlockView w, double[] h) {
        if (clearPath(w, in.x, in.y, in.z, h[0], h[1], h[2])) {
            path = null;
            return new double[]{h[0], h[1], h[2]};
        }
        if (path == null || pathFor == null || !sameSpot(pathFor, h) || ticks - pathAt >= 20) {
            int sx = Ray.floor(in.x), sy = Ray.floor(in.y), sz = Ray.floor(in.z);
            pathHeight = 3;
            path = flyPath.find(w, sx, sy, sz, Ray.floor(h[0]), Ray.floor(h[1]), Ray.floor(h[2]), Integer.MIN_VALUE, tun.pathNodes, 3);
            if (path == null) {
                pathHeight = 2;
                path = flyPath.find(w, sx, sy, sz, Ray.floor(h[0]), Ray.floor(h[1]), Ray.floor(h[2]), Integer.MIN_VALUE, tun.pathNodes, 2);
            }
            pathFor = h;
            pathAt = ticks;
            pathStep = 0;
            if (path == null) {
                int before = digs.size();
                dig(in, w, h);
                debug += " nopath digs " + digs.size();
                if (digs.size() == before) {
                    // nothing to break our way out: the spot is shut away from us, not us from it
                    int fx = Ray.floor(h[3]), fy = Ray.floor(h[4]), fz = Ray.floor(h[5]);
                    skip.put(key(fx, fy, fz), ticks + tun.skipTicks);
                    if (needAt(fx, fy, fz) == NEED_PLACE && blind.merge(key(fx, fy, fz), 1, Integer::sum) >= tun.blindTries) {
                        blind.remove(key(fx, fy, fz));
                        shaft(view, fx, fy, fz);
                    }
                    hover = null;
                }
                return new double[]{in.x, in.y, in.z};
            }
        }
        // drop the cells already passed, then aim at the farthest of the next few in plain sight
        int near = pathStep;
        double nearDist = Double.MAX_VALUE;
        for (int i = pathStep; i < Math.min(path.size(), pathStep + 6); i++) {
            int[] c = path.get(i);
            double d = sq(c[0] + 0.5 - in.x) + sq(c[1] + FlyPath.feet(pathHeight) - in.y) + sq(c[2] + 0.5 - in.z);
            if (d < nearDist) {
                nearDist = d;
                near = i;
            }
        }
        pathStep = near;
        if (pathStep >= path.size() - 1) return new double[]{h[0], h[1], h[2]};
        // none in plain sight: back to the middle of the cell the player is in (steering knows no walls)
        int[] aim = path.get(pathStep);
        for (int i = Math.min(path.size() - 1, pathStep + 6); i > pathStep; i--) {
            int[] c = path.get(i);
            if (clearPath(w, in.x, in.y, in.z, c[0] + 0.5, c[1] + FlyPath.feet(pathHeight), c[2] + 0.5)) {
                aim = c;
                break;
            }
        }
        if (aim == path.get(path.size() - 1)) return new double[]{h[0], h[1], h[2]};
        return new double[]{aim[0] + 0.5, aim[1] + FlyPath.feet(pathHeight), aim[2] + 0.5};
    }

    /**
     * The shortest straight line of built blocks (at most two) out to open air beyond the build, from the open cell or
     * from the closed pocket of air around it, going up first on ties, marked to be broken.
     */
    private void shaft(BuildView w, int x, int y, int z) {
        int[] order = {Dir.UP, Dir.NORTH, Dir.SOUTH, Dir.WEST, Dir.EAST, Dir.DOWN};
        List<Long> best = null;
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> pocket = new HashSet<>();
        queue.add(new int[]{x, y, z});
        pocket.add(key(x, y, z));
        while (!queue.isEmpty() && pocket.size() < 2048) {
            int[] p = queue.poll();
            for (int d : order) {
                List<Long> cells = new ArrayList<>();
                for (int i = 1; i <= 3; i++) {
                    int cx = p[0] + Dir.DX[d] * i, cy = p[1] + Dir.DY[d] * i, cz = p[2] + Dir.DZ[d] * i;
                    if (!collides(w, cx, cy, cz)) {
                        if (i == 1) {
                            if (!outside(cx, cy, cz) && pocket.add(key(cx, cy, cz))) queue.add(new int[]{cx, cy, cz});
                        } else if (outside(cx, cy, cz) && (best == null || cells.size() < best.size())) {
                            best = cells;
                        }
                        break;
                    }
                    long k = key(cx, cy, cz);
                    boolean ours = bp.inside(cx - ox, cy - oy, cz - oz) && bp.get(cx - ox, cy - oy, cz - oz) > Blueprint.AIR
                            || scaffoldUp.contains(k);
                    if (!ours) break;
                    cells.add(k);
                }
            }
            if (best != null && best.size() == 1) break;
        }
        if (best != null) digs.addAll(best);
    }

    /**
     * Boxed in: break a block next to the body that the eye can see, the one most toward the hover point (above the
     * head counts as toward it when the hover point is higher). It is placed again later, from outside.
     */
    private void dig(Input in, BlockView w, double[] h) {
        double ex = in.x, ey = in.y + EYE, ez = in.z;
        double hx = h[0] - in.x, hy = h[1] - in.y, hz = h[2] - in.z;
        double hl = Math.sqrt(hx * hx + hy * hy + hz * hz) + 1e-9;
        int fx = Ray.floor(in.x), fz = Ray.floor(in.z), y0 = Ray.floor(in.y), y1 = Ray.floor(in.y + HEIGHT + 0.05);
        long best = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int x = fx - 1; x <= fx + 1; x++)
            for (int z = fz - 1; z <= fz + 1; z++)
                for (int y = y0 - 1; y <= y1; y++) {
                    if (!collides(w, x, y, z)) continue;
                    double cx = x + 0.5 - in.x, cy = y + 0.5 - (in.y + 0.9), cz = z + 0.5 - in.z;
                    double cl = Math.sqrt(cx * cx + cy * cy + cz * cz) + 1e-9;
                    double score = (cx * hx + cy * hy + cz * hz) / (cl * hl);
                    if (score <= bestScore) continue;
                    // the eye must see one of its faces
                    boolean seen = false;
                    for (int f = 0; f < 6 && !seen; f++) {
                        double px = x + 0.5 + Dir.DX[f] * 0.5, py = y + 0.5 + Dir.DY[f] * 0.5, pz = z + 0.5 + Dir.DZ[f] * 0.5;
                        if (!inFront(f, ex - px, ey - py, ez - pz)) continue;
                        double dx = px - ex, dy = py - ey, dz = pz - ez;
                        Ray.Hit hit = ray.trace(w, ex, ey, ez, Ray.yawTo(dx, dz), Ray.pitchTo(dx, dy, dz), tun.reach);
                        seen = hit != null && hit.x == x && hit.y == y && hit.z == z;
                    }
                    if (!seen) continue;
                    bestScore = score;
                    best = key(x, y, z);
                }
        if (bestScore > -Double.MAX_VALUE) digs.add(best);
    }

    /** Feet height above the highest block top under the player (within two blocks; 2 if none). */
    private double floorGap(BlockView w, double x, double y, double z) {
        double gap = 2;
        for (int cx = Ray.floor(x - HALF_W); cx <= Ray.floor(x + HALF_W); cx++)
            for (int cz = Ray.floor(z - HALF_W); cz <= Ray.floor(z + HALF_W); cz++)
                for (int cy = Ray.floor(y) - 2; cy <= Ray.floor(y); cy++) {
                    boxes.clear();
                    w.collisionBoxes(cx, cy, cz, boxes);
                    for (Box b : boxes) {
                        if (b.maxY <= y + 1e-7 && b.maxX > x - HALF_W && b.minX < x + HALF_W && b.maxZ > z - HALF_W && b.minZ < z + HALF_W)
                            gap = Math.min(gap, y - b.maxY);
                    }
                }
        return gap;
    }

    private boolean clearPath(BlockView w, double x0, double y0, double z0, double x1, double y1, double z1) {
        double len = Math.sqrt(sq(x1 - x0) + sq(y1 - y0) + sq(z1 - z0));
        int steps = Math.max(1, (int) Math.ceil(len / 0.3));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t, z = z0 + (z1 - z0) * t;
            for (int cx = Ray.floor(x - HALF_W); cx <= Ray.floor(x + HALF_W); cx++)
                for (int cz = Ray.floor(z - HALF_W); cz <= Ray.floor(z + HALF_W); cz++)
                    for (int cy = Ray.floor(y); cy <= Ray.floor(y + HEIGHT); cy++)
                        if (collides(w, cx, cy, cz)) return false;
        }
        return true;
    }

    private static boolean sameSpot(double[] a, double[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    private static double sq(double d) {
        return d * d;
    }
}
