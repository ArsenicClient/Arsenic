package arsenic.utils.buildcore;

import arsenic.utils.botcore.BlockView;
import arsenic.utils.botcore.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* for a flying player over block cells. A node is a cell the feet are in; the player fits when that cell and the
 * one or two above have no collision (two leave room to drift up and down while flying). Moves go to all 26
 * neighbours, diagonals only when the cells they cut past are open too, so the path can always be flown.
 */
public final class FlyPath {
    /**
     * Feet height above a node's floor while following a path {@code height} cells high. Flying moves in steps of
     * about 0.375 blocks up or down (one key press, then 0.6 drag), too coarse to thread a gap two cells high, so
     * paths three high come first.
     */
    public static double feet(int height) {
        return height >= 3 ? 0.5 : 0.15;
    }

    private final List<Box> boxes = new ArrayList<>();
    private final Map<Long, Boolean> free = new HashMap<>();
    private BlockView w;

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private boolean open(int x, int y, int z) {
        long k = key(x, y, z);
        Boolean v = free.get(k);
        if (v != null) return v;
        boxes.clear();
        w.collisionBoxes(x, y, z, boxes);
        boolean o = boxes.isEmpty() && (w.flags(x, y, z) & BlockView.UNLOADED) == 0;
        free.put(k, o);
        return o;
    }

    private int height;

    public boolean fits(int x, int y, int z) {
        return open(x, y, z) && open(x, y + 1, z) && (height < 3 || open(x, y + 2, z));
    }

    private static final class Node implements Comparable<Node> {
        final int x, y, z;
        final double g, f;
        final Node parent;

        Node(int x, int y, int z, double g, double f, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }

    /**
     * Cells from start to goal (both included), or null when there is no way within {@code maxNodes}. {@code minY}
     * keeps the path off the ground (flying ends on touching it).
     */
    public List<int[]> find(BlockView w, int sx, int sy, int sz, int gx, int gy, int gz, int minY, int maxNodes, int height) {
        this.w = w;
        this.height = height;
        free.clear();
        if (!fits(gx, gy, gz)) return null;
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<Long, Double> best = new HashMap<>();
        openSet.add(new Node(sx, sy, sz, 0, dist(sx, sy, sz, gx, gy, gz), null));
        best.put(key(sx, sy, sz), 0.0);
        int expanded = 0;
        while (!openSet.isEmpty() && expanded < maxNodes) {
            Node n = openSet.poll();
            Double b = best.get(key(n.x, n.y, n.z));
            if (b != null && b < n.g) continue;
            if (n.x == gx && n.y == gy && n.z == gz) return unwind(n);
            expanded++;
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int x = n.x + dx, y = n.y + dy, z = n.z + dz;
                        if (y < minY || !fits(x, y, z) || !sweeps(n, dx, dy, dz)) continue;
                        double g = n.g + Math.sqrt(dx * dx + dy * dy + dz * dz);
                        long k = key(x, y, z);
                        Double old = best.get(k);
                        if (old != null && old <= g) continue;
                        best.put(k, g);
                        openSet.add(new Node(x, y, z, g, g + dist(x, y, z, gx, gy, gz), n));
                    }
        }
        return null;
    }

    /** No cutting corners: every cell a diagonal move passes beside (each part of the step taken alone) is open. */
    private boolean sweeps(Node n, int dx, int dy, int dz) {
        for (int m = 1; m < 7; m++) {
            int px = (m & 1) != 0 ? dx : 0, py = (m & 2) != 0 ? dy : 0, pz = (m & 4) != 0 ? dz : 0;
            if (px == dx && py == dy && pz == dz) continue;
            if ((px | py | pz) == 0) continue;
            if (!fits(n.x + px, n.y + py, n.z + pz)) return false;
        }
        return true;
    }

    private static double dist(int x, int y, int z, int gx, int gy, int gz) {
        int dx = x - gx, dy = y - gy, dz = z - gz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<int[]> unwind(Node n) {
        List<int[]> path = new ArrayList<>();
        for (; n != null; n = n.parent) path.add(0, new int[]{n.x, n.y, n.z});
        return path;
    }
}
