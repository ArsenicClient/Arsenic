package arsenic.utils.font;

import net.minecraft.network.chat.FontDescription;

/**
 * The client's fonts. The TTF files and their definitions live in {@code assets/arsenic/font/},
 * where Minecraft's resource loader picks them up, so there is nothing to generate at startup.
 */
public class Fonts {
    public final TTFontRenderer Comfortaa = new TTFontRenderer("comfortaa");
    public final TTFontRenderer Icon = new TTFontRenderer("icon");
    /** Minecraft's own font, for the places that deliberately match vanilla text. */
    public final TTFontRenderer Minecraft = new TTFontRenderer(FontDescription.DEFAULT);
}
