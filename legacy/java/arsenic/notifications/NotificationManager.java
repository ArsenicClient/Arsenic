package arsenic.notifications;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import net.minecraft.client.gui.ScaledResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class NotificationManager {

    private static final LinkedBlockingQueue<Notification> pendingNotifications = new LinkedBlockingQueue<>();
    private static final List<Notification> activeNotifications = new ArrayList<>();
    private static final int MAX_VISIBLE = 4;
    private static final int GAP = 5;

    public static void show(Notification notification) {
        pendingNotifications.add(notification);
    }

    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        ScaledResolution sr = event.getSr();
        render(sr);
    };

    private void render(ScaledResolution sr) {
        activeNotifications.removeIf(n -> !n.isShown());

        while (activeNotifications.size() < MAX_VISIBLE && !pendingNotifications.isEmpty()) {
            Notification n = pendingNotifications.poll();
            activeNotifications.add(n);
        }

        int height = Notification.HEIGHT;
        int baseY = sr.getScaledHeight() - 12 - height;

        for (int i = 0; i < activeNotifications.size(); i++) {
            Notification n = activeNotifications.get(i);
            n.targetY = baseY - i * (height + GAP);

            if (n.currentY < 0f)
                n.currentY = n.targetY;
            n.currentY += (n.targetY - n.currentY) * 0.18f;
            if (Math.abs(n.currentY - n.targetY) < 0.35f)
                n.currentY = n.targetY;

            long time = n.getTime();
            float alpha = 1f;
            float slideOffset = 0f;

            if (time < n.fadedIn) {
                float progress = (float) time / n.fadedIn;
                float eased = 1f - (1f - progress) * (1f - progress);
                slideOffset = (n.getWidth() + 12f) * (1f - eased);
                alpha = Math.min(1f, progress * 1.8f);
            } else if (time > n.fadeOut) {
                float progress = (float) (time - n.fadeOut) / (n.end - n.fadeOut);
                alpha = 1f - progress;
                slideOffset = (n.getWidth() + 12f) * progress * progress;
            }

            float x = sr.getScaledWidth() - n.getWidth() - 8f + slideOffset;
            n.render(x, n.currentY, alpha);
        }
    }
}
