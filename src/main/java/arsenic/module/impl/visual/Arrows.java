package arsenic.module.impl.visual;

import arsenic.gui.themes.ThemeManager;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(name = "Pointers", category = ModuleCategory.RENDER, hidden = true)
public class Arrows extends Module {


    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        if (mc.player == null || mc.level == null) return;

        float partial = event.getPartialTicks();

        // Camera basis: forward, right (horizontal), up. Built from the interpolated view rotation.
        double yaw = Math.toRadians(interp(mc.player.yRotO, mc.player.getYRot(), partial));
        double pitch = Math.toRadians(interp(mc.player.xRotO, mc.player.getXRot(), partial));

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        // right = normalize(cross(forward, worldUp))
        double rx = -fz, rz = fx;
        double rLen = Math.sqrt(rx * rx + rz * rz);
        if (rLen < 1e-6) { rx = 1; rz = 0; rLen = 1; }
        rx /= rLen; rz /= rLen;
        // up = cross(right, forward)
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        double eyeX = interp(mc.player.xo, mc.player.getX(), partial);
        double eyeY = interp(mc.player.yo, mc.player.getY(), partial) + mc.player.getEyeHeight();
        double eyeZ = interp(mc.player.zo, mc.player.getZ(), partial);

        double vfov = Math.toRadians(mc.options.fovSetting);
        double tanV = Math.tan(vfov / 2.0);
        double aspect = mc.displayHeight == 0 ? 1.0 : (double) mc.displayWidth / mc.displayHeight;
        double tanH = tanV * aspect;

        ScaledResolution sr = event.getSr();
        float cx = sr.getScaledWidth() / 2f;
        float cy = sr.getScaledHeight() / 2f;

        // the HUD ortho flips Y, making our triangles clockwise in window space;
        // with the GUI's GL_CULL_FACE enabled they'd be back-face culled away

        for (Player player : mc.level.playerEntities) {
            if (player == mc.player) continue;
            if (player.isRemoved() || player.isInvisible()) continue;
            if (AntiBot.isBot(player)) continue;
            float dist = mc.player.distanceTo(player);
            if (dist > 64) continue;

            double tx = interp(player.xo, player.getX(), partial) - eyeX;
            double ty = interp(player.yo, player.getY(), partial) + player.height * 0.5 - eyeY;
            double tz = interp(player.zo, player.getZ(), partial) - eyeZ;
            double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-6) continue;
            tx /= len; ty /= len; tz /= len;

            double fwd = tx * fx + ty * fy + tz * fz;
            double right = tx * rx + tz * rz;              // R has no y component
            double up = tx * ux + ty * uy + tz * uz;

            // On-screen? Skip — arrows are only for players you can't see. The 1.1
            // margin covers MC's dynamic FOV (sprint/speed widen the real frustum
            // beyond fovSetting), so we never point at someone already visible.
            if (fwd > 0.0) {
                double ndcX = (right / fwd) / tanH;
                double ndcY = (up / fwd) / tanV;
                if (Math.abs(ndcX) <= 1.1 && Math.abs(ndcY) <= 1.1) continue;
            }

            // Screen-space bearing to the target (HUD y grows downward, so up-world -> -y).
            // Directly behind, right/up are ~0 and atan2 degenerates - point down.
            double angle = (fwd < 0.0 && Math.abs(right) < 1e-4 && Math.abs(up) < 1e-4)
                    ? Math.PI / 2.0
                    : Math.atan2(-up, right);
            float t = Math.min(1f, dist / (float) 64);
            // Near/far now read as the theme's own two-tone gradient.
            int color = lerpColor(ThemeManager.getMainColor(), ThemeManager.getGradientColor(), t);

            drawArrow(cx, cy, (float) 34, (float) 11, angle, color);
        }

    };

    private void drawArrow(float cx, float cy, float radius, float len, double angle, int color) {
        double c = Math.cos(angle), s = Math.sin(angle);
        // Anchor the arrow on a ring around the crosshair, pointing outward along (c, s).
        float baseX = cx + (float) (c * radius);
        float baseY = cy + (float) (s * radius);
        float tipX = baseX + (float) (c * len);
        float tipY = baseY + (float) (s * len);
        float backX = baseX - (float) (c * len * 0.5);
        float backY = baseY - (float) (s * len * 0.5);
        float wid = len * 0.55f;
        float leftX = backX + (float) (-s * wid);
        float leftY = backY + (float) (c * wid);
        float rightX = backX - (float) (-s * wid);
        float rightY = backY - (float) (c * wid);

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(tipX, tipY);
        GL11.glVertex2f(leftX, leftY);
        GL11.glVertex2f(rightX, rightY);
        GL11.glEnd();
    }

    private static double interp(double prev, double now, float partial) {
        return prev + (now - prev) * partial;
    }

    private static float interp(float prev, float now, float partial) {
        return prev + (now - prev) * partial;
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t);
        int or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t);
        int ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
