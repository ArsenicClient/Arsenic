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

    public static void drawRect(float left, float top, float right, float bottom, int color) {
        float temp;

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

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderUtils.setColor(color);

        renderer.begin(7, DefaultVertexFormats.POSITION);
        renderer.pos(left, bottom, 0.0f).endVertex();
        renderer.pos(right, bottom, 0.0f).endVertex();
        renderer.pos(right, top, 0.0f).endVertex();
        renderer.pos(left, top, 0.0f).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    public static void drawCustom(int color, IVoidFunction v) {
        setup(color);
        GL11.glBegin(9);
        v.voidFunction();
        finish();
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color, boolean[] round) {
        x *= 2.0;
        y *= 2.0;
        x1 *= 2.0;
        y1 *= 2.0;

        setup(color);
        GL11.glBegin(9);
        round(x, y, x1, y1, radius, round);
        finish();
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color, boolean[] drawCorner) {
        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);
        x *= 2.0;
        y *= 2.0;
        x1 *= 2.0;
        y1 *= 2.0;
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        RenderUtils.setColor(color);
        GL11.glEnable(2848);
        GL11.glLineWidth(borderSize);
        GL11.glBegin(2);
        round(x, y, x1, y1, radius, drawCorner);
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glDisable(3042);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glLineWidth(1.0f);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void roundHelper(float x, float y, float radius, int pn, int pn2, int originalRotation,
                                   int finalRotation) {
        for (int i = originalRotation; i <= finalRotation; i += 1)
            GL11.glVertex2d(x + (radius * -pn) + (Math.sin((i * 3.141592653589793) / 180.0) * radius * pn),
                    y + (radius * pn2) + (Math.cos((i * 3.141592653589793) / 180.0) * radius * pn));
    }

    public static void round(float x, float y, float x1, float y1, float radius, final boolean[] round) {
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

    public static void drawCircle(float centrePointX, float centrePointY, float radius, int color) {
        float circleX1 = centrePointX - radius;
        float circleX2 = centrePointX + radius;
        float circleY1 = centrePointY + radius;
        float circleY2 = centrePointY - radius;
        drawRect(circleX1, circleY1, circleX2, circleY2, color);
        //drawRoundedRect(circleX1, circleY1, circleX2, circleY2, radius, color);
        //drawRoundedRect does not work for some reason
    }

    private static void setup(int color) {
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glPushAttrib(0);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        RenderUtils.setColor(color);
    }

    private static void finish() {
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glEnable(3042);
        GL11.glScalef(2f, 2f, 2f);
        GL11.glPopAttrib();
        RenderUtils.resetColor();
    }

}
