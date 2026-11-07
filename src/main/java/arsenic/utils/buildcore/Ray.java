package arsenic.utils.buildcore;

import arsenic.utils.botcore.BlockView;
import arsenic.utils.botcore.Box;

import java.util.ArrayList;
import java.util.List;

/** Line of sight the way the client picks the block under the crosshair (Entity.pick, ClipContext.Block.OUTLINE). */
public final class Ray {
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            SIN[i] = (float) Math.sin(i / 10430.378350470453);
        }
    }

    /** Mth.sin / Mth.cos, the game's lookup table. */
    public static float sin(double v) {
        return SIN[(int) ((long) (v * 10430.378350470453) & 65535L)];
    }

    public static float cos(double v) {
        return SIN[(int) ((long) (v * 10430.378350470453 + 16384.0) & 65535L)];
    }

    /** Entity.calculateViewVector. */
    public static double[] look(float yaw, float pitch) {
        float xr = pitch * (float) (Math.PI / 180.0);
        float yr = -yaw * (float) (Math.PI / 180.0);
        float yc = cos(yr), ys = sin(yr), xc = cos(xr), xs = sin(xr);
        return new double[]{ys * xc, -xs, yc * xc};
    }

    public static float yawTo(double dx, double dz) {
        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
    }

    public static float pitchTo(double dx, double dy, double dz) {
        return (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
    }

    /** What a ray hit: the block, the face and the point. */
    public static final class Hit {
        public int x, y, z, face;
        public double hx, hy, hz, dist;
    }

    private final List<Box> boxes = new ArrayList<>();
    private final int[] face = new int[1];

    /** The first block outline the ray from the eye along (yaw, pitch) meets within range, or null. */
    public Hit trace(BlockView w, double ex, double ey, double ez, float yaw, float pitch, double range) {
        double[] d = look(yaw, pitch);
        double tx = ex + d[0] * range, ty = ey + d[1] * range, tz = ez + d[2] * range;
        int x = floor(ex), y = floor(ey), z = floor(ez);
        int ex1 = floor(tx), ey1 = floor(ty), ez1 = floor(tz);
        int sx = d[0] > 0 ? 1 : d[0] < 0 ? -1 : 0, sy = d[1] > 0 ? 1 : d[1] < 0 ? -1 : 0, sz = d[2] > 0 ? 1 : d[2] < 0 ? -1 : 0;
        double dtx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / (tx - ex));
        double dty = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / (ty - ey));
        double dtz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / (tz - ez));
        double ntx = sx == 0 ? Double.MAX_VALUE : dtx * (sx > 0 ? 1 - frac(ex) : frac(ex));
        double nty = sy == 0 ? Double.MAX_VALUE : dty * (sy > 0 ? 1 - frac(ey) : frac(ey));
        double ntz = sz == 0 ? Double.MAX_VALUE : dtz * (sz > 0 ? 1 - frac(ez) : frac(ez));
        for (int guard = 0; guard < 64; guard++) {
            Hit h = clip(w, x, y, z, ex, ey, ez, tx, ty, tz);
            if (h != null) {
                h.dist *= range;
                return h;
            }
            if (x == ex1 && y == ey1 && z == ez1) return null;
            if (ntx < nty && ntx < ntz) {
                if (ntx > 1) return null;
                x += sx;
                ntx += dtx;
            } else if (nty < ntz) {
                if (nty > 1) return null;
                y += sy;
                nty += dty;
            } else {
                if (ntz > 1) return null;
                z += sz;
                ntz += dtz;
            }
        }
        return null;
    }

    private Hit clip(BlockView w, int x, int y, int z, double ax, double ay, double az, double bx, double by, double bz) {
        boxes.clear();
        w.rayBoxes(x, y, z, boxes);
        Hit best = null;
        for (Box b : boxes) {
            double t = b.clip(ax, ay, az, bx, by, bz, face);
            if (t < 0 || face[0] < 0) continue;
            if (best == null || t < best.dist) {
                if (best == null) best = new Hit();
                best.x = x;
                best.y = y;
                best.z = z;
                best.face = face[0];
                best.dist = t;
                best.hx = ax + (bx - ax) * t;
                best.hy = ay + (by - ay) * t;
                best.hz = az + (bz - az) * t;
            }
        }
        return best;
    }

    static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    private static double frac(double d) {
        return d - Math.floor(d);
    }
}
