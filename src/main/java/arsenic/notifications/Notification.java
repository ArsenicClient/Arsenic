package arsenic.notifications;

import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.hud.HudStyle;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.Minecraft;

public class Notification {
    public static final int HEIGHT = 34;

    protected static final Minecraft mc = Minecraft.getMinecraft();
    private final NotificationType type;
    private final String title;
    private final String message;
    private final MSTimer timer = new MSTimer();
    public final long fadedIn;
    public final long fadeOut;
    public final long end;
    public float currentY = -1f;
    public float targetY;

    public Notification(NotificationType type, String title, String message, int length) {
        this.type = type;
        this.title = title;
        this.message = message;
        fadedIn = 200L * length;
        fadeOut = fadedIn + 1500L * length;
        end = fadeOut + 300L * length;
    }

    public boolean isShown() {
        return getTime() <= end;
    }

    public long getTime() {
        return timer.getTime();
    }

    public void render(float x, float y, float alpha) {
        alpha = MathUtils.clamp01(alpha);
        if (alpha <= 0.01f) return;

        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();
        if (bodyFr == null || labelFr == null) return;

        float width = getWidth();
        float height = HEIGHT;
        float sx = HudStyle.snap(x);
        float sy = HudStyle.snap(y);
        float cy = sy + height / 2f;
        float pad = 7f;
        float badgeSize = 20f;

        HudStyle.card(sx, sy, sx + width, sy + height, HudStyle.RADIUS_CARD, (int) (235 * alpha));

        int badgeBg, badgeFg, barColor;
        String glyph;
        switch (type) {
            case WARNING:
                badgeBg = M3.alpha(M3.tertiaryContainer(), (int) (235 * alpha));
                badgeFg = M3.alpha(M3.onTertiaryContainer(), (int) (255 * alpha));
                barColor = M3.alpha(M3.tertiary(), (int) (220 * alpha));
                glyph = M3Icons.WARNING;
                break;
            case ERROR:
                badgeBg = M3.alpha(M3.errorContainer(), (int) (235 * alpha));
                badgeFg = M3.alpha(M3.onErrorContainer(), (int) (255 * alpha));
                barColor = M3.alpha(M3.error(), (int) (220 * alpha));
                glyph = M3Icons.ERROR;
                break;
            case INFO:
            default:
                badgeBg = M3.alpha(M3.primaryContainer(), (int) (235 * alpha));
                badgeFg = M3.alpha(M3.onPrimaryContainer(), (int) (255 * alpha));
                barColor = M3.alpha(M3.primary(), (int) (220 * alpha));
                glyph = M3Icons.CHECK;
                break;
        }

        HudStyle.iconBadge(sx + pad, cy, badgeSize, 6f, glyph, badgeBg, badgeFg);

        float textX = HudStyle.snap(sx + pad + badgeSize + 7f);
        float row1Cy = sy + 11.5f;
        float row2Cy = sy + 22.5f;

        labelFr.drawString(title, textX, row1Cy, M3.alpha(M3.onSurface(), (int) (255 * alpha)), labelFr.CENTREY);
        bodyFr.drawString(message, textX, row2Cy, M3.alpha(M3.onSurfaceVariant(), (int) (235 * alpha)), bodyFr.CENTREY);

        // Subtle remaining-time progress line along the bottom inset
        float progress = 1f - MathUtils.clamp01((float) getTime() / (float) Math.max(1L, end));
        if (progress > 0.01f) {
            float barX1 = sx + pad;
            float barMaxW = width - pad * 2f;
            float barY2 = sy + height - 2f;
            float barY1 = barY2 - 1.5f;
            DrawUtils.drawRoundedRect(barX1, barY1, barX1 + barMaxW * progress, barY2, 0.75f, barColor);
        }
        HudStyle.resetGl();
    }

    public int getWidth() {
        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();
        float titleW = labelFr != null ? labelFr.getWidth(title) : mc.fontRendererObj.getStringWidth(title);
        float msgW = bodyFr != null ? bodyFr.getWidth(message) : mc.fontRendererObj.getStringWidth(message);
        return Math.max((int) Math.ceil(Math.max(titleW, msgW) + 44f), 132);
    }

    public int getHeight() {
        return HEIGHT;
    }
}
