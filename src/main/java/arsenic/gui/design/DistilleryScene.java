package arsenic.gui.design;

import static arsenic.gui.design.PixelArt.PAL;
import static arsenic.gui.design.PixelArt.rect;

import arsenic.utils.java.SoundUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The rail's pixel-art scene: a little distillery on a lab bench, in the style of the Element main
 * menu. A burner heats a round flask; vapour runs along the tube and down the condenser's coil, and
 * drops fall into a beaker that slowly fills (and empties again when full).
 * <p>
 * Depth: a tiled back wall that drifts against the props as the mouse moves (parallax), warm light
 * thrown by the flame, contact shadows on the bench, dust motes in the light and steam off the beaker.
 * <p>
 * Interactive: hover lifts a shelf bottle; clicking a bottle pops its cork and fizzes it, the burner
 * roars, the flask boils over, the condenser rushes and the beaker gets poured out.
 * <p>
 * The scene is 54 x 54 pixels, scaled to the largest whole-screen-pixel size that fits the box, centred
 * and standing on the box's bottom edge, so nothing is cut off.
 */
public final class DistilleryScene {

    private DistilleryScene() {}

    /** The static artwork; everything that moves is drawn over it. */
    private static final String[] BASE = {
            "                                                      ",
            "             b                                        ",
            "            fbf                     b                 ",
            "       b    f f                    fbf                ",
            "      fbf   f  f            b      f f                ",
            "      f f   fy f           fbf    f   f               ",
            "     f   f  f  f   ffff    f f    fy  f   fff         ",
            "     f   f  f  f   f  f    f  f   f   f   f f         ",
            "     fpppf  fggf  f    f   f  f   f   f   f  f        ",
            "     fPppf  fGgf  fqqqqf   foof   fqqqf   fppf        ",
            "     fpppf  fggf  fQqqqf   fOof   fQqqf   fPpf        ",
            "     fffff  ffff  ffffff   ffff   fffff   ffff        ",
            "  tttttttttttttttttttttttttttttttttttttttttttttttttt  ",
            "  TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT  ",
            "    T                                            T    ",
            "    T                                            T    ",
            "                                                      ",
            "         fffffffffffffffffffffff                      ",
            "        fff                     f                     ",
            "        f f                      f                    ",
            "        f f                       f                   ",
            "        f f                       FFFFFF              ",
            "        f f                       FQQ  F              ",
            "       ff ff                      F  QQF m            ",
            "      f     f                     F    F m            ",
            "     f       f                    FQQ  F m            ",
            "    f         f                   F  QQF m            ",
            "    f         f                   F    mmm            ",
            "    fqqqqqqqqqf                   FQQ  F m            ",
            "    fqqqQqqqqqf                   F  QQF m            ",
            "    fqqqqqqQqqf                   F    F m            ",
            "     fqqqqqqqf                    FQQ  F m            ",
            "      fqqqqqf                     F  QQF m            ",
            "       fffff                      F    F m            ",
            "    mMMMMMMMMMm                   FQQ  F m            ",
            "    m         m                   F  QQF m            ",
            "    m         m                   F    F m            ",
            "    m         m                   FQQ  F m            ",
            "    m         m                   FFFQQF m            ",
            "    m         m                     f    m            ",
            "    m   MMM   m                 f        f            ",
            "    m   MMM   m                 f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f            ",
            "   m   xxxxx   m                f        f  eeeeeeee  ",
            "   m  mmmmmmm  m                f        f  eEEEEEEe  ",
            "   m  mmmmmmm  m                ffffffffff  EEEEEEEE  ",
            "tttttttttttttttttttttttttttttttttttttttttttttttttttttt",
            "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
            " cccccccccccccccccccccccccdcccccccccccccccccccccccccc ",
            " cccccccccnnccccccccccccccdccccccccccccccnncccccccccc ",
            " cccccccccccccccccccccccccdcccccccccccccccccccccccccc "};

    private static final int SW = 54, SH = 54;

    // where the moving parts sit, in scene pixels
    private static final int FLAME_X = 8, FLAME_Y = 39;
    private static final int FLASK_SURFACE = 28, FLASK_BOTTOM = 32, FLASK_NECK_X = 9, FLASK_NECK_TOP = 18;
    private static final int TUBE_Y = 17, TUBE_X1 = 10, TUBE_X2 = 31;
    private static final int OUTLET_X = 36, OUTLET_Y = 39;
    private static final int BEAKER_X1 = 33, BEAKER_X2 = 40, BEAKER_TOP = 40, BEAKER_BOTTOM = 47, BEAKER_ROWS = 7;
    private static final int BENCH_TOP = 49, SHELF_TOP = 12;

    // clickable areas: x1, y1, x2, y2 (inclusive)
    private static final int[] HIT_BURNER = {4, 34, 14, 48};
    private static final int[] HIT_FLASK = {3, 17, 15, 33};
    private static final int[] HIT_CONDENSER = {33, 20, 40, 38};
    private static final int[] HIT_BEAKER = {31, 39, 42, 48};

    /** Shelf bottles cut out of the artwork so each can lift, wobble and pop on its own. */
    private static final class Bottle {
        int x1, x2;                 // columns in the scene
        String[] rows;              // rows 0..SHELF_TOP-1 of just this bottle
        int liquidX = -1, liquidY = -1, corkX = -1, corkY = -1;
        float lift, wobble, fizz, corkUp, corkV;
        boolean corkFlying;
    }

    private static final String[] BACKDROP;     // the artwork minus the shelf bottles
    private static final List<Bottle> BOTTLES = new ArrayList<>();
    /** Coil pixels inside the condenser, in order, so the flow can light them in turn. */
    private static final int[][] COIL;

    static {
        char[][] g = new char[SH][];
        for (int y = 0; y < SH; y++) g[y] = BASE[y].toCharArray();
        // group the columns above the shelf into bottles
        int x = 0;
        while (x < SW) {
            if (!columnUsed(x)) { x++; continue; }
            Bottle b = new Bottle();
            b.x1 = x;
            while (x < SW && columnUsed(x)) x++;
            b.x2 = x - 1;
            b.rows = new String[SHELF_TOP];
            for (int y = 0; y < SHELF_TOP; y++) {
                StringBuilder row = new StringBuilder();
                for (int c = b.x1; c <= b.x2; c++) {
                    char ch = g[y][c];
                    row.append(ch);
                    if ("qpgo".indexOf(ch) >= 0 && b.liquidY < y) { b.liquidX = c; b.liquidY = y; }
                    if (ch == 'b' && b.corkY < 0) { b.corkX = c; b.corkY = y; }
                    g[y][c] = ' ';
                }
                b.rows[y] = row.toString();
            }
            BOTTLES.add(b);
        }
        BACKDROP = new String[SH];
        for (int y = 0; y < SH; y++) BACKDROP[y] = new String(g[y]);

        List<int[]> coil = new ArrayList<>();
        for (int y = 22; y <= 38; y++)
            for (int c = 35; c <= 38; c++)
                if (BASE[y].charAt(c) == 'Q')
                    coil.add(new int[]{c, y});
        COIL = coil.toArray(new int[0][]);
    }

    private static boolean columnUsed(int x) {
        for (int y = 0; y < SHELF_TOP; y++)
            if (BASE[y].charAt(x) != ' ')
                return true;
        return false;
    }

    // ---- state ----

    private static final Random RNG = new Random(0xD15L);
    private static long lastNanos;
    private static float time, hoverE, bubbleTimer, dripTimer = 0.8f, shelfTimer = 1.5f, moteTimer;
    private static float level = 1.5f;
    private static boolean draining;
    /** Seconds left on each click effect. */
    private static float roar, rush, boilOver;
    /** Cursor in scene pixels (-1 when away), and where the scene was last drawn. */
    private static float mouseSX = -1, mouseSY = -1, parallax;
    private static float lastOx, lastOy, lastP, lastClipTop;
    private static boolean lastHovered;

    private static final class Mote { float x, y, vx, vy, age, life; boolean live; int kind; }

    private static final Mote[] BUBBLES = new Mote[12];
    private static final Mote[] DRIPS = new Mote[3];
    private static final Mote[] FIZZ = new Mote[10];
    private static final Mote[] SPLASH = new Mote[12];
    private static final Mote[] DUST = new Mote[7];
    private static final Mote[] STEAM = new Mote[6];

    static {
        for (Mote[] pool : new Mote[][]{BUBBLES, DRIPS, FIZZ, SPLASH, DUST, STEAM})
            for (int i = 0; i < pool.length; i++) pool[i] = new Mote();
    }

    // ---- public API ----

    public static void draw(float x1, float y1, float x2, float y2, boolean hovered, float mouseX, float mouseY) {
        float bw = x2 - x1, bh = y2 - y1;
        if (bw < 60f || bh < 50f)
            return;
        // the largest pixel that fits the whole scene, rounded to whole screen pixels so the art stays crisp
        float s = arsenic.utils.render.GuiCanvas.scale();
        float p = Math.min(bw / SW, bh / SH);
        p = Math.max(1f, (float) Math.floor(p * s)) / s;
        // centred horizontally, standing on the bottom edge, aligned to the screen's pixel grid
        float ox = snap(x1 + (bw - SW * p) / 2f, s, arsenic.utils.render.GuiCanvas.offsetX());
        float oy = snap(y2 - SH * p, s, arsenic.utils.render.GuiCanvas.offsetY());
        lastOx = ox; lastOy = oy; lastP = p; lastClipTop = y1; lastHovered = hovered;
        mouseSX = hovered ? (mouseX - ox) / p : -1;
        mouseSY = hovered ? (mouseY - oy) / p : -1;
        step(hovered, mouseX, x1, x2);

        PixelArt.palette();
        PixelArt.begin();
        PixelArt.clipTop = y1;
        try {
            drawWall(ox, oy, p);
            drawLight(ox, oy, p);
            PixelArt.sprite(BACKDROP, ox, oy, p, 255);
            drawShadows(ox, oy, p);
            drawBottles(ox, oy, p);
            drawFlame(ox, oy, p);
            drawFlask(ox, oy, p);
            drawFlow(ox, oy, p);
            drawBeaker(ox, oy, p);
            drawAir(ox, oy, p);
        } finally {
            PixelArt.clipTop = Float.NEGATIVE_INFINITY;
            PixelArt.end();
        }
    }

    private static float snap(float v, float scale, float offset) {
        return (Math.round(v * scale + offset) - offset) / scale;
    }

    /** Handles a click at GUI coordinates; true if it hit something in the scene. */
    public static boolean click(float mouseX, float mouseY) {
        if (lastP <= 0f || mouseY < lastClipTop)
            return false;
        float sx = (mouseX - lastOx) / lastP, sy = (mouseY - lastOy) / lastP;
        Bottle b = bottleAt(sx, sy);
        if (b != null) {
            b.wobble = 0.6f;
            b.fizz = 1.4f;
            if (b.corkY >= 0 && !b.corkFlying) {
                b.corkFlying = true;
                b.corkUp = 0f;
                b.corkV = 14f;
            }
            SoundUtils.chordEnum();
            return true;
        }
        if (in(HIT_BURNER, sx, sy)) {
            roar = 4f;
            SoundUtils.chordEnable();
            return true;
        }
        if (in(HIT_FLASK, sx, sy)) {
            boilOver = 1.2f;
            for (int i = 0; i < 6; i++)
                spawn(BUBBLES, 6 + RNG.nextInt(7), FLASK_BOTTOM - RNG.nextInt(3), 0, -(4f + RNG.nextFloat() * 3f), 0);
            for (int i = 0; i < 5; i++)
                spawn(SPLASH, FLASK_NECK_X, FLASK_NECK_TOP, (RNG.nextFloat() - 0.5f) * 10f, -(10f + RNG.nextFloat() * 8f), 1);
            SoundUtils.chordOpen();
            return true;
        }
        if (in(HIT_CONDENSER, sx, sy)) {
            rush = 3f;
            SoundUtils.chordSlider();
            return true;
        }
        if (in(HIT_BEAKER, sx, sy)) {
            if (level > 1.6f)
                draining = true;
            for (int i = 0; i < 4; i++)
                spawn(SPLASH, BEAKER_X1 + RNG.nextInt(BEAKER_X2 - BEAKER_X1 + 1), BEAKER_BOTTOM + 1 - level,
                        (RNG.nextFloat() - 0.5f) * 6f, -(5f + RNG.nextFloat() * 4f), 0);
            SoundUtils.chordDisable();
            return true;
        }
        return false;
    }

    // ---- simulation ----

    private static boolean in(int[] r, float x, float y) {
        return x >= r[0] && x <= r[2] + 1 && y >= r[1] && y <= r[3] + 1;
    }

    private static Bottle bottleAt(float sx, float sy) {
        if (sy < 0 || sy > SHELF_TOP)
            return null;
        for (Bottle b : BOTTLES)
            if (sx >= b.x1 && sx <= b.x2 + 1)
                return b;
        return null;
    }

    private static void step(boolean hovered, float mouseX, float x1, float x2) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        time += dt;
        hoverE += ((hovered ? 1f : 0f) - hoverE) * Math.min(1f, dt * 6f);
        roar = Math.max(0f, roar - dt);
        rush = Math.max(0f, rush - dt);
        boilOver = Math.max(0f, boilOver - dt);
        // the wall drifts up to a pixel against the props, following the cursor across the rail
        float target = hovered ? ((mouseX - x1) / Math.max(1f, x2 - x1) - 0.5f) * 2f : 0f;
        parallax += (target - parallax) * Math.min(1f, dt * 4f);

        float heat = 1f + hoverE * 0.6f + (roar > 0f ? 1.6f : 0f) + (boilOver > 0f ? 2f : 0f);
        float flow = 1f + hoverE * 0.6f + (rush > 0f ? 2.5f : 0f) + (roar > 0f ? 0.8f : 0f);

        // boiling
        bubbleTimer -= dt * heat;
        if (bubbleTimer <= 0f) {
            bubbleTimer = 0.18f + RNG.nextFloat() * 0.3f;
            spawn(BUBBLES, 7 + RNG.nextInt(5), FLASK_BOTTOM + 0.4f, 0, -(3f + RNG.nextFloat() * 2.5f), 0);
        }
        for (Mote b : BUBBLES) {
            if (!b.live) continue;
            b.y += b.vy * dt * heat * 0.7f;
            if (b.y < FLASK_SURFACE) b.live = false;
        }

        // drips from the condenser
        dripTimer -= dt * flow;
        if (dripTimer <= 0f && !draining) {
            dripTimer = 1.3f + RNG.nextFloat() * 0.5f;
            spawn(DRIPS, OUTLET_X, OUTLET_Y + 1, 0, 0, 0);
        }
        float surface = BEAKER_BOTTOM + 1 - level;
        for (Mote d : DRIPS) {
            if (!d.live) continue;
            d.vy += dt * 40f;
            d.y += d.vy * dt;
            if (d.y >= surface - 0.5f) {
                d.live = false;
                level = Math.min(BEAKER_ROWS, level + 0.12f);
                spawn(SPLASH, OUTLET_X, surface - 1, -3f, -5f, 0);
                spawn(SPLASH, OUTLET_X, surface - 1, 3f, -5f, 0);
            }
        }
        for (Mote s : SPLASH) {
            if (!s.live) continue;
            s.vy += dt * 40f;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.age += dt;
            if (s.age > 0.8f || s.y > BENCH_TOP) s.live = false;
        }
        if (level >= BEAKER_ROWS - 0.01f) draining = true;
        if (draining) {
            level -= dt * 1.6f;
            if (level <= 1.5f) {
                level = 1.5f;
                draining = false;
            }
        }

        // shelf: a random bottle fizzes now and then; hover lifts the one under the cursor
        shelfTimer -= dt;
        if (shelfTimer <= 0f) {
            shelfTimer = 1.4f + RNG.nextFloat() * 1.8f;
            Bottle b = BOTTLES.get(RNG.nextInt(BOTTLES.size()));
            b.fizz = Math.max(b.fizz, 0.35f);
        }
        Bottle over = hovered ? bottleAt(mouseSX, mouseSY) : null;
        for (Bottle b : BOTTLES) {
            b.lift += ((b == over ? 1f : 0f) - b.lift) * Math.min(1f, dt * 12f);
            b.wobble = Math.max(0f, b.wobble - dt);
            if (b.fizz > 0f) {
                b.fizz -= dt;
                if (b.liquidY >= 0 && RNG.nextFloat() < dt * 9f)
                    spawn(FIZZ, Math.min(b.x2 - 1, b.liquidX + RNG.nextInt(Math.max(1, b.x2 - b.liquidX - 1))),
                            b.liquidY, 0, -2.2f, 0);
            }
            if (b.corkFlying) {
                b.corkV -= dt * 45f;
                b.corkUp += b.corkV * dt;
                if (b.corkUp <= 0f && b.corkV < 0f) {
                    b.corkUp = 0f;
                    b.corkFlying = false;
                }
            }
        }
        for (Mote f : FIZZ) {
            if (!f.live) continue;
            f.y += f.vy * dt;
            f.age += dt;
            if (f.age > 1.1f) f.live = false;
        }

        // dust drifting through the lamp light, steam off a warm beaker
        moteTimer -= dt;
        if (moteTimer <= 0f) {
            moteTimer = 0.6f + RNG.nextFloat() * 0.8f;
            Mote m = spawn(DUST, 2 + RNG.nextInt(SW - 4), 14 + RNG.nextInt(30), (RNG.nextFloat() - 0.5f) * 1.2f,
                    -0.4f - RNG.nextFloat() * 0.6f, 0);
            m.life = 3f + RNG.nextFloat() * 2f;
            if (level > 2.5f || roar > 0f) {
                Mote s = spawn(STEAM, BEAKER_X1 + 1 + RNG.nextInt(BEAKER_X2 - BEAKER_X1 - 1), BEAKER_TOP - 1,
                        (RNG.nextFloat() - 0.5f) * 0.8f, -2.2f, 0);
                s.life = 1.6f;
            }
        }
        for (Mote[] pool : new Mote[][]{DUST, STEAM})
            for (Mote m : pool) {
                if (!m.live) continue;
                m.age += dt;
                m.x += m.vx * dt;
                m.y += m.vy * dt;
                if (m.age >= m.life) m.live = false;
            }
    }

    private static Mote spawn(Mote[] pool, float x, float y, float vx, float vy, int kind) {
        Mote slot = null;
        for (Mote m : pool)
            if (!m.live) { slot = m; break; }
        if (slot == null) {                 // pool full: take the oldest
            slot = pool[0];
            for (Mote m : pool) if (m.age > slot.age) slot = m;
        }
        slot.live = true;
        slot.x = x; slot.y = y; slot.vx = vx; slot.vy = vy;
        slot.age = 0f; slot.life = 1f; slot.kind = kind;
        return slot;
    }

    // ---- drawing ----

    private static void px(float ox, float oy, float p, float x, float y, char c, int a) {
        rect(ox + Math.round(x) * p, oy + Math.round(y) * p, p, p, PAL[c], a);
    }

    /** A faint tiled wall behind everything; it shifts a pixel with the cursor for a sense of depth. */
    private static void drawWall(float ox, float oy, float p) {
        int shift = Math.round(-parallax);
        int line = PixelArt.wallLine();
        for (int y = 0; y < BENCH_TOP; y++) {
            boolean mortarRow = y % 6 == 5;
            int offset = (y / 6) % 2 == 0 ? 0 : 4;
            if (mortarRow) {
                rect(ox, oy + y * p, SW * p, p, line, 34);
                continue;
            }
            for (int x = 0; x < SW; x++)
                if ((x + offset + shift + 64) % 8 == 0)
                    rect(ox + x * p, oy + y * p, p, p, line, 26);
        }
    }

    /** Warm light from the flame: pixel-stepped round rings, brighter toward the flame, flickering with it. */
    private static void drawLight(float ox, float oy, float p) {
        float heat = 0.7f + hoverE * 0.25f + (roar > 0f ? 0.6f : 0f) + 0.1f * (float) Math.sin(time * 17f);
        int cx = FLAME_X + 1, cy = FLAME_Y - 2;
        int[] radii = {17, 13, 9, 5};
        int[] alpha = {14, 16, 20, 26};
        for (int i = 0; i < radii.length; i++) {
            int r = radii[i];
            int a = (int) (heat * alpha[i]);
            for (int dy = -r; dy <= r; dy++) {
                int half = (int) Math.floor(Math.sqrt(r * r - dy * dy));
                rect(ox + (cx - half) * p, oy + (cy + dy) * p, (2 * half + 1) * p, p, PAL['o'], a);
            }
        }
    }

    /** Contact shadows under everything that stands on the bench, and under the shelf. */
    private static void drawShadows(float ox, float oy, float p) {
        int dark = PixelArt.shadow();
        rect(ox + 2 * p, oy + BENCH_TOP * p, 15 * p, p, dark, 110);
        rect(ox + 30 * p, oy + BENCH_TOP * p, 14 * p, p, dark, 110);
        rect(ox + 43 * p, oy + BENCH_TOP * p, 10 * p, p, dark, 90);
        rect(ox + 2 * p, oy + (SHELF_TOP + 2) * p, 50 * p, p, dark, 70);
        // the lit side of the flask stand and burner catches the flame
        rect(ox + 5 * p, oy + 34 * p, p, 14 * p, PAL['O'], 30);
    }

    private static void drawBottles(float ox, float oy, float p) {
        for (Bottle b : BOTTLES) {
            int dy = -Math.round(b.lift);
            int dx = b.wobble > 0f ? (((int) (b.wobble * 20f)) % 2 == 0 ? 1 : -1) : 0;
            for (int y = 0; y < SHELF_TOP; y++) {
                String row = b.rows[y];
                for (int i = 0; i < row.length(); i++) {
                    char ch = row.charAt(i);
                    if (ch == ' ') continue;
                    int cx = b.x1 + i + dx, cy = y + dy;
                    if (ch == 'b' && b.corkFlying) cy -= Math.round(b.corkUp);
                    px(ox, oy, p, cx, cy, ch, 255);
                }
            }
            if (b.lift > 0.5f)        // the lifted bottle leaves its shadow on the shelf
                rect(ox + b.x1 * p, oy + (SHELF_TOP - 1) * p, (b.x2 - b.x1 + 1) * p, p, PixelArt.shadow(), 80);
        }
        for (Mote f : FIZZ)
            if (f.live)
                px(ox, oy, p, f.x, f.y, 'y', (int) (220 * (1f - f.age / 1.1f)));
    }

    private static void drawFlame(float ox, float oy, float p) {
        int frame = (int) (time * (8f + hoverE * 6f + (roar > 0f ? 8f : 0f))) % 4;
        boolean big = hoverE > 0.5f || roar > 0f;
        int h = 3 + (big ? 1 : 0) + (roar > 0f ? 1 : 0) + (frame == 1 ? 1 : 0);
        int lean = frame == 2 ? -1 : frame == 3 ? 1 : 0;
        int cx = FLAME_X + 1;
        px(ox, oy, p, cx, FLAME_Y, 'r', 255);
        for (int i = 1; i < h; i++) {
            int x = cx + (i >= h - 2 ? lean : 0);
            boolean tip = i == h - 1;
            px(ox, oy, p, x, FLAME_Y - i, tip ? 'o' : 'O', 255);
            if (!tip) {
                px(ox, oy, p, x - 1, FLAME_Y - i, 'o', 255);
                px(ox, oy, p, x + 1, FLAME_Y - i, 'o', 255);
            }
        }
        if (roar > 0f && frame % 2 == 0)       // sparks while it roars
            px(ox, oy, p, cx + (frame == 0 ? -2 : 2), FLAME_Y - h, 'O', 200);
        if (frame % 2 == 0)
            px(ox, oy, p, cx, FLASK_BOTTOM + 1, 'O', (int) (90 + 80 * hoverE));
    }

    private static void drawFlask(float ox, float oy, float p) {
        int ripple = 6 + (int) ((time * (4f + hoverE * 4f + (boilOver > 0f ? 10f : 0f))) % 7);
        px(ox, oy, p, ripple, FLASK_SURFACE, 'Q', 255);
        if (boilOver > 0f)                       // froth rising into the neck
            for (int i = 0; i < 3; i++)
                px(ox, oy, p, FLASK_NECK_X, FLASK_SURFACE - 1 - i - (int) ((1.2f - boilOver) * 3) % 4, 'Q', 200);
        for (Mote b : BUBBLES) {
            if (!b.live) continue;
            int y = Math.round(b.y);
            int lo = y >= 31 ? (y == 32 ? 7 : 6) : 5, hi = y >= 31 ? (y == 32 ? 11 : 12) : 13;
            int x = Math.round(b.x) + ((int) (b.y * 2f) % 2);
            if (x >= lo && x <= hi)
                px(ox, oy, p, x, y, 'Q', 235);
        }
    }

    private static void drawFlow(float ox, float oy, float p) {
        float speed = 1f + hoverE * 0.6f + (rush > 0f ? 2.5f : 0f) + (roar > 0f ? 0.8f : 0f);
        int len = TUBE_X2 - TUBE_X1;
        int puffs = rush > 0f ? 3 : 2;
        for (int k = 0; k < puffs; k++) {
            int pos = (int) ((time * 9f * speed + k * len / (float) puffs) % (len + 3));
            if (pos <= len)
                px(ox, oy, p, TUBE_X1 + pos, TUBE_Y, 'Q', 220);
            else
                px(ox, oy, p, TUBE_X2 + (pos - len), TUBE_Y + (pos - len), 'Q', 220);
        }
        int band = (int) (time * 7f * speed) % (COIL.length + 4);
        for (int i = 0; i < COIL.length; i++) {
            int d = band - i;
            if (d >= 0 && d < 3)
                px(ox, oy, p, COIL[i][0], COIL[i][1], 'y', 200 - d * 60);
        }
    }

    private static void drawBeaker(float ox, float oy, float p) {
        int rows = (int) Math.floor(level);
        // a slosh: while pouring or splashed, the surface tilts a pixel back and forth
        int tilt = draining ? (((int) (time * 6f)) % 2 == 0 ? 1 : -1) : 0;
        for (int r = 0; r < rows; r++) {
            int y = BEAKER_BOTTOM - r;
            boolean top = r == rows - 1;
            rect(ox + BEAKER_X1 * p, oy + y * p, (BEAKER_X2 - BEAKER_X1 + 1) * p, p, PAL[top ? 'Q' : 'q'], 255);
            if (top && tilt != 0)
                px(ox, oy, p, tilt > 0 ? BEAKER_X2 : BEAKER_X1, y - 1, 'Q', 255);
        }
        float frac = level - rows;
        if (frac > 0.05f && rows < BEAKER_ROWS)
            rect(ox + BEAKER_X1 * p, oy + (BEAKER_BOTTOM - rows) * p, (BEAKER_X2 - BEAKER_X1 + 1) * p, p,
                    PAL['Q'], (int) (200 * frac));
        // a glass highlight down the left wall gives it some roundness
        rect(ox + BEAKER_X1 * p, oy + (BEAKER_TOP + 1) * p, p, 4 * p, PAL['y'], 70);
        for (Mote d : DRIPS)
            if (d.live)
                px(ox, oy, p, d.x, d.y, 'q', 255);
        for (Mote s : SPLASH)
            if (s.live)
                px(ox, oy, p, s.x, s.y, 'Q', (int) (255 * (1f - s.age / 0.8f)));
    }

    private static void drawAir(float ox, float oy, float p) {
        for (Mote m : DUST) {
            if (!m.live) continue;
            float t = m.age / m.life;
            int a = (int) (80 * Math.sin(Math.PI * t));
            px(ox, oy, p, m.x, m.y, 'O', a);
        }
        for (Mote m : STEAM) {
            if (!m.live) continue;
            float t = m.age / m.life;
            px(ox, oy, p, m.x + (float) Math.sin(m.age * 5f) * 0.6f, m.y, 'f', (int) (120 * (1f - t)));
        }
    }
}
