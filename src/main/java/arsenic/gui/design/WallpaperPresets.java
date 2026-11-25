package arsenic.gui.design;

import java.awt.image.BufferedImage;

/**
 * Built-in wallpapers, painted in code (no image files to ship, nothing to download).
 * Each preset is a function of the pixel position; {@link #render} runs on a background thread.
 */
final class WallpaperPresets {

    static final String PREFIX = "builtin:";
    static final String[] NAMES = {
     "Aurora", "Sunset Peaks", "Synthwave", "Nebula", "Ocean Depths", "Midnight Mesh", "Pastel Mesh", "Ember"
    };

    private static final int W = 1920, H = 1080;
    private static final float ASPECT = W / (float) H;

    private WallpaperPresets() {}

    static boolean isPreset(String name) {
        return name.startsWith(PREFIX);
    }

    static String displayName(String name) {
        return name.substring(PREFIX.length());
    }

    static BufferedImage render(String name) {
        String n = displayName(name);
        int[] px = new int[W * H];
        // rows are independent, so every core paints its own share
 java.util.stream.IntStream.range(0, H).parallel().forEach(y -> {
     float[] c = new float[3];
     for (int x = 0; x < W; x++) {
  float u = x / (float) (W - 1), v = y / (float) (H - 1);
         switch (n) {
      case "Aurora": aurora(c, u, v, x, y); break;
      case "Sunset Peaks": sunset(c, u, v); break;
      case "Synthwave": synthwave(c, u, v, x, y); break;
      case "Nebula": nebula(c, u, v, x, y); break;
      case "Ocean Depths": ocean(c, u, v, x, y); break;
      case "Midnight Mesh": mesh(c, u, v, MIDNIGHT); break;
      case "Pastel Mesh": mesh(c, u, v, PASTEL); break;
      case "Ember": ember(c, u, v, x, y); break;
      default: set(c, 0.05f, 0.05f, 0.08f);
         }
  // a touch of film grain also hides gradient banding
  float g = (hash(x, y) - 0.5f) * (2.2f / 255f);
  px[y * W + x] = rgb(c[0] + g, c[1] + g, c[2] + g);
     }
        });
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, W, H, px, 0, W);
        return img;
    }

    // ================================================================== presets

    private static void aurora(float[] c, float u, float v, int x, int y) {
        vgrad(c, v, 0.02f, 0.025f, 0.08f, 0.02f, 0.09f, 0.14f);
 add(c, 0.9f, 0.95f, 1f, stars(x, y, 7f, 0.10f, 1) * (1f - v));
        float[][] cols = {{0.20f, 1.00f, 0.60f}, {0.10f, 0.80f, 0.90f}, {0.65f, 0.35f, 1.00f}};
        for (int k = 0; k < 3; k++) {
     float yc = 0.34f + 0.09f * k + 0.07f * sin(u * 5.5f + k * 1.9f) + 0.03f * sin(u * 13f + k * 4f);
     float t = v < yc ? (float) Math.exp(-(yc - v) / 0.17f) : (float) Math.exp(-(v - yc) / 0.012f);
     float rays = 0.45f + 0.55f * vnoise(u * 70f + k * 11f, v * 2.5f + k);
     float fade = 0.35f + 0.65f * smooth(0.1f, 0.9f, 0.5f + 0.5f * sin(u * 2.7f + k * 2.3f));
     float i = t * rays * fade * 0.6f;
     add(c, cols[k][0], cols[k][1], cols[k][2], i);
     // violet fringe at the top of each curtain
     if (v < yc)
         add(c, 0.6f, 0.2f, 0.8f, i * 0.35f * smooth(0.f, 0.25f, yc - v));
        }
        float ridge = 0.80f + 0.07f * fbm(u * 3.5f * ASPECT, 2.3f, 4) - 0.03f * sin(u * 4f);
        if (v > ridge)
     set(c, 0.01f, 0.018f, 0.035f);
 else
     add(c, 0.1f, 0.5f, 0.4f, 0.08f * smooth(ridge - 0.12f, ridge, v));
    }

    private static void sunset(float[] c, float u, float v) {
        if (v < 0.45f)
     mix3(c, v / 0.45f, 0.13f, 0.08f, 0.30f, 0.62f, 0.22f, 0.45f);
        else
     mix3(c, Math.min(1f, (v - 0.45f) / 0.2f), 0.62f, 0.22f, 0.45f, 1.0f, 0.62f, 0.36f);
        // sun + glow
 float dx = (u - 0.5f) * ASPECT, dy = v - 0.60f, d = (float) Math.sqrt(dx * dx + dy * dy);
 add(c, 1f, 0.55f, 0.35f, 0.55f * (float) Math.exp(-d / 0.16f));
        if (d < 0.085f)
     mix(c, 1f, 0.93f, 0.75f, smooth(0.085f, 0.08f, d));
        // thin clouds
        float cl = fbm(u * 2.5f * ASPECT, v * 14f, 5);
        add(c, 1f, 0.6f, 0.65f, 0.35f * smooth(0.55f, 0.8f, cl) * smooth(0.05f, 0.25f, v) * smooth(0.55f, 0.35f, v));
        // four mountain ranges, far to near
        float[][] cols = {{0.55f, 0.27f, 0.42f}, {0.36f, 0.16f, 0.33f}, {0.20f, 0.08f, 0.22f}, {0.07f, 0.03f, 0.10f}};
        for (int i = 0; i < 4; i++) {
     float base = 0.58f + i * 0.09f;
     float ridge = base - (0.16f - i * 0.025f) * fbm(u * (2.2f + i * 1.2f) * ASPECT + i * 7.3f, i * 3.1f, 5);
     if (v > ridge) {
  float shade = 1f - 0.25f * smooth(ridge, ridge + 0.3f, v);
  set(c, cols[i][0] * shade, cols[i][1] * shade, cols[i][2] * shade);
     }
        }
    }

    private static void synthwave(float[] c, float u, float v, int x, int y) {
 float horizon = 0.62f;
        if (v < horizon) {
     mix3(c, v / horizon, 0.04f, 0.01f, 0.12f, 0.50f, 0.08f, 0.48f);
     add(c, 1f, 1f, 1f, stars(x, y, 9f, 0.09f, 3) * (1f - v / horizon));
     // striped sun
     float dx = (u - 0.5f) * ASPECT, dy = v - 0.43f, d = (float) Math.sqrt(dx * dx + dy * dy);
     add(c, 1f, 0.3f, 0.6f, 0.5f * (float) Math.exp(-Math.max(0f, d - 0.2f) / 0.08f));
     if (d < 0.2f) {
         float t = (v - 0.23f) / 0.4f;
         float below = (v - 0.43f) / 0.2f;
         boolean cut = below > 0f && frac(v * 55f) < below * 0.7f;
         if (!cut)
      mix(c, 1f, 0.88f - 0.6f * t, 0.3f + 0.25f * t, smooth(0.2f, 0.197f, d));
     }
     // distant mountains
     float ridge = horizon - 0.09f * Math.abs(fbm(u * 4f * ASPECT, 5f, 4) - 0.25f) * 2.2f;
     if (v > ridge)
  set(c, 0.10f, 0.02f, 0.20f);
        } else {
     set(c, 0.05f, 0.0f, 0.10f);
     float z = 0.05f / (v - horizon + 0.004f);
     float gx = (u - 0.5f) * ASPECT * z * 6f;
     float gz = z * 3f;
     // distance to the nearest grid line, and how much one pixel covers, in grid units
     float lx = Math.abs(frac(gx + 0.5f) - 0.5f);
     float lz = Math.abs(frac(gz + 0.5f) - 0.5f);
     float wx = ASPECT * z * 6f / W;
     float wz = 3f * z * z / 0.05f / H;
     float gridX = smooth(wx * 1.8f + 0.004f, wx * 0.4f, lx);
     float gridZ = smooth(wz * 1.8f + 0.004f, wz * 0.4f, lz) * smooth(0.5f, 0.15f, wz);
     float line = Math.max(gridX, gridZ);
     float fog = (float) Math.exp(-z * 0.8f);
     add(c, 1f, 0.2f, 0.85f, line * (0.25f + 0.75f * fog));
     add(c, 0.9f, 0.2f, 0.7f, 0.5f * (float) Math.exp(-(v - horizon) / 0.03f));
        }
    }

    private static void nebula(float[] c, float u, float v, int x, int y) {
        set(c, 0.008f, 0.01f, 0.03f);
 float px = u * ASPECT * 2.2f, py = v * 2.2f;
        float q = fbm(px + 3.1f, py + 1.7f, 4);
        float n = fbm(px + q * 1.8f, py + q * 1.2f, 6);
        float hue = fbm(px * 0.6f + 9f, py * 0.6f, 3);
        float r = lerp(0.95f, 0.25f, hue), g = lerp(0.25f, 0.30f, hue), b = lerp(0.55f, 0.95f, hue);
        float density = (float) Math.pow(smooth(0.35f, 0.85f, n), 1.6f);
 float dust = 1f - 0.65f * smooth(0.55f, 0.75f, fbm(px * 2f + 20f, py * 2f, 5));
        add(c, r, g, b, density * dust * 0.95f);
        add(c, 1f, 0.85f, 0.95f, (float) Math.pow(density, 4f) * 0.4f);
        add(c, 0.95f, 0.95f, 1f, stars(x, y, 6f, 0.12f, 7));
 add(c, 0.8f, 0.9f, 1f, bigStars(x, y, 90f, 0.25f, 11));
    }

    private static void ocean(float[] c, float u, float v, int x, int y) {
        if (v < 0.5f)
     mix3(c, v / 0.5f, 0.06f, 0.42f, 0.58f, 0.02f, 0.14f, 0.32f);
        else
     mix3(c, (v - 0.5f) / 0.5f, 0.02f, 0.14f, 0.32f, 0.0f, 0.02f, 0.07f);
 // god rays fanning out from above the top edge
        float fx = (u - 0.5f) * ASPECT / (v + 0.35f);
 float rays = (float) Math.pow(vnoise(fx * 9f, 1.3f), 3f) + 0.5f * (float) Math.pow(vnoise(fx * 21f, 7.7f), 4f);
        add(c, 0.55f, 0.9f, 1f, rays * 0.32f * (1f - v) * (1f - v));
        // caustic shimmer near the surface
        float ca = fbm(u * ASPECT * 9f, v * 9f, 3);
        add(c, 0.6f, 1f, 1f, 0.12f * smooth(0.55f, 0.7f, ca) * smooth(0.35f, 0f, v));
        // drifting particles
        add(c, 0.6f, 0.85f, 1f, stars(x, y, 11f, 0.08f, 5) * 0.45f * (0.3f + v));
    }

    private static final float[][] MIDNIGHT = {
     {0.04f, 0.05f, 0.12f},
     {0.30f, 0.12f, 0.55f, 0.18f, 0.22f, 0.45f},
     {0.06f, 0.30f, 0.55f, 0.85f, 0.30f, 0.40f},
     {0.55f, 0.10f, 0.35f, 0.60f, 0.85f, 0.35f},
     {0.05f, 0.40f, 0.45f, 0.95f, 0.95f, 0.30f},
    };
    private static final float[][] PASTEL = {
     {0.93f, 0.90f, 0.97f},
     {1.00f, 0.75f, 0.70f, 0.15f, 0.20f, 0.45f},
     {0.70f, 0.75f, 1.00f, 0.85f, 0.25f, 0.45f},
     {0.70f, 0.95f, 0.85f, 0.35f, 0.90f, 0.40f},
     {0.98f, 0.72f, 0.90f, 0.80f, 0.85f, 0.35f},
    };

    /** Soft "mesh gradient": coloured blobs {r, g, b, x, y, radius} blended over a base colour. */
    private static void mesh(float[] c, float u, float v, float[][] pal) {
        // a slow warp keeps the blobs organic instead of perfectly round
        float wu = u + 0.06f * (fbm(u * 3f, v * 3f, 3) - 0.5f);
        float wv = v + 0.06f * (fbm(u * 3f + 5f, v * 3f + 5f, 3) - 0.5f);
        float wr = pal[0][0] * 0.3f, wg = pal[0][1] * 0.3f, wb = pal[0][2] * 0.3f, ws = 0.3f;
 for (int i = 1; i < pal.length; i++) {
     float[] p = pal[i];
     float dx = (wu - p[3]) * ASPECT, dy = wv - p[4];
     float w = (float) Math.exp(-(dx * dx + dy * dy) / (p[5] * p[5]));
     wr += p[0] * w; wg += p[1] * w; wb += p[2] * w; ws += w;
        }
        set(c, wr / ws, wg / ws, wb / ws);
    }

    private static void ember(float[] c, float u, float v, int x, int y) {
        mix3(c, v, 0.03f, 0.015f, 0.02f, 0.16f, 0.04f, 0.02f);
        float dx = (u - 0.5f) * ASPECT, dy = v - 1.05f;
 add(c, 1f, 0.35f, 0.08f, 0.55f * (float) Math.exp(-Math.sqrt(dx * dx * 0.5f + dy * dy) / 0.35f));
        float smoke = fbm(u * ASPECT * 2.5f, v * 2.5f - 3f, 5);
        add(c, 0.6f, 0.2f, 0.08f, 0.25f * smooth(0.45f, 0.8f, smoke) * v);
        // floating sparks: brighter and denser near the bottom
        float s = bigStars(x, y, 26f, 0.35f * v, 13);
        add(c, 1f, 0.6f, 0.2f, s * 1.3f);
        add(c, 1f, 0.75f, 0.4f, stars(x, y, 8f, 0.05f * v, 17));
    }

    // ================================================================== helpers

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
 h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216f;
    }

    private static float vnoise(float x, float y) {
 int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
 float sx = xf * xf * (3 - 2 * xf), sy = yf * yf * (3 - 2 * yf);
        float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }

    private static float fbm(float x, float y, int octaves) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
     sum += amp * vnoise(x, y);
     norm += amp;
     x = x * 2.03f + 17.1f;
     y = y * 2.01f + 9.7f;
     amp *= 0.5f;
        }
 return sum / norm;
    }

    /** Small soft dots, at most one per cell. */
    private static float stars(int x, int y, float cell, float prob, int seed) {
        int cx = (int) Math.floor(x / cell), cy = (int) Math.floor(y / cell);
        if (hash(cx * 31 + seed * 977, cy * 17 - seed * 131) > prob)
     return 0f;
 float sx = (cx + 0.15f + 0.7f * hash(cx * 3 + 1 + seed, cy * 5 + 2)) * cell;
        float sy = (cy + 0.15f + 0.7f * hash(cx * 7 + 3, cy * 11 + 4 + seed)) * cell;
        float h = hash(cx * 13 + 5, cy * 19 + 6 + seed);
 float size = 0.5f + 1.3f * h * h * h;
        float dx = x - sx, dy = y - sy;
        return (0.35f + 0.65f * h) * (float) Math.exp(-(dx * dx + dy * dy) / (size * size));
    }

    /** Rare bright stars with a glow and a faint cross. */
    private static float bigStars(int x, int y, float cell, float prob, int seed) {
 float best = 0f;
        int cx0 = (int) Math.floor(x / cell), cy0 = (int) Math.floor(y / cell);
        for (int cy = cy0 - 1; cy <= cy0 + 1; cy++)
     for (int cx = cx0 - 1; cx <= cx0 + 1; cx++) {
         if (hash(cx * 59 + seed, cy * 23 - seed) > prob)
      continue;
         float sx = (cx + 0.5f * hash(cx + 7, cy * 3 + seed) + 0.25f) * cell;
         float sy = (cy + 0.5f * hash(cx * 5 + seed, cy + 9) + 0.25f) * cell;
  float dx = x - sx, dy = y - sy;
  float d2 = dx * dx + dy * dy;
         float glow = (float) Math.exp(-d2 / 6f) + 0.25f * (float) Math.exp(-d2 / 120f);
         float cross = 0.3f * ((float) Math.exp(-dy * dy / 0.8f - Math.abs(dx) / 9f)
       + (float) Math.exp(-dx * dx / 0.8f - Math.abs(dy) / 9f));
  best = Math.max(best, glow + cross);
     }
 return best;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float frac(float x) { return x - (float) Math.floor(x); }
    private static float sin(float x) { return (float) Math.sin(x); }

    private static float smooth(float e0, float e1, float x) {
        float t = (x - e0) / (e1 - e0);
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return t * t * (3f - 2f * t);
    }

    private static void set(float[] c, float r, float g, float b) { c[0] = r; c[1] = g; c[2] = b; }

    private static void add(float[] c, float r, float g, float b, float k) {
        c[0] += r * k; c[1] += g * k; c[2] += b * k;
    }

    private static void mix(float[] c, float r, float g, float b, float t) {
        c[0] = lerp(c[0], r, t); c[1] = lerp(c[1], g, t); c[2] = lerp(c[2], b, t);
    }

    private static void mix3(float[] c, float t, float r1, float g1, float b1, float r2, float g2, float b2) {
 t = t < 0f ? 0f : (t > 1f ? 1f : t);
        set(c, lerp(r1, r2, t), lerp(g1, g2, t), lerp(b1, b2, t));
    }

    private static void vgrad(float[] c, float v, float r1, float g1, float b1, float r2, float g2, float b2) {
        mix3(c, v, r1, g1, b1, r2, g2, b2);
    }

    private static int rgb(float r, float g, float b) {
        int ri = (int) (Math.max(0f, Math.min(1f, r)) * 255f + 0.5f);
        int gi = (int) (Math.max(0f, Math.min(1f, g)) * 255f + 0.5f);
        int bi = (int) (Math.max(0f, Math.min(1f, b)) * 255f + 0.5f);
 return (ri << 16) | (gi << 8) | bi;
    }
}
