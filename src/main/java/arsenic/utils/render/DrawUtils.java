package arsenic.utils.render;

import arsenic.utils.java.UtilityClass;
import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public class DrawUtils extends UtilityClass {

    public static ShaderUtil roundedShader = new ShaderUtil("roundedRect");
    private static final ShaderUtil roundedGradientShader = new ShaderUtil("roundedRectGradient");

    /**
     * When > 0, rounded-rect corner masking uses this fixed scale factor instead
     * of the live GUI scale. The ClickGUI renders at a constant scale (Normal),
     * so it sets this while drawing to keep corner rounding identical on every
     * GUI-scale setting. HUD elements leave it at -1 to use the real scale.
     */
    public static float overrideScaleFactor = -1f;

    /**
     * Unit direction the drop shadows are cast in (screen space, +y = down).
     * Driven by the ClickGUI "Sun Angle" setting so the light source can be moved.
     */
    public static float shadowDirX = 0f;
    public static float shadowDirY = 1f;

    public static void drawRect(float x, float y, float x1, float y1, int color) {
        float finalX = x * 2f;
        float finalY = y * 2f;
        float finalX1 = x1 * 2f;
        float finalY1 = y1 * 2f;

        drawCustom(color, () -> {
            glVertex2d(finalX, finalY1);
            glVertex2d(finalX1, finalY1);
            glVertex2d(finalX1, finalY);
            glVertex2d(finalX, finalY);
        });
    }

    public static void drawCustom(int color, Runnable v) {
        setup(color);
        glBegin(9);
        v.run();
        finish();
    }


    //no worke :(
    public static void drawCustomOutline(int color, float borderWidth, Runnable v) {
        setupOutline(color, borderWidth);
        v.run();
        finishOutline();
    }
    public static void drawCustomWithOutline(int fillColour, int borderColour, float borderWidth, Runnable v) {
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
            glVertex2d(x + (radius * -pn) + (Math.sin((i * Math.PI) / 180.0) * radius * pn),
                    y + (radius * pn2) + (Math.cos((i * Math.PI) / 180.0) * radius * pn));
    }

    private static void round(float x, float y, float x1, float y1, float radius, final boolean[] round) {
        if (round[0])
            roundHelper(x, y, radius, -1, 1, 0, 90);
        else
            glVertex2d(x, y);

        if (round[1])
            roundHelper(x, y1, radius, -1, -1, 90, 180);
        else
            glVertex2d(x, y1);

        if (round[2])
            roundHelper(x1, y1, radius, 1, -1, 0, 90);
        else
            glVertex2d(x1, y1);

        if (round[3])
            roundHelper(x1, y, radius, 1, 1, 90, 180);
        else
            glVertex2d(x1, y);
    }

    public static void drawRoundedOutline(float x, float y, float x1, float y1, final float radius, final float borderSize, final int color) {
        drawRoundedOutline(x, y, x1, y1, radius, borderSize, color, new boolean[]{true, true, true, true});
    }

    public static void drawBorderedRoundedRect(float x, float y, float d, float y1, float radius, float borderSize, int borderC, int insideC, boolean[] round) {
        drawRoundedRect(x, y, d, y1, radius, insideC, round);
        drawRoundedOutline(x, y, d, y1, radius, borderSize, borderC, round);
    }

    public static void drawRoundedRect(float x, float y, float x1, float y1, final float radius, final int color) {
        drawShaderRect(x,y,x1-x,y1-y,radius,color);
    }
    public static void drawGradientRoundedRect(float x, float y, float x1, float y1, final float radius, final int bottomLeft, int topLeft, int bottomRight, int topRight) {
        Color bl = new Color((bottomLeft >> 16) & 0xFF, (bottomLeft >> 8) & 0xFF, bottomLeft & 0xFF, (bottomLeft >> 24) & 0xFF);
        Color br = new Color((bottomRight >> 16) & 0xFF, (bottomRight >> 8) & 0xFF, bottomRight & 0xFF, (bottomRight >> 24) & 0xFF);
        Color tl = new Color((topLeft >> 16) & 0xFF, (topLeft >> 8) & 0xFF, topLeft & 0xFF, (topLeft >> 24) & 0xFF);
        Color tr = new Color((topRight >> 16) & 0xFF, (topRight >> 8) & 0xFF, topRight & 0xFF, (topRight >> 24) & 0xFF);
        drawGradientRound(x,y,x1-x,y1-y,radius,bl,tl,br,tr);
    }
    /**
     * Soft directional drop shadow. Layers grow outward with a quadratic alpha
     * falloff (dark and tight against the element, fading softly outward) and
     * are cast downward so the element reads as physically raised toward the
     * viewer. Larger {@code spread}/{@code alpha} = the element floats higher,
     * i.e. appears closer. Draw this BEFORE the element's own fill.
     *
     * @param radius corner radius of the element being shadowed
     * @param spread elevation - how far (px) the shadow reaches / how high it floats
     * @param alpha  darkness of the shadow's core (0-255)
     */
    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha) {
        drawShadow(x1, y1, x2, y2, radius, spread, alpha, 6);
    }

    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, int layers) {
        if (alpha <= 0 || layers <= 0)
            return;
        // cast the shadow away from the light source ("sun"); distance scales with elevation
        float offX = shadowDirX * spread * 0.6f;
        float offY = shadowDirY * spread * 0.6f;
        // largest, faintest layer first; darkest tightest layer last (on top)
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;            // 1 = outer edge, ~0 = tight to element
            float grow = spread * t;
            float fade = (1f - t) * (1f - t);        // quadratic: strong near element
            int a = (int) (alpha * fade);
            if (a <= 0)
                continue;
            int col = new Color(0, 0, 0, Math.min(255, a)).getRGB();
            drawRoundedRect(x1 - grow + offX, y1 - grow + offY, x2 + grow + offX, y2 + grow + offY, radius + grow, col);
        }
    }

    /**
     * Subtle light rim around a raised element - a faint "glass edge" that,
     * together with the drop shadow beneath, sells the sense that the element
     * sits above the layer behind it. Draw this AFTER the element's fill.
     *
     * @param color base RGB of the rim (alpha byte ignored)
     * @param alpha rim opacity (0-255)
     */
    public static void drawEdgeHighlight(float x1, float y1, float x2, float y2, float radius, int color, int alpha) {
        if (alpha <= 0)
            return;
        // thicker, softer rim: a brighter inner line plus a fainter wider glow
        int inner = (Math.min(255, alpha) << 24) | (color & 0x00FFFFFF);
        int outer = (Math.min(255, alpha / 2) << 24) | (color & 0x00FFFFFF);
        drawRoundedOutline(x1, y1, x2, y2, radius, 3.0f, outer);
        drawRoundedOutline(x1, y1, x2, y2, radius, 1.5f, inner);
    }

    public static void drawShaderRect(float x, float y, float width, float height, float radius, int c) {
        //this is done to fix alpha issues with the rect. Don't chage it - cosmic
        Color color = new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >> 24) & 0xFF);
        RenderUtils.resetColor();
        RenderUtils.startBlend();
        RenderUtils.applyGuiBlend();
        RenderUtils.setAlphaLimit(0);
        roundedShader.init();

        setupRoundedRectUniforms(x, y, width, height, radius - 1f, roundedShader);
        roundedShader.setUniformi("blur",1);
        roundedShader.setUniformf("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

        ShaderUtil.drawQuads(x, y, width+0.6f, height+0.6f);
        roundedShader.unload();
        RenderUtils.endBlend();
    }

    public static void drawGradientRound(float x, float y, float width, float height, float radius, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight) {
        RenderUtils.setAlphaLimit(0);
        RenderUtils.resetColor();
        RenderUtils.startBlend();
        roundedGradientShader.init();
        setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader);
        //Top left
        roundedGradientShader.setUniformf("color1", topLeft.getRed() / 255f, topLeft.getGreen() / 255f, topLeft.getBlue() / 255f, topLeft.getAlpha() / 255f);
        // Bottom Left
        roundedGradientShader.setUniformf("color2", bottomLeft.getRed() / 255f, bottomLeft.getGreen() / 255f, bottomLeft.getBlue() / 255f, bottomLeft.getAlpha() / 255f);
        //Top Right
        roundedGradientShader.setUniformf("color3", topRight.getRed() / 255f, topRight.getGreen() / 255f, topRight.getBlue() / 255f, topRight.getAlpha() / 255f);
        //Bottom Right
        roundedGradientShader.setUniformf("color4", bottomRight.getRed() / 255f, bottomRight.getGreen() / 255f, bottomRight.getBlue() / 255f, bottomRight.getAlpha() / 255f);
        ShaderUtil.drawQuads(x, y, width+0.6f, height+0.6f);
        roundedGradientShader.unload();
        RenderUtils.endBlend();
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
                glVertex2d(realX1, realY1);
                glVertex2d(realX1 + (realWidth/2f), realY1 + realHeight);
                glVertex2d(realX1 + realWidth, realY1);
            } else {
                glVertex2d(realX1 + (realWidth/2f), realY1 + realHeight);
                glVertex2d(realX1, realY1);
                glVertex2d(realX1 + realWidth, realY1);
            }
        });
    }

    private static void setup(int color) {
        glScaled(0.5, 0.5, 0.5);
        glPushAttrib(0);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glEnable(GL_LINE_SMOOTH);
        RenderUtils.setColor(color);
    }

    private static void finish() {
        glEnd();
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
        glDisable(GL_LINE_SMOOTH);
        glScalef(2f, 2f, 2f);
        glPopAttrib();
        RenderUtils.resetColor();
    }

    private static void setupOutline(int color, float borderSize) {
        setup(color);
        glLineWidth(borderSize);
        glBegin(2);
    }
    private static void finishOutline() {
        finish();
        glLineWidth(1.0f);
    }
    private static void setupRoundedRectUniforms(float x, float y, float width, float height, float radius, ShaderUtil roundedTexturedShader) {
        float sf = overrideScaleFactor > 0 ? overrideScaleFactor : new ScaledResolution(mc).getScaleFactor();
        roundedTexturedShader.setUniformf("location", x * sf,
                (mc.displayHeight - (height * sf)) - (y * sf));
        roundedTexturedShader.setUniformf("rectSize", width * sf, height * sf);
        roundedTexturedShader.setUniformf("radius", radius);
    }
}
