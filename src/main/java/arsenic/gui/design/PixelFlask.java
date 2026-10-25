package arsenic.gui.design;

import java.util.Random;

/**
 * The pixel-art Erlenmeyer flask shared by the logo and the rail mascot, so both look and bubble the
 * same. Each instance keeps its own bubbles; {@link #draw} advances them once per call.
 */
public final class PixelFlask {

    static final String[] SPRITE = {
            "    fffff    ",
            "     f f     ",
            "     f f     ",
            "     f f     ",
            "     f f     ",
            "     f f     ",
            "    f   f    ",
            "    f   f    ",
            "   f     f   ",
            "  f       f  ",
            " fqqqqqqqqqf ",
            " fqqqqqqqqqf ",
            "fqqqqqqqqqqqf",
            "fqqqqqqqqqqqf",
            "fqqqqqqqqqqqf",
            "fqqqqqqqqqqqf",
            " fffffffffff "};

    public static final int W = 13, H = 17;
    /** Flask-local liquid rows; the surface is LIQ_TOP. */
    private static final int LIQ_TOP = 10, LIQ_BOTTOM = 15;

    private static final class Bubble { float x, y, speed; boolean live; }

    private final Bubble[] bubbles = new Bubble[10];
    private final Random rng;
    private long lastNanos;
    private float time, timer, hover, burst;

    public PixelFlask(long seed) {
        rng = new Random(seed);
        for (int i = 0; i < bubbles.length; i++) bubbles[i] = new Bubble();
    }

    /** A short rush of bubbles, e.g. when a module is switched on. */
    public void pulse() {
        burst = 0.7f;
        for (int i = 0; i < 4; i++) spawn();
    }

    /** Draws at top-left (x, y) with pixel size {@code p}; the flask is {@link #W} x {@link #H} pixels. */
    public void draw(float x, float y, float p, boolean hovered) {
        step(hovered);
        PixelArt.palette();
        PixelArt.begin();
        PixelArt.sprite(SPRITE, x, y, p, 255);
        // the surface ripples: one lighter pixel walks along it
        int ripple = 2 + (int) ((time * (3f + hover * 3f)) % 9);
        PixelArt.rect(x + ripple * p, y + LIQ_TOP * p, p, p, PixelArt.PAL['Q'], 255);
        // shine down the left of the bulb
        PixelArt.rect(x + 2 * p, y + 12 * p, p, 2 * p, PixelArt.PAL['y'], 150);
        for (Bubble b : bubbles) {
            if (!b.live) continue;
            int by = Math.round(b.y);
            int bx = Math.round(b.x) + ((int) (b.y * 1.5f) % 2);       // a one-pixel wobble on the way up
            int lo = by >= 12 ? 1 : 2, hi = by >= 12 ? 11 : 10;       // the bulb narrows near the surface
            if (bx < lo || bx > hi) continue;
            PixelArt.rect(x + bx * p, y + by * p, p, p, PixelArt.PAL['Q'], 230);
        }
        // a puff of vapour leaves the neck now and then
        float vt = (time % 2.4f) / 2.4f;
        if (vt < 0.5f)
            PixelArt.rect(x + 6 * p, y - (1 + Math.round(vt * 6)) * p, p, p, PixelArt.PAL['f'],
                    (int) (160 * (1f - vt * 2f)));
        PixelArt.end();
    }

    private void step(boolean hovered) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        time += dt;
        hover += ((hovered ? 1f : 0f) - hover) * Math.min(1f, dt * 8f);
        burst = Math.max(0f, burst - dt);

        float rate = 1f + hover * 1.5f + (burst > 0f ? 3f : 0f);
        timer -= dt * rate;
        if (timer <= 0f) {
            timer = 0.45f + rng.nextFloat() * 0.5f;
            spawn();
        }
        for (Bubble b : bubbles) {
            if (!b.live) continue;
            b.y -= dt * b.speed * (1f + hover);
            if (b.y < LIQ_TOP) b.live = false;
        }
    }

    private void spawn() {
        for (Bubble b : bubbles)
            if (!b.live) {
                b.live = true;
                b.x = 2 + rng.nextInt(9);
                b.y = LIQ_BOTTOM + 0.5f;
                b.speed = 2.2f + rng.nextFloat() * 1.8f;
                return;
            }
    }
}
