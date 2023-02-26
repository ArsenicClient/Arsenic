package arsenic.utils.render;

import java.awt.Color;

import arsenic.utils.java.UtilityClass;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

public class RenderUtils extends UtilityClass {

    public static void drawRect(double left, double top, double right, double bottom, int color) {
        double temp;

        if (left < right) {
            temp = left;
            left = right;
            right = temp;
        }

        if (top < bottom) {
            temp = top;
            top = bottom;
            bottom = temp;
        }

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, green, blue, alpha);

        renderer.begin(7, DefaultVertexFormats.POSITION);
        renderer.pos(left, bottom, 0.0D).endVertex();
        renderer.pos(right, bottom, 0.0D).endVertex();
        renderer.pos(right, top, 0.0D).endVertex();
        renderer.pos(left, top, 0.0D).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    public static void drawRect(double left, double top, double right, double bottom, Color color) {
        drawRect(left, top, right, bottom, color.getRGB());
    }

}
