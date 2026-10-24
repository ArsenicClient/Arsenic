package arsenic.notifications;

import arsenic.utils.timer.MSTimer;
import arsenic.main.Arsenic;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class Notification {
    public static final int HEIGHT = 42;
    protected static final Minecraft mc = Minecraft.getMinecraft();
    private final NotificationType type;
    private final String title;
    private final String message;
    private final MSTimer timer = new MSTimer();
    public final long fadedIn;
    public final long fadeOut;
    public final long end;
    public float currentY;
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

    public void render(int x, float y, float alpha) {
        if (alpha <= 0.01f) return;

        int width = getWidth();
        int height = HEIGHT;

        DrawUtils.drawShadow(x, y, x + width, y + height, 12f, 12f, (int) (90 * alpha), 5);
        DrawUtils.drawRoundedRect(x, y, x + width, y + height, 12f,
                M3.alpha(M3.surfaceContainerHigh(), (int) (242 * alpha)));
        DrawUtils.drawRoundedOutline(x, y, x + width, y + height, 12f, 1f,
                M3.alpha(M3.outlineVariant(), (int) (120 * alpha)));

        int accent = getBorderColor(alpha);
        DrawUtils.drawRoundedRect(x + 8f, y + 11f, x + 11f, y + height - 11f, 1.5f, accent);

        int titleColor = M3.alpha(M3.onSurface(), (int) (255 * alpha));
        int subColor = M3.alpha(M3.onSurfaceVariant(), (int) (230 * alpha));

        M3.label().drawString(title, x + 20f, y + 13f, titleColor, M3.label().CENTREY);
        M3.body().drawString(message, x + 20f, y + 28f, subColor, M3.body().CENTREY);
        RenderUtils.resetColorText();
    }

    private int getBorderColor(float alpha) {
        int alphaMask = (int) (0xFF * alpha) << 24;
        int base;
        switch (type) {
            case INFO:
                base = M3.primary();
                break;
            case WARNING:
                base = 0xFFFFD37A;
                break;
            default:
                base = M3.error();
                break;
        }
        return (alphaMask & 0xFF000000) | (base & 0x00FFFFFF);
    }

    public int getWidth() {
        int titleW = (int) Math.ceil(M3.label().getWidth(title));
        int msgW = (int) Math.ceil(M3.body().getWidth(message));
        return Math.max(Math.max(titleW, msgW) + 32, 150);
    }
}
