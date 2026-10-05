package arsenic.module.impl.visual.custommainmenu;

import arsenic.utils.java.MathUtils;
import arsenic.utils.java.ColorUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class OceanScene {


    private static final String[] FISH = {
            "    dd    ",
            " d bbbbbb ",
            "ddbbbbbbpb",
            "ddbbbbbbbb",
            " d lllllll ",
            "    ll    "};

    private static final int SHARK_COLS = 52, SHARK_ROWS = 21, SHARK_CY = 11;
    private static final String[] SHARK = buildShark();

    private static String[] buildShark() {
        char[][] g = new char[SHARK_ROWS][SHARK_COLS];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');
        int cy = SHARK_CY;
        double[] hb = new double[SHARK_COLS];
        for (int c = 0; c < SHARK_COLS; c++) {
            double u = c / (double) (SHARK_COLS - 1);
            if (u < 0.2) hb[c] = 1.3 + (u / 0.2) * 1.2;
            else if (u < 0.62) hb[c] = 2.5 + ((u - 0.2) / 0.42) * 3.3;
            else hb[c] = 5.8 * Math.pow(Math.max(0, 1 - (u - 0.62) / 0.38), 0.9);
        }
        for (int c = 6; c < SHARK_COLS; c++) {
            for (int r = 0; r < SHARK_ROWS; r++) {
                double dy = r - cy;
                if (Math.abs(dy + 0.5) <= hb[c]) g[r][c] = dy > hb[c] * 0.1 ? 'l' : 'd';
            }
        }
        for (int c = 0; c < 8; c++) {
            double k = 8 - c;
            int top = (int) Math.round(cy - 1 - k * 0.95), bottom = (int) Math.round(cy - 1 - k * 0.4);
            for (int r = top; r <= Math.max(top, bottom); r++) if (r >= 0) g[r][c] = 'f';
            int lTop = (int) Math.round(cy + 1 + k * 0.2), lBottom = (int) Math.round(cy + 1 + k * 0.65);
            for (int r = lTop; r <= Math.max(lTop, lBottom) && r < SHARK_ROWS; r++) g[r][c] = 'f';
        }
        for (int c = 22; c <= 30; c++) {
            double height = c < 25 ? (c - 21) * 1.7 : (33 - c) * 0.6;
            int base = (int) Math.round(cy - hb[c]);
            for (int r = (int) Math.round(base - height); r < base; r++)
                if (r >= 0) g[r][c] = 'f';
        }
        for (int j = 1; j <= 4; j++) {
            int base = (int) Math.round(cy + hb[36]);
            int c0 = (int) Math.round(37 - j * 1.5);
            for (int c = c0; c <= c0 + 2; c++) if (base + j - 1 < SHARK_ROWS) g[base + j - 1][c] = 'f';
        }
        for (int k = 0; k < 3; k++)
            for (int r = cy - 2; r <= cy + 1; r++) g[r][33 + k * 2] = 'f';
        g[cy - 2][SHARK_COLS - 11] = 'e';
        for (int c = SHARK_COLS - 10; c <= SHARK_COLS - 5; c++) g[cy + 2][c] = c % 2 == 0 ? 't' : 'l';
        String[] out = new String[SHARK_ROWS];
        for (int r = 0; r < SHARK_ROWS; r++) out[r] = new String(g[r]);
        return out;
    }

    private static final String[] WHALE = {
            "                    dddd                 ",
            "               dddddddddddd              ",
            "         ddddddddddddddddddddd           ",
            " dd   dddddddddddddddddddddddddd         ",
            "  ddd dddddddddddddddddddddddddddddd     ",
            "   dddddddddddddddddddddddddddddddddddd  ",
            "  ddd llllllllllllllllllllllllllllllllll ",
            " dd    lllllllllllllllllllllllllll        "};

    private static final String[] CHEST_BODY = {
            "dbbbbggbbbbd",
            "dddddggddddd",
            "dbbbbggbbbbd",
            "dbbbbbbbbbbd",
            "dddddddddddd"};
    private static final String[] CHEST_LID = {
            " dddddddddd ",
            "dbbbbbbbbbbd",
            "dbbbbggbbbbd"};
    private static final String[] CHEST_COINS = {"dgggggggggd"};

    private static final String[] JELLY = {
            "   bbbbb   ",
            "  bbbbbbb  ",
            " bbbbbbbbb ",
            " bblbbblbb ",
            "bbbbbbbbbbb"};

    private static final int[] FISH_COLOURS = {0xFF8A3D, 0xFFD23F, 0x3FD0FF, 0xFF5D8F, 0x7CFF6B, 0xB48CFF};
    private static final int[] GEM_COLOURS = {0xF2C744, 0xF2C744, 0x6BE4FF, 0xFF7BC8, 0x9CFF8A};

    private static final int SHARK_PX = 3, FISH_FAR_PX = 2, CHEST_PX = 3, JELLY_PX = 3;
    private static final float SHARK_MOUTH = 68f;


    private static final class Fish {
        float x, y, speed, fx, fy, phase, respawnAt;
        int dir, px, layer, colour;
        boolean dead;
        int[] pal;
    }

    private static final class Bubble {
        float x, y, speed, phase;
        int size;
    }

    private static final class Jelly {
        float x, y, speed, phase, boost;
        int colour;
    }

    private static final class Spark {
        float x, y, vx, vy, life, max;
        int colour;
    }

    private static final class Shark {
        float x, y, heading, speed, age, retarget, mouth, snapCooldown;
        int snaps;
        boolean leaving;
        Fish target;
    }

    private final Random rnd = new Random(42);
    private final List<Fish> fish = new ArrayList<>();
    private final List<Bubble> bubbles = new ArrayList<>();
    private final List<Jelly> jellies = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Shark> sharks = new ArrayList<>();
    private int w, h;

    private float[] weedX, weedH, weedPhase, weedKick;
    private float[] sandTop;
    private float[] rayX, rayW, raySlant, rayPhase;

    private float nextShark = 6f;
    private float whaleX, whaleY, nextWhale = 22f;
    private int whaleDir;
    private boolean whaleActive;
    private float chestOpenUntil, nextChest = 14f, chestBubbleTimer;
    private float time;

    private float lastParX, lastParY;
    private float chestL, chestT, chestR, chestB;


    private int main, bg;

    private static int shade(int rgb, float f) {
        return ColorUtils.mixRgb(rgb, 0x000000, f);
    }

    private static float wrapPi(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }


    public void resize(int w, int h) {
        if (w == this.w && h == this.h && !fish.isEmpty()) return;
        this.w = w;
        this.h = h;
        fish.clear();
        bubbles.clear();
        jellies.clear();
        sparks.clear();
        sharks.clear();

        for (int i = 0; i < 16; i++) fish.add(newFish(true, i % 3 == 0 ? 0 : 1));

        for (int i = 0; i < 3; i++) {
            Jelly j = new Jelly();
            j.x = w * (0.15f + 0.35f * i) + rnd.nextFloat() * 40;
            j.y = h * (0.25f + rnd.nextFloat() * 0.4f);
            j.speed = 6 + rnd.nextFloat() * 6;
            j.phase = rnd.nextFloat() * 6.28f;
            j.colour = i == 1 ? 0xFF8CD8 : (i == 2 ? 0x9CE8FF : 0xC09CFF);
            jellies.add(j);
        }

        for (int i = 0; i < 30; i++) {
            Bubble b = new Bubble();
            respawnBubble(b, true);
            bubbles.add(b);
        }

        int weeds = Math.max(8, w / 38);
        weedX = new float[weeds];
        weedH = new float[weeds];
        weedPhase = new float[weeds];
        weedKick = new float[weeds];
        for (int i = 0; i < weeds; i++) {
            weedX[i] = (i + 0.1f + rnd.nextFloat() * 0.8f) * (w / (float) weeds);
            weedH[i] = 35 + rnd.nextFloat() * 80;
            weedPhase[i] = rnd.nextFloat() * 6.28f;
        }

        sandTop = new float[w / 6 + 2];
        for (int i = 0; i < sandTop.length; i++)
            sandTop[i] = 10 + (float) (Math.sin(i * 0.31) * 3 + Math.sin(i * 0.9) * 1.5) + rnd.nextInt(3);

        rayX = new float[6];
        rayW = new float[6];
        raySlant = new float[6];
        rayPhase = new float[6];
        for (int i = 0; i < 6; i++) {
            rayX[i] = w * (i + rnd.nextFloat()) / 6f;
            rayW[i] = 30 + rnd.nextFloat() * 60;
            raySlant[i] = 50 + rnd.nextFloat() * 70;
            rayPhase[i] = rnd.nextFloat() * 6.28f;
        }
    }

    private Fish newFish(boolean anywhere, int layer) {
        Fish f = new Fish();
        reroll(f, anywhere, layer);
        return f;
    }

    private void reroll(Fish f, boolean anywhere, int layer) {
        f.layer = layer;
        f.dir = rnd.nextBoolean() ? 1 : -1;
        f.px = layer == 0 ? FISH_FAR_PX : (rnd.nextInt(3) == 0 ? 4 : 3);
        f.speed = (layer == 0 ? 14 : 22) + rnd.nextFloat() * 30;
        f.y = h * (0.12f + rnd.nextFloat() * 0.6f);
        f.x = anywhere ? rnd.nextFloat() * w : (f.dir > 0 ? -60 : w + 60);
        f.phase = rnd.nextFloat() * 6.28f;
        f.colour = rnd.nextInt(7) == 0 ? main : FISH_COLOURS[rnd.nextInt(FISH_COLOURS.length)];
        f.pal = null;
        f.fx = f.fy = 0;
        f.dead = false;
    }

    private void respawnBubble(Bubble b, boolean anywhere) {
        b.x = rnd.nextFloat() * w;
        b.y = anywhere ? rnd.nextFloat() * h : h + 4;
        b.speed = 12 + rnd.nextFloat() * 26;
        b.phase = rnd.nextFloat() * 6.28f;
        b.size = 2 + rnd.nextInt(3);
    }

    private void burst(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            Bubble b = new Bubble();
            b.x = x + (rnd.nextFloat() - 0.5f) * 14;
            b.y = y + (rnd.nextFloat() - 0.5f) * 10;
            b.speed = 25 + rnd.nextFloat() * 45;
            b.phase = rnd.nextFloat() * 6.28f;
            b.size = 2 + rnd.nextInt(3);
            bubbles.add(b);
        }
    }


    public boolean click(float mx, float my) {
        if (weedX != null) {
            for (int i = 0; i < weedX.length; i++) {
                if (i % 2 == 0) continue;
                int segs = (int) (weedH[i] / 5);
                for (int s = 0; s < segs; s++) {
                    float[] r = weedSeg(i, s, 1);
                    if (mx >= r[0] - 4 && mx <= r[0] + r[2] + 4 && my >= r[1] - 1 && my <= r[1] + r[3] + 1)
                        return true;
                }
            }
        }

        if (mx >= chestL - 4 && mx <= chestR + 4 && my >= chestT - 8 && my <= chestB + 4) {
            if (time < chestOpenUntil) {
                chestOpenUntil = time;
            } else {
                chestOpenUntil = time + 5f;
                nextChest = time + 14 + rnd.nextFloat() * 6;
                spill((chestL + chestR) / 2f, chestT - 6);
            }
            return true;
        }

        for (Fish f : fish) {
            if (f.dead || f.layer != 1) continue;
            float fx = f.x + lastParX * 24, fy = f.y + lastParY * 24;
            float fw = 10 * f.px, fh = 6 * f.px;
            if (mx >= fx - 4 && mx <= fx + fw + 4 && my >= fy - 4 && my <= fy + fh + 4) {
                float away = fx + fw / 2 < mx ? -1 : 1;
                f.dir = (int) away;
                f.fx += away * 380;
                f.fy += (rnd.nextFloat() - 0.5f) * 260;
                burst(fx + fw / 2, fy + fh / 2, 5);
                return true;
            }
        }

        for (Jelly j : jellies) {
            float jx = j.x + lastParX * 22, jy = j.y + lastParY * 22;
            if (mx >= jx - 6 && mx <= jx + 11 * JELLY_PX + 6 && my >= jy - 4 && my <= jy + 14 * JELLY_PX) {
                j.boost = 1f;
                burst(jx + 16, jy + 10, 4);
                return true;
            }
        }

        burst(mx, my, 6);
        return false;
    }

    private void spill(float x, float y) {
        for (int i = 0; i < 12; i++) {
            Spark s = new Spark();
            s.x = x + (rnd.nextFloat() - 0.5f) * 20;
            s.y = y;
            s.vx = (rnd.nextFloat() - 0.5f) * 50;
            s.vy = -(25 + rnd.nextFloat() * 55);
            s.max = s.life = 2.2f + rnd.nextFloat() * 1.6f;
            s.colour = GEM_COLOURS[rnd.nextInt(GEM_COLOURS.length)];
            sparks.add(s);
        }
        burst(x, y, 10);
    }


    public void update(float dt, float mouseX, float mouseY) {
        time += dt;

        updateSharks(dt);

        if (!whaleActive && time >= nextWhale) {
            whaleActive = true;
            whaleDir = rnd.nextBoolean() ? 1 : -1;
            whaleX = whaleDir > 0 ? -220 : w + 220;
            whaleY = h * (0.15f + rnd.nextFloat() * 0.25f);
        }
        if (whaleActive) {
            whaleX += whaleDir * 24 * dt;
            if (whaleX < -260 || whaleX > w + 260) {
                whaleActive = false;
                nextWhale = time + 60 + rnd.nextFloat() * 40;
            }
        }

        for (Fish f : fish) {
            if (f.dead) {
                if (time >= f.respawnAt) reroll(f, false, f.layer);
                continue;
            }
            f.x += (f.dir * f.speed + f.fx) * dt;
            f.y += ((float) Math.sin(time * 1.7f + f.phase) * 7 + f.fy) * dt;
            float decay = (float) Math.exp(-2.2f * dt);
            f.fx *= decay;
            f.fy *= decay;
            if (f.layer == 1) {
                scare(f, mouseX, mouseY, 70f, 260f, dt);
                for (Shark s : sharks)
                    scare(f, s.x + (float) Math.cos(s.heading) * SHARK_MOUTH, s.y + (float) Math.sin(s.heading) * SHARK_MOUTH, 170f, 520f, dt);
            }
            f.y = Math.max(h * 0.05f, Math.min(h * 0.82f, f.y));
            if ((f.dir > 0 && f.x > w + 70) || (f.dir < 0 && f.x < -70))
                reroll(f, false, f.layer);
        }

        for (Jelly j : jellies) {
            float stroke = (float) Math.max(0, Math.sin(time * 1.1f + j.phase));
            j.y -= (j.speed * stroke - 4f + j.boost * 90f) * dt;
            j.boost = Math.max(0, j.boost - dt * 1.4f);
            j.x += (float) Math.sin(time * 0.35f + j.phase) * 8 * dt;
            if (j.y < -40) j.y = h * 0.8f;
            if (j.y > h * 0.85f) j.y = h * 0.85f;
        }

        for (int i = 0; i < weedKick.length; i++) weedKick[i] = Math.max(0, weedKick[i] - dt * 0.9f);

        Iterator<Bubble> it = bubbles.iterator();
        while (it.hasNext()) {
            Bubble b = it.next();
            b.y -= b.speed * dt;
            b.x += (float) Math.sin(time * 1.5f + b.phase) * 10 * dt;
            if (b.y < -6) {
                if (bubbles.size() > 40) it.remove();
                else respawnBubble(b, false);
            }
        }

        Iterator<Spark> si = sparks.iterator();
        while (si.hasNext()) {
            Spark s = si.next();
            s.life -= dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += 14 * dt;
            s.vx *= (float) Math.exp(-1.2f * dt);
            if (s.life <= 0) si.remove();
        }

        if (time >= nextChest && time >= chestOpenUntil) {
            chestOpenUntil = time + 1.8f;
            nextChest = time + 12 + rnd.nextFloat() * 8;
            spill((chestL + chestR) / 2f, chestT - 6);
        }
        if (time < chestOpenUntil) {
            chestBubbleTimer -= dt;
            if (chestBubbleTimer <= 0) {
                chestBubbleTimer = 0.09f;
                Bubble b = new Bubble();
                b.x = chestL + 8 + rnd.nextFloat() * (chestR - chestL - 16);
                b.y = chestT - 4;
                b.speed = 25 + rnd.nextFloat() * 30;
                b.phase = rnd.nextFloat() * 6.28f;
                b.size = 2 + rnd.nextInt(3);
                bubbles.add(b);
            }
        }
    }

    private void updateSharks(float dt) {
        if (time >= nextShark && sharks.size() < 2) {
            Shark s = new Shark();
            boolean left = rnd.nextBoolean();
            s.x = left ? -170 : w + 170;
            s.y = h * (0.25f + rnd.nextFloat() * 0.35f);
            s.heading = left ? 0f : (float) Math.PI;
            s.speed = 80;
            sharks.add(s);
            nextShark = time + 26 + rnd.nextFloat() * 20;
        }

        Iterator<Shark> it = sharks.iterator();
        while (it.hasNext()) {
            Shark s = it.next();
            s.age += dt;
            s.mouth = Math.max(0, s.mouth - dt * 2.5f);
            if (!s.leaving && (s.snaps >= 6 || s.age > 38f)) s.leaving = true;

            float hx = s.x + (float) Math.cos(s.heading) * SHARK_MOUTH;
            float hy = s.y + (float) Math.sin(s.heading) * SHARK_MOUTH;

            s.retarget -= dt;
            if (s.target != null && (s.target.dead || s.target.layer != 1)) s.target = null;
            if (!s.leaving && s.retarget <= 0) {
                s.retarget = 0.35f;
                Fish best = null;
                float bd = 520f * 520f;
                for (Fish f : fish) {
                    if (f.dead || f.layer != 1) continue;
                    float dx = f.x + 5 * f.px - hx, dy = f.y + 3 * f.px - hy, d = dx * dx + dy * dy;
                    if (d < bd) {
                        bd = d;
                        best = f;
                    }
                }
                s.target = best;
            }

            float desired;
            float chase = 0;
            if (s.leaving) {
                float tx = s.x < w / 2f ? -400 : w + 400;
                desired = (float) Math.atan2(s.y - s.y, tx - s.x);
            } else if (s.target != null) {
                float tx = s.target.x + 5 * s.target.px, ty = s.target.y + 3 * s.target.px;
                desired = (float) Math.atan2(ty - hy, tx - hx);
                float d = (float) Math.hypot(tx - hx, ty - hy);
                chase = d < 260 ? 1f : 0.4f;
            } else {
                desired = (float) Math.sin(time * 0.3f + s.age) * 0.35f + (Math.cos(s.heading) >= 0 ? 0f : (float) Math.PI);
            }
            if (s.y < h * 0.12f && Math.sin(desired) < 0) desired = (float) Math.atan2(0.6f, Math.cos(desired));
            if (s.y > h * 0.72f && Math.sin(desired) > 0) desired = (float) Math.atan2(-0.6f, Math.cos(desired));

            float turn = (1.5f + chase * 1.0f) * dt;
            float diff = wrapPi(desired - s.heading);
            s.heading += Math.max(-turn, Math.min(turn, diff));
            float targetSpeed = s.leaving ? 90f : 78f + chase * 70f;
            s.speed += (targetSpeed - s.speed) * Math.min(1f, dt * 2.5f);
            s.x += (float) Math.cos(s.heading) * s.speed * dt;
            s.y += (float) Math.sin(s.heading) * s.speed * dt;

            hx = s.x + (float) Math.cos(s.heading) * SHARK_MOUTH;
            hy = s.y + (float) Math.sin(s.heading) * SHARK_MOUTH;
            s.snapCooldown -= dt;
            if (s.snapCooldown <= 0) {
                for (Fish f : fish) {
                    if (f.dead || f.layer != 1) continue;
                    float dx = f.x + 5 * f.px - hx, dy = f.y + 3 * f.px - hy;
                    if (dx * dx + dy * dy < 34f * 34f) {
                        s.snaps++;
                        s.mouth = 1f;
                        s.snapCooldown = 1.2f;
                        float side = (dx * (float) Math.sin(s.heading) - dy * (float) Math.cos(s.heading)) >= 0 ? 1f : -1f;
                        f.fx += -(float) Math.sin(s.heading) * side * 420f + (float) Math.cos(s.heading) * 120f;
                        f.fy += (float) Math.cos(s.heading) * side * 420f;
                        burst(hx, hy, 4);
                        break;
                    }
                }
            }

            if (s.x < -260 || s.x > w + 260) it.remove();
        }
    }

    private void scare(Fish f, float ox, float oy, float radius, float strength, float dt) {
        float dx = f.x - ox, dy = f.y - oy, d2 = dx * dx + dy * dy;
        if (d2 > radius * radius || d2 < 0.01f) return;
        float d = (float) Math.sqrt(d2), k = (1f - d / radius) * strength * dt;
        f.fx += dx / d * k * 3;
        f.fy += dy / d * k * 3;
    }


    private final Tessellator tess = Tessellator.getInstance();
    private WorldRenderer wr;

    private void begin() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
    }

    private void end() {
        tess.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableTexture2D();
    }

    private void rect(float x, float y, float rw, float rh, int rgb, int a) {
        if (a <= 0) return;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        wr.pos(x, y, 0).color(r, g, b, a).endVertex();
        wr.pos(x, y + rh, 0).color(r, g, b, a).endVertex();
        wr.pos(x + rw, y + rh, 0).color(r, g, b, a).endVertex();
        wr.pos(x + rw, y, 0).color(r, g, b, a).endVertex();
    }

    private void vgrad(float x1, float x2, float y1, float y2, float shiftBottom, int rgb, int aTop, int aBottom) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        wr.pos(x1, y1, 0).color(r, g, b, aTop).endVertex();
        wr.pos(x1 + shiftBottom, y2, 0).color(r, g, b, aBottom).endVertex();
        wr.pos(x2 + shiftBottom, y2, 0).color(r, g, b, aBottom).endVertex();
        wr.pos(x2, y1, 0).color(r, g, b, aTop).endVertex();
    }

    private static int spriteWidth(String[] rows) {
        int m = 0;
        for (String r : rows) m = Math.max(m, r.length());
        return m;
    }

    private void sprite(String[] rows, float x, float y, int px, boolean flip, int[] pal, int alpha) {
        int sw = spriteWidth(rows);
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
                float rx = flip ? x + (sw - e) * px : x + c * px;
                rect(rx, y + row * px, (e - c) * px, px, pal[ch], alpha);
                c = e;
            }
        }
    }

    private void orientedSprite(String[] rows, int px, float cx, float cy, float vx, float vy, int[] pal, int alpha, Runnable extra) {
        boolean flip = vx < 0;
        float ang = (float) Math.atan2(vy, Math.abs(vx)) * (flip ? -1f : 1f);
        ang = Math.max(-1.45f, Math.min(1.45f, ang));
        float sw = spriteWidth(rows) * px, sh = rows.length * px;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.rotate((float) Math.toDegrees(ang), 0, 0, 1);
        begin();
        sprite(rows, -sw / 2f, -sh / 2f, px, flip, pal, alpha);
        if (extra != null) extra.run();
        end();
        GlStateManager.popMatrix();
    }

    private int[] palette(int body) {
        int[] p = new int[128];
        p['b'] = body;
        p['d'] = shade(body, 0.3f);
        p['l'] = ColorUtils.mixRgb(body, 0xFFFFFF, 0.45f);
        p['p'] = 0x101018;
        p['e'] = 0xFFFFFF;
        p['f'] = shade(body, 0.45f);
        p['t'] = 0xFFFFFF;
        p['g'] = 0xF2C744;
        return p;
    }

    private void radialGlow(float cx, float cy, float radius, int rgb, int alpha) {
        if (alpha <= 0) return;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        WorldRenderer v = tess.getWorldRenderer();
        v.begin(6, DefaultVertexFormats.POSITION_COLOR);
        v.pos(cx, cy, 0).color(r, g, b, alpha).endVertex();
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI * 2 / 32;
            v.pos(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, 0).color(r, g, b, 0).endVertex();
        }
        tess.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableTexture2D();
    }

    private void drawFish(Fish f, float ox, float oy, int alpha, int water, boolean far) {
        if (f.dead) return;
        if (f.pal == null) f.pal = palette(far ? ColorUtils.mixRgb(f.colour, water, 0.6f) : f.colour);
        float vx = f.dir * f.speed + f.fx;
        float vy = (float) Math.sin(time * 1.7f + f.phase) * 7 + f.fy;
        float cx = f.x + ox + 5 * f.px, cy = f.y + oy + 3 * f.px;
        float wag = (float) Math.sin(time * 9f + f.phase) * 2.2f;
        orientedSprite(FISH, f.px, cx, cy, vx, vy * 1.0f + wag, f.pal, alpha, null);
    }

    public void draw(int mainColour, int bgColour, float parX, float parY, float fade) {
        this.main = mainColour & 0xFFFFFF;
        this.bg = bgColour & 0xFFFFFF;
        this.lastParX = parX;
        this.lastParY = parY;
        int a = (int) (255 * fade);
        int water = ColorUtils.mixRgb(bg, main, 0.5f);

        begin();
        for (int i = 0; i < rayX.length; i++) {
            float sway = (float) Math.sin(time * 0.25f + rayPhase[i]) * 18;
            float x = rayX[i] + sway + parX * 18;
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.6f + rayPhase[i] * 2);
            vgrad(x, x + rayW[i], 0, h * 0.85f, raySlant[i], ColorUtils.mixRgb(main, 0xFFFFFF, 0.5f), (int) (30 * pulse * fade), 0);
        }
        end();

        if (whaleActive) {
            begin();
            int[] pal = new int[128];
            pal['d'] = ColorUtils.mixRgb(water, bg, 0.55f);
            pal['l'] = ColorUtils.mixRgb(water, bg, 0.3f);
            sprite(WHALE, whaleX + parX * 6, whaleY + parY * 6, 5, whaleDir < 0, pal, (int) (110 * fade));
            end();
        }

        drawSeaweed(0, parX, a);
        for (Fish f : fish)
            if (f.layer == 0) drawFish(f, parX * 10, parY * 10, (int) (200 * fade), water, true);

        for (Jelly j : jellies) {
            float jx = j.x + parX * 22, jy = j.y + parY * 22;
            float pulse = 0.8f + 0.2f * (float) Math.sin(time * 2.2f + j.phase) + j.boost * 0.5f;
            radialGlow(jx + 16, jy + 10, 46 + j.boost * 20, j.colour, (int) (Math.min(1f, 60 * pulse / 60f) * 60 * fade));
        }
        begin();
        for (Jelly j : jellies) {
            float jx = j.x + parX * 22, jy = j.y + parY * 22;
            float squish = (float) Math.sin(time * 2.2f + j.phase);
            int px = JELLY_PX;
            sprite(JELLY, jx, jy, px, false, palette(j.colour), (int) (150 * fade));
            for (int t = 0; t < 4; t++) {
                float tx = jx + (2 + t * 2.4f) * px;
                int segs = 6 + (squish < 0 ? 2 : 0);
                for (int s = 0; s < segs; s++) {
                    float off = (float) Math.sin(time * 2f + t + s * 0.6f) * 2f;
                    rect(tx + off, jy + 5 * px + s * px * 1.4f, px, px * 1.4f, j.colour, (int) ((120 - s * 12) * fade));
                }
            }
        }
        end();

        drawSeabed(parX, a);

        for (Fish f : fish)
            if (f.layer == 1) drawFish(f, parX * 24, parY * 24, (int) (240 * fade), water, false);

        for (Shark s : sharks) drawShark(s, parX, parY, water, fade);

        begin();
        for (Bubble b : bubbles) {
            float x = b.x + parX * 28, y = b.y + parY * 28;
            rect(x, y, b.size, b.size, 0xCFE9FF, (int) (70 * fade));
            rect(x, y, 1, 1, 0xFFFFFF, (int) (130 * fade));
        }
        for (Spark s : sparks) {
            float k = Math.min(1f, s.life / (s.max * 0.5f));
            float glint = 0.7f + 0.3f * (float) Math.sin(time * 14f + s.x);
            float x = s.x + parX * 28, y = s.y + parY * 28;
            rect(x - 1, y - 1, 5, 5, s.colour, (int) (60 * k * fade));
            rect(x, y, 3, 3, s.colour, (int) (255 * k * glint * fade));
            rect(x + 1, y + 1, 1, 1, 0xFFFFFF, (int) (255 * k * fade));
        }
        end();

        drawSeaweed(1, parX, a);
    }


    /**
     * Just the light rays and bubbles, for screens that sit over the game world: enough to feel
     * like the same sea without hiding the world behind a full scene.
     */
    public void drawAmbient(int mainColour, int bgColour, float parX, float parY, float fade) {
        this.main = mainColour & 0xFFFFFF;
        this.bg = bgColour & 0xFFFFFF;
        begin();
        for (int i = 0; i < rayX.length; i++) {
            float sway = (float) Math.sin(time * 0.25f + rayPhase[i]) * 18;
            float x = rayX[i] + sway + parX * 18;
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * 0.6f + rayPhase[i] * 2);
            vgrad(x, x + rayW[i], 0, h * 0.85f, raySlant[i], ColorUtils.mixRgb(main, 0xFFFFFF, 0.5f), (int) (26 * pulse * fade), 0);
        }
        for (Bubble b : bubbles) {
            float x = b.x + parX * 28, y = b.y + parY * 28;
            rect(x, y, b.size, b.size, 0xCFE9FF, (int) (70 * fade));
            rect(x, y, 1, 1, 0xFFFFFF, (int) (130 * fade));
        }
        end();
    }
    private void drawShark(Shark s, float parX, float parY, int water, float fade) {
        float vx = (float) Math.cos(s.heading), vy = (float) Math.sin(s.heading);
        int[] pal = new int[128];
        pal['d'] = ColorUtils.mixRgb(0x5B6B7D, water, 0.15f);
        pal['l'] = ColorUtils.mixRgb(0xCFD8E3, water, 0.15f);
        pal['f'] = ColorUtils.mixRgb(0x46556A, water, 0.15f);
        pal['e'] = 0x000000;
        pal['t'] = 0xFFFFFF;
        float cx = s.x + parX * 30, cy = s.y + parY * 30;
        float wag = (float) Math.sin(time * 4.5f + s.x * 0.01f) * 0.08f;
        final boolean flip = vx < 0;
        orientedSprite(SHARK, SHARK_PX, cx, cy, vx, vy + wag,
                pal, (int) (240 * fade), () -> {
                    if (s.mouth > 0.15f) {
                        int sw = spriteWidth(SHARK), sh = SHARK.length;
float mx = (SHARK_COLS - 10) * SHARK_PX - sw * SHARK_PX / 2f;                        float my = (SHARK_CY + 2) * SHARK_PX - sh * SHARK_PX / 2f;
                        float rx = flip ? -mx - 6 * SHARK_PX : mx;
                        rect(rx, my, 6 * SHARK_PX, 3 * SHARK_PX, 0x7A1020, (int) (240 * s.mouth * fade));
                    }
                });
    }

    private float[] weedSeg(int i, int s, int layer) {
        int segH = layer == 0 ? 4 : 5, segW = layer == 0 ? 4 : 6;
        float par = 20;
        int segs = (int) (weedH[i] * (layer == 0 ? 0.8f : 1f) / segH);
        float t = s / (float) Math.max(1, segs);
        float amp = (2f + t * 8f) * (1f + weedKick[i] * 3.2f);
        float sway = (float) Math.sin(time * (1.2f + weedKick[i] * 3f) + weedPhase[i] + s * 0.33f) * amp;
        float lean = lastParX * (layer == 0 ? 6f : 16f) * t;
        float rootX = weedX[i] + lastParX * par;
        int idx = MathUtils.clamp((int) (weedX[i] / 6), 0, sandTop.length - 1);
        float rootY = h - sandTop[idx] + 3;
        return new float[]{rootX + sway + lean, rootY - (s + 1) * segH, segW * (1f - t * 0.4f), segH};
    }

    private void drawSeaweed(int layer, float parX, int a) {
        begin();
        int green = ColorUtils.mixRgb(0x2FA55F, main, layer == 0 ? 0.55f : 0.25f);
        int segH = layer == 0 ? 4 : 5;
        int alpha = layer == 0 ? (int) (a * 0.38f) : (int) (a * 0.9f);
        for (int i = 0; i < weedX.length; i++) {
            if ((i % 2 == 0) != (layer == 0)) continue;
            int segs = (int) (weedH[i] * (layer == 0 ? 0.8f : 1f) / segH);
            for (int s = 0; s < segs; s++) {
                float t = s / (float) segs;
                float[] r = weedSeg(i, s, layer);
                rect(r[0], r[1], r[2], r[3], ColorUtils.mixRgb(shade(green, 0.4f), green, t), alpha);
            }
            if (layer == 1) {
                float[] base = weedSeg(i, 0, 1);
                int idx = MathUtils.clamp((int) (weedX[i] / 6), 0, sandTop.length - 1);
                float gy = h - sandTop[idx];
                int dark = shade(green, 0.55f);
                rect(base[0] - 3, gy - 2, 12, 5, dark, alpha);
                rect(base[0] - 6, gy, 18, 3, ColorUtils.mixRgb(0x8C7650, bg, 0.7f), a);
            }
        }
        end();
    }

    private void drawSeabed(float parX, int a) {
        int sand = ColorUtils.mixRgb(0xC2A878, bg, 0.62f);
        int sandDark = ColorUtils.mixRgb(0x8C7650, bg, 0.7f);
        float ox = parX * 20;
        begin();
        for (int i = 0; i < sandTop.length; i++) {
            float x = i * 6 + ox;
            rect(x, h - sandTop[i], 6, sandTop[i], i % 5 == 0 ? sandDark : sand, a);
        }
        int rock = ColorUtils.mixRgb(0x6B7280, bg, 0.5f);
        for (int r = 0; r < 3; r++) {
            float rx = w * (0.12f + r * 0.38f) + ox;
            int idx = Math.min(sandTop.length - 1, (int) (w * (0.12f + r * 0.38f) / 6));
            rect(rx, h - sandTop[idx] - 6, 22, 7, rock, a);
            rect(rx + 4, h - sandTop[idx] - 11, 13, 6, shade(rock, 0.2f), a);
        }

        float cxw = w * 0.72f;
        int cidx = Math.min(sandTop.length - 1, (int) (cxw / 6));
        float cx = cxw + ox, base = h - sandTop[cidx] + 2;
        int[] pal = new int[128];
        pal['b'] = ColorUtils.mixRgb(0x8B5A2B, bg, 0.35f);
        pal['d'] = ColorUtils.mixRgb(0x5E3A1A, bg, 0.35f);
        pal['g'] = 0xF2C744;
        boolean open = time < chestOpenUntil;
        int px = CHEST_PX;
        float bodyY = base - CHEST_BODY.length * px;
        sprite(CHEST_BODY, cx, bodyY, px, false, pal, a);
        if (open) {
            sprite(CHEST_COINS, cx + px, bodyY - px, px, false, pal, a);
            sprite(CHEST_LID, cx, bodyY - CHEST_LID.length * px - 5 * px, px, false, pal, a);
        } else {
            sprite(CHEST_LID, cx, bodyY - CHEST_LID.length * px, px, false, pal, a);
        }
        end();
        chestL = cx;
        chestR = cx + 12 * px;
        chestB = base;
        chestT = bodyY - CHEST_LID.length * px - (open ? 5 * px : 0);
        if (open) radialGlow(cx + 18, bodyY - 6, 44, 0xF2C744, (int) (80 * (a / 255f)));
    }
}
