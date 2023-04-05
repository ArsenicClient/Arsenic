package arsenic.utils.render;

import arsenic.utils.interfaces.IFontRenderer;
import org.lwjgl.opengl.GL11;

public class ScalableFontRenderer<T extends IFontRenderer> implements IFontRenderer {

    private IFontRenderer fr;
    private float scale, scaleReciprocal;

    public ScalableFontRenderer(T fr) {
        this.fr = fr;
    }

    public void setScale(float scale) {
        this.scale = scale;
        this.scaleReciprocal = scale/1;
    }

    public void resetScale() {
        setScale(1f);
    }


    @Override
    public void drawString(String text, float x, float y, int color) {
        GL11.glScalef(scale, scale, scale);
        fr.drawString(text, x * scaleReciprocal, y * scaleReciprocal, color);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        GL11.glScalef(scale, scale, scale);
        fr.drawStringWithShadow(text, x * scaleReciprocal, y * scaleReciprocal, color);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawStringWithOutline(String text, float x, float y, int color) {
        GL11.glScalef(scale, scale, scale);
        fr.drawStringWithOutline(text, x * scaleReciprocal, y * scaleReciprocal, color);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public float getWidth(String text) {
        return fr.getWidth(text) * scale;
    }

    @Override
    public float getHeight(String text) {
        return fr.getHeight(text) * scale;
    }

    @Override
    public void drawScaledString(String text, float x, float y, int color, float scale) {
        GL11.glScalef(scale, scale, scale);
        fr.drawScaledString(text, x * scaleReciprocal, y * scaleReciprocal, color, scale);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawWrappingString(float x, float y, int color, String... texts) {
        GL11.glScalef(scale, scale, scale);
        fr.drawWrappingString(x * scaleReciprocal, y * scaleReciprocal, color, texts);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawScaledWrappingString(float x, float y, int color, float scale, String... texts) {
        GL11.glScalef(scale, scale, scale);
        fr.drawScaledWrappingString(x * scaleReciprocal, y * scaleReciprocal, color, scale, texts);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawYCenteredString(String text, float x, float y, int color) {
        GL11.glScalef(scale, scale, scale);
        fr.drawYCenteredString(text, x * scaleReciprocal, y * scaleReciprocal, color);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawXCenteredString(String text, float x, float y, int color) {
        GL11.glScalef(scale, scale, scale);
        fr.drawXCenteredString(text, x * scaleReciprocal, y * scaleReciprocal, color);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawXCenteredWrappingString(float x, float y, int color, String... texts) {
        GL11.glScalef(scale, scale, scale);
        fr.drawXCenteredWrappingString( x * scaleReciprocal, y * scaleReciprocal, color, texts);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    @Override
    public void drawScaledXCenteredWrappingString(float x, float y, int color, float scale, String... texts) {
        GL11.glScalef(scale, scale, scale);
        fr.drawScaledXCenteredWrappingString( x * scaleReciprocal, y * scaleReciprocal, color, scale, texts);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }


}
