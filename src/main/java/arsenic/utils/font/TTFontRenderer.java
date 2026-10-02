package arsenic.utils.font;

import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

/**
 * A TrueType font drawn through Minecraft's own text renderer.
 * <p>
 * The 1.8 version rasterised glyphs with AWT and uploaded its own textures. Modern Minecraft loads
 * TTF fonts natively from {@code assets/<ns>/font/<name>.json}, so this just selects that font via
 * a {@link Style} and draws with float positions by translating the GUI pose. Legacy
 * {@code §} formatting codes still work because plain strings go through the vanilla decomposer.
 */
public class TTFontRenderer implements IFontRenderer {

    private final FontDescription font;
    private final Style style;
    private final FontRendererExtension<TTFontRenderer> extension = new FontRendererExtension<>(this);

    public TTFontRenderer(String fontName) {
        this(new FontDescription.Resource(Identifier.fromNamespaceAndPath("arsenic", fontName)));
    }

    public TTFontRenderer(FontDescription font) {
        this.font = font;
        this.style = Style.EMPTY.withFont(font);
    }

    @Override
    public FontRendererExtension<?> getFontRendererExtension() {
        return extension;
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        draw(text, x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        draw(text, x, y, color, true);
    }

    private void draw(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty())
            return;
        GuiGraphicsExtractor graphics = RenderContext.graphics();
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        graphics.text(vanilla(), sequence(text), 0, 0, RenderContext.applyAlpha(fixColor(color)), shadow);
        pose.popMatrix();
    }

    @Override
    public float getWidth(String text) {
        if (text == null || text.isEmpty())
            return 0;
        return vanilla().width(FormattedText.of(text, style));
    }

    @Override
    public float getHeight(String text) {
        return vanilla().lineHeight;
    }

    private FormattedCharSequence sequence(String text) {
        return Language.getInstance().getVisualOrder(FormattedText.of(text, style));
    }

    private static Font vanilla() {
        return Minecraft.getInstance().font;
    }

    /** 1.8's font renderer treated a zero alpha byte as opaque; modern skips the text instead. */
    static int fixColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
