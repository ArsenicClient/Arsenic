package arsenic.gui.themes;

import arsenic.utils.java.ColorUtils;

public final class Contrast {

    private Contrast() {}

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int rgb) {
        return 0.2126 * lin(rgb >> 16 & 255) + 0.7152 * lin(rgb >> 8 & 255) + 0.0722 * lin(rgb & 255);
    }

    public static int over(int fg, int bg) {
        int a = fg >>> 24;
        if (a == 0) a = 255;
        int r = ((fg >> 16 & 255) * a + (bg >> 16 & 255) * (255 - a)) / 255;
        int g = ((fg >> 8 & 255) * a + (bg >> 8 & 255) * (255 - a)) / 255;
        int b = ((fg & 255) * a + (bg & 255) * (255 - a)) / 255;
        return r << 16 | g << 8 | b;
    }

    public static double ratio(int fg, int bg) {
        double a = luminance(over(fg, bg)), b = luminance(bg & 0xFFFFFF);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double worst(int fg, int[] surfaces) {
        double w = Double.MAX_VALUE;
        for (int s : surfaces) w = Math.min(w, ratio(fg, s));
        return w;
    }

    public static int ensure(int fg, int[] surfaces, double target) {
        if (worst(fg, surfaces) >= target) return fg;
        double avg = 0;
        for (int s : surfaces) avg += luminance(s);
        int end = avg / surfaces.length < 0.4 ? 0xFFFFFF : 0x000000;
        int alpha = fg & 0xFF000000;
        for (int i = 1; i <= 50; i++) {
            float t = i / 50f;
            int c = alpha | ColorUtils.mixRgb(fg & 0xFFFFFF, end, t);
            if (worst(c, surfaces) >= target) return c;
        }
        return alpha | end;
    }

}
