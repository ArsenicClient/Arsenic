package dev.kino.utils.font;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class Fonts {

    public final TTFontRenderer FR =
            new TTFontRenderer(getFontFromLocation("font.ttf", 21), true, true);
    public final TTFontRenderer MEDIUM_FR =
            new TTFontRenderer(getFontFromLocation("font.ttf", 20), true, true);
    public final TTFontRenderer SMALL_FR =
            new TTFontRenderer(getFontFromLocation("font.ttf", 18), true, true);

    public void initTextures() {
        FR.generateTextures();
        MEDIUM_FR.generateTextures();
        SMALL_FR.generateTextures();
    }

    private Font getFontFromLocation(String fileName, int size) {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(Fonts.class.getResourceAsStream("assets/kino/font.ttf")))
                    .deriveFont(Font.PLAIN, size);
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
}
