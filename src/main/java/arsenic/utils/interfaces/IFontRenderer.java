package arsenic.utils.interfaces;

import org.lwjgl.opengl.GL11;

public interface IFontRenderer {

    //should make this better at some point

    void drawString(String text, float x, float y, int color);
    void drawStringWithShadow(String text, float x, float y, int color);

    default void drawStringWithOutline(String text, float x, float y, int color) {
        this.drawString(text, x - 0.5f, y, 0xFF000000);
        this.drawString(text, x + 0.5f, y, 0xFF000000);
        this.drawString(text, x, y - 0.5f, 0xFF000000);
        this.drawString(text, x, y + 0.5f, 0xFF000000);
        this.drawString(text, x, y, color);
    }

    float getWidth(String text);

    default float getHeight(String text) {
        return 11.0F;
    }

    default void drawScaledString(String text, float x, float y, int color, float scale) {
        GL11.glScalef(scale, scale, scale);
        float scaleReciprocal = 1f/scale;
        drawString(text, x * scaleReciprocal, y * scaleReciprocal, color);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    //will make a better implementation of this later
    default void drawWrappingString(float x, float y, int color, String... texts) {
        for(String text : texts) {
            drawString(text, x, y, color);
            y+= getHeight(text);
        }
    }

    default void drawScaledWrappingString(float x, float y, int color, float scale, String... texts) {
        GL11.glScalef(scale, scale, scale);
        float scaleReciprocal = 1f/scale;
        drawWrappingString(x * scaleReciprocal, y * scaleReciprocal, color, texts);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }

    default void drawYCenteredString(String text, float x, float y, int color) {
        drawString(text, x, y - (getHeight(text)/2f), color);
    }


    default void drawXCenteredString(String text, float x, float y, int color) {
        drawString(text, x - (getWidth(text)/2f), y, color);
    }

    default void drawXCenteredWrappingString(float x, float y, int color, String... texts) {
        for(String text : texts) {
            drawXCenteredString(text, x, y, color);
            y+= getHeight(text);
        }
    }

    default void drawScaledXCenteredWrappingString(float x, float y, int color, float scale, String... texts) {
        GL11.glScalef(scale, scale, scale);
        float scaleReciprocal = 1f/scale;
        drawXCenteredWrappingString(x * scaleReciprocal, y * scaleReciprocal, color, texts);
        GL11.glScalef(scaleReciprocal, scaleReciprocal, scaleReciprocal);
    }
}
