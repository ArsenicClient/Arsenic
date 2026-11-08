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
    /** Stair and slab properties that were not valid, so the default was used. */
    final Map<String, Integer> badState = new LinkedHashMap<>();
    int outside;
    /** Buried blocks cleared after the script ran. */
    int hollowed;

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
        return id <= 0 ? "air" : Blueprint.blockName(bp.palette.get(id)).replace("minecraft:", "");
    }

    /**
     * A stair. {@code facing} is the way it climbs ("north", "south", "east", "west": the tall back is on that side;
     * north is -z, south +z, east +x, west -x) and {@code half} is "bottom" (a normal stair) or "top" (upside down,
     * for ceilings and eaves).
     */
    protected void stairs(double x, double y, double z, String block, String facing, String half) {
        block(x, y, z, shaped(block, "_stairs") + "[facing=" + facing + ",half=" + half + "]");
    }

    /** A slab: {@code type} is "bottom" (the lower half of the cell) or "top". A full cell is just a plain block. */
    protected void slab(double x, double y, double z, String block, String type) {
        block(x, y, z, shaped(block, "_slab") + "[type=" + type + "]");
    }

    private static String shaped(String block, String suffix) {
        String b = block == null ? "" : block.trim().toLowerCase().replace("minecraft:", "");
        return b.endsWith(suffix) ? b : b + suffix;
    }

    /**
     * A gable roof of stairs over x1..x2 by z1..z2 with its lowest row (the eaves) at height y: stairs climb from both
     * long edges to the ridge, with a row of {@code fill} under each stair's inner side so the roof is two blocks
     * thick and holds together, and the space under it left empty. {@code ridgeAlongX} says which way the ridge
     * runs (along x, or along z). The triangular gable ends are left open: wall them in with box/line.
     */
    protected void gableRoof(double x1, double z1, double x2, double z2, double y, String stairs, String fill, boolean ridgeAlongX) {
        int ax = lo(x1, x2), bx = hi(x1, x2), az = lo(z1, z2), bz = hi(z1, z2), y0 = (int) Math.floor(y);
        int from = ridgeAlongX ? az : ax, to = ridgeAlongX ? bz : bx;
        int s0 = ridgeAlongX ? ax : az, s1 = ridgeAlongX ? bx : bz;
        // the low edge's stairs climb toward +across (south for a ridge along x, east for one along z)
        String up = ridgeAlongX ? "south" : "east", down = ridgeAlongX ? "north" : "west";
        for (int i = 0; from + i <= to - i; i++) {
            int a = from + i, b = to - i, h = y0 + i;
            for (int s = s0; s <= s1; s++) {
                if (a < b) {
                    put(ridgeAlongX, s, h, a, stairs, up, "bottom");
                    put(ridgeAlongX, s, h, b, stairs, down, "bottom");
                }
                // one cell in from each stair, under the next step up, so each row rests on the one below
                if (a + 1 <= b - 1) {
                    block(ridgeAlongX ? s : a + 1, h, ridgeAlongX ? a + 1 : s, fill);
                    block(ridgeAlongX ? s : b - 1, h, ridgeAlongX ? b - 1 : s, fill);
                }
            }
        }
    }

    private void put(boolean ridgeAlongX, int along, int y, int across, String stairs, String facing, String half) {
        stairs(ridgeAlongX ? along : across, y, ridgeAlongX ? across : along, stairs, facing, half);
    }

    /**
     * Hollows everything built so far: solid cells more than {@code thickness} cells from air, from a see-through
     * block (glass) or from the edge of the box are cleared, leaving a shell. Call it last. The builder can never
     * stand inside a block, and a filled-in build costs a block for each cell nobody sees.
     */
    protected void hollowOut(int thickness) {
        hollow(bp, palette, Math.max(1, thickness));
    }

    protected void hollowOut() {
        hollowOut(1);
    }

    /** An upright hollow cylinder from y up h cells: a wall of the given thickness, with a floor and a roof of it too. */
    protected void hollowCylinder(double cx, double y, double cz, double r, double h, double thickness, String block) {
        int n = (int) Math.max(1, Math.round(h));
        for (int i = 0; i < n; i++) {
            if (i < thickness || i >= n - thickness) disk(cx, y + i, cz, r, block);
            else ring(cx, y + i, cz, r, thickness, block);
        }
    }

    /** A hollow ellipsoid: cells between the surface and {@code thickness} cells in. */
    protected void hollowEllipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double thickness, String block) {
        for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++)
            for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
                for (int z = (int) Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
                    double ex = (x + 0.5 - cx) / Math.max(rx, 0.01), ey = (y + 0.5 - cy) / Math.max(ry, 0.01), ez = (z + 0.5 - cz) / Math.max(rz, 0.01);
                    double ix = (x + 0.5 - cx) / Math.max(rx - thickness, 0.01), iy = (y + 0.5 - cy) / Math.max(ry - thickness, 0.01),
                            iz = (z + 0.5 - cz) / Math.max(rz - thickness, 0.01);
                    boolean inner = rx - thickness > 0 && ry - thickness > 0 && rz - thickness > 0 && ix * ix + iy * iy + iz * iz < 1.0;
                    if (ex * ex + ey * ey + ez * ez <= 1.0 && !inner) block(x, y, z, block);
                }
    }

    /**
     * Clears the cells of a build that nobody could see or reach: solid cells further than {@code thickness} from air,
     * from a see-through block or from the edge of the box (which is air too). Returns how many were cleared.
     */
    static int hollow(Blueprint bp, Palette palette, int thickness) {
        int sx = bp.sizeX, sy = bp.sizeY, sz = bp.sizeZ, n = sx * sy * sz;
        int[] dist = new int[n];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int i = 0; i < n; i++) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            if (seeThrough(bp, palette, bp.get(x, y, z))) {
                dist[i] = 0;
                queue[tail++] = i;
            }
        }
        for (int i = 0; i < n; i++) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            if (dist[i] < 0 && (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1)) {
                dist[i] = 1;
                queue[tail++] = i;
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (head < tail) {
            int c = queue[head++];
            int cx = c % sx, cz = (c / sx) % sz, cy = c / (sx * sz);
            for (int[] d : dirs) {
                int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                int j = (ny * sz + nz) * sx + nx;
                if (dist[j] >= 0) continue;
                dist[j] = dist[c] + 1;
                queue[tail++] = j;
            }
        }
        int cleared = 0;
        for (int i = 0; i < n; i++) {
            if (dist[i] <= thickness) continue;
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            if (bp.get(x, y, z) > 0) {
                bp.set(x, y, z, Blueprint.AIR);
                cleared++;
            }
        }
        return cleared;
    }

    /** Air, or a block that shows what is behind it (glass, ice); leaves count as solid. */
    private static boolean seeThrough(Blueprint bp, Palette palette, int id) {
        if (id <= 0) return true;
        String name = Blueprint.blockName(bp.palette.get(id)).replace("minecraft:", "");
        return !name.endsWith("_leaves") && palette.colour(name)[3] < 200;
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

    /**
     * A blend of blocks. Pass the result as the block of any shape call and each cell it places gets one of these at
     * random (name a block twice to make it twice as likely): {@code disk(x, 0, z, 4, mix("basalt", "tuff", "basalt"))}.
     */
    protected String mix(String... blocks) {
        return String.join("|", blocks);
    }

    /**
     * A cone or frustum standing on y, h cells tall, radius baseR at the foot and topR at the top, as a gapless shell one
     * block thick (a disk at the foot closes the base). Use the same numbers for {@link #flow}.
     */
    protected void cone(double cx, double y, double cz, double baseR, double topR, double h, String block) {
        int n = (int) Math.max(1, Math.round(h));
        double step = Math.abs(baseR - topR) / n;
        for (int i = 0; i < n; i++) {
            double r = coneRadius(baseR, topR, n, i);
            if (i == 0 || r < 1.5) disk(cx, y + i, cz, r, block);
            else ring(cx, y + i, cz, r, Math.max(1.2, step + 0.8), block);
        }
    }

    /**
     * A strip running down the surface of a {@link #cone} with the same cx, y, cz, baseR, topR and h: from the top to the
     * foot at compass {@code angle} (radians; 0 is +x, PI/2 is +z), width cells wide, swaying sideways by up to
     * {@code sway} cells. For lava, streams, paths, stripes.
     */
    protected void flow(double cx, double y, double cz, double baseR, double topR, double h, double angle, double width, double sway, String block) {
        int n = (int) Math.max(1, Math.round(h));
        for (int i = n - 1; i >= 0; i--) {
            double r = Math.max(0.5, coneRadius(baseR, topR, n, i) - 0.5);
            double a = angle + Math.sin(i * 0.8) * sway / Math.max(r, 1);
            for (int s = 0; s < Math.max(1, Math.round(width)); s++) {
                double off = s - (Math.max(1, Math.round(width)) - 1) / 2.0;
                block(cx + r * Math.cos(a) - Math.sin(a) * off, y + i, cz + r * Math.sin(a) + Math.cos(a) * off, block);
            }
        }
    }

    private static double coneRadius(double baseR, double topR, int n, int i) {
        return n <= 1 ? baseR : baseR + (topR - baseR) * i / (n - 1);
    }

    /** A dome of radius r and height h standing on y, as a gapless shell one block thick, closed at the foot. */
    protected void mound(double cx, double y, double cz, double r, double h, String block) {
        int n = (int) Math.max(1, Math.round(h));
        double prev = r;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n, rr = r * Math.sqrt(Math.max(0, 1 - t * t));
            if (i == 0) disk(cx, y, cz, rr, block);
            else if (rr < 1.5) disk(cx, y + i, cz, rr, block);
            else ring(cx, y + i, cz, rr, Math.max(1.2, prev - rr + 0.8), block);
            prev = rr;
        }
    }

    /** An irregular flat blotch at height y of about radius r (ash, grass, sand, a puddle): a disk with a ragged edge. */
    protected void patch(double cx, double y, double cz, double r, String block) {
        double[] edge = new double[16];
        for (int i = 0; i < edge.length; i++) edge[i] = r * (0.65 + 0.45 * random());
        for (int x = (int) Math.floor(cx - r - 1); x <= Math.ceil(cx + r + 1); x++)
            for (int z = (int) Math.floor(cz - r - 1); z <= Math.ceil(cz + r + 1); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                double a = Math.atan2(dz, dx) / (2 * Math.PI) * edge.length;
                int i0 = Math.floorMod((int) Math.floor(a), edge.length), i1 = (i0 + 1) % edge.length;
                double f = a - Math.floor(a), lim = edge[i0] * (1 - f) + edge[i1] * f;
                if (Math.sqrt(dx * dx + dz * dz) <= lim) block(x, y, z, block);
            }
    }

    /**
     * n boulders lying on the floor at height y, centred at random in x1..x2 by z1..z2, each a squashed ball of radius
     * rmin..rmax (a blend from {@link #mix} gives them mottled colours).
     */
    protected void rocks(int n, double x1, double z1, double x2, double z2, double y, double rmin, double rmax, String block) {
        for (int i = 0; i < n; i++) {
            double r = rmin + random() * (rmax - rmin);
            ellipsoid(Math.min(x1, x2) + random() * Math.abs(x2 - x1), y + r * 0.45, Math.min(z1, z2) + random() * Math.abs(z2 - z1), r, r * 0.7, r, block);
        }
    }

    /**
     * Scatters n single blocks in the region (embers, rubble, flowers, snow). A block only lands in an empty cell that
     * rests on something (the floor or a block), so it never leaves a floating speck.
     */
    protected void sprinkle(int n, double x1, double y1, double z1, double x2, double y2, double z2, String block) {
        for (int i = 0; i < n; i++) {
            double x = Math.min(x1, x2) + random() * Math.abs(x2 - x1), y = Math.min(y1, y2) + random() * Math.abs(y2 - y1),
                    z = Math.min(z1, z2) + random() * Math.abs(z2 - z1);
            if (get(x, y, z).equals("air") && (Math.floor(y) <= 0 || !get(x, y - 1, z).equals("air"))) block(x, y, z, block);
        }
    }

    /** An upright column of h cells on y, with an optional different block on top (null for none). */
    protected void pillar(double x, double y, double z, double h, String block, String cap) {
        int n = (int) Math.max(1, Math.round(h));
        for (int i = 0; i < n; i++) block(x, y + i, z, i == n - 1 && cap != null ? cap : block);
    }

    /** A tree with its trunk's foot at (x, y, z), h tall, with a round crown of leaves on top. */
    protected void tree(double x, double y, double z, double h, String trunk, String leaves) {
        int n = (int) Math.max(2, Math.round(h));
        for (int i = 0; i < n; i++) block(x, y + i, z, trunk);
        double r = Math.max(1.5, n * 0.35);
        ellipsoid(Math.floor(x) + 0.5, y + n - 1, Math.floor(z) + 0.5, r, r * 0.8, r, leaves);
        block(x, y + n - 1, z, trunk);
    }

    /** A bare tree: a trunk h tall with a few short branches (and a few leaves if {@code leaves} is not null). */
    protected void deadTree(double x, double y, double z, double h, String log, String leaves) {
        int n = (int) Math.max(3, Math.round(h));
        for (int i = 0; i < n; i++) block(x, y + i, z, log);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int k = 0; k < dirs.length; k++) {
            int by = n - 2 - (k % 2);
            block(x + dirs[k][0], y + by, z + dirs[k][1], log);
            if (leaves != null && random() < 0.7) block(x + dirs[k][0] * 2, y + by, z + dirs[k][1] * 2, leaves);
        }
    }

    /**
     * A plume (smoke, steam, a cloud trail) of n puffs from (x, y, z), each one moving (dx, dy, dz) from the last, hollow
     * balls whose radius grows from r0 to r1.
     */
    protected void plume(double x, double y, double z, double dx, double dy, double dz, int n, double r0, double r1, String block) {
        for (int i = 0; i < n; i++) {
            double r = n <= 1 ? r0 : r0 + (r1 - r0) * i / (n - 1);
            if (r < 2) sphere(x + dx * i, y + dy * i, z + dz * i, r, block);
            else shell(x + dx * i, y + dy * i, z + dz * i, r, 1.2, block);
        }
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
        if (block != null && block.indexOf('|') >= 0) {
            String[] parts = block.split("\\|");
            block = parts[rng.nextInt(parts.length)];
        }
        String b = block == null ? "" : block.trim().toLowerCase().replace("minecraft:", "");
        String props = "";
        int br = b.indexOf('[');
        if (br >= 0) {
            props = b.substring(br);
            b = b.substring(0, br).trim();
        }
        if (b.equals("air")) return Blueprint.AIR;
        if (!palette.has(b)) {
            unknown.merge(b, 1, Integer::sum);
            return -1;
        }
        return bp.idOf("minecraft:" + b + state(b, props));
    }

    /** The state suffix for a stair or slab from "[facing=east,half=top]" (bad or missing values fall back); "" for others. */
    private String state(String b, String props) {
        boolean stairs = b.endsWith("_stairs"), slab = b.endsWith("_slab");
        // with every block allowed, other states ("[axis=x]", "[half=top,open=true]") are kept as written; the game
        // drops values a block does not have when it is placed
        if (!stairs && !slab) return palette.level == Palette.Level.ALL ? props.replace(" ", "") : "";
        Map<String, String> p = new LinkedHashMap<>();
        for (String kv : props.replace("[", "").replace("]", "").split(",")) {
            int e = kv.indexOf('=');
            if (e > 0) p.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
        }
        if (stairs) {
            String facing = choose(b, "facing", p.get("facing"), "north", "north", "south", "east", "west");
            String half = choose(b, "half", p.get("half"), "bottom", "bottom", "top");
            return "[facing=" + facing + ",half=" + half + ",shape=straight,waterlogged=false]";
        }
        String type = choose(b, "type", p.get("type"), "bottom", "bottom", "top");
        return "[type=" + type + ",waterlogged=false]";
    }

    private String choose(String b, String prop, String value, String fallback, String... allowed) {
        if (value == null) return fallback;
        for (String a : allowed)
            if (a.equals(value)) return value;
        badState.merge(b + " " + prop + "=" + value, 1, Integer::sum);
        return fallback;
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
