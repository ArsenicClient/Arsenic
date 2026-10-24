package arsenic.inject;

import arsenic.gui.design.M3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.InputStream;

/**
 * Material You 3 tokens for the injector, generated from the client's default (Classic) accent
 * with the exact same {@code arsenic.gui.design.M3.Scheme} the client uses, so the palette is
 * identical to the one in game.
 */
final class InjectorTheme {

    // the client's default theme accent: ThemeManager's "Classic" seed
    private static final int SEED = 0xFFDD425E;
    private static final M3.Scheme SCHEME = M3.schemeFor(SEED);

    static final Color SURFACE = new Color(SCHEME.surface);
    static final Color SURFACE_LOW = new Color(SCHEME.surfaceContainerLow);
    static final Color SURFACE_CONTAINER = new Color(SCHEME.surfaceContainer);
    static final Color SURFACE_HIGH = new Color(SCHEME.surfaceContainerHigh);
    static final Color SURFACE_HIGHEST = new Color(SCHEME.surfaceContainerHighest);
    static final Color SURFACE_VARIANT = new Color(SCHEME.surfaceVariant);
    static final Color PRIMARY = new Color(SCHEME.primary);
    static final Color ON_PRIMARY = new Color(SCHEME.onPrimary);
    static final Color PRIMARY_CONTAINER = new Color(SCHEME.primaryContainer);
    static final Color ON_PRIMARY_CONTAINER = new Color(SCHEME.onPrimaryContainer);
    static final Color SECONDARY_CONTAINER = new Color(SCHEME.secondaryContainer);
    static final Color ON_SECONDARY_CONTAINER = new Color(SCHEME.onSecondaryContainer);
    static final Color ON_SURFACE = new Color(SCHEME.onSurface);
    static final Color ON_SURFACE_VARIANT = new Color(SCHEME.onSurfaceVariant);
    static final Color OUTLINE = new Color(SCHEME.outline);
    static final Color OUTLINE_VARIANT = new Color(SCHEME.outlineVariant);
    static final Color SUCCESS = new Color(0xFFA6DDB5);
    static final Color SUCCESS_CONTAINER = new Color(0xFF1B3B27);
    static final Color WARNING = new Color(0xFFF2C66D);
    static final Color WARNING_CONTAINER = new Color(0xFF3E3118);
    static final Color DANGER = new Color(SCHEME.error);
    static final Color ON_DANGER = new Color(SCHEME.onError);
    static final Color DANGER_CONTAINER = new Color(SCHEME.errorContainer);
    static final Color SCRIM = new Color(0xFF000000);

    static final String REFRESH = "\uE5D5";
    static final String CHECK = "\uE5CA";
    static final String WARNING_ICON = "\uE002";
    static final String ERROR = "\uE000";
    static final String CUBE = "\uE9FE";
    static final String MEMORY = "\uE322";

    private static final Font REGULAR = load("/assets/arsenic/Inter-Regular.ttf");
    private static final Font MEDIUM = load("/assets/arsenic/Inter-Medium.ttf");
    private static final Font SEMIBOLD = load("/assets/arsenic/Inter-SemiBold.ttf");
    private static final Font BOLD = load("/assets/arsenic/Inter-Bold.ttf");
    private static final Font ICONS = load("/assets/arsenic/MaterialIcons-Regular.ttf");

    private InjectorTheme() {
    }

    static Font regular(float size) {
        return font(REGULAR, Font.PLAIN, size);
    }

    static Font medium(float size) {
        return font(MEDIUM, Font.PLAIN, size);
    }

    static Font semibold(float size) {
        return font(SEMIBOLD, Font.PLAIN, size);
    }

    static Font bold(float size) {
        return font(BOLD, Font.PLAIN, size);
    }

    static Font mono(float size) {
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
    }

    static Font icon(float size) {
        return font(ICONS, Font.PLAIN, size);
    }

    private static Font font(Font base, int style, float size) {
        if (base != null)
            return base.deriveFont(style, size);
        return new Font(Font.SANS_SERIF, style, Math.round(size));
    }

    private static Font load(String path) {
        try (InputStream in = InjectorTheme.class.getResourceAsStream(path)) {
            return in != null ? Font.createFont(Font.TRUETYPE_FONT, in) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static Color alpha(Color color, int value) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), value);
    }

    static Color alpha(Color color, float value) {
        return alpha(color, Math.round(Math.max(0f, Math.min(1f, value)) * 255f));
    }

    static Color mix(Color from, Color to, float amount) {
        float keep = 1f - Math.max(0f, Math.min(1f, amount));
        return new Color(
                Math.round(from.getRed() * keep + to.getRed() * (1f - keep)),
                Math.round(from.getGreen() * keep + to.getGreen() * (1f - keep)),
                Math.round(from.getBlue() * keep + to.getBlue() * (1f - keep)));
    }
}
