package arsenic.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import arsenic.main.Arsenic;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import arsenic.utils.java.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

public class RenderUtils extends UtilityClass {

    public static void setColor(final int color) {
        final float a = ((color >> 24) & 0xFF) / 255.0f;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        GL11.glColor4f(r, g, b, a);
    }

    public static void resetColorText() {
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    public static void resetColor() {
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    public static ResourceLocation getResourcePath(String s) {
        InputStream inputStream = Arsenic.class.getResourceAsStream(s);
        BufferedImage bf;
        try {
            assert inputStream != null;
            bf = ImageIO.read(inputStream);
            return Minecraft.getMinecraft().renderEngine.getDynamicTextureLocation("Arsenic", new DynamicTexture(bf));
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            e.printStackTrace();
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

    public static int interpolateColoursInt(int a, int b, float f) {
        return interpolateColoursColor(new Color(a), new Color(b),f).getRGB();
    }

}
