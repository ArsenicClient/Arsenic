package arsenic.utils.render;

import arsenic.main.Arsenic;
import arsenic.utils.misc.BufferUtil;
import lombok.Getter;
import org.lwjgl.input.Keyboard;
import org.lwjgl.nanovg.*;
import org.lwjgl.opengl.Display;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BOTTOM;
import static org.lwjgl.nanovg.NanoVGGL2.nvgDelete;

public class NVGWrapper {
    private long cx;
    public long getCx() {
        return cx;
    }

    private static HashMap<Integer, Integer> glTextures = new HashMap<>();
    private static HashMap<String, ByteBuffer> fonts = new HashMap<>();
    private static HashMap<String, ByteBuffer> imageMem = new HashMap<>();
    private static HashMap<String, Integer> images = new HashMap<>();

    public void init() {
        cx = NanoVGGL2.nvgCreate(NanoVGGL2.NVG_ANTIALIAS);
    }

    public void initFont(String fontName, String ext) throws IOException {
        ByteBuffer buff = BufferUtil.getResourceBytes("fonts/"+fontName+"."+ext, 1024);
        if(buff == null) {
            Arsenic.getArsenic().getLogger().info("Error loading {} font", fontName);
            return;
        }
        nvgCreateFontMem(cx, fontName, buff, 0);
        fonts.put(fontName,buff);
    }
    public void initImage(String imageName, String ext) throws IOException {
        ByteBuffer buff = BufferUtil.getResourceBytes("images/"+imageName+"."+ext, 1024);
        fonts.put(imageName,buff);
        images.put(imageName, nvgCreateImageMem(cx, 0, buff));
    }

    public void terminate() {
        for (ByteBuffer f : fonts.values()) {
            MemoryUtil.memFree(f);
        }
        for (ByteBuffer f : imageMem.values()) {
            MemoryUtil.memFree(f);
        }

        nvgDelete(cx);
    }

    public void beginFrame() {
        nvgBeginFrame(cx, Display.getWidth(), Display.getHeight(), 1f);
    }

    public void endFrame() {
        nvgEndFrame(cx);
    }

    public void glTexture(float x, float y, float width, float height, float radius, int texture) {
        int reference = 0;

        if(glTextures.containsKey(texture))
            reference = glTextures.get(texture);
        else {
            reference = NanoVGGL2.nvglCreateImageFromHandle(cx, texture, (int) width, -(int) height, 0);
            if (reference == 0L) throw new RuntimeException("Unable to create texture!");

            glTextures.put(texture, reference);
        }
        nvgImageSize(cx,reference,new int[]{(int) width}, new int[]{-(int) height});
        NVGPaint p = NVGPaint.calloc();
        float sizeMultiplier = 8;
        nvgImagePattern(cx, x - width / 4 * sizeMultiplier / 2, y - height / 4 * sizeMultiplier / 2, width * sizeMultiplier, height * sizeMultiplier, 0, reference, 1f, p);
        nvgBeginPath(cx);
        nvgRoundedRect(cx, x, y, width, height, radius);
        nvgFillPaint(cx, p);
        nvgFill(cx);
        nvgClosePath(cx);
        p.free();
    }

    public void round(Number x, Number y, Number width, Number height, Number radius , Color c) {
        round(x,y,width,height,radius,radius,radius,radius,c);
    }
    public void stroke(Number x, Number y, Number width, Number height, Number radius ,Number strokeWidth, Color c) {
        stroke(x,y,width,height,radius,radius,radius,radius,strokeWidth,c);
    }
    public void round(Number x, Number y, Number width, Number height, Number r1, Number r2, Number r3, Number r4 , Color c) {
        NVGColor colorized = colorize(c);

        nvgBeginPath(cx);
        nvgRoundedRectVarying(cx, x.floatValue(), y.floatValue(), width.floatValue(), height.floatValue(), r1.floatValue(),r2.floatValue(),r3.floatValue(),r4.floatValue());
        nvgFillColor(cx, colorized);
        nvgFill(cx);
        nvgClosePath(cx);

        colorized.free();

        if(Keyboard.isKeyDown(Keyboard.KEY_HOME))
            stroke(x,y,width,height,0, .8f,new Color(136, 67, 255, 255));
    }
    public void stroke(Number x, Number y, Number width, Number height, Number r1, Number r2, Number r3, Number r4 ,Number strokeWidth , Color c) {
        NVGColor colorized = colorize(c);

        nvgBeginPath(cx);
        nvgStrokeWidth(cx,strokeWidth.floatValue());
        nvgRoundedRectVarying(cx, x.floatValue(), y.floatValue(), width.floatValue(), height.floatValue(), r1.floatValue(),r2.floatValue(),r3.floatValue(),r4.floatValue());
        nvgStrokeColor(cx, colorized);
        nvgStroke(cx);
        nvgClosePath(cx);

        colorized.free();
    }

    public void rect(float x, float y, float width, float height, Color color) {
        NVGColor colorized = colorize(color);

        nvgBeginPath(cx);
        nvgRect(cx, x, y, width, height);
        nvgFillColor(cx, colorized);
        nvgFill(cx);
        nvgClosePath(cx);

        colorized.free();

        if(Keyboard.isKeyDown(Keyboard.KEY_HOME))
            stroke(x,y,width,height,0, .8f,new Color(255, 255, 255, 255));
    }

    public void circle(float x, float y, float radius, Color color) {
        NVGColor colorized = colorize(color);

        nvgBeginPath(cx);
        nvgCircle(cx, x, y, radius);
        nvgFillColor(cx, colorized);
        nvgFill(cx);
        nvgClosePath(cx);

        colorized.free();
    }

    public void line(float x, float y, float targetX, float targetY, float thickness, Color color) {
        NVGColor colorized = colorize(color);

        nvgSave(cx);
        nvgBeginPath(cx);
        nvgMoveTo(cx, x, y);
        nvgLineTo(cx, targetX, targetY);
        nvgStrokeColor(cx, colorized);
        nvgStrokeWidth(cx, thickness);
        nvgStroke(cx);
        nvgClosePath(cx);
        nvgRestore(cx);

        colorized.free();
    }

    public void lineGradient(float x, float y, float targetX, float targetY, float thickness, Color a, Color b) {
        NVGColor colorizedA = colorize(a);
        NVGColor colorizedB = colorize(b);
        NVGPaint paint = NVGPaint.calloc();

        nvgLinearGradient(cx, x, y, targetX, targetY, colorizedA, colorizedB, paint);

        nvgSave(cx);
        nvgBeginPath(cx);
        nvgMoveTo(cx, x, y);
        nvgLineTo(cx, x, y);
        nvgStrokePaint(cx, paint);
        nvgStrokeWidth(cx, thickness);
        nvgStroke(cx);
        nvgClosePath(cx);
        nvgRestore(cx);

        colorizedA.free();
        colorizedB.free();
        paint.free();
    }

    public void roundedLinearGradient(float x, float y, float width, float height, float start_x, float start_y, float end_x, float end_y, float radius, Color start, Color end) {
        NVGColor startcolor = colorize(start);
        NVGColor endcolor = colorize(end);

        nvgBeginPath(cx);
        NVGPaint gradient = NVGPaint.calloc();
        nvgLinearGradient(cx, start_x,start_y,end_x,end_y, startcolor, endcolor, gradient);
        nvgRoundedRect(cx, x, y, width, height, radius);
        nvgFillPaint(cx, gradient);
        nvgFill(cx);
        nvgClosePath(cx);

        startcolor.free();
        endcolor.free();
    }

    public void text(String text, float x, float y, String font, float size, Color color) {
        text(text, x, y, font, size, color, Alignment.LEFT_TOP);
    }

    public void text(String text, float x, float y, String font, float size, Color color, Alignment alignment) {
        NVGColor colorized = colorize(color);

        nvgBeginPath(cx);

        nvgFontFace(cx, font);
        nvgFontSize(cx, size);
        nvgTextAlign(cx, alignment.getAlignment());
        nvgFillColor(cx, colorized);

        nvgText(cx, x, y, text);

        nvgClosePath(cx);

        colorized.free();
    }

    public void scissor(float x, float y, float width, float height, Runnable block) {
        nvgSave(cx);
        nvgIntersectScissor(cx, x, y, width, height);
        block.run();
        nvgRestore(cx);
    }

    public void line2colors(float x1, float y1, float x2, float y2, float thickness, Color c, Color c2) {
        NVGColor clr = colorize(c);
        NVGColor clr2 = colorize(c2);

        NVGPaint paint = NVGPaint.calloc();
        nvgLinearGradient(cx,x1,y2,x2,y2,clr,clr2,paint);

        nvgSave(cx);
        nvgBeginPath(cx);
        nvgMoveTo(cx,x1,y1);
        nvgLineTo(cx,x2,y2);
        nvgStrokePaint(cx,paint);
        nvgStrokeWidth(cx,thickness);
        nvgStroke(cx);
        nvgClosePath(cx);
        nvgRestore(cx);

        clr.free();
        clr2.free();
        paint.free();
    }

    public void image(String image, float x, float y, float width, float height, float radius, float alpha) {
        NVGPaint paint = NVGPaint.calloc();

        nvgImageSize(cx,images.get(image),new int[]{(int) width}, new int[]{-(int) height});
        nvgBeginPath(cx);
        nvgImagePattern(cx,x,y,width,height,0,images.get(image), alpha, paint);
        nvgRoundedRect(cx,x,y,width,height,radius);
        nvgFillPaint(cx,paint);
        nvgFill(cx);
        nvgClosePath(cx);

        paint.free();
    }

    public void dropShadow(float x, float y, float w, float h, float r, float spread, Color c, Color c2,boolean clipInside) {
        NVGPaint shadowPaint = NVGPaint.calloc();
        NVGColor colorA = colorize(c);
        NVGColor colorB = colorize(c2);

        nvgBoxGradient(cx, x - spread, y - spread, w + spread*2, h + spread*2, r + spread, spread*2, colorA, colorB, shadowPaint);
        nvgBeginPath(cx);
        nvgRoundedRect(cx, x - spread - spread*2*2, y - spread - spread*2*2, w + 2 * spread + 2 * spread*2*2, h + 2 * spread + 2 * spread*2*2, r + spread*3);
        if(clipInside) {
            nvgPathWinding(cx, NVG_HOLE);
            nvgRoundedRect(cx, x, y, w, h, r);
        }
        nvgFillPaint(cx, shadowPaint);
        nvgFill(cx);
        nvgClosePath(cx);
        shadowPaint.free();
        colorA.free();
        colorB.free();
    }


    public void shadow(float x, float y, float width, float height, float radius, float spread, Color color) {
        NVGColor shadowColor = colorize(color);
        NVGColor transparentColor = colorize(new Color(0,0,0,0));
        NVGPaint shadowPaint = NVGPaint.calloc();

        nvgBoxGradient(cx, x,y, width,height, radius, spread, shadowColor, transparentColor, shadowPaint);
        nvgBeginPath(cx);
        nvgRoundedRect(cx, x-spread/2f,y-spread/2f, width+spread,height+spread, radius+spread/2);
        nvgFillPaint(cx, shadowPaint);
        nvgFill(cx);
//        nvgPathWinding(cx, NVG_HOLE);
//        nvgPathWinding(cx, NVG_HOLE);

        shadowColor.free();
        transparentColor.free();
        shadowPaint.free();
    }

    public void translate(float x, float y) {
        nvgTranslate(cx, x, y);
    }

    public void rotate(float angle) {
        nvgRotate(cx, angle);
    }

    public float textWidth(String text, String face, float size) {
        float[] bounds = new float[4];

        float f = 0;

        nvgSave(cx);
        nvgFontFace(cx, face);
        nvgFontSize(cx, size);
        f  = nvgTextBounds(cx, 0, 0, text, bounds);
        nvgRestore(cx);

        return f;
    }

    public float textHeight(String face, float size) {
        float[] ascender = new float[1];
        float[] descender = new float[1];
        float[] lineh = new float[1];

        nvgFontFace(cx, face);
        nvgFontSize(cx, size);
        nvgTextMetrics(cx, ascender, descender, lineh);

        return lineh[0];
    }

    public void save() {
        nvgSave(cx);
    }

    public void restore() {
        nvgRestore(cx);
    }

    public enum Alignment {
        LEFT_TOP(NVG_ALIGN_LEFT | NVG_ALIGN_TOP),
        CENTER_TOP(NVG_ALIGN_CENTER | NVG_ALIGN_TOP),
        RIGHT_TOP(NVG_ALIGN_RIGHT | NVG_ALIGN_TOP),

        LEFT_MIDDLE(NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE),
        CENTER_MIDDLE(NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE),
        RIGHT_MIDDLE(NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE),

        LEFT_BOTTOM(NVG_ALIGN_LEFT | NVG_ALIGN_BOTTOM),
        CENTER_BOTTOM(NVG_ALIGN_CENTER | NVG_ALIGN_BOTTOM),
        RIGHT_BOTTOM(NVG_ALIGN_RIGHT | NVG_ALIGN_BOTTOM);

        @Getter
        private final int alignment;

        Alignment(int alignment) {
            this.alignment = alignment;
        }
    }

    public NVGColor colorize(Color in) {
        return NVGColor.calloc().r(in.getRed()/255f).g(in.getGreen()/255f).b(in.getBlue()/255f).a(in.getAlpha()/255f);
    }
}
