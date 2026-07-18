package arsenic.utils.font;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glColor4d;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glScaled;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTranslated;
import static org.lwjgl.opengl.GL11.glVertex2d;
import static org.lwjgl.opengl.GL11.glVertex2f;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Random;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import arsenic.utils.interfaces.IFontRenderer;

public class TTFontRenderer implements IFontRenderer {

    private static final char COLOR_INVOKER = '\247';
    private static final Random RANDOM = new Random();
    private final Font font;
    public final CharacterData[] charData = new CharacterData[256];
    private final int[] colorCodes = new int[32];
    private final int margin;
    private final boolean antiAlias;
    private final boolean fracMetrics;

    private final FontRendererExtension<TTFontRenderer> fontRendererExtension = new FontRendererExtension<>(this);

    public TTFontRenderer(Font font, boolean antiAlias, boolean fracMetrics) {
        generateColors();
        this.font = font;
        this.margin = 6;
        this.antiAlias = antiAlias;
        this.fracMetrics = fracMetrics;
    }

    @Override
    public FontRendererExtension<?> getFontRendererExtension() {
        return fontRendererExtension;
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        renderString(text, x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        double s = 0.5;

        glTranslated(s, s, 0);
        renderString(text, x, y, color, true);
        glTranslated(-s, -s, 0);
        renderString(text, x, y, color, false);
    }

    @Override
    public float getWidth(String text) {
        if (text == null || text.length() == 0)
            return 0;
        float width = 0;
        CharacterData[] characterData = charData;
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char character = text.charAt(i);

            if (character == COLOR_INVOKER || (i > 0 ? text.charAt(i - 1) : '.') == COLOR_INVOKER
                    || !isValid(character))
                continue;

            CharacterData charData = characterData[character];

            width += (charData.width - (2 * margin)) / 2;
        }

        return width;
    }

    @Override
    public float getHeight(@NotNull String text) {
        float height = 0;

        CharacterData[] characterData = charData;

        int length = text.length();

        for (int i = 0; i < length; i++) {
            char character = text.charAt(i);
            if ((i > 0 ? text.charAt(i - 1) : '.') == COLOR_INVOKER || character == COLOR_INVOKER
                    || !isValid(character))
                continue;

            CharacterData charData = characterData[character];
            height = Math.max(height, charData.height);
        }

        return (height - margin) / 2;
    }

    public void generateTextures() {
        for (int i = 0; i < 256; i++) {
            char c = (char) i;
            if (isValid(c))
                setup(c);
        }
    }

    private void setup(char character) {
        // Width & Height must be >= 1
        BufferedImage utilityImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D utilityGraphics = (Graphics2D) utilityImage.getGraphics();
        utilityGraphics.setFont(font);
        FontMetrics fontMetrics = utilityGraphics.getFontMetrics();
        Rectangle2D characterBounds = fontMetrics.getStringBounds(String.valueOf(character), utilityGraphics);
        BufferedImage characterImage = new BufferedImage(
                (int) StrictMath.ceil(characterBounds.getWidth() + (2 * margin)),
                (int) StrictMath.ceil(characterBounds.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = (Graphics2D) characterImage.getGraphics();
        graphics.setFont(font);
        // Fill background with clear rect
        graphics.setColor(new Color(255, 255, 255, 0));
        graphics.fillRect(0, 0, characterImage.getWidth(), characterImage.getHeight());
        graphics.setColor(Color.WHITE);
        // Setup rendering hints - push AWT toward the highest-quality glyph raster
        if (antiAlias)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (fracMetrics)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        graphics.drawString(String.valueOf(character), margin, fontMetrics.getAscent());

        int textureId = glGenTextures();
        createTexture(textureId, characterImage);

        charData[character] = new CharacterData(characterImage.getWidth(), characterImage.getHeight(), textureId);
    }

    private void createTexture(int textureId, @NotNull BufferedImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];

        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

        ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = pixels[y * image.getWidth() + x];

                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));

                // Sharpen coverage instead of binarizing: anything at ~55%+ coverage
                // becomes fully opaque (solid interior, no background bleed-through),
                // anything under ~10% is dropped, and the narrow band between ramps
                // linearly. After the 0.5x downscale that leaves roughly a 1px smooth
                // edge - text reads as one solid color but stays crisp, not jagged.
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha <= 24)
                    alpha = 0;
                else if (alpha >= 140)
                    alpha = 255;
                else
                    alpha = (alpha - 24) * 255 / (140 - 24);
                buffer.put((byte) alpha);
            }
        }

        buffer.flip();

        glBindTexture(GL_TEXTURE_2D, textureId);
        // Clamp to edge so linear filtering never bleeds the opposite side of the glyph
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        // LINEAR filtering: the glyph is rasterized at 2x and drawn at 0.5x, so
        // linear minification is a clean supersample. NEAREST here skips every other
        // texel and shreds the edges. No mipmaps - level 1 would box-filter glyph
        // texels against transparent ones and wash the whole string out.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        // Upload texture
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.getWidth(), image.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE,
                buffer);
    }

    private void renderString(CharSequence text, float x, float y, int color, boolean shadow) {
        if (text == null || text.length() == 0)
            return;

        glPushMatrix();

        // Snapshot every piece of GL state we touch. Minecraft 1.8.9 GUIs leave
        // texture/alpha-test/blend/lighting state in unpredictable configurations
        // depending on what was drawn before us (which is why the font only looked
        // right on some backgrounds). Restoring via glPopAttrib also keeps the real
        // GL state in sync with GlStateManager's cache afterwards.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_LINE_BIT);

        glEnable(GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // Alpha test (commonly left on as GREATER 0.1 by vanilla) clips the
        // antialiased edge pixels and makes text look thin and jagged.
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Make sure the glyph texture is combined with glColor the way we expect,
        // even if something set a different texture env mode earlier.
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

        if ((color & 0xFC000000) == 0) { color |= 0xFF000000; }
        if (color == 0x20FFFFFF) { color = 0xFFAFAFAF; }

        glScaled(0.5, 0.5, 1);

        x -= margin / 2f;
        y -= 2;

        x *= 2;
        y *= 2;

        CharacterData[] characterData = charData;

        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        int length = text.length();

        float multiplier = (shadow ? 4 : 1);

        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        glColor4f(r / multiplier, g / multiplier, b / multiplier, a);

        for (int i = 0; i < length; i++) {
            char character = text.charAt(i);
            char previous = i > 0 ? text.charAt(i - 1) : '.';
            if (previous == COLOR_INVOKER)
                continue;
            if (character == COLOR_INVOKER) {
                if (i + 1 >= length)
                    break;
                int index = "0123456789ABCDEFKLMNOR".indexOf(text.charAt(i + 1));
                if (index < 16) {
                    obfuscated = false;
                    strikethrough = false;
                    underlined = false;
                    if (index < 0)
                        index = 15;
                    if (shadow)
                        index += 16;
                    int textColor = this.colorCodes[index];
                    glColor4f((textColor >> 16) / 255.0F, (textColor >> 8 & 255) / 255.0F, (textColor & 255) / 255.0F,
                            a);
                } else if (index == 16)
                    obfuscated = true;
                else if (index == 18)
                    strikethrough = true;
                else if (index == 19)
                    underlined = true;
                else {
                    obfuscated = false;
                    strikethrough = false;
                    underlined = false;

                    glColor4d(1 / multiplier, 1 / multiplier, 1 / multiplier, a);
                }
            } else {
                if (!isValid(character))
                    continue;

                if (obfuscated)
                    character += (int) (RANDOM.nextInt(Math.max(0, 256 - character)));

                final CharacterData charData = characterData[character];

                drawChar(charData, x, y);
                if (strikethrough)
                    drawLine(x, y + charData.height / 2f, x + charData.width, y + charData.height / 2f, 3);
                if (underlined)
                    drawLine(x, y + charData.height - 15, x + charData.width, y + charData.height - 15, 3);
                x += charData.width - (2 * margin);
            }
        }

        // Restores blend/alpha-test/texture/lighting/color exactly as we found them.
        GL11.glPopAttrib();
        glPopMatrix();
    }

    private boolean isValid(char c) {
        return c > 10 && c < 256 && c != 127;
    }

    public void drawChar(@NotNull CharacterData characterData, float x, float y) {
        characterData.bind();
        glBegin(GL_QUADS);
        {
            glTexCoord2f(0, 0);
            glVertex2d(x, y);
            glTexCoord2f(0, 1);
            glVertex2d(x, y + characterData.height);
            glTexCoord2f(1, 1);
            glVertex2d(x + characterData.width, y + characterData.height);
            glTexCoord2f(1, 0);
            glVertex2d(x + characterData.width, y);
        }
        glEnd();
    }

    private void drawLine(float x, float y, float x2, float y2, float width) {
        glDisable(GL_TEXTURE_2D);
        glLineWidth(width);
        glBegin(GL_LINES);
        {
            glVertex2f(x, y);
            glVertex2f(x2, y2);
        }
        glEnd();
        glEnable(GL_TEXTURE_2D);
    }

    private void generateColors() {
        for (int i = 0; i < 32; i++) {
            int thingy = (i >> 3 & 1) * 85;
            int red = (i >> 2 & 1) * 170 + thingy;
            int green = (i >> 1 & 1) * 170 + thingy;
            int blue = (i & 1) * 170 + thingy;
            if (i == 6)
                red += 85;
            if (i >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            this.colorCodes[i] = (red & 255) << 16 | (green & 255) << 8 | blue & 255;
        }
    }

    public static class CharacterData {
        private final int textureId;
        public float width;
        public float height;

        private CharacterData(float width, float height, int textureId) {
            this.width = width;
            this.height = height;
            this.textureId = textureId;
        }

        public void bind() {
            glBindTexture(GL_TEXTURE_2D, textureId);
        }
    }
}
