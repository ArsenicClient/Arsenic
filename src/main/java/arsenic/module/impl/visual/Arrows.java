package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

@ModuleInfo(name = "Pointers", category = ModuleCategory.WORLD, hidden = true)
public class Arrows extends Module {

    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(0, 120, 34, 1));
    public final DoubleProperty length = new DoubleProperty("Size", new DoubleValue(4, 30, 11, 1));
    public final DoubleProperty maxDistance = new DoubleProperty("Max Distance", new DoubleValue(8, 256, 64, 1));
    public final ColourProperty nearColor = new ColourProperty("Near", 0xFFFF4040);
    public final ColourProperty farColor = new ColourProperty("Far", 0xFF40FF80);

    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        float partial = event.getPartialTicks();

        // Camera basis: forward, right (horizontal), up. Built from the interpolated view rotation.
        double yaw = Math.toRadians(interp(mc.thePlayer.prevRotationYaw, mc.thePlayer.rotationYaw, partial));
        double pitch = Math.toRadians(interp(mc.thePlayer.prevRotationPitch, mc.thePlayer.rotationPitch, partial));

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

        double eyeX = interp(mc.thePlayer.prevPosX, mc.thePlayer.posX, partial);
        double eyeY = interp(mc.thePlayer.prevPosY, mc.thePlayer.posY, partial) + mc.thePlayer.getEyeHeight();
        double eyeZ = interp(mc.thePlayer.prevPosZ, mc.thePlayer.posZ, partial);

        double vfov = Math.toRadians(mc.gameSettings.fovSetting);
        double tanV = Math.tan(vfov / 2.0);
        double aspect = mc.displayHeight == 0 ? 1.0 : (double) mc.displayWidth / mc.displayHeight;
        double tanH = tanV * aspect;

        ScaledResolution sr = event.getSr();
        float cx = sr.getScaledWidth() / 2f;
        float cy = sr.getScaledHeight() / 2f;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        // the HUD ortho flips Y, making our triangles clockwise in window space;
        // with the GUI's GL_CULL_FACE enabled they'd be back-face culled away
        GlStateManager.disableCull();

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (player.isDead || player.isInvisible()) continue;
            if (AntiBot.isBot(player)) continue;
            float dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist > maxDistance.getValue().getInput()) continue;

            double tx = interp(player.prevPosX, player.posX, partial) - eyeX;
            double ty = interp(player.prevPosY, player.posY, partial) + player.height * 0.5 - eyeY;
            double tz = interp(player.prevPosZ, player.posZ, partial) - eyeZ;
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
            float t = Math.min(1f, dist / (float) maxDistance.getValue().getInput());
            int color = lerpColor(nearColor.getValue(), farColor.getValue(), t);

            drawArrow(cx, cy, (float) radius.getValue().getInput(), (float) length.getValue().getInput(), angle, color);
        }

        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GL11.glColor4f(1, 1, 1, 1);
        GlStateManager.popMatrix();
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
        GL11.glColor4f(r, g, b, a);

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
