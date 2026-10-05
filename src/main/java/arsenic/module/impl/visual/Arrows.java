package arsenic.module.impl.visual;

import arsenic.utils.render.QuadBatch;
import arsenic.gui.themes.ThemeManager;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(name = "Pointers", category = ModuleCategory.RENDER, hidden = true)
public class Arrows extends Module {


    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        if (mc.player == null || mc.level == null) return;

        float partial = event.getPartialTicks();

        double yaw = Math.toRadians(interp(mc.player.yRotO, mc.player.getYRot(), partial));
        double pitch = Math.toRadians(interp(mc.player.xRotO, mc.player.getXRot(), partial));

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -fz, rz = fx;
        double rLen = Math.sqrt(rx * rx + rz * rz);
        if (rLen < 1e-6) { rx = 1; rz = 0; rLen = 1; }
        rx /= rLen; rz /= rLen;
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        double eyeX = interp(mc.player.xo, mc.player.getX(), partial);
        double eyeY = interp(mc.player.yo, mc.player.getY(), partial) + mc.player.getEyeHeight();
        double eyeZ = interp(mc.player.zo, mc.player.getZ(), partial);

        double vfov = Math.toRadians(mc.gameRenderer.mainCamera().getFov());
        double tanV = Math.tan(vfov / 2.0);
        double aspect = mc.getWindow().getHeight() == 0 ? 1.0 : (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
        double tanH = tanV * aspect;

        float cx = event.getWidth() / 2f;
        float cy = event.getHeight() / 2f;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.isRemoved() || player.isInvisible()) continue;
            if (AntiBot.isBot(player)) continue;
            float dist = mc.player.distanceTo(player);
            if (dist > 64) continue;

            double tx = interp(player.xo, player.getX(), partial) - eyeX;
            double ty = interp(player.yo, player.getY(), partial) + player.getBbHeight() * 0.5 - eyeY;
            double tz = interp(player.zo, player.getZ(), partial) - eyeZ;
            double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-6) continue;
            tx /= len; ty /= len; tz /= len;

            double fwd = tx * fx + ty * fy + tz * fz;
            double right = tx * rx + tz * rz;
            double up = tx * ux + ty * uy + tz * uz;

            if (fwd > 0.0) {
                double ndcX = (right / fwd) / tanH;
                double ndcY = (up / fwd) / tanV;
                if (Math.abs(ndcX) <= 1.1 && Math.abs(ndcY) <= 1.1) continue;
            }

            double angle = (fwd < 0.0 && Math.abs(right) < 1e-4 && Math.abs(up) < 1e-4)
                    ? Math.PI / 2.0
                    : Math.atan2(-up, right);
            float t = Math.min(1f, dist / (float) 64);
            int color = lerpColor(ThemeManager.getMainColor(), ThemeManager.getGradientColor(), t);

            drawArrow(cx, cy, (float) 34, (float) 11, angle, color);
        }

    };

    private void drawArrow(float cx, float cy, float radius, float len, double angle, int color) {
        double c = Math.cos(angle), s = Math.sin(angle);
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

        new QuadBatch().triangle(tipX, tipY, leftX, leftY, rightX, rightY, color).submit();
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
