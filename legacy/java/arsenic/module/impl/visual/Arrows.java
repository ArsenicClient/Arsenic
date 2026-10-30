package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.design.M3;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

@ModuleInfo(name = "Pointers", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL)
public class Arrows extends Module {

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        float partialTicks = event.getPartialTicks();
        ScaledResolution sr = event.getSr();
        float radius = 38.0f;
        float centerX = sr.getScaledWidth() / 2.0f;
        float centerY = sr.getScaledHeight() / 2.0f;

        double playerX = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partialTicks;
        double playerZ = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partialTicks;
        float playerYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partialTicks;

        for (EntityPlayer target : mc.theWorld.playerEntities) {
            if (!isValidTarget(target)) {
                continue;
            }

            double targetX = target.prevPosX + (target.posX - target.prevPosX) * partialTicks;
            double targetZ = target.prevPosZ + (target.posZ - target.prevPosZ) * partialTicks;

            float angle = (float) Math.toDegrees(Math.atan2(targetZ - playerZ, targetX - playerX)) - playerYaw - 90.0f;
            double distance = Math.hypot(targetX - playerX, targetZ - playerZ);

            float t = MathUtils.clamp01(1.0f - (float) (distance / 48.0));
            int alpha = Math.max(55, (int) (240 * t));

            boolean isFriend = Arsenic.getArsenic().getFriendManager().isFriend(target);
            float hpPct = MathUtils.clamp01(target.getHealth() / Math.max(1f, target.getMaxHealth()));
            int baseCol = isFriend
                    ? M3.tertiary()
                    : (hpPct < 0.3f ? M3.error() : M3.primary());
            int wingCol = isFriend
                    ? M3.tertiaryContainer()
                    : (hpPct < 0.3f ? M3.errorContainer() : M3.primaryContainer());

            drawM3Pointer(centerX, centerY, angle, radius, baseCol, wingCol, alpha);
        }
    };

    private boolean isValidTarget(EntityPlayer target) {
        if (target == mc.thePlayer || target.isInvisible())
            return false;
        return !AntiBot.isBot(target);
    }

    private void drawM3Pointer(float centerX, float centerY, float angle, float radius, int tipRgb, int wingRgb, int alpha) {
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0.0f);
        GL11.glRotatef(angle, 0.0f, 0.0f, 1.0f);
        GL11.glTranslatef(0.0f, -radius, 0.0f);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);

        // Subtle M3 dark container backing so the pointer chevron pops cleanly on bright skies
        int backCol = M3.alpha(M3.surfaceContainerLowest(), (int) (alpha * 0.78f));
        RenderUtils.setColor(backCol);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(0.0f, -6.2f);
        GL11.glVertex2f(-4.8f, 4.6f);
        GL11.glVertex2f(0.0f, 1.8f);

        GL11.glVertex2f(0.0f, -6.2f);
        GL11.glVertex2f(0.0f, 1.8f);
        GL11.glVertex2f(4.8f, 4.6f);
        GL11.glEnd();

        // Left & right chevron wings with M3 primary -> container tonal shading
        int tipCol = M3.alpha(tipRgb, alpha);
        int sideCol = M3.alpha(M3.mix(wingRgb, tipRgb, 0.65f), alpha);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_TRIANGLES);
        RenderUtils.setColor(tipCol);
        GL11.glVertex2f(0.0f, -5.0f);
        RenderUtils.setColor(sideCol);
        GL11.glVertex2f(-3.8f, 3.6f);
        RenderUtils.setColor(tipCol);
        GL11.glVertex2f(0.0f, 1.0f);

        RenderUtils.setColor(tipCol);
        GL11.glVertex2f(0.0f, -5.0f);
        RenderUtils.setColor(tipCol);
        GL11.glVertex2f(0.0f, 1.0f);
        RenderUtils.setColor(sideCol);
        GL11.glVertex2f(3.8f, 3.6f);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);

        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }
}
