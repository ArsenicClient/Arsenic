package arsenic.utils.render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * The target indicator styles. The look of every style comes from targetfx.fsh; the geometry here is only the surface
 * each effect is painted on, with texcoords and normals carrying what the shader needs. This class uses nothing but
 * OpenGL, so it can be drawn outside the game to preview it.
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

    /** What is being drawn round. Positions are relative to the render origin, as everything in world rendering is. */
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

    // match the vertex shader's layouts and the fragment shader's effects
    private static final int GEOM_MESH = 0, GEOM_BILLBOARD = 1;
    private static final int FX_TUBE = 0, FX_TRAIL = 1, FX_ORB = 2, FX_CRYSTAL = 3, FX_METAL = 4, FX_HEALTH = 5,
            FX_HEALTH_WALL = 6;

    // Parts hidden behind blocks or the target's own body are drawn at this strength, so the effect stays visible
    // through walls but still reads as being in the world.
    private static final float HIDDEN = 0.3f;

    private static int program;
    // true while drawing what shows through walls; most styles draw the same either way
    private static boolean hiddenPass;

    private TargetFx() {
    }

    /**
     * Draws the style round the target with the given linked targetfx program, or a plain ring if program is 0.
     * time is in seconds and only needs to keep counting up.
     */
    public static void render(Style style, Target t, int program, double time) {
        TargetFx.program = program;
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_CURRENT_BIT | GL_LINE_BIT);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glDepthMask(false);

        if (program == 0) {
            fallback(t);
        } else {
            // the shader outputs premultiplied colour with only partial coverage, so glow adds onto what is behind it
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glUseProgram(program);
            float r = (t.color >> 16 & 255) / 255f, g = (t.color >> 8 & 255) / 255f, b = (t.color & 255) / 255f;
            glUniform3f(loc("uColor"), r, g, b);
            float[] hp = healthColor(t.health);
            glUniform3f(loc("uColor2"), hp[0], hp[1], hp[2]);
            glUniform1f(loc("uTime"), (float) (time % 3600));
            glUniform1f(loc("uHit"), t.hit);

            // once through everything with the depth test off, then again with it on, so the visible parts add up
            // to full strength and the hidden ones stay at HIDDEN
            glDisable(GL_DEPTH_TEST);
            glUniform1f(loc("uAlpha"), t.alpha * HIDDEN);
            hiddenPass = true;
            draw(style, t, time);
            hiddenPass = false;
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
            glUniform1f(loc("uAlpha"), t.alpha * (1 - HIDDEN));
            draw(style, t, time);

            glUseProgram(0);
        }
        glPopAttrib();
    }

    private static void draw(Style style, Target t, double time) {
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
        // eases from the feet to the top of the head and back
        double phase = time * 2.6;
        double ringY = t.y + 0.1 + (t.height - 0.2) * (0.5 - 0.5 * Math.cos(phase));
        float speed = (float) Math.sin(phase);

        setup(GEOM_MESH, FX_TRAIL, 0);
        glUniform1f(loc("uParam"), speed);
        wall(t.x, ringY, t.z, t.radius, -0.8, 0.8, 96);

        setup(GEOM_MESH, FX_TUBE, 0);
        torus(t.x, ringY, t.z, t.radius, 0.04, 0.1, 96, 16);
    }

    private static final int CROWN_POINTS = 8, CROWN_STEPS = 24; // steps per point, even so each peak is sampled
    private static final double CROWN_BAND = 0.11, CROWN_SPIKE = 0.17, CROWN_FLARE = 0.06, CROWN_THICK = 0.02;

    // A gold king's crown floating over the head, spinning slowly, with gems set in its band. A hit makes it hop.
    private static void crown(Target t, double time) {
        double hop = t.hit > 0 ? 0.18 * Math.sin(Math.PI * (1 - t.hit)) : 0;
        double y0 = t.y + t.height + 0.1 + 0.025 * Math.sin(time * 2) + hop;
        double r0 = t.radius * 0.45;
        double spin = time * 0.5;
        double gemY = y0 + CROWN_BAND * 0.5;

        // the gems' glow is all that shows through walls
        if (hiddenPass) {
            crownGemGlow(t, y0, r0, spin, gemY);
            return;
        }

        // The gold is solid, so lay its depth down first and then colour only the nearest surface. Otherwise the far
        // side of the band would show through the near side.
        setup(GEOM_MESH, FX_METAL, 0);
        glUniform1f(loc("uAlpha"), t.alpha);
        glColorMask(false, false, false, false);
        glDepthMask(true);
        crownMetal(t, y0, r0, spin);
        glColorMask(true, true, true, true);
        glDepthMask(false);
        crownMetal(t, y0, r0, spin);
        glUniform1f(loc("uAlpha"), t.alpha * (1 - HIDDEN));

        // a faceted gem set into the band below each point, half sunk into the gold
        glEnable(GL_CULL_FACE);
        setup(GEOM_MESH, FX_CRYSTAL, 0);
        for (int i = 0; i < CROWN_POINTS; i++) {
            double a = spin + (i + 0.5) * Math.PI * 2 / CROWN_POINTS;
            double c = Math.cos(a), s = Math.sin(a), r = crownRadius(r0, gemY - y0) - 0.03;
            glUniform1f(loc("uParam"), i / (float) CROWN_POINTS);
            crystal(t.x + r * c, gemY, t.z + r * s, c, 0, s, 0.1, 0.042, 0.55, 6, a);
        }
        glDisable(GL_CULL_FACE);
        crownGemGlow(t, y0, r0, spin, gemY);
    }

    private static void crownGemGlow(Target t, double y0, double r0, double spin, double gemY) {
        setup(GEOM_BILLBOARD, FX_ORB, 0.09f);
        glUniform1f(loc("uGain"), 0.6f);
        glBegin(GL_QUADS);
        for (int i = 0; i < CROWN_POINTS; i++) {
            double a = spin + (i + 0.5) * Math.PI * 2 / CROWN_POINTS, r = crownRadius(r0, gemY - y0) + 0.04;
            billboard(t.x + r * Math.cos(a), gemY, t.z + r * Math.sin(a));
        }
        glEnd();
    }

    // The crown flares outward going up
    private static double crownRadius(double r0, double up) {
        return r0 + CROWN_FLARE * up / (CROWN_BAND + CROWN_SPIKE);
    }

    // Height of the crown's top edge above its base, with u counting points round it: the band, rising into a point
    // with slightly hollow sides in the middle of each
    private static double crownHeight(double u) {
        double d = Math.abs(u - Math.floor(u) - 0.5);
        double spike = Math.max(0, 1 - d / 0.3);
        return CROWN_BAND + CROWN_SPIKE * Math.pow(spike, 1.6);
    }

    // The band with its points, its trim, and a gold ball on each point
    private static void crownMetal(Target t, double y0, double r0, double spin) {
        int steps = CROWN_POINTS * CROWN_STEPS;
        double slope = CROWN_FLARE / (CROWN_BAND + CROWN_SPIKE);
        // outer face, inner face, then the strips closing the top edge and the bottom
        for (int side = 0; side < 4; side++) {
            glBegin(GL_TRIANGLE_STRIP);
            for (int i = 0; i <= steps; i++) {
                double a = spin + i * Math.PI * 2 / steps;
                double c = Math.cos(a), s = Math.sin(a);
                double h = crownHeight(i / (double) CROWN_STEPS);
                double ro = crownRadius(r0, h), ri = ro - CROWN_THICK;
                float tu = i / (float) steps;
                if (side < 2) {
                    double sign = side == 0 ? 1 : -1, off = side == 0 ? 0 : CROWN_THICK;
                    glNormal3d(sign * c, -sign * slope, sign * s);
                    glTexCoord2f(tu, 0);
                    glVertex3d(t.x + (r0 - off) * c, y0, t.z + (r0 - off) * s);
                    glTexCoord2f(tu, (float) (h / (CROWN_BAND + CROWN_SPIKE)));
                    glVertex3d(t.x + (ro - off) * c, y0 + h, t.z + (ro - off) * s);
                } else if (side == 2) {
                    glNormal3d(0, 1, 0);
                    glTexCoord2f(tu, 1);
                    glVertex3d(t.x + ro * c, y0 + h, t.z + ro * s);
                    glVertex3d(t.x + ri * c, y0 + h, t.z + ri * s);
                } else {
                    glNormal3d(0, -1, 0);
                    glTexCoord2f(tu, 0);
                    glVertex3d(t.x + r0 * c, y0, t.z + r0 * s);
                    glVertex3d(t.x + (r0 - CROWN_THICK) * c, y0, t.z + (r0 - CROWN_THICK) * s);
                }
            }
            glEnd();
        }

        // raised trim round the bottom and the top of the band
        torus(t.x, y0 + 0.008, t.z, r0 + 0.004, 0.012, 0.012, steps, 8);
        torus(t.x, y0 + CROWN_BAND - 0.008, t.z, crownRadius(r0, CROWN_BAND) + 0.004, 0.012, 0.012, steps, 8);

        for (int i = 0; i < CROWN_POINTS; i++) {
            double a = spin + (i + 0.5) * Math.PI * 2 / CROWN_POINTS;
            double r = crownRadius(r0, CROWN_BAND + CROWN_SPIKE) - CROWN_THICK / 2;
            sphere(t.x + r * Math.cos(a), y0 + CROWN_BAND + CROWN_SPIKE + 0.018, t.z + r * Math.sin(a), 0.026, 12, 8);
        }
    }

    // The bar fills from the side facing the viewer outward both ways, so what is left stays in front and the gap
    // opens at the back. Each segment is one heart.
    private static void healthArc(Target t) {
        double r = t.radius * (1 + 0.06 * t.hit);
        double y = t.y + 0.02;
        int hearts = Math.max(4, Math.min(20, Math.round(t.maxHealth / 2)));
        float facing = (float) Math.atan2(t.dirZ, t.dirX);

        setup(GEOM_MESH, FX_HEALTH_WALL, 0);
        glUniform4f(loc("uHealth"), t.health, t.healthLag, t.absorption, hearts);
        glUniform1f(loc("uFacing"), facing);
        wall(t.x, y, t.z, r, 0, 0.45, 128);

        setup(GEOM_MESH, FX_HEALTH, 0);
        glUniform4f(loc("uHealth"), t.health, t.healthLag, t.absorption, hearts);
        glUniform1f(loc("uFacing"), facing);
        annulus(t.x, y, t.z, r, 0.16, 192);
    }

    // Without the shader: a plain ring at the feet
    private static void fallback(Target t) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glLineWidth(2f);
        glColor4f((t.color >> 16 & 255) / 255f, (t.color >> 8 & 255) / 255f, (t.color & 255) / 255f, t.alpha);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            glVertex3d(t.x + t.radius * Math.cos(a), t.y + 0.05, t.z + t.radius * Math.sin(a));
        }
        glEnd();
    }

    // ---- geometry ----

    private static void setup(int geom, int fx, float width) {
        glUniform1i(loc("uGeom"), geom);
        glUniform1i(loc("uFx"), fx);
        glUniform1f(loc("uWidth"), width);
        glUniform1f(loc("uGain"), 1f);
    }

    // A flat ring round radius rad reaching half out to either side, texcoord.x round it 0..1 and texcoord.y the
    // distance out from rad in blocks
    private static void annulus(double cx, double cy, double cz, double rad, double half, int steps) {
        glBegin(GL_TRIANGLE_STRIP);
        glNormal3f(0, 1, 0);
        for (int i = 0; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps;
            double c = Math.cos(a), s = Math.sin(a);
            float u = i / (float) steps;
            glTexCoord2f(u, (float) -half);
            glVertex3d(cx + (rad - half) * c, cy, cz + (rad - half) * s);
            glTexCoord2f(u, (float) half);
            glVertex3d(cx + (rad + half) * c, cy, cz + (rad + half) * s);
        }
        glEnd();
    }

    // An open cylinder from cy+v0 to cy+v1, with texcoord.x round it 0..1 and texcoord.y the height above cy
    private static void wall(double cx, double cy, double cz, double rad, double v0, double v1, int steps) {
        glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps;
            double c = Math.cos(a), s = Math.sin(a);
            float u = i / (float) steps;
            glNormal3d(c, 0, s);
            glTexCoord2f(u, (float) v0);
            glVertex3d(cx + rad * c, cy + v0, cz + rad * s);
            glTexCoord2f(u, (float) v1);
            glVertex3d(cx + rad * c, cy + v1, cz + rad * s);
        }
        glEnd();
    }

    // A ring of radius rad whose cross-section is an ellipse w wide and h tall (half-sizes), texcoord.x round the ring
    private static void torus(double cx, double cy, double cz, double rad, double w, double h, int around, int across) {
        for (int j = 0; j < across; j++) {
            glBegin(GL_TRIANGLE_STRIP);
            for (int i = 0; i <= around; i++) {
                double a = i * Math.PI * 2 / around;
                double c = Math.cos(a), s = Math.sin(a);
                float u = i / (float) around;
                for (int k = 0; k < 2; k++) {
                    double p = (j + k) * Math.PI * 2 / across;
                    double pc = Math.cos(p), ps = Math.sin(p);
                    double rr = rad + w * pc;
                    // the normal of an ellipse (w cos p, h sin p) is (h cos p, w sin p)
                    double nr = h * pc, ny = w * ps, nl = Math.sqrt(nr * nr + ny * ny);
                    glNormal3d(nr / nl * c, ny / nl, nr / nl * s);
                    glTexCoord2f(u, (float) ((j + k) / (double) across));
                    glVertex3d(cx + rr * c, cy + h * ps, cz + rr * s);
                }
            }
            glEnd();
        }
    }

    // A UV sphere
    private static void sphere(double cx, double cy, double cz, double r, int lon, int lat) {
        for (int j = 0; j < lat; j++) {
            glBegin(GL_TRIANGLE_STRIP);
            for (int i = 0; i <= lon; i++) {
                double th = i * Math.PI * 2 / lon;
                for (int k = 1; k >= 0; k--) {
                    double ph = -Math.PI / 2 + (j + k) * Math.PI / lat;
                    double nx = Math.cos(ph) * Math.cos(th), ny = Math.sin(ph), nz = Math.cos(ph) * Math.sin(th);
                    glNormal3d(nx, ny, nz);
                    glTexCoord2f(i / (float) lon, (j + k) / (float) lat);
                    glVertex3d(cx + r * nx, cy + r * ny, cz + r * nz);
                }
            }
            glEnd();
        }
    }

    // A crystal standing on (bx, by, bz) along the unit axis: a point at the bottom widening to a girdle of the given
    // radius at `waist` of the way up, then narrowing to a point at len. texcoord.y runs 0 at the bottom to 1 at the
    // top. Every facet gets its own flat normal and is wound counter-clockwise seen from outside.
    private static void crystal(double bx, double by, double bz, double ax, double ay, double az, double len, double rad,
                                double waist, int sides, double spin) {
        double[] p = perpendicular(ax, ay, az);
        double[] q = {ay * p[2] - az * p[1], az * p[0] - ax * p[2], ax * p[1] - ay * p[0]};
        double[] bottom = {bx, by, bz};
        double[] top = {bx + ax * len, by + ay * len, bz + az * len};
        double gx = bx + ax * len * waist, gy = by + ay * len * waist, gz = bz + az * len * waist;
        double[][] ring = new double[sides][];
        for (int i = 0; i < sides; i++) {
            double a = spin + i * Math.PI * 2 / sides;
            double c = Math.cos(a) * rad, s = Math.sin(a) * rad;
            ring[i] = new double[]{gx + c * p[0] + s * q[0], gy + c * p[1] + s * q[1], gz + c * p[2] + s * q[2]};
        }
        glBegin(GL_TRIANGLES);
        for (int i = 0; i < sides; i++) {
            double[] v0 = ring[i], v1 = ring[(i + 1) % sides];
            facet(top, 1, v0, v1, (float) waist, gx, gy, gz);
            facet(bottom, 0, v1, v0, (float) waist, gx, gy, gz);
        }
        glEnd();
    }

    // One triangle of a crystal from its point to two girdle corners, flipped if needed so it faces away from the
    // girdle's centre
    private static void facet(double[] tip, float tipV, double[] a, double[] b, float girdleV, double gx, double gy, double gz) {
        double ux = a[0] - tip[0], uy = a[1] - tip[1], uz = a[2] - tip[2];
        double vx = b[0] - tip[0], vy = b[1] - tip[1], vz = b[2] - tip[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-12)
            return;
        double cx = (tip[0] + a[0] + b[0]) / 3 - gx, cy = (tip[1] + a[1] + b[1]) / 3 - gy, cz = (tip[2] + a[2] + b[2]) / 3 - gz;
        if (nx * cx + ny * cy + nz * cz < 0) {
            double[] swap = a;
            a = b;
            b = swap;
            nl = -nl;
        }
        glNormal3d(nx / nl, ny / nl, nz / nl);
        glTexCoord2f(0.5f, tipV);
        glVertex3d(tip[0], tip[1], tip[2]);
        glTexCoord2f(0f, girdleV);
        glVertex3d(a[0], a[1], a[2]);
        glTexCoord2f(1f, girdleV);
        glVertex3d(b[0], b[1], b[2]);
    }

    // The four corners of a camera-facing quad at a point, inside glBegin(GL_QUADS)
    private static void billboard(double x, double y, double z) {
        glTexCoord2f(-1, -1);
        glVertex3d(x, y, z);
        glTexCoord2f(1, -1);
        glVertex3d(x, y, z);
        glTexCoord2f(1, 1);
        glVertex3d(x, y, z);
        glTexCoord2f(-1, 1);
        glVertex3d(x, y, z);
    }

    // A unit vector at right angles to the unit vector (x, y, z)
    private static double[] perpendicular(double x, double y, double z) {
        // cross with whichever of up or x points least along it
        double hx = Math.abs(y) < 0.9 ? 0 : 1, hy = Math.abs(y) < 0.9 ? 1 : 0;
        double px = y * 0 - z * hy, py = z * hx - x * 0, pz = x * hy - y * hx;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        return new double[]{px / pl, py / pl, pz / pl};
    }

    // Red when low, through yellow, to green when full
    private static float[] healthColor(float health) {
        float h = Math.max(0f, Math.min(1f, health));
        return new float[]{Math.min(1f, 2f * (1 - h)), Math.min(1f, 2f * h), 0.15f};
    }

    private static int loc(String name) {
        return glGetUniformLocation(program, name);
    }
}
