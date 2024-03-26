package arsenic.utils.font;

import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;

public class Fonts {
    public final TTFontRenderer FR = new TTFontRenderer(getFontFromLocation("font.ttf", 21), true, true);
    public final TTFontRenderer MEDIUM_FR = new TTFontRenderer(getFontFromLocation("font.ttf", 20), true, true);
    public final TTFontRenderer BIG_FR = new TTFontRenderer(getFontFromLocation("font.ttf", 32), true, true);
    public final TTFontRenderer SMALL_FR = new TTFontRenderer(getFontFromLocation("font.ttf", 18), true, true);

    public void initTextures() {
        Field[] fields = Fonts.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.getType() == TTFontRenderer.class) {
                try {
                    field.setAccessible(true);
                    ((TTFontRenderer) field.get(this)).generateTextures();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private @Nullable Font getFontFromLocation(String fileName, int size) {
        try {
            return Font
                    .createFont(Font.TRUETYPE_FONT,
                            Objects.requireNonNull(Fonts.class.getResourceAsStream("/assets/arsenic/" + fileName)))
                    .deriveFont(Font.PLAIN, size);
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
