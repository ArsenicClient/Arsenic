package arsenic.utils.render;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * The target indicator styles. 1.8 painted these with a fragment shader over hand-built meshes; here they are
 * built from gizmo quads and lines, so they only draw inside an {@code EventRenderWorldLast} listener. Positions
 * are absolute world coordinates, as everything drawn with gizmos is.
 */
public final class TargetFx {

    public enum Style {
        /** A thick glowing ring sliding up and down the body, trailing light behind it. */
        CLASSIC,
        /** A spinning gold crown over the head, set with gems, that hops on each hit. */
        CROWN,
        /** A segmented health bar wrapped round the feet, one segment a heart, with the damage just taken draining away. */
        HEALTH_ARC
    }

    /** What is being drawn round. */
    public static final class Target {
        public double x, y, z;            // feet
        public double height = 1.8;
        public double radius = 0.7;
        public float health = 1f;         // 0..1
        public float healthLag = 1f;      // health a moment ago, which drains down to health after a hit; 0..1
        public float absorption;          // absorption hearts as a fraction of max health
        public float maxHealth = 20f;
        public float hit;                 // 1 the moment a hit lands, falling to 0
        public double dirX, dirZ;         // horizontal direction from the target toward the viewer
        public int color = 0xFFFFFF;
        public float alpha = 1f;
    }

    private TargetFx() {
    }

    /** Draws the style round the target. time is in seconds and only needs to keep counting up. */
    public static void render(Style style, Target t, double time) {
        switch (style) {
            case CROWN:
                crown(t, time);
                break;
            case HEALTH_ARC:
                healthArc(t);
                break;
            case CLASSIC:
            default:
                classic(t, time);
                break;
        }
    }

    // ---- styles ----

    private static void classic(Target t, double time) {
        // eases from the feet to the top of the head and back, the trail hanging on the side it came from
        double phase = time * 2.6;
        double ringY = t.y + 0.1 + (t.height - 0.2) * (0.5 - 0.5 * Math.cos(phase));
        double speed = Math.sin(phase);
        double trailY = ringY - speed * 0.35;

        int segments = 64;
        int band = argb(t.color, t.alpha * 0.45f);
        int rim = argb(t.color, t.alpha);
        Vec3 prevTop = null, prevBottom = null;
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2 / segments;
            double x = t.x + t.radius * Math.cos(a), z = t.z + t.radius * Math.sin(a);
            Vec3 top = new Vec3(x, ringY, z);
            Vec3 bottom = new Vec3(x, trailY, z);
            if (prevTop != null) {
                Gizmos.rect(prevTop, top, bottom, prevBottom, GizmoStyle.fill(band)).setAlwaysOnTop();
                Gizmos.line(prevTop, top, rim, 3f).setAlwaysOnTop();
            }
            prevTop = top;
            prevBottom = bottom;
        }
    }

    private static final int CROWN_POINTS = 8, CROWN_STEPS = 6;
    private static final double CROWN_BAND = 0.11, CROWN_SPIKE = 0.17, CROWN_FLARE = 0.06;

    // A gold king's crown floating over the head, spinning slowly, with gems set in its band. A hit makes it hop.
    private static void crown(Target t, double time) {
        double hop = t.hit > 0 ? 0.18 * Math.sin(Math.PI * (1 - t.hit)) : 0;
        double y0 = t.y + t.height + 0.1 + 0.025 * Math.sin(time * 2) + hop;
        double r0 = t.radius * 0.45;
        double spin = time * 0.5;

        int gold = argb(0xFFC93A, t.alpha);
        int goldDark = argb(0xB07A10, t.alpha * 0.85f);
        int steps = CROWN_POINTS * CROWN_STEPS;
        Vec3 prevBase = null, prevTop = null;
        for (int i = 0; i <= steps; i++) {
            double a = spin + i * Math.PI * 2 / steps;
            double h = crownHeight(i / (double) CROWN_STEPS);
            double rt = crownRadius(r0, h);
            Vec3 base = new Vec3(t.x + r0 * Math.cos(a), y0, t.z + r0 * Math.sin(a));
            Vec3 top = new Vec3(t.x + rt * Math.cos(a), y0 + h, t.z + rt * Math.sin(a));
            if (prevBase != null) {
                Gizmos.rect(prevBase, base, top, prevTop, GizmoStyle.fill(goldDark)).setAlwaysOnTop();
                Gizmos.line(prevTop, top, gold, 2f).setAlwaysOnTop();
                Gizmos.line(prevBase, base, gold, 2f).setAlwaysOnTop();
            }
            prevBase = base;
            prevTop = top;
        }

        // a gem in the band below each point, in the theme colour
        double gemY = y0 + CROWN_BAND * 0.5;
        int gem = argb(t.color, t.alpha);
        for (int i = 0; i < CROWN_POINTS; i++) {
            double a = spin + (i + 0.5) * Math.PI * 2 / CROWN_POINTS;
            double r = crownRadius(r0, CROWN_BAND * 0.5) + 0.01;
            Gizmos.point(new Vec3(t.x + r * Math.cos(a), gemY, t.z + r * Math.sin(a)), gem, 6f).setAlwaysOnTop();
            double rt = crownRadius(r0, CROWN_BAND + CROWN_SPIKE);
            Gizmos.point(new Vec3(t.x + rt * Math.cos(a), y0 + CROWN_BAND + CROWN_SPIKE + 0.02, t.z + rt * Math.sin(a)),
                    gold, 5f).setAlwaysOnTop();
        }
    }

    // The crown flares outward going up
    private static double crownRadius(double r0, double up) {
        return r0 + CROWN_FLARE * up / (CROWN_BAND + CROWN_SPIKE);
    }

    // Height of the crown's top edge above its base, with u counting points round it: the band, rising into a point
    // in the middle of each
    private static double crownHeight(double u) {
        double d = Math.abs(u - Math.floor(u) - 0.5);
        double spike = Math.max(0, 1 - d / 0.3);
        return CROWN_BAND + CROWN_SPIKE * Math.pow(spike, 1.6);
    }

    // The bar fills from the side facing the viewer outward both ways, so what is left stays in front and the gap
    // opens at the back. Each segment is one heart.
    private static void healthArc(Target t) {
        double r = t.radius * (1 + 0.06 * t.hit);
        double y = t.y + 0.02;
        int hearts = Math.max(4, Math.min(20, Math.round(t.maxHealth / 2)));
        double facing = Math.atan2(t.dirZ, t.dirX);
        double gap = 0.12; // fraction of a segment left empty between hearts

        int full = healthColor(t.health, t.alpha);
        int lag = argb(0xFFFFFF, t.alpha * 0.7f);
        int empty = argb(0x000000, t.alpha * 0.35f);
        int absorb = argb(0xFFD23A, t.alpha);
        for (int i = 0; i < hearts; i++) {
            // segment i's distance from the front, as a fraction of the half circle on its side
            int fromFront = i / 2;
            double side = i % 2 == 0 ? 1 : -1;
            double f0 = fromFront / (double) ((hearts + 1) / 2), f1 = (fromFront + 1 - gap) / (double) ((hearts + 1) / 2);
            double a0 = facing + side * f0 * Math.PI, a1 = facing + side * f1 * Math.PI;
            double level = fromFront / (double) ((hearts + 1) / 2);
            int color = level < t.health ? full : level < t.healthLag ? lag : empty;
            segment(t.x, y, t.z, r, a0, a1, 0.16, color);
            if (t.absorption > 0 && level < t.absorption)
                segment(t.x, y + 0.03, t.z, r + 0.09, a0, a1, 0.05, absorb);
        }
    }

    // a flat curved strip of the ring from angle a0 to a1
    private static void segment(double cx, double cy, double cz, double r, double a0, double a1, double width, int color) {
        int steps = 6;
        Vec3 prevIn = null, prevOut = null;
        for (int s = 0; s <= steps; s++) {
            double a = a0 + (a1 - a0) * s / steps;
            double c = Math.cos(a), sn = Math.sin(a);
            Vec3 in = new Vec3(cx + (r - width / 2) * c, cy, cz + (r - width / 2) * sn);
            Vec3 out = new Vec3(cx + (r + width / 2) * c, cy, cz + (r + width / 2) * sn);
            if (prevIn != null)
                Gizmos.rect(prevIn, in, out, prevOut, GizmoStyle.fill(color)).setAlwaysOnTop();
            prevIn = in;
            prevOut = out;
        }
    }

    private static int healthColor(float health, float alpha) {
        float h = Math.max(0f, Math.min(1f, health));
        int r = (int) (255 * Math.min(1f, 2f * (1 - h))), g = (int) (255 * Math.min(1f, 2f * h)), b = 38;
        return argb((r << 16) | (g << 8) | b, alpha);
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
