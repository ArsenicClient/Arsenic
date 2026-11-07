package arsenic.utils.buildgen;

import arsenic.utils.buildcore.Blueprint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws a build so a model (and a person) can judge it: two isometric views from opposite corners, the top and the
 * front, each block one flat colour shaded by face. Glass is drawn see-through.
 */
public final class Render {
    private Render() {
    }

    public static BufferedImage sheet(Blueprint bp, Palette palette, String title) {
        int cell = 400;
        BufferedImage img = new BufferedImage(cell * 2, cell * 2 + 28, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x9aa6b2));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(new Color(0x20262c));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.drawString(title, 8, 20);
        iso(g, bp, palette, 0, 28, cell, false);
        iso(g, bp, palette, cell, 28, cell, true);
        flat(g, bp, palette, 0, 28 + cell, cell, true);
        flat(g, bp, palette, cell, 28 + cell, cell, false);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("iso from +x+z", 6, 28 + 14);
        g.drawString("iso from -x-z", cell + 6, 28 + 14);
        g.drawString("top (x right, z down)", 6, 28 + cell + 14);
        g.drawString("front (x right, y up)", cell + 6, 28 + cell + 14);
        g.setColor(new Color(0x5a6470));
        g.setStroke(new BasicStroke(1));
        g.drawLine(cell, 28, cell, img.getHeight());
        g.drawLine(0, 28 + cell, img.getWidth(), 28 + cell);
        g.dispose();
        return img;
    }

    /** Isometric view looking down from the +x+z corner (or -x-z when flipped). */
    private static void iso(Graphics2D g, Blueprint bp, Palette palette, int ox, int oy, int size, boolean flip) {
        int sx = bp.sizeX, sy = bp.sizeY, sz = bp.sizeZ;
        double span = (sx + sz) * 0.866 + 2, tall = (sx + sz) * 0.5 + sy + 2;
        double s = Math.min((size - 20) / span, (size - 30) / tall);
        double cx = ox + size / 2.0 - (sx - sz) * 0.866 * s / 2, cy = oy + 24 + sy * s;
        // floor outline
        g.setColor(new Color(0x7d8994));
        int[][] fl = {{0, 0}, {sx, 0}, {sx, sz}, {0, sz}};
        Polygon floor = new Polygon();
        for (int[] p : fl) {
            double[] q = project(p[0], 0, p[1], s, cx, cy);
            floor.addPoint((int) q[0], (int) q[1]);
        }
        g.fillPolygon(floor);
        // far cells first: the viewer is at large x + z + y
        for (int sum = 0; sum <= sx + sy + sz; sum++)
            for (int y = 0; y < sy; y++)
                for (int x = 0; x < sx; x++) {
                    int z = sum - x - y;
                    if (z < 0 || z >= sz) continue;
                    int bx = flip ? sx - 1 - x : x, bz = flip ? sz - 1 - z : z;
                    int id = bp.get(bx, y, bz);
                    if (id <= 0) continue;
                    int[] c = palette.colour(Blueprint.blockName(bp.palette.get(id)).replace("minecraft:", ""));
                    int dx = flip ? -1 : 1, dz = flip ? -1 : 1;
                    boolean clear = c[3] < 200;
                    if (clear || !opaque(bp, palette, bx, y + 1, bz)) face(g, c, 1.0, s, cx, cy, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                    if (clear || !opaque(bp, palette, bx + dx, y, bz)) face(g, c, 0.78, s, cx, cy, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1);
                    if (clear || !opaque(bp, palette, bx, y, bz + dz)) face(g, c, 0.6, s, cx, cy, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1);
                }
    }

    private static boolean opaque(Blueprint bp, Palette palette, int x, int y, int z) {
        int id = bp.get(x, y, z);
        return id > 0 && palette.colour(Blueprint.blockName(bp.palette.get(id)).replace("minecraft:", ""))[3] >= 200;
    }

    private static void face(Graphics2D g, int[] c, double shade, double s, double cx, double cy, double... p) {
        Polygon poly = new Polygon();
        for (int i = 0; i < p.length; i += 3) {
            double[] q = project(p[i], p[i + 1], p[i + 2], s, cx, cy);
            poly.addPoint((int) Math.round(q[0]), (int) Math.round(q[1]));
        }
        int a = Math.max(90, c[3]);
        g.setColor(new Color(clamp(c[0] * shade), clamp(c[1] * shade), clamp(c[2] * shade), a));
        g.fillPolygon(poly);
        if (s >= 6) {
            g.setColor(new Color(0, 0, 0, 40));
            g.drawPolygon(poly);
        }
    }

    private static double[] project(double x, double y, double z, double s, double cx, double cy) {
        return new double[]{cx + (x - z) * 0.866 * s, cy + (x + z) * 0.5 * s - y * s};
    }

    /** Orthographic view: top-down (the highest block in each column) or from the front (-z, nearest block). */
    private static void flat(Graphics2D g, Blueprint bp, Palette palette, int ox, int oy, int size, boolean top) {
        int w = bp.sizeX, h = top ? bp.sizeZ : bp.sizeY, depth = top ? bp.sizeY : bp.sizeZ;
        double s = Math.min((size - 30.0) / w, (size - 30.0) / h);
        double x0 = ox + (size - w * s) / 2, y0 = oy + 20 + (size - 20 - h * s) / 2;
        g.setColor(new Color(0x7d8994));
        g.fillRect((int) x0, (int) y0, (int) Math.ceil(w * s), (int) Math.ceil(h * s));
        for (int u = 0; u < w; u++)
            for (int v = 0; v < h; v++) {
                double r = 0, gr = 0, b = 0, keep = 1;
                boolean any = false;
                // front to back, mixing see-through blocks with what is behind
                for (int d = 0; d < depth && keep > 0.05; d++) {
                    int x = u, y = top ? depth - 1 - d : h - 1 - v, z = top ? v : d;
                    int id = bp.get(x, y, z);
                    if (id <= 0) continue;
                    int[] c = palette.colour(Blueprint.blockName(bp.palette.get(id)).replace("minecraft:", ""));
                    double a = c[3] / 255.0, shade = 1.0 - 0.45 * d / depth;
                    r += keep * a * c[0] * shade;
                    gr += keep * a * c[1] * shade;
                    b += keep * a * c[2] * shade;
                    keep *= 1 - a;
                    any = true;
                }
                if (!any) continue;
                r += keep * 0x7d;
                gr += keep * 0x89;
                b += keep * 0x94;
                g.setColor(new Color(clamp(r), clamp(gr), clamp(b)));
                g.fillRect((int) (x0 + u * s), (int) (y0 + v * s), (int) Math.ceil(s), (int) Math.ceil(s));
            }
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
