package arsenic.utils.font;

import net.minecraft.network.chat.FontDescription;

/**
 * The client's fonts. The TrueType files live in {@code assets/arsenic/font/} and are rasterised
 * by {@link TTFontRenderer} at whatever size they are shown; the sizes are the ones the 1.8 client
 * used, so layouts measure the same.
 */
public class Fonts {
    public final TTFontRenderer Comfortaa = new TTFontRenderer("comfortaa.ttf", 17);
    public final TTFontRenderer Icon = new TTFontRenderer("icon.ttf", 20);
    /** Minecraft's own font, for the places that deliberately match vanilla text. */
    public final VanillaFontRenderer Minecraft = new VanillaFontRenderer(FontDescription.DEFAULT);
}
