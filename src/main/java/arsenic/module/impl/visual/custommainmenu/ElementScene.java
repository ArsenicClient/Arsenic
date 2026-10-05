package arsenic.module.impl.visual.custommainmenu;

import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.QuadBatch;
import arsenic.utils.render.RenderContext;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * "Element 33": a crystal lab built around arsenic itself. A faint hexagonal lattice drifts behind
 * everything; lamps throw beams through haze; atoms with orbiting electrons drift through the room
 * while glowing crystal shards hover and turn in quarter turns; glassware and a hazard crate stand
 * on a dark bench and let off vapour; hazard drones sweep the room and scan the atoms.
 *
 * Every colour is derived from the theme colours passed in, so the whole scene recolours with the
 * client theme. Everything is a rectangle, like the other scenes, and nothing rotates smoothly:
 * crystals and atoms only turn in 90 degree steps.
 */
public final class ElementScene {

    // ---- sprites ----------------------------------------------------------------------------------

    private static final String[] DRONE = {
            "   y y   ",
            "  ddddd  ",
            " ddddddd ",
            " dyeeeyd ",
            " ddddddd ",
            "  d   d  "};

    private static final String[] CRATE_BODY = {
            "yyddyyddyyddyy",
            "ykkkkkkkkkkkky",
            "dkkkkggkkkkkkd",
            "dkkkkggkkkkkkd",
            "ykkkkkkkkkkkky",
            "yyddyyddyyddyy"};
    private static final String[] CRATE_LID = {
            "yyddyyddyyddyy",
            "dyyddyyddyyddy"};

    private static final String[] FLASK = buildFlask(13, 17);
    private static final String[] BEAKER = buildBeaker(11, 13);

    private static final int P = 3;                        // pixel size of the pixel-art props
    private static final int TILES_DRIFTING = 4;
    private static final float FLOOR_H = 30f;

    // ---- entities ---------------------------------------------------------------------------------

    private static final class Atom {
        float x, y, vx, phase, spin, fx, fy, nextTurn;
        int quarter, layer;
        float size;
    }

    private static final class Shard {
        float x, y, phase, boost, nextTurn, flash;
        int variant, rot;
    }

    private static final class Prop {
        int kind;                    // 0 flask, 1 crate, 2 beaker
        float fx;                    // horizontal position as a fraction of the width
        float openUntil;
        float l, t, r, b;            // last drawn bounds, for clicks
        float bubbleTimer;
    }

    private static final class Puff {
        float x, y, size, life, max, vx;
    }

    private static final class Spark {
        float x, y, vx, vy, life, max;
        boolean fragment;
        int colour;
    }

    private static final class Drip {
        float x, y, v;
    }

    private static final class Drone {
        float x, y, heading, speed, age, retarget, eye, cooldown;
        int scans;
        boolean leaving;
        Atom target;
    }

    private static final class FarCrystal {
        float x, w, h;
        String[] sprite;
    }

    private static final class Hex {
        float x, y, size, v, phase;
    }

    private final Random rnd = new Random(33);
    private final List<Atom> atoms = new ArrayList<>();
    private final List<Shard> shards = new ArrayList<>();
    private final List<Prop> props = new ArrayList<>();
    private final List<Puff> puffs = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Drip> drips = new ArrayList<>();
    private final List<Drone> drones = new ArrayList<>();
    private final List<FarCrystal> farCrystals = new ArrayList<>();
    private final List<Hex> hexes = new ArrayList<>();
    private final String[][][] shardSprites = new String[3][4][];
    private final String[][] spireSprites = new String[3][];

    private int w, h;
    private float time;
    private float nextDrone = 7f, nextGiant = 18f, nextDrip = 2f, nextAuto = 9f, puffTimer;
    private boolean giantActive;
    private float giantX, giantY;
    private int giantDir;
    private float lastParX, lastParY;

    private int main, bg;

    // vertices collected between begin and end, in the 1.8 draw mode they were written for
    private int mode;
    private float[] vx = new float[256], vy = new float[256];
    private int[] vc = new int[256];
    private int count;

    // ---- colours (all derived from the theme) -----------------------------------------------------

    private int light(float t) {
        return ColorUtils.mixRgb(main, 0xFFFFFF, t);
    }

    private int dark(float t) {
        return ColorUtils.mixRgb(main, 0x000000, t);
    }

    private int[] crystalPalette() {
        int[] p = new int[128];
        p['b'] = main;
        p['l'] = light(0.55f);
        p['d'] = dark(0.45f);
        p['e'] = light(0.85f);
        p['g'] = light(0.4f);
        p['y'] = main;
        p['k'] = ColorUtils.mixRgb(bg, 0x000000, 0.55f);
        return p;
    }

    // ---- procedural sprites -----------------------------------------------------------------------

    /** A faceted prism with a pointed top: light left facet, mid face, dark right facet, bright edge. */
    private static String[] buildCrystal(int width, int height) {
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');
        float taper = height * 0.32f;
        for (int r = 0; r < height; r++) {
            float hw = r < taper ? Math.max(0.6f, (width / 2f) * (r + 1) / taper) : width / 2f;
            if (r >= height - 2) hw = Math.max(1f, hw - (r - (height - 3)) * 0.8f);
            for (int c = 0; c < width; c++) {
                float dx = c + 0.5f - width / 2f;
                if (Math.abs(dx) > hw) continue;
                char ch = dx < -hw / 3f ? 'l' : (dx < hw / 3f ? 'b' : 'd');
                if (dx < -hw + 1f) ch = 'e';
                g[r][c] = ch;
            }
        }
        String[] out = new String[height];
        for (int r = 0; r < height; r++) out[r] = new String(g[r]);
        return out;
    }

    private static String[] rotate90(String[] s) {
        int rows = s.length, cols = 0;
        for (String r : s) cols = Math.max(cols, r.length());
        char[][] g = new char[cols][rows];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < s[r].length(); c++)
                g[c][rows - 1 - r] = s[r].charAt(c);
        String[] out = new String[cols];
        for (int i = 0; i < cols; i++) out[i] = new String(g[i]);
        return out;
    }

    /** A round-bottomed flask: glass outline, narrow neck, lip. 'q' marks where liquid can sit. */
    private static String[] buildFlask(int width, int height) {
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');
        int neckW = 3, neckRows = 5, cx = width / 2;
        for (int r = 0; r < height; r++) {
            if (r < neckRows) {
                for (int c = cx - neckW / 2; c <= cx + neckW / 2; c++) g[r][c] = (c == cx - neckW / 2 || c == cx + neckW / 2) ? 'g' : ' ';
                if (r == 0) for (int c = cx - neckW / 2 - 1; c <= cx + neckW / 2 + 1; c++) g[r][c] = 'g';
            } else {
                float t = (r - neckRows) / (float) (height - neckRows - 1);
                float hw = (float) (neckW / 2.0 + (width / 2.0 - neckW / 2.0) * Math.sqrt(Math.max(0, 1 - Math.pow(1 - t * 1.05, 2))));
                for (int c = 0; c < width; c++) {
                    float dx = Math.abs(c + 0.5f - width / 2f);
                    if (dx <= hw) g[r][c] = dx > hw - 1f ? 'g' : 'q';
                }
            }
        }
        String[] out = new String[height];
        for (int r = 0; r < height; r++) out[r] = new String(g[r]);
        return out;
    }

    private static String[] buildBeaker(int width, int height) {
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++) {
                boolean edge = c == 0 || c == width - 1 || r == height - 1;
                if (edge) g[r][c] = 'g';
                else g[r][c] = 'q';
            }
        for (int c = -1; c <= width; c++) if (c >= 0 && c < width) g[0][c] = 'g';
        for (int r = 2; r < height - 2; r += 3) g[r][1] = 'g';        // graduations
        String[] out = new String[height];
        for (int r = 0; r < height; r++) out[r] = new String(g[r]);
        return out;
    }

    // ---- setup ------------------------------------------------------------------------------------

    public void resize(int w, int h) {
        if (w == this.w && h == this.h && !atoms.isEmpty()) return;
        this.w = w;
        this.h = h;
        atoms.clear();
        shards.clear();
        props.clear();
        puffs.clear();
        sparks.clear();
        drips.clear();
        drones.clear();
        farCrystals.clear();
        hexes.clear();

        int[] widths = {9, 7, 11}, heights = {17, 13, 21};
        for (int v = 0; v < 3; v++) {
            String[] s = buildCrystal(widths[v], heights[v]);
            for (int r = 0; r < 4; r++) {
                shardSprites[v][r] = s;
                s = rotate90(s);
            }
            spireSprites[v] = buildCrystal(widths[v] + 4, heights[v] + 8);
        }

        for (int i = 0; i < 8; i++) {
            Atom a = new Atom();
            a.layer = i < 2 ? 0 : 1;
            a.x = rnd.nextFloat() * w;
            a.y = h * (0.12f + rnd.nextFloat() * 0.5f);
            a.vx = (a.layer == 0 ? 5 : 9) + rnd.nextFloat() * 12;
            if (rnd.nextBoolean()) a.vx = -a.vx;
            a.phase = rnd.nextFloat() * 6.28f;
            a.size = a.layer == 0 ? 0.7f : 1f + rnd.nextFloat() * 0.35f;
            a.nextTurn = 4 + rnd.nextFloat() * 8;
            atoms.add(a);
        }
        for (int i = 0; i < 5; i++) {
            Shard s = new Shard();
            s.x = w * (0.08f + 0.84f * (i + rnd.nextFloat() * 0.6f) / 5f);
            s.y = h * (0.18f + rnd.nextFloat() * 0.4f);
            s.phase = rnd.nextFloat() * 6.28f;
            s.variant = rnd.nextInt(3);
            s.rot = rnd.nextInt(4);
            s.nextTurn = 3 + rnd.nextFloat() * 6;
            shards.add(s);
        }
        int[] kinds = {0, 2, 1, 0, 2};
        float[] fx = {0.14f, 0.21f, 0.70f, 0.86f, 0.93f};
        for (int i = 0; i < kinds.length; i++) {
            Prop p = new Prop();
            p.kind = kinds[i];
            p.fx = fx[i];
            props.add(p);
        }
        for (int i = 0; i < 14; i++) {
            FarCrystal c = new FarCrystal();
            c.x = w * (i + rnd.nextFloat()) / 14f;
            c.w = 10 + rnd.nextFloat() * 14;
            c.h = 22 + rnd.nextFloat() * 40;
            c.sprite = buildCrystal(5 + rnd.nextInt(4), 10 + rnd.nextInt(10));
            farCrystals.add(c);
        }
        for (int i = 0; i < 10; i++) {
            Hex hx = new Hex();
            hx.x = rnd.nextFloat() * w;
            hx.y = rnd.nextFloat() * h;
            hx.size = 5 + rnd.nextFloat() * 9;
            hx.v = 3 + rnd.nextFloat() * 7;
            hx.phase = rnd.nextFloat() * 6.28f;
            hexes.add(hx);
        }
    }

    private float floorTop() {
        return h - FLOOR_H;
    }

    // ---- interaction ------------------------------------------------------------------------------

    /** A click at GUI coordinates; returns true if it landed on something in the scene. */
    public boolean click(float mx, float my) {
        // the floor is scenery
        for (Prop p : props) {
            if (mx >= p.l - 3 && mx <= p.r + 3 && my >= p.t - 8 && my <= p.b + 3) {
                if (time < p.openUntil) p.openUntil = time;
                else openProp(p, 5f);
                return true;
            }
        }
        for (Atom a : atoms) {
            if (a.layer != 1) continue;
            float ax = a.x + lastParX * 24, ay = a.y + lastParY * 24, r = 30f * a.size;
            if ((mx - ax) * (mx - ax) + (my - ay) * (my - ay) < r * r) {
                float away = ax < mx ? -1f : 1f;
                a.fx += away * 340f;
                a.fy += (rnd.nextFloat() - 0.5f) * 220f;
                a.spin = 1f;
                a.quarter++;
                burst(ax, ay, 8);
                return true;
            }
        }
        for (Shard s : shards) {
            String[] sp = shardSprites[s.variant][s.rot];
            float sx = s.x + lastParX * 20, sy = s.y + lastParY * 20 + (float) Math.sin(time * 1.1f + s.phase) * 6f;
            float sw = spriteW(sp) * P, sh = sp.length * P;
            if (mx >= sx - 6 && mx <= sx + sw + 6 && my >= sy - 6 && my <= sy + sh + 6) {
                s.boost = 1f;
                s.flash = 0.5f;
                burst(sx + sw / 2, sy + sh / 2, 6);
                return true;
            }
        }
        if (my >= floorTop()) return true;
        burst(mx, my, 8);
        return false;
    }

    private void openProp(Prop p, float seconds) {
        p.openUntil = time + seconds;
        float cx = (p.l + p.r) / 2f;
        for (int i = 0; i < 14; i++) {
            Spark s = new Spark();
            s.x = cx + (rnd.nextFloat() - 0.5f) * 14;
            s.y = p.t;
            s.vx = (rnd.nextFloat() - 0.5f) * 90;
            s.vy = -(40 + rnd.nextFloat() * 90);
            s.max = s.life = 0.9f + rnd.nextFloat() * 0.7f;
            s.colour = rnd.nextInt(3) == 0 ? 0xFFFFFF : 0;
            sparks.add(s);
        }
        for (int i = 0; i < 9; i++) {
            Spark s = new Spark();
            s.x = cx + (rnd.nextFloat() - 0.5f) * 10;
            s.y = p.t - 2;
            s.vx = (rnd.nextFloat() - 0.5f) * 70;
            s.vy = -(60 + rnd.nextFloat() * 70);
            s.max = s.life = 2.2f;
            s.fragment = true;
            sparks.add(s);
        }
        for (int i = 0; i < 6; i++) emitPuff(cx + (rnd.nextFloat() - 0.5f) * 8, p.t);
    }

    private void burst(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            s.x = x;
            s.y = y;
            double a = rnd.nextFloat() * Math.PI * 2;
            float sp = 30 + rnd.nextFloat() * 80;
            s.vx = (float) Math.cos(a) * sp;
            s.vy = (float) Math.sin(a) * sp;
            s.max = s.life = 0.5f + rnd.nextFloat() * 0.6f;
            sparks.add(s);
        }
    }

    private void emitPuff(float x, float y) {
        Puff p = new Puff();
        p.x = x;
        p.y = y;
        p.size = 3 + rnd.nextFloat() * 2;
        p.vx = (rnd.nextFloat() - 0.5f) * 10;
        p.max = p.life = 2.6f + rnd.nextFloat() * 1.8f;
        puffs.add(p);
    }

    // ---- update -----------------------------------------------------------------------------------

    public void update(float dt, float mouseX, float mouseY) {
        time += dt;

        for (Atom a : atoms) {
            a.x += (a.vx + a.fx) * dt;
            a.y += ((float) Math.sin(time * 0.7f + a.phase) * 5f + a.fy) * dt;
            float decay = (float) Math.exp(-2.2f * dt);
            a.fx *= decay;
            a.fy *= decay;
            a.spin = Math.max(0f, a.spin - dt * 0.7f);
            if (a.layer == 1) {
                scare(a, mouseX, mouseY, 80f, 240f, dt);
                for (Drone d : drones) scare(a, d.x, d.y + 20, 110f, 360f, dt);
            }
            a.y = Math.max(h * 0.08f, Math.min(h * 0.68f, a.y));
            if (a.vx > 0 && a.x > w + 60) a.x = -60;
            if (a.vx < 0 && a.x < -60) a.x = w + 60;
            if (time >= a.nextTurn) {
                a.quarter++;
                a.nextTurn = time + 5 + rnd.nextFloat() * 9;
            }
        }
        for (Shard s : shards) {
            s.y -= s.boost * 90f * dt;
            s.boost = Math.max(0f, s.boost - dt * 1.2f);
            s.flash = Math.max(0f, s.flash - dt);
            if (s.y < -30) s.y = h * 0.6f;
            if (time >= s.nextTurn) {
                s.rot = (s.rot + 1) & 3;              // quarter turns only
                s.flash = 0.3f;
                s.nextTurn = time + 4 + rnd.nextFloat() * 6;
            }
        }

        updateDrones(dt);

        // glassware lets off vapour, and the crate leaks a little when open
        puffTimer -= dt;
        if (puffTimer <= 0f) {
            puffTimer = 0.28f;
            for (Prop p : props) {
                float cx = (p.l + p.r) / 2f;
                if (time < p.openUntil) emitPuff(cx, p.t);
                else if (p.kind != 1 && rnd.nextInt(3) == 0) emitPuff(cx, p.t - 2);
            }
            emitPuff(w * (0.35f + rnd.nextFloat() * 0.3f), floorTop());       // floor vent
        }
        Iterator<Puff> pi = puffs.iterator();
        while (pi.hasNext()) {
            Puff p = pi.next();
            p.life -= dt;
            p.y -= (16 + (1f - p.life / p.max) * 10) * dt;
            p.x += p.vx * dt;
            p.size += 5f * dt;
            if (p.life <= 0) pi.remove();
        }

        Iterator<Spark> si = sparks.iterator();
        while (si.hasNext()) {
            Spark s = si.next();
            s.life -= dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += (s.fragment ? 220f : 60f) * dt;
            s.vx *= (float) Math.exp(-1.1f * dt);
            if (s.fragment && s.y > floorTop() - 1) {
                s.y = floorTop() - 1;
                s.vy *= -0.3f;
                s.vx *= 0.5f;
            }
            if (s.life <= 0) si.remove();
        }

        nextDrip -= dt;
        if (nextDrip <= 0f) {
            Drip d = new Drip();
            d.x = w * (0.1f + rnd.nextFloat() * 0.8f);
            d.y = -8f;
            drips.add(d);
            nextDrip = 1.6f + rnd.nextFloat() * 2.4f;
        }
        Iterator<Drip> di = drips.iterator();
        while (di.hasNext()) {
            Drip d = di.next();
            d.v += 520f * dt;
            d.y += d.v * dt;
            if (d.y >= floorTop() - 2) {
                burst(d.x, floorTop() - 2, 4);
                emitPuff(d.x, floorTop() - 4);
                di.remove();
            }
        }

        // props open on their own now and then
        if (time >= nextAuto) {
            Prop p = props.get(rnd.nextInt(props.size()));
            if (time >= p.openUntil && p.r > p.l) openProp(p, 2f);
            nextAuto = time + 9 + rnd.nextFloat() * 6;
        }
        for (Prop p : props) {
            if (time < p.openUntil) {
                p.bubbleTimer -= dt;
                if (p.bubbleTimer <= 0f) {
                    p.bubbleTimer = 0.1f;
                    Spark s = new Spark();
                    s.x = (p.l + p.r) / 2f + (rnd.nextFloat() - 0.5f) * (p.r - p.l) * 0.5f;
                    s.y = p.t;
                    s.vx = (rnd.nextFloat() - 0.5f) * 20;
                    s.vy = -(30 + rnd.nextFloat() * 40);
                    s.max = s.life = 0.8f;
                    sparks.add(s);
                }
            }
        }

        if (!giantActive && time >= nextGiant) {
            giantActive = true;
            giantDir = rnd.nextBoolean() ? 1 : -1;
            giantX = giantDir > 0 ? -200 : w + 200;
            giantY = h * (0.2f + rnd.nextFloat() * 0.25f);
        }
        if (giantActive) {
            giantX += giantDir * 14 * dt;
            if (giantX < -260 || giantX > w + 260) {
                giantActive = false;
                nextGiant = time + 55 + rnd.nextFloat() * 40;
            }
        }
        for (Hex hx : hexes) {
            hx.y -= hx.v * dt;
            if (hx.y < -20) {
                hx.y = h + 20;
                hx.x = rnd.nextFloat() * w;
            }
        }
    }

    private void scare(Atom a, float ox, float oy, float radius, float strength, float dt) {
        float dx = a.x - ox, dy = a.y - oy, d2 = dx * dx + dy * dy;
        if (d2 > radius * radius || d2 < 0.01f) return;
        float d = (float) Math.sqrt(d2), k = (1f - d / radius) * strength * dt;
        a.fx += dx / d * k * 3;
        a.fy += dy / d * k * 3;
    }

    private static float wrapPi(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private void updateDrones(float dt) {
        if (time >= nextDrone && drones.size() < 2) {
            Drone d = new Drone();
            boolean left = rnd.nextBoolean();
            d.x = left ? -40 : w + 40;
            d.y = h * (0.2f + rnd.nextFloat() * 0.3f);
            d.heading = left ? 0f : (float) Math.PI;
            d.speed = 50;
            drones.add(d);
            nextDrone = time + 25 + rnd.nextFloat() * 20;
        }
        Iterator<Drone> it = drones.iterator();
        while (it.hasNext()) {
            Drone d = it.next();
            d.age += dt;
            d.eye = Math.max(0f, d.eye - dt);
            d.cooldown -= dt;
            if (!d.leaving && (d.scans >= 6 || d.age > 38f)) d.leaving = true;

            d.retarget -= dt;
            if (d.target != null && d.target.layer != 1) d.target = null;
            if (!d.leaving && d.retarget <= 0f) {
                d.retarget = 0.4f;
                Atom best = null;
                float bd = 480f * 480f;
                for (Atom a : atoms) {
                    if (a.layer != 1) continue;
                    float dx = a.x - d.x, dy = a.y - d.y, dd = dx * dx + dy * dy;
                    if (dd < bd) {
                        bd = dd;
                        best = a;
                    }
                }
                d.target = best;
            }

            float desired;
            float chase = 0f;
            if (d.leaving) {
                desired = d.x < w / 2f ? (float) Math.PI : 0f;
            } else if (d.target != null) {
                float tx = d.target.x, ty = d.target.y - 26f;     // hover above the atom
                desired = (float) Math.atan2(ty - d.y, tx - d.x);
                chase = Math.hypot(tx - d.x, ty - d.y) < 160 ? 1f : 0.4f;
            } else {
                desired = (float) Math.sin(time * 0.4f + d.age) * 0.3f + (Math.cos(d.heading) >= 0 ? 0f : (float) Math.PI);
            }
            if (d.y < h * 0.1f && Math.sin(desired) < 0) desired = (float) Math.atan2(0.5f, Math.cos(desired));
            if (d.y > h * 0.6f && Math.sin(desired) > 0) desired = (float) Math.atan2(-0.5f, Math.cos(desired));
            float turn = (1.6f + chase) * dt;
            d.heading += Math.max(-turn, Math.min(turn, wrapPi(desired - d.heading)));
            float targetSpeed = d.leaving ? 80f : 48f + chase * 50f;
            d.speed += (targetSpeed - d.speed) * Math.min(1f, dt * 2.5f);
            d.x += (float) Math.cos(d.heading) * d.speed * dt;
            d.y += (float) Math.sin(d.heading) * d.speed * dt;

            // scan: an atom under the drone gets a flash, shifts orbit, and drifts clear; it is never harmed
            if (d.cooldown <= 0f) {
                for (Atom a : atoms) {
                    if (a.layer != 1) continue;
                    float dx = a.x - d.x, dy = a.y - (d.y + 26f);
                    if (dx * dx + dy * dy < 36f * 36f) {
                        d.scans++;
                        d.eye = 1.2f;
                        d.cooldown = 1.6f;
                        a.quarter++;
                        a.spin = 0.8f;
                        a.fx += (dx >= 0 ? 1f : -1f) * 260f;
                        burst(a.x, a.y, 6);
                        break;
                    }
                }
            }
            if (d.x < -120 || d.x > w + 120) it.remove();
        }
    }

    // ---- drawing helpers --------------------------------------------------------------------------

    private void begin(int mode) {
        this.mode = mode;
        count = 0;
    }

    /** Submits what was collected: mode 7 as quads of four vertices, mode 6 as a triangle fan. */
    private void end() {
        QuadBatch batch = new QuadBatch();
        if (mode == 6) {
            for (int i = 1; i + 1 < count; i++)
                batch.triangle(vx[0], vy[0], vc[0], vx[i], vy[i], vc[i], vx[i + 1], vy[i + 1], vc[i + 1]);
        } else {
            for (int i = 0; i + 3 < count; i += 4)
                batch.quad(vx[i], vy[i], vc[i], vx[i + 1], vy[i + 1], vc[i + 1], vx[i + 2], vy[i + 2], vc[i + 2], vx[i + 3], vy[i + 3], vc[i + 3]);
        }
        batch.submit();
        count = 0;
    }

    private void v(float x, float y, int rgb, int a) {
        if (count == vx.length) {
            vx = java.util.Arrays.copyOf(vx, count * 2);
            vy = java.util.Arrays.copyOf(vy, count * 2);
            vc = java.util.Arrays.copyOf(vc, count * 2);
        }
        vx[count] = x;
        vy[count] = y;
        vc[count] = (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
        count++;
    }

    private void rect(float x, float y, float rw, float rh, int rgb, int a) {
        if (a <= 0) return;
        v(x, y, rgb, a);
        v(x, y + rh, rgb, a);
        v(x + rw, y + rh, rgb, a);
        v(x + rw, y, rgb, a);
    }

    private void glow(float cx, float cy, float radius, int rgb, int alpha) {
        if (alpha <= 0) return;
        begin(6);
        v(cx, cy, rgb, alpha);
        for (int i = 0; i <= 28; i++) {
            double a = i * Math.PI * 2 / 28;
            v(cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, rgb, 0);
        }
        end();
    }

    private static int spriteW(String[] rows) {
        int m = 0;
        for (String r : rows) m = Math.max(m, r.length());
        return m;
    }

    private void sprite(String[] rows, float x, float y, int px, boolean flip, int[] pal, int alpha) {
        int sw = spriteW(rows);
        for (int row = 0; row < rows.length; row++) {
            String s = rows[row];
            int c = 0;
            while (c < s.length()) {
                char ch = s.charAt(c);
                if (ch == ' ') {
                    c++;
                    continue;
                }
                int e = c;
                while (e < s.length() && s.charAt(e) == ch) e++;
                rect(flip ? x + (sw - e) * px : x + c * px, y + row * px, (e - c) * px, px, pal[ch], alpha);
                c = e;
            }
        }
    }

    /** Stepped line of small squares, so diagonals look like pixel art rather than smooth lines. */
    private void pixelLine(float x0, float y0, float x1, float y1, int rgb, int a) {
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)) / 3f);
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            rect(x0 + dx * t - 1, y0 + dy * t - 1, 2, 2, rgb, a);
        }
    }

    /** Hexagon edges: top, upper-left and upper-right, so a tiling draws every shared edge exactly once. */
    private void hexEdges(float cx, float cy, float r, int rgb, int a) {
        float hh = r * 0.866f;
        pixelLine(cx - r / 2, cy - hh, cx + r / 2, cy - hh, rgb, a);
        pixelLine(cx - r, cy, cx - r / 2, cy - hh, rgb, a);
        pixelLine(cx + r, cy, cx + r / 2, cy - hh, rgb, a);
    }

    private void hexOutline(float cx, float cy, float r, int rgb, int a) {
        float hh = r * 0.866f;
        pixelLine(cx - r / 2, cy - hh, cx + r / 2, cy - hh, rgb, a);
        pixelLine(cx + r / 2, cy - hh, cx + r, cy, rgb, a);
        pixelLine(cx + r, cy, cx + r / 2, cy + hh, rgb, a);
        pixelLine(cx + r / 2, cy + hh, cx - r / 2, cy + hh, rgb, a);
        pixelLine(cx - r / 2, cy + hh, cx - r, cy, rgb, a);
        pixelLine(cx - r, cy, cx - r / 2, cy - hh, rgb, a);
    }

    /** An elliptical orbit as a ring of dots, tilted by {@code tilt}. */
    private void orbit(float cx, float cy, float rx, float ry, float tilt, int dots, int rgb, int a, float dot) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        for (int i = 0; i < dots; i++) {
            double t = i * Math.PI * 2 / dots;
            float ex = (float) Math.cos(t) * rx, ey = (float) Math.sin(t) * ry;
            rect(cx + ex * ct - ey * st - dot / 2, cy + ex * st + ey * ct - dot / 2, dot, dot, rgb, a);
        }
    }

    private float[] orbitPoint(float cx, float cy, float rx, float ry, float tilt, double t) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float ex = (float) Math.cos(t) * rx, ey = (float) Math.sin(t) * ry;
        return new float[]{cx + ex * ct - ey * st, cy + ex * st + ey * ct};
    }

    // ---- the scene --------------------------------------------------------------------------------

    public void draw(int mainColour, int bgColour, float parX, float parY, float fade) {
        this.main = mainColour & 0xFFFFFF;
        this.bg = bgColour & 0xFFFFFF;
        this.lastParX = parX;
        this.lastParY = parY;
        int a = (int) (255 * fade);

        // a large faint lattice, drifting
        float R = 56f, colW = R * 1.5f, rowH = R * 1.732f;
        float ox = ((time * 5f + parX * 14f) % (colW * 2)), oy = ((time * 2.5f + parY * 14f) % rowH);
        begin(7);
        for (int c = -2; c * colW - ox < w + R; c++) {
            for (int r = -1; r * rowH - oy < h + rowH; r++) {
                float cx = c * colW - ox, cy = r * rowH - oy + ((c & 1) != 0 ? rowH / 2f : 0f);
                hexEdges(cx, cy, R, main, (int) (15 * fade));
            }
        }
        end();

        drawBeams(parX, fade);

        if (giantActive) drawGiantAtom(parX, parY, fade);

        // far crystals along the back wall, dim
        int[] farPal = crystalPalette();
        for (int i = 0; i < farPal.length; i++) if (farPal[i] != 0) farPal[i] = ColorUtils.mixRgb(farPal[i], bg, 0.65f);
        begin(7);
        for (FarCrystal c : farCrystals) {
            float py = floorTop() - c.sprite.length * 2f + 2;
            sprite(c.sprite, c.x + parX * 8, py + parY * 4, 2, false, farPal, (int) (200 * fade));
        }
        end();

        // small floating hexagons
        begin(7);
        for (Hex hx : hexes)
            hexOutline(hx.x + parX * 14 + (float) Math.sin(time * 0.5f + hx.phase) * 6f, hx.y + parY * 14, hx.size, light(0.2f), (int) (46 * fade));
        end();

        // far atoms, then glow behind the shards
        for (Atom at : atoms) if (at.layer == 0) drawAtom(at, parX * 10, parY * 10, fade * 0.55f);

        drawShards(parX, parY, fade);
        drawFloor(parX, a);
        drawVapour(parX, fade);
        for (Atom at : atoms) if (at.layer == 1) drawAtom(at, parX * 24, parY * 24, fade);
        for (Drone d : drones) drawDrone(d, parX, parY, fade);
        drawSparks(parX, fade);
        drawForeground(parX, parY, fade);
    }

    /** Just the beams and drifting vapour, for screens that sit over the game world. */
    public void drawAmbient(int mainColour, int bgColour, float parX, float parY, float fade) {
        this.main = mainColour & 0xFFFFFF;
        this.bg = bgColour & 0xFFFFFF;
        lastParX = parX;
        lastParY = parY;
        drawBeams(parX, fade * 0.8f);
        drawVapour(parX, fade * 0.7f);
        drawSparks(parX, fade * 0.7f);
    }

    private void drawBeams(float parX, float fade) {
        begin(7);
        for (int i = 0; i < 4; i++) {
            float x = w * (0.12f + 0.25f * i) + (float) Math.sin(time * 0.2f + i * 1.7f) * 16f + parX * 16f;
            float pulse = 0.65f + 0.35f * (float) Math.sin(time * 0.5f + i * 2.1f);
            int al = (int) (28 * pulse * fade);
            float topW = 6, botW = 50 + i * 8, slant = (i % 2 == 0 ? 36 : -36);
            v(x, 0, light(0.5f), al);
            v(x + slant - botW / 2, h * floorFrac(), light(0.5f), 0);
            v(x + slant + botW / 2, h * floorFrac(), light(0.5f), 0);
            v(x + topW, 0, light(0.5f), al);
        }
        end();
    }

    private float floorFrac() {
        return (h - FLOOR_H) / h;
    }

    private void drawGiantAtom(float parX, float parY, float fade) {
        float cx = giantX + parX * 6, cy = giantY + parY * 6;
        int a = (int) (22 * fade);
        begin(7);
        for (int i = 0; i < 3; i++) orbit(cx, cy, 130, 46, (float) Math.toRadians(i * 60 + 15), 96, main, a, 3f);
        rect(cx - 7, cy - 7, 14, 14, main, a * 2);
        float[] e = orbitPoint(cx, cy, 130, 46, (float) Math.toRadians(15), time * 0.5);
        rect(e[0] - 4, e[1] - 4, 8, 8, light(0.5f), a * 3);
        end();
    }

    private void drawAtom(Atom at, float ox, float oy, float fade) {
        float cx = at.x + ox, cy = at.y + oy;
        float rx = 26f * at.size, ry = 9f * at.size;
        float quarterTurn = (float) Math.toRadians((at.quarter & 3) * 90);
        float speedMul = 1f + at.spin * 5f;
        glow(cx, cy, 34f * at.size, main, (int) (30 * fade));
        begin(7);
        for (int i = 0; i < 3; i++) {
            float tilt = quarterTurn + (float) Math.toRadians(i * 60);
            orbit(cx, cy, rx, ry, tilt, 40, light(0.25f), (int) (62 * fade), 1.6f);
        }
        // nucleus: a small cluster of blocks
        float s = 3f * at.size;
        rect(cx - s * 1.5f, cy - s * 0.5f, s * 3, s, dark(0.2f), (int) (230 * fade));
        rect(cx - s * 0.5f, cy - s * 1.5f, s, s * 3, dark(0.2f), (int) (230 * fade));
        rect(cx - s * 0.5f, cy - s * 0.5f, s, s, light(0.7f), (int) (255 * fade));
        for (int i = 0; i < 3; i++) {
            float tilt = quarterTurn + (float) Math.toRadians(i * 60);
            float[] e = orbitPoint(cx, cy, rx, ry, tilt, time * (1.4f + i * 0.5f) * speedMul + at.phase + i * 2.1f);
            rect(e[0] - 2.5f, e[1] - 2.5f, 5, 5, light(0.7f), (int) (255 * fade));
            rect(e[0] - 1, e[1] - 1, 2, 2, 0xFFFFFF, (int) (255 * fade));
        }
        end();
    }

    private void drawShards(float parX, float parY, float fade) {
        int[] pal = crystalPalette();
        for (Shard s : shards) {
            String[] sp = shardSprites[s.variant][s.rot];
            float sw = spriteW(sp) * P, sh = sp.length * P;
            float x = s.x + parX * 20, y = s.y + parY * 20 + (float) Math.sin(time * 1.1f + s.phase) * 6f;
            float pulse = 0.65f + 0.35f * (float) Math.sin(time * 1.9f + s.phase) + s.flash * 1.4f;
            glow(x + sw / 2, y + sh / 2, 40f + s.flash * 30f, main, (int) (Math.min(1.4f, pulse) * 46 * fade));
            begin(7);
            sprite(sp, x, y, P, false, pal, (int) (235 * fade));
            if (s.flash > 0f) rect(x, y, sw, sh, 0xFFFFFF, (int) (s.flash * 160 * fade));
            end();
        }
    }

    private void drawFloor(float parX, int a) {
        float top = floorTop();
        int slab = ColorUtils.mixRgb(bg, 0x000000, 0.55f);
        begin(7);
        rect(0, top, w, FLOOR_H, slab, (int) (a * 0.94f));
        rect(0, top, w, 2, light(0.15f), (int) (a * 0.38f));
        // bench tiles
        for (float x = (parX * 18) % 44f - 44f; x < w; x += 44f) rect(x, top + 2, 1, FLOOR_H, ColorUtils.mixRgb(slab, 0x000000, 0.5f), (int) (a * 0.7f));
        rect(0, top + 12, w, 1, ColorUtils.mixRgb(slab, main, 0.12f), (int) (a * 0.5f));
        end();

        int[] pal = crystalPalette();
        for (Prop p : props) {
            String[] body;
            if (p.kind == 0) body = FLASK;
            else if (p.kind == 1) body = CRATE_BODY;
            else body = BEAKER;
            int sw = spriteW(body) * P, sh = body.length * P;
            float x = p.fx * w + parX * 18 - sw / 2f, y = top + 3 - sh;
            boolean open = time < p.openUntil;
            p.l = x;
            p.r = x + sw;
            p.b = top + 3;
            p.t = y - (p.kind == 1 && open ? 14 : 0);

            if (p.kind == 1) {
                begin(7);
                sprite(body, x, y, P, false, pal, a);
                if (open) {
                    sprite(CRATE_LID, x, y - 5 * P, P, false, pal, a);
                } else {
                    sprite(CRATE_LID, x, y - CRATE_LID.length * P + 2 * P - 2 * P, P, false, pal, a);
                }
                end();
                if (open) glow(x + sw / 2f, y, 46, main, (int) (95 * (a / 255f)));
            } else {
                // glass in light colours, liquid in the theme colour with a bubbling surface
                int[] gpal = new int[128];
                gpal['g'] = light(0.75f);
                gpal['q'] = main;
                float level = p.kind == 0 ? 0.62f : 0.7f;
                int liquidTop = (int) (body.length * (1f - level));
                begin(7);
                for (int row = 0; row < body.length; row++) {
                    String s = body[row];
                    for (int c = 0; c < s.length(); c++) {
                        char ch = s.charAt(c);
                        if (ch == ' ') continue;
                        if (ch == 'g') rect(x + c * P, y + row * P, P, P, gpal['g'], (int) (a * 0.62f));
                        else if (row >= liquidTop)
                            rect(x + c * P, y + row * P, P, P,
                                    row == liquidTop ? light(0.45f) : main, (int) (a * (open ? 0.92f : 0.68f)));
                        else rect(x + c * P, y + row * P, P, P, light(0.5f), (int) (a * 0.07f));
                    }
                }
                // a bubble climbing through the liquid
                float bt = (time * 0.9f + p.fx * 7f) % 1f;
                rect(x + sw / 2f - 1 + (float) Math.sin(time * 3f + p.fx * 9) * 3f, y + sh - bt * (sh - liquidTop * P) - P, 2, 2, 0xFFFFFF, (int) (a * 0.7f * (1f - bt)));
                end();
                glow(x + sw / 2f, y + sh * 0.7f, 30, main, (int) (open ? 70 : 34) * a / 255);
            }
        }

        // floor vent
        begin(7);
        float vx = w * 0.5f + parX * 18;
        rect(vx - 14, top + 1, 28, 4, ColorUtils.mixRgb(slab, 0x000000, 0.4f), a);
        for (int i = 0; i < 4; i++) rect(vx - 12 + i * 7, top + 1, 3, 4, ColorUtils.mixRgb(slab, main, 0.2f), a);
        end();
    }

    private void drawVapour(float parX, float fade) {
        // a low bank of haze along the floor
        begin(7);
        float top = floorTop();
        v(0, top - 70, main, 0);
        v(w, top - 70, main, 0);
        v(w, top, main, (int) (30 * fade));
        v(0, top, main, (int) (30 * fade));
        end();
        begin(7);
        for (Puff p : puffs) {
            float k = p.life / p.max;                       // 1 fresh, 0 gone
            int al = (int) (Math.sin(Math.min(1f, (1f - k) * 6f) * Math.PI / 2) * k * 70 * fade);
            rect(p.x + parX * 18 - p.size / 2, p.y - p.size / 2, p.size, p.size, light(0.35f), al);
        }
        end();
    }

    private void drawDrone(Drone d, float parX, float parY, float fade) {
        float x = d.x + parX * 24, y = d.y + parY * 24;
        boolean flip = Math.cos(d.heading) < 0;
        float bob = (float) Math.sin(time * 3f + d.x * 0.02f) * 2f;
        int[] pal = new int[128];
        pal['d'] = ColorUtils.mixRgb(bg, main, 0.30f);
        pal['y'] = main;
        pal['e'] = d.eye > 0 ? 0xFFFFFF : light(0.5f);
        int sw = spriteW(DRONE) * P;
        float sx = x - sw / 2f, sy = y + bob;
        // the scanner cone
        begin(7);
        float ex = x, ey = sy + 4 * P;
        float cone = d.eye > 0f ? 1f : 0.55f;
        v(ex - 3, ey, light(0.5f), (int) (70 * cone * fade));
        v(ex + 3, ey, light(0.5f), (int) (70 * cone * fade));
        v(ex + 26, ey + 52, light(0.5f), 0);
        v(ex - 26, ey + 52, light(0.5f), 0);
        end();
        begin(7);
        sprite(DRONE, sx, sy, P, flip, pal, (int) (245 * fade));
        end();
        if (d.eye > 0f) glow(ex, ey, 24f, light(0.6f), (int) (110 * Math.min(1f, d.eye) * fade));
    }

    private void drawSparks(float parX, float fade) {
        begin(7);
        for (Spark s : sparks) {
            float k = Math.min(1f, s.life / (s.max * 0.5f));
            float x = s.x + parX * 22;
            if (s.fragment) rect(x, s.y, 3, 3, light(0.3f), (int) (255 * k * fade));
            else {
                int col = s.colour == 0xFFFFFF ? 0xFFFFFF : light(0.55f);
                rect(x - 1, s.y - 1, 4, 4, main, (int) (60 * k * fade));
                rect(x, s.y, 2, 2, col, (int) (255 * k * fade));
            }
        }
        end();
    }

    private void drawForeground(float parX, float parY, float fade) {
        int[] pal = crystalPalette();
        for (int i = 0; i < pal.length; i++) if (pal[i] != 0) pal[i] = ColorUtils.mixRgb(pal[i], bg, 0.28f);
        float top = floorTop();
        float[] xs = {0.03f, 0.10f, 0.955f, 0.985f};
        int[] vs = {0, 2, 1, 0};
        begin(7);
        for (int i = 0; i < xs.length; i++) {
            String[] sp = spireSprites[vs[i]];
            int px = 4;
            float x = xs[i] * w + parX * 34 - spriteW(sp) * px / 2f;
            sprite(sp, x, top + 6 - sp.length * px, px, false, pal, (int) (255 * fade));
        }
        end();
        begin(7);
        for (Drip d : drips) {
            rect(d.x - 1 + parX * 30, d.y - 8, 2, 5, light(0.4f), (int) (210 * fade));
            rect(d.x - 2 + parX * 30, d.y - 4, 4, 4, light(0.4f), (int) (210 * fade));
        }
        end();
    }

    // ---- the periodic-table tile ------------------------------------------------------------------

    /**
     * The element's own tile: atomic number, symbol, name and mass in a bordered box. {@code glow} 0..1
     * adds a soft pulse behind it. Used beside the logo on the main menu.
     */
    public static void drawElementTile(float x, float y, float size, int rgb, float glow, float fade) {
        Minecraft mc = Minecraft.getInstance();
        var g = RenderContext.graphics();
        int a = Math.max(0, Math.min(255, (int) (255 * fade)));
        rgb &= 0xFFFFFF;
        QuadBatch batch = new QuadBatch();
        // soft pulse behind the tile
        float cx = x + size / 2f, cy = y + size / 2f, gr = size * 0.95f;
        int centre = ((int) (60 * glow * fade) << 24) | rgb;
        for (int i = 0; i < 24; i++) {
            double a0 = i * Math.PI * 2 / 24, a1 = (i + 1) * Math.PI * 2 / 24;
            batch.triangle(cx, cy, centre, cx + (float) Math.cos(a0) * gr, cy + (float) Math.sin(a0) * gr, rgb,
                    cx + (float) Math.cos(a1) * gr, cy + (float) Math.sin(a1) * gr, rgb);
        }
        // body and border
        batch.rect(x, y, x + size, y + size, ((a / 8) << 24) | rgb);
        batch.rect(x, y, x + size, y + 1, (a << 24) | rgb);
        batch.rect(x, y + size - 1, x + size, y + size, (a << 24) | rgb);
        batch.rect(x, y, x + 1, y + size, (a << 24) | rgb);
        batch.rect(x + size - 1, y, x + size, y + size, (a << 24) | rgb);
        batch.submit();

        int col = (a << 24) | rgb;
        float s = size / 56f;
        text(g, mc, "33", x + 4 * s, y + 4 * s, s, col);
        String mass = "74.92";
        text(g, mc, mass, x + size - 4 * s - mc.font.width(mass) * s * 0.7f, y + 5 * s, s * 0.7f, col);
        float big = size / 56f * 2.6f;
        text(g, mc, "As", x + size / 2f - mc.font.width("As") * big / 2f, y + size * 0.30f, big, (a << 24) | 0xFFFFFF);
        float name = s * 0.85f;
        text(g, mc, "Arsenic", x + size / 2f - mc.font.width("Arsenic") * name / 2f, y + size - 11 * s, name, col);
    }

    private static void text(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc, String text, float x, float y, float scale, int colour) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, colour, false);
        g.pose().popMatrix();
    }
}
