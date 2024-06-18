package arsenic.utils.font;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public class Fonts {

    public final TTFontRenderer FR = new TTFontRenderer(getFontFromLocation("font.ttf", 21), true, true);
    public final TTFontRenderer MEDIUM_FR = new TTFontRenderer(getFontFromLocation("font.ttf", 20), true, true);
    public final TTFontRenderer SMALL_FR = new TTFontRenderer(getFontFromLocation("font.ttf", 18), true, true);

    public void initTextures() {
        try {
            for (Field declaredField : getClass().getDeclaredFields())
                ((TTFontRenderer) declaredField.get(this)).generateTextures();
        }
        catch(Exception e){e.printStackTrace();}
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
