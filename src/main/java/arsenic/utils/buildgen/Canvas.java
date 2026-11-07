package arsenic.utils.buildgen;

import arsenic.utils.buildcore.Blueprint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * What a generated build script extends: the model writes {@link #build()} (and any helpers) using the shape methods
 * below, which fill a W x H x D box. x and z are horizontal, y is up from the plot floor at 0. Coordinates are
 * doubles and are rounded down to cells, so the model can do its maths without casts. Cells outside the box and
 * blocks outside the palette are counted and skipped rather than thrown, so one bad call costs a few blocks, not the
 * build; {@link ScriptRunner} reports them back to the model.
 */
public abstract class Canvas {
    protected int W, H, D;
    /** Seeded, so a script draws the same build every run. */
    protected final Random rng = new Random(1234);

    private Blueprint bp;
    private Palette palette;
    private long deadline;
    private long calls;
    final Map<String, Integer> unknown = new LinkedHashMap<>();
    int outside;

    public abstract void build();

    void setUp(Blueprint bp, Palette palette, long deadline) {
        this.bp = bp;
        this.palette = palette;
        this.deadline = deadline;
        W = bp.sizeX;
        H = bp.sizeY;
        D = bp.sizeZ;
    }

    // ---- shapes ------------------------------------------------------------------------------------

    /** One block; "air" clears the cell. */
    protected void block(double x, double y, double z, String block) {
        tick();
        int id = id(block);
        if (id < 0) return;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        if (!bp.inside(ix, iy, iz)) {
            outside++;
            return;
        }
        bp.set(ix, iy, iz, id);
    }

    /** The block at a cell ("air" when empty or outside). */
    protected String get(double x, double y, double z) {
        int id = bp.get((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return id <= 0 ? "air" : bp.palette.get(id).replace("minecraft:", "");
    }

    /** A filled box between two corners, both included. */
    protected void box(double x1, double y1, double z1, double x2, double y2, double z2, String block) {
        for (int x = lo(x1, x2); x <= hi(x1, x2); x++)
            for (int y = lo(y1, y2); y <= hi(y1, y2); y++)
                for (int z = lo(z1, z2); z <= hi(z1, z2); z++) block(x, y, z, block);
    }

    /** The walls, floor and roof of a box, one block thick. */
    protected void hollowBox(double x1, double y1, double z1, double x2, double y2, double z2, String block) {
        int ax = lo(x1, x2), bx = hi(x1, x2), ay = lo(y1, y2), by = hi(y1, y2), az = lo(z1, z2), bz = hi(z1, z2);
        for (int x = ax; x <= bx; x++)
            for (int y = ay; y <= by; y++)
                for (int z = az; z <= bz; z++)
                    if (x == ax || x == bx || y == ay || y == by || z == az || z == bz) block(x, y, z, block);
    }

    /** A line of blocks with no gaps (each step moves one cell along one or more axes). */
    protected void line(double x1, double y1, double z1, double x2, double y2, double z2, String block) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            block(x1 + dx * t + 0.5, y1 + dy * t + 0.5, z1 + dz * t + 0.5, block);
        }
    }

    /** A solid ball of cells whose centres are within r of (cx, cy, cz). */
    protected void sphere(double cx, double cy, double cz, double r, String block) {
        ellipsoid(cx, cy, cz, r, r, r, block);
    }

    protected void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, String block) {
        for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++)
            for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
                for (int z = (int) Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
                    double ex = (x + 0.5 - cx) / Math.max(rx, 0.01), ey = (y + 0.5 - cy) / Math.max(ry, 0.01), ez = (z + 0.5 - cz) / Math.max(rz, 0.01);
                    if (ex * ex + ey * ey + ez * ez <= 1.0) block(x, y, z, block);
                }
    }

    /** A hollow ball: cells between r - thickness and r from the centre. */
    protected void shell(double cx, double cy, double cz, double r, double thickness, String block) {
        for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++)
            for (int y = (int) Math.floor(cy - r); y <= Math.ceil(cy + r); y++)
                for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                    double d = Math.sqrt(sq(x + 0.5 - cx) + sq(y + 0.5 - cy) + sq(z + 0.5 - cz));
                    if (d <= r && d > r - thickness) block(x, y, z, block);
                }
    }

    /** An upright cylinder from y up h cells. */
    protected void cylinder(double cx, double y, double cz, double r, double h, String block) {
        for (int i = 0; i < Math.max(1, Math.round(h)); i++) disk(cx, y + i, cz, r, block);
    }

    /** A flat, filled circle in the horizontal plane at height y. */
    protected void disk(double cx, double y, double cz, double r, String block) {
        for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++)
            for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++)
                if (sq(x + 0.5 - cx) + sq(z + 0.5 - cz) <= r * r) block(x, y, z, block);
    }

    /** A flat ring in the horizontal plane at height y, between r - thickness and r. */
    protected void ring(double cx, double y, double cz, double r, double thickness, String block) {
        for (int x = (int) Math.floor(cx - r); x <= Math.ceil(cx + r); x++)
            for (int z = (int) Math.floor(cz - r); z <= Math.ceil(cz + r); z++) {
                double d = Math.sqrt(sq(x + 0.5 - cx) + sq(z + 0.5 - cz));
                if (d <= r && d > r - thickness) block(x, y, z, block);
            }
    }

    /** A random number in [0, 1), from the seeded generator. */
    protected double random() {
        return rng.nextDouble();
    }

    /** One of the given blocks at random. */
    protected String pick(String... blocks) {
        return blocks[rng.nextInt(blocks.length)];
    }

    /** Blocks placed so far. */
    protected int count() {
        return bp.solidCount();
    }

    // ---- internals ---------------------------------------------------------------------------------

    Palette palette() {
        return palette;
    }

    private void tick() {
        if ((++calls & 1023) == 0 && System.nanoTime() > deadline) throw new IllegalStateException("script took too long");
        if (calls > 20_000_000) throw new IllegalStateException("script placed more than 20 million blocks");
    }

    private int id(String block) {
        String b = block == null ? "" : block.trim().toLowerCase().replace("minecraft:", "");
        if (b.equals("air")) return Blueprint.AIR;
        if (!palette.has(b)) {
            unknown.merge(b, 1, Integer::sum);
            return -1;
        }
        return bp.idOf("minecraft:" + b);
    }

    private static int lo(double a, double b) {
        return (int) Math.floor(Math.min(a, b));
    }

    private static int hi(double a, double b) {
        return (int) Math.floor(Math.max(a, b));
    }

    private static double sq(double d) {
        return d * d;
    }
}
