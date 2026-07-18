package arsenic.utils.render.shader;

import arsenic.utils.java.FileUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

public class ShaderUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final int programID;

    // ----- fullscreen shader registry (uses this same class, no duplication) -----

    /** Blend factor constants (some LWJGL builds don't expose these in GL14). */
    private static final int GL_CONSTANT_ALPHA_ = 0x8003;
    private static final int GL_ONE_MINUS_CONSTANT_ALPHA_ = 0x8004;

    /** Selectable background shaders (see {@code ClickGui.BgShader}). */
    public static final String[] BACKGROUNDS = {
            "aurora", "starfield", "synthwave", "liquidChrome",
            "fireStorm", "oceanCaustics", "nebula", "zippyZaps"
    };

    public enum BlendMode {
        NORMAL, ADDITIVE, SCREEN
    }

    private static final Map<String, ShaderUtil> FULLSCREEN_CACHE = new LinkedHashMap<>();

    /** Lazily compiles and caches a fullscreen shader by name. */
    public static ShaderUtil getCached(String name) {
        ShaderUtil s = FULLSCREEN_CACHE.get(name);
        if (s == null) {
            s = new ShaderUtil(name);
            FULLSCREEN_CACHE.put(name, s);
        }
        return s;
    }

    public static void renderFullscreen(String name, float alpha, float speed) {
        renderFullscreen(name, alpha, speed, BlendMode.NORMAL);
    }

    public static void renderFullscreen(String name, float alpha, float speed, BlendMode blend) {
        renderFullscreen(name, alpha, speed, blend, 0, 0f);
    }

    /**
     * Draws the named shader across the whole framebuffer, independent of GUI scale,
     * restoring GL state through {@link GlStateManager} so GUI colours are untouched.
     * <p>
     * When {@code tintStrength > 0} the result is pulled toward {@code tintColor}
     * (an ARGB int, alpha ignored) so the background can be made to match the GUI's
     * theme colour. The same colour is also handed to the shader as a
     * {@code themeColor} uniform (harmlessly ignored by shaders that don't use it).
     *
     * @param tintColor    ARGB colour to tint toward - pass {@code ThemeManager.getMainColor()}
     * @param tintStrength 0 = untouched shader, 1 = fully washed to the theme colour
     */
    public static void renderFullscreen(String name, float alpha, float speed, BlendMode blend, int tintColor, float tintStrength) {
        if (alpha <= 0.001f)
            return;

        float tr = ((tintColor >> 16) & 0xFF) / 255f;
        float tg = ((tintColor >> 8) & 0xFF) / 255f;
        float tb = (tintColor & 0xFF) / 255f;

        ShaderUtil shader = getCached(name);
        int w = mc.displayWidth;
        int h = mc.displayHeight;

        // scale-independent fullscreen projection (actual pixels)
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, w, h, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        GlStateManager.disableAlpha();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL14.glBlendColor(1f, 1f, 1f, alpha);
        switch (blend) {
            case ADDITIVE:
                GlStateManager.blendFunc(GL_CONSTANT_ALPHA_, GL_ONE);
                break;
            case SCREEN:
                GlStateManager.blendFunc(GL_ONE_MINUS_DST_COLOR, GL_CONSTANT_ALPHA_);
                break;
            default:
                GlStateManager.blendFunc(GL_CONSTANT_ALPHA_, GL_ONE_MINUS_CONSTANT_ALPHA_);
                break;
        }

        shader.init();
        shader.setUniformf("time", ((System.currentTimeMillis() % 1000000L) / 5000f) * speed);
        shader.setUniformf("resolution", w, h);
        shader.setUniformf("themeColor", tr, tg, tb);
        drawQuads(0, 0, w, h);
        shader.unload();

        // Wash the shader toward the theme colour so the background matches the GUI.
        if (tintStrength > 0f) {
            org.lwjgl.opengl.GL14.glBlendColor(0f, 0f, 0f, 0f);
            GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(tr, tg, tb, Math.min(1f, tintStrength) * alpha);
            drawQuads(0, 0, w, h);
        }

        // restore
        org.lwjgl.opengl.GL14.glBlendColor(0f, 0f, 0f, 0f);
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    /**
     * Draws the "paperBurn" dissolve across the whole screen on top of the GUI.
     * The shader discards burnt-through fragments (revealing the GUI beneath) and
     * paints an ember edge over the remaining sheet.
     *
     * @param progress  0 = sheet fully covers the GUI, 1 = fully burnt away
     * @param tintColor ARGB theme colour the paper is faintly tinted with
     */
    public static void renderBurn(float progress, int tintColor) {
        ShaderUtil shader = getCached("paperBurn");
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        float tr = ((tintColor >> 16) & 0xFF) / 255f;
        float tg = ((tintColor >> 8) & 0xFF) / 255f;
        float tb = (tintColor & 0xFF) / 255f;

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, w, h, 0.0, -1.0, 1.0);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        GlStateManager.disableAlpha();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1f, 1f, 1f, 1f);

        shader.init();
        shader.setUniformf("time", (System.currentTimeMillis() % 1000000L) / 1000f);
        shader.setUniformf("resolution", w, h);
        shader.setUniformf("progress", progress);
        shader.setUniformf("themeColor", tr, tg, tb);
        drawQuads(0, 0, w, h);
        shader.unload();

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    public ShaderUtil(String fragmentShaderLoc, String vertexShaderLoc) {
        int program = glCreateProgram();
        try {
            int fragmentShaderID = createShader(mc.getResourceManager().getResource(new ResourceLocation("shaders/" + fragmentShaderLoc + ".fsh")).getInputStream(), GL_FRAGMENT_SHADER);
            glAttachShader(program, fragmentShaderID);

            int vertexShaderID = createShader(mc.getResourceManager().getResource(new ResourceLocation(vertexShaderLoc)).getInputStream(), GL_VERTEX_SHADER);
            glAttachShader(program, vertexShaderID);


        } catch (IOException e) {
            e.printStackTrace();
        }

        glLinkProgram(program);
        int status = glGetProgrami(program, GL_LINK_STATUS);

        if (status == 0) {
            throw new IllegalStateException("Shader failed to link!");
        }
        this.programID = program;
    }

    public ShaderUtil(String fragmentShaderLoc) {
        this(fragmentShaderLoc, "shaders/vertex.vsh");
    }


    public void init() {
        glUseProgram(programID);
    }

    public void unload() {
        glUseProgram(0);
    }

    public int getUniform(String name) {
        return glGetUniformLocation(programID, name);
    }


    public void setUniformf(String name, float... args) {
        int loc = glGetUniformLocation(programID, name);
        switch (args.length) {
            case 1:
                glUniform1f(loc, args[0]);
                break;
            case 2:
                glUniform2f(loc, args[0], args[1]);
                break;
            case 3:
                glUniform3f(loc, args[0], args[1], args[2]);
                break;
            case 4:
                glUniform4f(loc, args[0], args[1], args[2], args[3]);
                break;
        }
    }

    public void setUniformi(String name, int... args) {
        int loc = glGetUniformLocation(programID, name);
        if (args.length > 1) glUniform2i(loc, args[0], args[1]);
        else glUniform1i(loc, args[0]);
    }

    public static void drawQuads(float x, float y, float width, float height) {
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex2f(x, y);
        glTexCoord2f(0, 1);
        glVertex2f(x, y + height);
        glTexCoord2f(1, 1);
        glVertex2f(x + width, y + height);
        glTexCoord2f(1, 0);
        glVertex2f(x + width, y);
        glEnd();
    }

    public static void drawQuads() {
        ScaledResolution sr = new ScaledResolution(mc);
        float width = (float) sr.getScaledWidth_double();
        float height = (float) sr.getScaledHeight_double();
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1);
        glVertex2f(0, 0);
        glTexCoord2f(0, 0);
        glVertex2f(0, height);
        glTexCoord2f(1, 0);
        glVertex2f(width, height);
        glTexCoord2f(1, 1);
        glVertex2f(width, 0);
        glEnd();
    }

    public static void drawQuads(float width, float height) {
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1);
        glVertex2f(0, 0);
        glTexCoord2f(0, 0);
        glVertex2f(0, height);
        glTexCoord2f(1, 0);
        glVertex2f(width, height);
        glTexCoord2f(1, 1);
        glVertex2f(width, 0);
        glEnd();
    }

    private int createShader(InputStream inputStream, int shaderType) {
        int shader = glCreateShader(shaderType);
        glShaderSource(shader, FileUtils.readInputStream(inputStream));
        glCompileShader(shader);


        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            System.out.println(glGetShaderInfoLog(shader, 4096));
            throw new IllegalStateException(String.format("Shader (%s) failed to compile!", shaderType));
        }

        return shader;
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
        return createFrameBuffer(framebuffer, false);
    }
    public static boolean needsNewFramebuffer(Framebuffer framebuffer) {
        return framebuffer == null || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight;
    }
    public static Framebuffer createFrameBuffer(Framebuffer framebuffer, boolean depth) {
        if (needsNewFramebuffer(framebuffer)) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            return new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
        }
        return framebuffer;
    }
}