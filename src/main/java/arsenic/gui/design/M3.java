package arsenic.gui.design;

import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.RenderUtils;

/**
 * Material You 3 design tokens: a tonal colour scheme generated from the active theme's
 * accent colour, plus the shape, type and motion scales the UI is built from.
 */
public final class M3 {

    public static final float SHAPE_XS = 4f;
    public static final float SHAPE_S = 8f;
    public static final float SHAPE_M = 12f;
    public static final float SHAPE_L = 16f;
    public static final float SHAPE_XL = 24f;
    public static final float SHAPE_FULL = 999f;

    public static final int DUR_FAST = 120;
    public static final int DUR_SHORT = 180;
    public static final int DUR_MEDIUM = 240;
    public static final int DUR_LONG = 360;

    private static int cachedSeed;
    private static Scheme cachedScheme;

    private M3() {}

    /** The generated dark scheme for a seed colour, without needing the client booted. */
    public static Scheme schemeFor(int seed) {
        return Scheme.of(seed);
    }

    public static Scheme colors() {
        int seed = ThemeManager.getMainColor();
        if (cachedScheme == null || cachedSeed != seed) {
            cachedSeed = seed;
            cachedScheme = Scheme.of(seed);
        }
        return cachedScheme;
    }

    public static int primary() { return colors().primary; }
    public static int onPrimary() { return colors().onPrimary; }
    public static int primaryContainer() { return colors().primaryContainer; }
    public static int onPrimaryContainer() { return colors().onPrimaryContainer; }
    public static int secondary() { return colors().secondary; }
    public static int onSecondary() { return colors().onSecondary; }
    public static int secondaryContainer() { return colors().secondaryContainer; }
    public static int onSecondaryContainer() { return colors().onSecondaryContainer; }
    public static int tertiary() { return colors().tertiary; }
    public static int onTertiary() { return colors().onTertiary; }
    public static int tertiaryContainer() { return colors().tertiaryContainer; }
    public static int onTertiaryContainer() { return colors().onTertiaryContainer; }
    public static int surface() { return colors().surface; }
    public static int surfaceContainerLowest() { return colors().surfaceContainerLowest; }
    public static int surfaceContainerLow() { return colors().surfaceContainerLow; }
    public static int surfaceContainer() { return colors().surfaceContainer; }
    public static int surfaceContainerHigh() { return colors().surfaceContainerHigh; }
    public static int surfaceVariant() { return colors().surfaceVariant; }
    public static int surfaceContainerHighest() { return colors().surfaceContainerHighest; }
    public static int onSurface() { return colors().onSurface; }
    public static int onSurfaceVariant() { return colors().onSurfaceVariant; }
    public static int outline() { return colors().outline; }
    public static int outlineVariant() { return colors().outlineVariant; }
    public static int error() { return colors().error; }
    public static int onError() { return colors().onError; }
    public static int errorContainer() { return colors().errorContainer; }
    public static int scrim() { return colors().scrim; }
    public static int inverseSurface() { return colors().inverseSurface; }
    public static int inverseOnSurface() { return colors().inverseOnSurface; }

    public static FontRendererExtension<?> body() {
        return Arsenic.getInstance().getFonts().Inter.getFontRendererExtension();
    }

    public static FontRendererExtension<?> label() {
        return Arsenic.getInstance().getFonts().InterMedium.getFontRendererExtension();
    }

    public static FontRendererExtension<?> title() {
        return Arsenic.getInstance().getFonts().InterSemiBold.getFontRendererExtension();
    }

    public static FontRendererExtension<?> headline() {
        return Arsenic.getInstance().getFonts().InterBold.getFontRendererExtension();
    }

    public static int alpha(int argb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
    }

    public static int alpha(int argb, float pct) {
        return alpha(argb, Math.round(255f * Math.max(0f, Math.min(1f, pct))));
    }

    public static int withAlphaOf(int argb, int alphaSource) {
        return (alphaSource & 0xFF000000) | (argb & 0x00FFFFFF);
    }

    public static int mix(int from, int to, float pct) {
        return RenderUtils.interpolateColoursInt(from, to, Math.max(0f, Math.min(1f, pct)));
    }

    public static int readableOn(int background) {
        float lum = 0.2126f * ((background >> 16) & 0xFF)
                + 0.7152f * ((background >> 8) & 0xFF)
                + 0.0722f * (background & 0xFF);
        return lum > 145f ? 0xFF15161A : 0xFFF4F5F7;
    }

    /** The generated dark-scheme roles for one seed colour. */
    public static final class Scheme {
        public final int seed;
        public final int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public final int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
        public final int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
        public final int surface, surfaceDim, surfaceBright;
        public final int surfaceContainerLowest, surfaceContainerLow, surfaceContainer;
        public final int surfaceContainerHigh, surfaceContainerHighest, surfaceVariant;
        public final int onSurface, onSurfaceVariant, outline, outlineVariant;
        public final int error, onError, errorContainer, scrim;
        public final int inverseSurface, inverseOnSurface;

        private Scheme(int seed, double h, double c) {
            this.seed = seed;
            double cp = Math.max(0.055, Math.min(0.19, c));
            double cn = Math.min(0.016, Math.max(0.006, c * 0.12));
            double cv = Math.min(0.045, Math.max(0.012, c * 0.3));

            primary = rgb(80, cp, h);
            onPrimary = rgb(20, cp, h);
            primaryContainer = rgb(30, cp, h);
            onPrimaryContainer = rgb(90, cp, h);

            secondary = rgb(80, cp * 0.42, h);
            onSecondary = rgb(20, cp * 0.42, h);
            secondaryContainer = rgb(30, cp * 0.42, h);
            onSecondaryContainer = rgb(90, cp * 0.42, h);

            tertiary = rgb(80, cp * 0.75, h + 60);
            onTertiary = rgb(20, cp * 0.75, h + 60);
            tertiaryContainer = rgb(30, cp * 0.75, h + 60);
            onTertiaryContainer = rgb(90, cp * 0.75, h + 60);

            surface = rgb(6, cn, h);
            surfaceDim = rgb(6, cn, h);
            surfaceBright = rgb(24, cn, h);
            surfaceContainerLowest = rgb(4, cn, h);
            surfaceContainerLow = rgb(10, cn, h);
            surfaceContainer = rgb(12, cn, h);
            surfaceContainerHigh = rgb(17, cn, h);
            surfaceContainerHighest = rgb(22, cn, h);
            surfaceVariant = rgb(30, cv, h);
            onSurface = rgb(90, cn, h);
            onSurfaceVariant = rgb(80, cv, h);
            outline = rgb(60, cv, h);
            outlineVariant = rgb(30, cv, h);

            error = rgb(80, 0.16, 25);
            onError = rgb(20, 0.16, 25);
            errorContainer = rgb(30, 0.16, 25);
            scrim = 0xFF000000;

            inverseSurface = rgb(90, cn, h);
            inverseOnSurface = rgb(20, cn, h);
        }

        static Scheme of(int argb) {
            double[] lab = srgbToOklab(
                    ((argb >> 16) & 0xFF) / 255.0,
                    ((argb >> 8) & 0xFF) / 255.0,
                    (argb & 0xFF) / 255.0);
            double c = Math.sqrt(lab[1] * lab[1] + lab[2] * lab[2]);
            double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
            if (h < 0) h += 360;
            return new Scheme(argb, h, c);
        }

        private static int rgb(double tone, double chroma, double hue) {
            double L = Math.max(0, Math.min(1, tone / 100.0));
            double rad = Math.toRadians(hue);
            double a = Math.cos(rad) * chroma;
            double b = Math.sin(rad) * chroma;

            double[] lin = oklabToLinear(L, a, b);
            if (lin[0] < -0.001 || lin[0] > 1.001 || lin[1] < -0.001 || lin[1] > 1.001
                    || lin[2] < -0.001 || lin[2] > 1.001) {
                double lo = 0, hi = chroma;
                for (int i = 0; i < 18; i++) {
                    double mid = (lo + hi) * 0.5;
                    double[] t = oklabToLinear(L, Math.cos(rad) * mid, Math.sin(rad) * mid);
                    if (t[0] < 0 || t[0] > 1 || t[1] < 0 || t[1] > 1 || t[2] < 0 || t[2] > 1)
                        hi = mid;
                    else
                        lo = mid;
                }
                lin = oklabToLinear(L, Math.cos(rad) * lo, Math.sin(rad) * lo);
            }

            int r = linearToSrgb(lin[0]);
            int g = linearToSrgb(lin[1]);
            int bl = linearToSrgb(lin[2]);
            return 0xFF000000 | (r << 16) | (g << 8) | bl;
        }

        private static double[] srgbToOklab(double r, double g, double b) {
            r = srgbToLinear(r);
            g = srgbToLinear(g);
            b = srgbToLinear(b);

            double l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
            double m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
            double s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

            double l_ = Math.cbrt(l);
            double m_ = Math.cbrt(m);
            double s_ = Math.cbrt(s);

            return new double[]{
                    0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
                    1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
                    0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
            };
        }

        private static double[] oklabToLinear(double L, double a, double b) {
            double l_ = L + 0.3963377774 * a + 0.2158037573 * b;
            double m_ = L - 0.1055613458 * a - 0.0638541728 * b;
            double s_ = L - 0.0894841775 * a - 1.2914855480 * b;

            double l = l_ * l_ * l_;
            double m = m_ * m_ * m_;
            double s = s_ * s_ * s_;

            return new double[]{
                    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
            };
        }

        private static double srgbToLinear(double v) {
            return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }

        private static int linearToSrgb(double v) {
            v = Math.max(0, Math.min(1, v));
            double s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
            return (int) Math.round(s * 255.0);
        }
    }
}
