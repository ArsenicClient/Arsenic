package arsenic.utils.render;

import arsenic.main.Arsenic;
import arsenic.module.impl.visual.ESP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

public final class ChamsRenderer {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int POLYGON_OFFSET_FILL = 32823;

    private static final float OFFSET_UNITS = 1_100_000f;

    private static boolean offsetActive;
    private static boolean colourActive;

    private ChamsRenderer() {
    }


    public static void pre(EntityLivingBase entity) {
        if (!isTarget(entity))
            return;

        GL11.glEnable(POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1f, -OFFSET_UNITS);
        offsetActive = true;
    }

    public static void post(EntityLivingBase entity) {
        if (!offsetActive)
            return;
        offsetActive = false;

        GL11.glDisable(POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1f, OFFSET_UNITS);
    }


    public static void beginModel(EntityLivingBase entity) {
        ESP esp = settings();
        if (esp == null || !esp.isChamsFlat() || !isTarget(entity))
            return;

        int colour = esp.getChamsColour(entity);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderUtils.setAlphaLimit(0);
        GlStateManager.color(((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f,
                (colour & 0xFF) / 255f, esp.getChamsAlpha());
        colourActive = true;
    }

    public static void endModel() {
        if (!colourActive)
            return;
        colourActive = false;

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        RenderUtils.setAlphaLimit(0.1f);
    }


    private static boolean isTarget(EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || !(entity instanceof EntityPlayer))
            return false;
        ESP esp = settings();
        return esp != null && esp.isChamsEnabled();
    }

    private static ESP settings() {
        try {
            ESP esp = Arsenic.getArsenic().getModuleManager().getModuleByClass(ESP.class);
            return esp != null && esp.isEnabled() ? esp : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
