package arsenic.utils.java;

import arsenic.gui.themes.Theme;
import arsenic.main.Arsenic;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.TickMode;

import java.awt.*;

public class ColorUtils extends UtilityClass {

    public static int setColor(int value, int i, int newValue) {
        if(newValue > 0xFF)
            newValue = 0xFF;
        else if (newValue < 0)
            newValue = 0;
        int a = 24 - (i*8);
        return ((value & ~(0xFF << a)) | (newValue << a));
    }

    public static int getColor(int value, int i) {
        return (value >> (8 * (3 - i))) & 0xFF;
    }

    public static int getThemeRainbowColor(long speed, long delay) {
        speed *= 1000;
        delay *= -1;
        float percent = ((System.currentTimeMillis() + delay) % speed)/((float) speed);
        percent =TickMode.SINE.toSmoothPercent(2 * percent);
        Theme theme = Arsenic.getArsenic().getThemeManager().getCurrentTheme();
        return RenderUtils.interpolateColoursInt(theme.getMainColor(), theme.getWhite(), percent);
    }

    public static int getRainbow(float speed, long delay) {
        return Color.HSBtoRGB(((System.currentTimeMillis() + delay) % (int)(speed * 1000)) / (speed * 1000), 0.6f, 0.86f);
    }

}
