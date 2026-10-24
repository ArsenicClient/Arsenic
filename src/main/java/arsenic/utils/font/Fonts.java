package arsenic.utils.font;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public class Fonts {


    public final TTFontRenderer Comfortaa = new TTFontRenderer(getFontFromLocation("comfortaa.ttf", 17), true, true);
    public final TTFontRenderer Icon = new TTFontRenderer(getFontFromLocation("icon.ttf", 20), true, true);
    public final TTFontRenderer Inter = new TTFontRenderer(getFontFromLocation("Inter-Regular.ttf", 18), true, true);
    public final TTFontRenderer InterMedium = new TTFontRenderer(getFontFromLocation("Inter-Medium.ttf", 18), true, true);
    public final TTFontRenderer InterSemiBold = new TTFontRenderer(getFontFromLocation("Inter-SemiBold.ttf", 19), true, true);
    public final TTFontRenderer InterBold = new TTFontRenderer(getFontFromLocation("Inter-Bold.ttf", 22), true, true);
    public final TTFontRenderer Material = new TTFontRenderer(getFontFromLocation("MaterialIcons-Regular.ttf", 32), true, true);

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
