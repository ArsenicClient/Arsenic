package arsenic.utils.botcore;

import java.util.ArrayList;
import java.util.List;

public final class Terrain {
    public static final double EPS = 1e-6;
    public static final double HW = 0.3;
    public static final double HEIGHT = 1.8;
    public static final double EYE = 1.62;
    public static final double STEP = 0.6;

    private static final Box[] NONE = new Box[0];

    private static final class Cell {
        Box[] boxes;
        Box[] ray;
        int flags;
    }

    public final BlockView view;
    public boolean mineOwn;
    private final LongMap<Cell> cache = new LongMap<>(4096);
    private final List<Box> scratch = new ArrayList<>();

    public Terrain(BlockView view, boolean mineOwn) {
        this.view = view;
        this.mineOwn = mineOwn;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    private long[] virtual;

    public void setVirtual(long[] placed) {
        virtual = placed;
    }

    private Cell virtualCell(int x, int y, int z) {
        Cell c = new Cell();
        c.flags = BlockView.PLACE_AGAINST;
        c.boxes = new Box[]{Box.local(x, y, z, 0, 0, 0, 1, 1, 1)};
        c.ray = c.boxes;
        return c;
    }

    private Cell cell(int x, int y, int z) {
        long k = key(x, y, z);
        if (virtual != null) {
            for (long v : virtual) {
                if (v == k) {
                    return virtualCell(x, y, z);
                }
            }
        }
        Cell c = cache.get(k);
        if (c == null) {
            c = new Cell();
            c.flags = view.flags(x, y, z);
            scratch.clear();
            view.collisionBoxes(x, y, z, scratch);
            c.boxes = scratch.isEmpty() ? NONE : scratch.toArray(new Box[0]);
            cache.put(k, c);
        }
        return c;
    }

    public int flags(int x, int y, int z) {
        return cell(x, y, z).flags;
    }

    public boolean is(int x, int y, int z, int flag) {
        return (cell(x, y, z).flags & flag) != 0;
    }

    public Box[] boxes(int x, int y, int z) {
        return cell(x, y, z).boxes;
    }

    public boolean empty(int x, int y, int z) {
        Cell c = cell(x, y, z);
        return c.boxes.length == 0 && (c.flags & (BlockView.DANGER | BlockView.LIQUID | BlockView.UNLOADED)) == 0;
    }

    public boolean climbable(int x, int y, int z) {
        return is(x, y, z, BlockView.CLIMBABLE);
    }

    public int fitsBox(Box b) {
        int x0 = floor(b.minX + EPS), x1 = floor(b.maxX - EPS);
        int y0 = floor(b.minY + EPS), y1 = floor(b.maxY - EPS);
        int z0 = floor(b.minZ + EPS), z1 = floor(b.maxZ - EPS);
        int own = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0 - 1; y <= y1; y++) {
                    Cell c = cell(x, y, z);
                    if (y >= y0 && (c.flags & (BlockView.DANGER | BlockView.LIQUID | BlockView.UNLOADED)) != 0) {
                        return -1;
                    }
                    boolean counted = false;
                    for (Box cb : c.boxes) {
                        if (cb.intersects(b)) {
                            if (mineOwn && (c.flags & BlockView.OWN_BLOCK) != 0) {
                                if (!counted) {
                                    own++;
                                    counted = true;
                                }
                            } else {
                                return -1;
                            }
                        }
                    }
                }
            }
        }
        return own;
    }

    public int fits(double px, double py, double pz) {
        double fx = px - 0.5, fz = pz - 0.5, y2 = py * 2;
        boolean cacheable = virtual == null && fx == Math.floor(fx) && fz == Math.floor(fz) && y2 == Math.floor(y2);
        long k = 0;
        if (cacheable) {
            k = key((int) fx, (int) y2, (int) fz);
            Integer hit = fitCache.get(k);
            if (hit != null) return hit;
        }
        int r = fitsBox(new Box(px - HW + EPS, py + EPS, pz - HW + EPS, px + HW - EPS, py + HEIGHT - EPS, pz + HW - EPS));
        if (cacheable) fitCache.put(k, r);
        return r;
    }

    private final LongMap<Integer> fitCache = new LongMap<>(4096);

    public double floorAt(int x, int y, int z) {
        double best = Double.NEGATIVE_INFINITY;
        double fx0 = x + 0.5 - HW, fx1 = x + 0.5 + HW, fz0 = z + 0.5 - HW, fz1 = z + 0.5 + HW;
        for (int yy = y - 1; yy <= y; yy++) {
            Cell c = cell(x, yy, z);
            if (mineOwn && (c.flags & BlockView.OWN_BLOCK) != 0 && yy == y) {
                continue;
            }
            for (Box b : c.boxes) {
                if (b.maxX > fx0 && b.minX < fx1 && b.maxZ > fz0 && b.minZ < fz1 && b.maxY <= y + 0.5 + EPS && b.maxY > best) {
                    best = b.maxY;
                }
            }
        }
        if (best < y - EPS) {
            return Double.NaN;
        }
        return best;
    }

    public boolean supportAt(double px, double feet, double pz) {
        double x0 = px - HW, x1 = px + HW, z0 = pz - HW, z1 = pz + HW;
        int y = floor(feet + EPS);
        for (int x = floor(x0); x <= floor(x1 - EPS); x++) {
            for (int z = floor(z0); z <= floor(z1 - EPS); z++) {
                for (int yy = y - 1; yy <= y; yy++) {
                    for (Box b : cell(x, yy, z).boxes) {
                        if (b.maxX > x0 && b.minX < x1 && b.maxZ > z0 && b.minZ < z1
                                && b.maxY <= feet + STEP && b.maxY >= feet - STEP) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public int pushSide(int x, int y, int z) {
        for (int d = 2; d < 6; d++) {
            for (Box b : boxes(x, y, z)) {
                boolean alongZ = d == Dir.NORTH || d == Dir.SOUTH;
                boolean thin = alongZ ? b.maxZ - b.minZ < 0.3 : b.maxX - b.minX < 0.3;
                double edge = d == Dir.NORTH ? b.minZ - z : d == Dir.SOUTH ? (z + 1) - b.maxZ : d == Dir.WEST ? b.minX - x : (x + 1) - b.maxX;
                if (thin && edge < 1e-3) {
                    return d;
                }
            }
        }
        for (int d = 2; d < 6; d++) {
            int ax = x + Dir.DX[d], az = z + Dir.DZ[d];
            if (boxes(ax, y, az).length > 0 && !climbable(ax, y, az)) {
                return d;
            }
        }
        return -1;
    }

    public boolean dangerBelow(int x, int y, int z) {
        return is(x, y - 1, z, BlockView.DANGER);
    }

    public double standHeight(int x, int y, int z) {
        double h = floorAt(x, y, z);
        if (Double.isNaN(h)) {
            if (climbable(x, y, z) || climbable(x, y - 1, z)) {
                h = y;
            } else {
                return Double.NaN;
            }
        }
        if (fits(x + 0.5, h, z + 0.5) < 0) {
            return Double.NaN;
        }
        return h;
    }

    public boolean solidFloor(int x, int y, int z) {
        return !Double.isNaN(floorAt(x, y, z));
    }


    private Box[] rayBoxes(int x, int y, int z) {
        Cell c = cell(x, y, z);
        if (c.ray == null) {
            scratch.clear();
            view.rayBoxes(x, y, z, scratch);
            c.ray = scratch.isEmpty() ? NONE : scratch.toArray(new Box[0]);
        }
        return c.ray;
    }

    public long rayTrace(double ax, double ay, double az, double bx, double by, double bz, int[] faceOut) {
        int x = floor(ax), y = floor(ay), z = floor(az);
        int ex = floor(bx), ey = floor(by), ez = floor(bz);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tmx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? (x + 1 - ax) : (ax - x)) * tdx;
        double tmy = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? (y + 1 - ay) : (ay - y)) * tdy;
        double tmz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? (z + 1 - az) : (az - z)) * tdz;
        int[] f = new int[1];
        for (int i = 0; i < 400; i++) {
            double best = 2;
            int bestFace = -1;
            for (Box b : rayBoxes(x, y, z)) {
                double t = b.clip(ax, ay, az, bx, by, bz, f);
                if (t >= 0 && t < best) {
                    best = t;
                    bestFace = f[0];
                }
            }
            if (bestFace != -1 || best < 2) {
                if (faceOut != null) faceOut[0] = bestFace;
                return key(x, y, z);
            }
            if (x == ex && y == ey && z == ez) {
                break;
            }
            if (tmx < tmy && tmx < tmz) {
                if (tmx > 1) break;
                x += sx;
                tmx += tdx;
            } else if (tmy < tmz) {
                if (tmy > 1) break;
                y += sy;
                tmy += tdy;
            } else {
                if (tmz > 1) break;
                z += sz;
                tmz += tdz;
            }
        }
        return Long.MIN_VALUE;
    }

    public static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }
}
