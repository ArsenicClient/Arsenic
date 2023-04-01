package arsenic.utils.render;

import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.java.UtilityClass;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class DrawUtils extends UtilityClass {

    // horrible shitcode need to fix

    public static void drawRect(float x, float y, float x1, float y1, int color) {
        float finalX = x * 2f;
        float finalY = y * 2f;
        float finalX1 = x1 * 2f;
        float finalY1 = y1 * 2f;

        drawCustom(color, () -> {
            GL11.glVertex2d(finalX, finalY1);
            GL11.glVertex2d(finalX1, finalY1);
            GL11.glVertex2d(finalX1, finalY);
            GL11.glVertex2d(finalX, finalY);
        });
    }

    public static void drawCustom(int color, IVoidFunction v) {
        setup(color);
        GL11.glBegin(9);
        v.voidFunction();
        finish();
    }


    //no worke :(
    public static void drawCustomOutline(int color, float borderWidth, IVoidFunction v) {
        setupOutline(color, borderWidth);
        v.voidFunction();
        finishOutline();
    }
    public static void drawCustomWithOutline(int fillColour, int borderColour, float borderWidth, IVoidFunction v) {
        drawCustom(fillColour, v);
        drawCustomOutline(borderColour, borderWidth, v);
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color, boolean[] round) {
        float finalX = x * 2f;
        float finalY = y * 2f;
        float finalX1 = x1 * 2f;
        float finalY1 = y1 * 2f;

        drawCustom(color, () -> round(finalX, finalY, finalX1, finalY1, radius, round));
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color, boolean[] round) {
        float finalX = x * 2f;
        float finalY = y * 2f;
        float finalX1 = x1 * 2f;
        float finalY1 = y1 * 2f;

        drawCustomOutline(color, borderSize, () -> round(finalX, finalY, finalX1, finalY1, radius, round));
    }

    private static void roundHelper(float x, float y, float radius, int pn, int pn2, int originalRotation,
                                    int finalRotation) {
        for (int i = originalRotation; i <= finalRotation; i += 1)
            GL11.glVertex2d(x + (radius * -pn) + (Math.sin((i * 3.141592653589793) / 180.0) * radius * pn),
                    y + (radius * pn2) + (Math.cos((i * 3.141592653589793) / 180.0) * radius * pn));
    }

    private static void round(float x, float y, float x1, float y1, float radius, final boolean[] round) {
        if (round[0])
            roundHelper(x, y, radius, -1, 1, 0, 90);
        else
            GL11.glVertex2d(x, y);

        if (round[1])
            roundHelper(x, y1, radius, -1, -1, 90, 180);
        else
            GL11.glVertex2d(x, y1);

        if (round[2])
            roundHelper(x1, y1, radius, 1, -1, 0, 90);
        else
            GL11.glVertex2d(x1, y1);

        if (round[3])
            roundHelper(x1, y, radius, 1, 1, 90, 180);
        else
            GL11.glVertex2d(x1, y);
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color) {
        drawRoundedOutline(x, y, x1, y1, radius, borderSize, color, new boolean[]{true, true, true, true});
    }

    public static void drawBorderedRoundedRect(float x, float y, float d, float y1, float radius, float borderSize, int borderC, int insideC, boolean[] round) {
        drawRoundedRect(x, y, d, y1, radius, insideC, round);
        drawRoundedOutline(x, y, d, y1, radius, borderSize, borderC, round);
    }

    //y = lower, y1 = upper
    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color) {
        drawRoundedRect(x, y, x1, y1, radius, color, new boolean[]{true, true, true, true});
    }

    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC) {
        drawRoundedRect(x, y, x1, y1, radius, insideC);
        drawRoundedOutline(x, y, x1, y1, radius, borderSize, borderC);
    }

    public static void drawBorderedCircle(float centrePointX, float centrePointY, float radius, float borderSize, int borderColour, int insideColour) {
        drawCircle(centrePointX, centrePointY, radius, insideColour);
        drawCircleOutline(centrePointX, centrePointY, radius, borderSize, borderColour);
    }

    public static void drawCircleOutline(float centrePointX, float centrePointY, float radius, float borderSize, int color) {
        float circleX1 = centrePointX - radius;
        float circleX2 = centrePointX + radius;
        float circleY1 = centrePointY - radius;
        float circleY2 = centrePointY + radius;
        drawRoundedOutline(circleX1, circleY1, circleX2, circleY2, radius * 2, borderSize, color);
    }

    public static void drawCircle(float centrePointX, float centrePointY, float radius, int color) {
        float circleX1 = centrePointX - radius;
        float circleX2 = centrePointX + radius;
        float circleY1 = centrePointY - radius;
        float circleY2 = centrePointY + radius;
        drawRoundedRect(circleX1, circleY1, circleX2, circleY2, radius * 2, color);
    }

    //draws a perfect triangle when height == width
    public static void drawTriangle(float x1, float y1, float width, float height, int colour) {
        final float realY1 = y1 * 2;
        final float realX1 = x1 * 2;
        final float realWidth = width * 2;
        final float realHeight = (float) ((height)*Math.sqrt(3));
        DrawUtils.drawCustom(colour, () -> {
            if(realHeight > 0) {
                GL11.glVertex2d(realX1, realY1);
                GL11.glVertex2d(realX1 + (realWidth/2f), realY1 + realHeight);
                GL11.glVertex2d(realX1 + realWidth, realY1);
            } else {
                GL11.glVertex2d(realX1 + (realWidth/2f), realY1 + realHeight);
                GL11.glVertex2d(realX1, realY1);
                GL11.glVertex2d(realX1 + realWidth, realY1);
            }
        });
    }

    private static void setup(int color) {
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glPushAttrib(0);
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glEnable(2848);
        RenderUtils.setColor(color);
    }

    private static void finish() {
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glScalef(2f, 2f, 2f);
        GL11.glPopAttrib();
        RenderUtils.resetColor();
    }

    private static void setupOutline(int color, float borderSize) {
        setup(color);
        GL11.glLineWidth(borderSize);
        GL11.glBegin(2);
    }
    private static void finishOutline() {
        finish();
        GL11.glLineWidth(1.0f);
    }

}
