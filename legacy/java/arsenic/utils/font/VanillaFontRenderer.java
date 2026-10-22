package arsenic.utils.font;

import arsenic.utils.interfaces.IFontRenderer;
import net.minecraft.client.gui.FontRenderer;

import java.util.Map;
import java.util.WeakHashMap;

import static arsenic.module.impl.player.NameHider.format;

/** {@link IFontRenderer} view of a vanilla {@link FontRenderer}. */
public final class VanillaFontRenderer implements IFontRenderer {

    private static final Map<FontRenderer, VanillaFontRenderer> VIEWS = new WeakHashMap<>();

    private final FontRenderer font;
    private final FontRendererExtension<VanillaFontRenderer> extension = new FontRendererExtension<>(this);

    private VanillaFontRenderer(FontRenderer font) {
        this.font = font;
    }

    public static VanillaFontRenderer of(FontRenderer font) {
        return VIEWS.computeIfAbsent(font, VanillaFontRenderer::new);
    }

    @Override
    public FontRendererExtension<?> getFontRendererExtension() {
        return extension;
    }

    @Override
    public void drawString(String text, float x, float y, int color) {
        font.drawString(format(text), x, y, color, false);
    }

    @Override
    public void drawStringWithShadow(String text, float x, float y, int color) {
        font.drawString(format(text), x, y, color, true);
    }

    @Override
    public float getWidth(String text) {
        return font.getStringWidth(format(text));
    }

    @Override
    public float getHeight(String text) {
        return font.FONT_HEIGHT;
    }
}
