package arsenic.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import arsenic.utils.java.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public class RenderUtils extends UtilityClass {

    private static Minecraft mc = Minecraft.getMinecraft();

    public static void glScissor(int x, int y, int width, int height, int scale) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, (mc.displayHeight - ((((y / height) + height)) * scale)), width * scale,
                (height + y) * scale);
    }

    public static void glScissor(int x, int y, int width, int height) {
        glScissor(x, y, width, height, new ScaledResolution(mc).getScaleFactor());
    }

    public static void setColor(final int color) {
        final float a = ((color >> 24) & 0xFF) / 255.0f;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        GL11.glColor4f(r, g, b, a);
    }

    public static void resetColor() {
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static ResourceLocation getResourcePath(String s) {
        InputStream inputStream = RenderUtils.class.getResourceAsStream(s);
        BufferedImage bf;
        try {
            assert inputStream != null;
            bf = ImageIO.read(inputStream);
            return Minecraft.getMinecraft().renderEngine.getDynamicTextureLocation("arsenic", new DynamicTexture(bf));
        } catch (IOException | IllegalArgumentException | NullPointerException noway) {
            return new ResourceLocation("null");
        }
    }

    public static Color interpolateColoursColor(Color a, Color b, float f) {
        float rf = 1 - f;
        int red = (int) (a.getRed() * rf + b.getRed() * f);
        int green = (int) (a.getGreen() * rf + b.getGreen() * f);
        int blue = (int) (a.getBlue() * rf + b.getBlue() * f);
        int alpha = (int) (a.getAlpha() * rf + b.getAlpha() * f);
        return new Color(red, green, blue, alpha);
    }

    public static int interpolateColours(Color a, Color b, float f) {
        return interpolateColoursColor(a,b,f).getRGB();
    }

}
