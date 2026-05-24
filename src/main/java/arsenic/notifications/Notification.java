package arsenic.notifications;

import arsenic.main.Arsenic;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.Minecraft;

public class Notification {
    protected static final Minecraft mc = Minecraft.getMinecraft();
    private final NotificationType type;
    private final String title;
    private final String message;
    private final long start;
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
        start = System.currentTimeMillis();
    }

    public boolean isShown() {
        return getTime() <= end;
    }

    public long getTime() {
        return System.currentTimeMillis() - start;
    }

    public void render(int x, float y, float alpha) {
        if (alpha <= 0.01f) return;

        int width = getWidth();
        int height = 30;

        int bgColor = ((int) (0x90 * alpha) << 24) | 0x101010;
        int borderColor = getBorderColor(alpha);

        DrawUtils.drawRoundedRect(x, y, x + width, y + height, 4, bgColor);
        DrawUtils.drawRoundedOutline(x, y, x + width, y + height, 4, 1, borderColor);

        int textColor = ((int) (0xFF * alpha) << 24) | 0xFFFFFF;
        int subColor = ((int) (0xCC * alpha) << 24) | 0xCCCCCC;

        mc.fontRendererObj.drawString(title, x + 8, (int) y + 5, textColor);
        mc.fontRendererObj.drawString(message, x + 8, (int) y + 17, subColor);
    }

    private int getBorderColor(float alpha) {
        int alphaMask = (int) (0xFF * alpha) << 24;
        int themeColor = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        int r = (themeColor >> 16) & 0xFF;
        int g = (themeColor >> 8) & 0xFF;
        int b = themeColor & 0xFF;
        switch (type) {
            case INFO:
                return alphaMask | ((r) << 16) | ((g) << 8) | b;
            case WARNING:
                return alphaMask | 0xFFD600;
            default:
                return alphaMask | 0xFF1744;
        }
    }

    public int getWidth() {
        int titleW = mc.fontRendererObj.getStringWidth(title);
        int msgW = mc.fontRendererObj.getStringWidth(message);
        return Math.max(Math.max(titleW, msgW) + 24, 120);
    }

    private int getHeight() {
        return 30;
    }
}
