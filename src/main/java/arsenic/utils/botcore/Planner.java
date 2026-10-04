package arsenic.utils.botcore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A* over the places a player can stand. Moves: walk (cardinal/diagonal), step or jump up a block,
 * walk off any height (no fall damage), sprint-jump gaps, climb ladders/vines, pillar up and bridge
 * across with the bot's blocks, and mine the bot's own blocks out of the way.
 */
public final class Planner {

    /** What the player can do right now. */
    public static final class Caps {
        /** Movement speed relative to normal (speed effect, pants attribute). */
        public double speed = 1;
        /** Blocks available to build with. */
        public int blocks = 0;
        /** A pickaxe is at hand to mine the bot's own blocks. */
        public boolean pickaxe = false;
        /** Sprint-jumping along open runs (makes them cheaper). */
        public boolean hop = false;
    }

    public static final class Result {
        public final List<Step> steps;
        /** Whether the last step is in the goal (otherwise it is the closest the search got). */
        public final boolean complete;
        public final int nodes;
        public final long millis;

        Result(List<Step> steps, boolean complete, int nodes, long millis) {
            this.steps = steps;
            this.complete = complete;
            this.nodes = nodes;
            this.millis = millis;
        }
    }

    /** Polled while searching; return true to abandon the search. */
    public interface Cancel {
        boolean cancelled();
    }

    private static final double SQRT2 = Math.sqrt(2);
    private static final double AVOID_PENALTY = 40;

    private static final class Node {
        final int x, y, z;
        final double feet;
        double g = Double.MAX_VALUE;
        double h;
        double f;
        Node parent;
        Step step;
        int blocksUsed;
        /** Blocks this branch of the plan has placed so far (keys), so later moves can stand on them. */
        long[] placed;
        int heap = -1;
        boolean closed;

        Node(int x, int y, int z, double feet) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.feet = feet;
        }
    }

    private final Terrain t;
    private final Goal goal;
    private final Tuning tun;
    private final Caps caps;
    private final Set<Long> avoid;
    private final LongMap<Node> nodes = new LongMap<>(4096);
    private Node[] heap = new Node[1024];
    private int heapSize = 0;

    private Planner(BlockView view, Goal goal, Tuning tun, Caps caps, Set<Long> avoid) {
        this.t = new Terrain(view, caps.pickaxe);
        this.goal = goal;
        this.tun = tun;
        this.caps = caps;
        this.avoid = avoid;
    }

    /**
     * Search from the player's feet block (sx, sy, sz) at height {@code feet}. Returns a path to the
     * goal, or to the closest point reached if time runs out, or null if no move is possible at all.
     */
    public static Result plan(BlockView view, int sx, int sy, int sz, double feet, Goal goal, Tuning tun, Caps caps,
                              Set<Long> avoid, Cancel cancel) {
        return plan(view, sx, sy, sz, feet, goal, tun, caps, avoid, cancel, null);
    }

    /** As above; {@code preview}, if given, now and then gets the best route found so far (for drawing). */
    public static Result plan(BlockView view, int sx, int sy, int sz, double feet, Goal goal, Tuning tun, Caps caps,
                              Set<Long> avoid, Cancel cancel, java.util.function.Consumer<List<Step>> preview) {
        Planner p = new Planner(view, goal, tun, caps, avoid);
        p.preview = preview;
        return p.run(sx, sy, sz, feet, cancel);
    }

    private java.util.function.Consumer<List<Step>> preview;

    /** Speed the heuristic should assume: faster when sprint-jumping, to match the run cost. */
    private double effSpeed() {
        return caps.speed / (caps.hop ? tun.hopRunFactor : 1);
    }

    private Result run(int sx, int sy, int sz, double feet, Cancel cancel) {
        long start = System.currentTimeMillis();
        long deadline = start + tun.searchMillis;
        Node s = node(sx, sy, sz, feet);
        s.g = 0;
        s.h = goal.heuristic(sx, sy, sz, feet, tun, effSpeed());
        s.f = tun.heuristicWeight * s.h;
        s.step = new Step(sx, sy, sz, feet, Step.START, 0);
        push(s);
        Node best = s;
        int expanded = 0;
        while (heapSize > 0) {
            if ((expanded & 31) == 0 && (System.currentTimeMillis() > deadline || (cancel != null && cancel.cancelled()))) {
                break;
            }
            if (expanded >= tun.maxNodes) {
                break;
            }
            Node n = pop();
            n.closed = true;
            expanded++;
            t.setVirtual(n.placed);
            if (goal.reached(t, n.x, n.y, n.z, n.feet)) {
                t.setVirtual(null);
                return new Result(build(n), true, expanded, System.currentTimeMillis() - start);
            }
            if (n.h < best.h || (n.h == best.h && n.g < best.g)) {
                best = n;
            }
            if (preview != null && (expanded & 2047) == 0) {
                preview.accept(build(best));
            }
            expand(n);
            t.setVirtual(null);
        }
        if (cancel != null && cancel.cancelled()) {
            return null;
        }
        if (best == s) {
            return new Result(build(s), false, expanded, System.currentTimeMillis() - start);
        }
        return new Result(build(best), false, expanded, System.currentTimeMillis() - start);
    }

    private List<Step> build(Node n) {
        List<Step> out = new ArrayList<>();
        for (Node c = n; c != null; c = c.parent) {
            out.add(c.step);
        }
        Collections.reverse(out);
        return out;
    }

    private Node node(int x, int y, int z, double feet) {
        long k = Terrain.key(x, y, z);
        Node n = nodes.get(k);
        if (n == null) {
            n = new Node(x, y, z, feet);
            nodes.put(k, n);
        }
        return n;
    }

    private void offer(Node from, int x, int y, int z, double feet, int type, double cost, int px, int py, int pz) {
        if (avoid != null && avoid.contains(Terrain.key(x, y, z))) {
            cost += AVOID_PENALTY;
        }
        Node n = node(x, y, z, feet);
        if (n.closed) {
            return;
        }
        double g = from.g + cost;
        if (g >= n.g) {
            return;
        }
        n.g = g;
        n.parent = from;
        n.blocksUsed = from.blocksUsed + (px != Integer.MIN_VALUE ? 1 : 0);
        if (px != Integer.MIN_VALUE) {
            long[] prev = from.placed;
            int len = prev == null ? 0 : Math.min(prev.length, 63);
            long[] next = new long[len + 1];
            if (len > 0) System.arraycopy(prev, prev.length - len, next, 0, len);
            next[len] = Terrain.key(px, py, pz);
            n.placed = next;
        } else {
            n.placed = from.placed;
        }
        n.step = new Step(x, y, z, feet, type, px, py, pz, cost);
        n.h = goal.heuristic(x, y, z, feet, tun, effSpeed());
        n.f = g + tun.heuristicWeight * n.h;
        if (n.heap < 0) {
            push(n);
        } else {
            siftUp(n.heap);
        }
    }

    private void offer(Node from, int x, int y, int z, double feet, int type, double cost) {
        offer(from, x, y, z, feet, type, cost, Integer.MIN_VALUE, 0, 0);
    }

    // ---- moves -----------------------------------------------------------------------------------

    private static final int[][] CARD = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final int[][] DIAG = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    private void expand(Node n) {
        int x = n.x, y = n.y, z = n.z;
        double h = n.feet;
        double fh = t.floorAt(x, y, z);
        // a start a little off its floor (stepping, slipping off a slab edge) counts as standing on it
        boolean floor = !Double.isNaN(fh) && Math.abs(fh - h) < 0.6;
        if (floor) {
            h = fh;
        }
        boolean holding = !floor && (t.climbable(x, y, z) || t.climbable(x, y - 1, z));
        if (!floor && !holding) {
            // in the air (only ever the start node): just fall
            for (int ny = y - 1; ny >= y - tun.maxDrop; ny--) {
                double hb = t.standHeight(x, ny, z);
                if (!Double.isNaN(hb) && t.solidFloor(x, ny, z)) {
                    offer(n, x, ny, z, hb, Step.DROP, fallTicks(h - hb));
                    break;
                }
                if (t.fits(x + 0.5, ny, z + 0.5) < 0) {
                    break;
                }
            }
            return;
        }
        double run = tun.sprint / caps.speed * (caps.hop ? tun.hopRunFactor : 1);
        boolean canBuild = caps.blocks > n.blocksUsed;
        boolean atEdge = false;

        for (int[] d : CARD) {
            int dx = d[0], dz = d[1];
            int nx = x + dx, nz = z + dz;
            boolean walkable = false;
            // walk, step up a stair/slab, or jump up a block
            for (int ny = y + 1; ny >= y - 1; ny--) {
                double hb = t.standHeight(nx, ny, nz);
                if (Double.isNaN(hb)) {
                    continue;
                }
                double dh = hb - h;
                if (dh > 1.25 + Terrain.EPS || dh < -Terrain.STEP - Terrain.EPS) {
                    continue;
                }
                double top = Math.max(h, hb);
                int mineMid = t.fits(x + 0.5 + dx * 0.5, top, z + 0.5 + dz * 0.5);
                int mineEnd = t.fits(nx + 0.5, hb, nz + 0.5);
                if (mineMid < 0 || mineEnd < 0) {
                    continue;
                }
                double mine = (mineMid + mineEnd) * tun.minePerBlock;
                if (dh <= Terrain.STEP + Terrain.EPS) {
                    // walking through a vine/ladder block is capped at 0.15 blocks a tick
                    offer(n, nx, ny, nz, hb, Step.WALK, run + mine + (t.climbable(nx, ny, nz) ? tun.climbWalkExtra : 0));
                    walkable = true;
                } else if (floor || holding) {
                    int mineUp = t.fits(x + 0.5, hb, z + 0.5); // room to rise at the start
                    if (mineUp >= 0) {
                        offer(n, nx, ny, nz, hb, Step.ASCEND, run + tun.ascendExtra + mine + mineUp * tun.minePerBlock);
                        walkable = true;
                    }
                }
            }
            if (walkable || !floor) {
                continue;
            }
            // the next column has no floor at our level: drop, jump across, or bridge
            boolean edgeClear = t.fits(x + 0.5 + dx * 0.5, h, z + 0.5 + dz * 0.5) >= 0 && t.fits(nx + 0.5, h, nz + 0.5) >= 0;
            if (!edgeClear) {
                continue;
            }
            drop(n, nx, nz, h, run);
            atEdge = true;
            if (canBuild) {
                bridge(n, dx, dz, h, run);
            }
        }

        if (floor) {
            for (int[] d : DIAG) {
                diagonal(n, d[0], d[1], h, run);
                if (!atEdge && Double.isNaN(t.floorAt(x + d[0], y, z + d[1])) && t.fits(x + d[0] + 0.5, h, z + d[1] + 0.5) >= 0) {
                    atEdge = true;
                }
            }
            if (atEdge) {
                parkour(n, h, run);
            }
        }

        // ladders and vines
        // up a ladder/vine: only with something to push against (vines hanging free can't be climbed)
        if (t.climbable(x, y, z) && t.pushSide(x, y, z) >= 0) {
            double hb = t.standHeight(x, y + 1, z);
            if (!Double.isNaN(hb)) {
                int mine = t.fits(x + 0.5, y + 1, z + 0.5);
                if (mine >= 0) {
                    offer(n, x, y + 1, z, y + 1, Step.CLIMB_UP, tun.climbUp + mine * tun.minePerBlock);
                }
            }
        }
        if (t.climbable(x, y - 1, z)) {
            double hb = t.standHeight(x, y - 1, z);
            if (!Double.isNaN(hb)) {
                offer(n, x, y - 1, z, hb, Step.CLIMB_DOWN, tun.climbDown);
            }
        }

        // dig down through a block of our own we're standing on (e.g. back down a pillar we built
        // in a shaft), landing on whatever is under it
        if (caps.pickaxe && floor && Math.abs(h - y) < 1e-3 && t.is(x, y - 1, z, BlockView.OWN_BLOCK)
                && (n.placed == null || !contains(n.placed, Terrain.key(x, y - 1, z)))) {
            for (int ny = y - 1; ny >= y - tun.maxDrop; ny--) {
                double hb = t.floorAt(x, ny, z);
                if (ny == y - 1 && !Double.isNaN(hb) && hb >= y - 1e-3) {
                    continue; // that is the block being mined
                }
                if (!Double.isNaN(hb)) {
                    if (!t.dangerBelow(x, ny, z) && t.fits(x + 0.5, hb, z + 0.5) >= 0) {
                        offer(n, x, ny, z, hb, Step.MINE_DOWN, tun.minePerBlock + fallTicks(h - hb) * tun.dropPerFall);
                    }
                    break;
                }
                if (t.fits(x + 0.5, ny, z + 0.5) < 0) {
                    break;
                }
            }
        }

        // pillar straight up
        if (canBuild && floor && Math.abs(h - y) < 1e-3 && t.is(x, y, z, BlockView.REPLACEABLE)
                && t.is(x, y - 1, z, BlockView.PLACE_AGAINST)) {
            int mine = t.fits(x + 0.5, y + 1, z + 0.5);
            if (mine >= 0) {
                offer(n, x, y + 1, z, y + 1, Step.PILLAR, tun.pillar + tun.placePenalty + mine * tun.minePerBlock, x, y, z);
            }
        }
    }

    private static boolean contains(long[] keys, long k) {
        for (long v : keys) if (v == k) return true;
        return false;
    }

    /** Where a drop down a column lands (feet block Y), or NO_LANDING, by (column, start height). */
    private final LongMap<Integer> dropCache = new LongMap<>(256);
    private static final int NO_LANDING = Integer.MIN_VALUE;

    private void drop(Node n, int nx, int nz, double h, double run) {
        // the long scan down a column is the same for every node along an edge: remember it
        // (unless this branch of the plan has placed blocks, which could change it)
        long key = Terrain.key(nx, n.y, nz) * 31 + (long) Math.floor(h * 2);
        Integer cached = n.placed == null ? dropCache.get(key) : null;
        int land = cached != null ? cached : scanDrop(nx, nz, n.y, h);
        if (cached == null && n.placed == null) {
            dropCache.put(key, land);
        }
        if (land == NO_LANDING) {
            return;
        }
        double hb = t.floorAt(nx, land, nz);
        // falling through vines/ladders is capped at 0.15 blocks a tick
        int slowed = 0;
        for (int yy = land; yy < n.y; yy++) {
            if (t.climbable(nx, yy, nz)) slowed++;
        }
        offer(n, nx, land, nz, hb, Step.DROP, run * 0.8 + fallTicks(h - hb) * tun.dropPerFall + slowed * tun.climbDown);
    }

    private int scanDrop(int nx, int nz, int y, double h) {
        for (int ny = y - 1; ny >= y - tun.maxDrop; ny--) {
            double hb = t.floorAt(nx, ny, nz);
            if (!Double.isNaN(hb)) {
                if (h - hb <= Terrain.STEP + Terrain.EPS) {
                    continue; // a small step down, already a walk
                }
                if (t.dangerBelow(nx, ny, nz) || t.fits(nx + 0.5, hb, nz + 0.5) < 0) {
                    return NO_LANDING;
                }
                return ny;
            }
            if (t.fits(nx + 0.5, ny, nz + 0.5) < 0) {
                return NO_LANDING; // something in the way of the fall
            }
        }
        return NO_LANDING;
    }

    /**
     * Sprint-jumps from the edge to any block in range, in any direction (straight, diagonal or a
     * knight's-move offset), as long as the arc between is clear. Range is the measured reach
     * (Tuning.parkourReach) for the landing height and speed.
     */
    private void parkour(Node n, double h, double run) {
        if (t.fits(n.x + 0.5, h + 1.15, n.z + 0.5) < 0) {
            return; // no room to jump
        }
        int r = (int) Math.ceil(tun.maxJump(-2, caps.speed));
        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                double dist = Math.sqrt(ox * ox + oz * oz);
                if (dist < 1.9 || dist > r + 1e-6) {
                    continue; // next-door blocks are walks
                }
                int lx = n.x + ox, lz = n.z + oz;
                for (int dy = 1; dy >= -2; dy--) {
                    int ny = n.y + dy;
                    if (!t.solidFloor(lx, ny, lz)) {
                        continue;
                    }
                    double hb = t.standHeight(lx, ny, lz);
                    if (Double.isNaN(hb) || t.dangerBelow(lx, ny, lz)) {
                        continue;
                    }
                    double dh = hb - h;
                    if (dh > 1.0 + Terrain.EPS || dist > tun.maxJump(dh, caps.speed) + 1e-6) {
                        continue;
                    }
                    if (!arcClear(n.x + 0.5, n.z + 0.5, lx + 0.5, lz + 0.5, h, hb)) {
                        continue;
                    }
                    double cost = run * dist + tun.parkourExtra + tun.parkourPerGap * (dist - 1);
                    offer(n, lx, ny, lz, hb, Step.PARKOUR, cost);
                    break;
                }
            }
        }
    }

    /** Whether the player's box clears everything along a jump from (ax, az) to (bx, bz). */
    private boolean arcClear(double ax, double az, double bx, double bz, double h, double hb) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
        int steps = Math.max(2, (int) Math.ceil(len / 0.25));
        double land = Math.max(hb, h);
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            double px = ax + (bx - ax) * f, pz = az + (bz - az) * f;
            double y = f < 0.2 ? h : f > 0.8 ? land + 0.05 : h + 1.15;
            if (t.fits(px, y, pz) != 0) {
                return false;
            }
            if (f > 0.8 && t.fits(px, Math.max(land, h + 0.6), pz) != 0) {
                return false; // nowhere to come down into
            }
        }
        return true;
    }

    private void bridge(Node n, int dx, int dz, double h, double run) {
        int nx = n.x + dx, nz = n.z + dz;
        int py = n.y - 1;
        if (h - n.y > 0.5 + Terrain.EPS || !t.is(nx, py, nz, BlockView.REPLACEABLE)) {
            return;
        }
        boolean support = false;
        for (int f = 0; f < 6 && !support; f++) {
            int ax = nx + Dir.DX[f], ay = py + Dir.DY[f], az = nz + Dir.DZ[f];
            if (ay == n.y && ax == nx && az == nz) {
                continue;
            }
            support = t.is(ax, ay, az, BlockView.PLACE_AGAINST);
        }
        if (!support || t.fits(nx + 0.5, n.y, nz + 0.5) != 0) {
            return;
        }
        offer(n, nx, n.y, nz, n.y, Step.BRIDGE, tun.bridge + tun.placePenalty + run, nx, py, nz);
    }

    private void diagonal(Node n, int dx, int dz, double h, double run) {
        int nx = n.x + dx, nz = n.z + dz;
        for (int ny = n.y + 1; ny >= n.y - 1; ny--) {
            double hb = t.standHeight(nx, ny, nz);
            if (Double.isNaN(hb) || !t.solidFloor(nx, ny, nz)) {
                continue;
            }
            double dh = hb - h;
            if (Math.abs(dh) > Terrain.STEP + Terrain.EPS) {
                continue;
            }
            double top = Math.max(h, hb);
            // both blocks beside the corner must be clear, or the player catches on it
            if (t.fits(n.x + dx + 0.5, top, n.z + 0.5) != 0 || t.fits(n.x + 0.5, top, n.z + dz + 0.5) != 0) {
                continue;
            }
            if (t.fits(nx + 0.5, hb, nz + 0.5) != 0) {
                continue;
            }
            offer(n, nx, ny, nz, hb, Step.DIAGONAL, run * tun.diagonalFactor);
            return;
        }
    }

    // ---- fall time ---------------------------------------------------------------------------------

    private static final double[] FALL = new double[600];

    static {
        double y = 0, v = 0;
        int n = 1;
        for (int tick = 1; n < FALL.length; tick++) {
            v = (v - 0.08) * 0.98;
            y += v;
            while (n < FALL.length && -y >= n * 0.5) {
                FALL[n++] = tick;
            }
        }
    }

    /** Ticks to fall {@code blocks} blocks from standing still. */
    public static double fallTicks(double blocks) {
        int i = (int) Math.ceil(blocks * 2);
        return FALL[Math.max(0, Math.min(i, FALL.length - 1))];
    }

    // ---- binary heap on f ----------------------------------------------------------------------------

    private void push(Node n) {
        if (heapSize == heap.length) {
            Node[] bigger = new Node[heap.length * 2];
            System.arraycopy(heap, 0, bigger, 0, heapSize);
            heap = bigger;
        }
        heap[heapSize] = n;
        n.heap = heapSize;
        siftUp(heapSize++);
    }

    private Node pop() {
        Node top = heap[0];
        top.heap = -1;
        heapSize--;
        if (heapSize > 0) {
            heap[0] = heap[heapSize];
            heap[0].heap = 0;
            siftDown(0);
        }
        heap[heapSize] = null;
        return top;
    }

    private boolean less(Node a, Node b) {
        return a.f < b.f || (a.f == b.f && a.h < b.h);
    }

    private void siftUp(int i) {
        Node n = heap[i];
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (!less(n, heap[p])) {
                break;
            }
            heap[i] = heap[p];
            heap[i].heap = i;
            i = p;
        }
        heap[i] = n;
        n.heap = i;
    }

    private void siftDown(int i) {
        Node n = heap[i];
        while (true) {
            int l = 2 * i + 1;
            if (l >= heapSize) {
                break;
            }
            int c = l + 1 < heapSize && less(heap[l + 1], heap[l]) ? l + 1 : l;
            if (!less(heap[c], n)) {
                break;
            }
            heap[i] = heap[c];
            heap[i].heap = i;
            i = c;
        }
        heap[i] = n;
        n.heap = i;
    }
}
