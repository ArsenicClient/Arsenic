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
 * Text drawn through Minecraft's own text renderer, for the places that deliberately match vanilla
 * (the "Minecraft" font option). Float positions come from translating the GUI pose, and legacy
 * {@code §} formatting codes work because plain strings go through the vanilla decomposer.
 */
public class VanillaFontRenderer implements IFontRenderer {

    private final FontDescription font;
    private final Style style;
    private final FontRendererExtension<VanillaFontRenderer> extension = new FontRendererExtension<>(this);

    public VanillaFontRenderer(FontDescription font) {
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
