
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.StringUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the server's "STREAK! of N kills by [level] name" broadcasts and works out how fast each player is killing
 * from how quickly their streak milestones arrive.
 */
@ModuleInfo(name = "KillTracker", category = ModuleCategory.RENDER, description = "Tracks kills per minute from the server's streak messages")
public class KillTracker extends Module {

    public static final int WIDTH = 132;
    public static final int HEADER = 17;
    public static final int ROW = 13;
    private final HudElement hud = hudElement("KillTracker", 4, 100, WIDTH, HEADER + ROW * 5 + 4);

    public final DoubleProperty rows = new DoubleProperty("Rows", new DoubleValue(1, 10, 5, 1));
    public final DoubleProperty window = new DoubleProperty("KPM Window (s)", new DoubleValue(30, 300, 120, 5));
    public final DoubleProperty expire = new DoubleProperty("Hide After (s)", new DoubleValue(30, 600, 180, 10));

    private static final Pattern STREAK = Pattern.compile("STREAK!\\s+of\\s+(\\d+)\\s+kills\\s+by\\s+(?:\\[(\\d+)]\\s*)?(\\w{1,16})");

    private final ConcurrentLinkedQueue<String> pendingChat = new ConcurrentLinkedQueue<>();
    private final Map<String, Stat> stats = new HashMap<>();
    private Object lastWorld;

    private static class Stat {
        final String name;
        int level = -1;
        int streak;
        long lastSeen;
        final ArrayDeque<long[]> samples = new ArrayDeque<>(); // {time, streak}
        float shownKpm;

        Stat(String name) {
            this.name = name;
        }
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            S02PacketChat chat = (S02PacketChat) event.getPacket();
            if (chat.getType() == 2) return; // action bar
            String text = StringUtils.stripControlCodes(chat.getChatComponent().getUnformattedText());
            if (STREAK.matcher(text).find()) {
                pendingChat.add(text);
                event.cancel(); // the panel replaces the chat spam
            }
        }
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.theWorld == null || mc.thePlayer == null) {
            pendingChat.clear();
            return;
        }
        if (lastWorld != mc.theWorld) {
            lastWorld = mc.theWorld;
            stats.clear();
            pendingChat.clear();
        }
        String line;
        while ((line = pendingChat.poll()) != null) parse(line);
    };

    private void parse(String line) {
        Matcher m = STREAK.matcher(line);
        if (!m.find()) return;
        int streak = Integer.parseInt(m.group(1));
        String name = m.group(3);
        long now = System.currentTimeMillis();

        Stat s = stats.computeIfAbsent(name, Stat::new);
        if (m.group(2) != null) s.level = Integer.parseInt(m.group(2));
        if (!s.samples.isEmpty() && streak == s.streak) return; // same broadcast twice
        if (streak < s.streak) s.samples.clear(); // died and started over
        s.streak = streak;
        s.lastSeen = now;
        s.samples.add(new long[]{now, streak});
    }

    /** Kills per minute between the oldest sample in the window and now, so it decays when a player goes quiet. */
    private float kpm(Stat s, long now) {
        long cutoff = now - (long) (window.getValue().getInput() * 1000);
        while (s.samples.size() >= 2) {
            Iterator<long[]> it = s.samples.iterator();
            it.next();
            if (it.next()[0] < cutoff) s.samples.pollFirst();
            else break;
        }
        if (s.samples.size() < 2) return 0f;
        long[] first = s.samples.peekFirst();
        long[] last = s.samples.peekLast();
        long span = Math.max(15000, now - first[0]);
        return (last[1] - first[1]) * 60000f / span;
    }

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (mc.thePlayer == null) return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        long now = System.currentTimeMillis();
        long expireMs = (long) (expire.getValue().getInput() * 1000);
        stats.values().removeIf(s -> now - s.lastSeen > expireMs);

        List<Stat> ranked = new ArrayList<>(stats.values());
        for (Stat s : ranked) s.shownKpm += (kpm(s, now) - s.shownKpm) * 0.15f;
        ranked.sort((a, b) -> {
            int c = Float.compare(b.shownKpm, a.shownKpm);
            return c != 0 ? c : Integer.compare(b.streak, a.streak);
        });
        int max = (int) rows.getValue().getInput();
        if (ranked.size() > max) ranked = ranked.subList(0, max);

        int shown = Math.max(1, ranked.size());
        hud.setSize(WIDTH, HEADER + ROW * shown + 4);
        int x = hud.x, y = hud.y;
        int theme = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();

        DrawUtils.drawRoundedRect(x, y, x + WIDTH, y + hud.height, 5, new Color(18, 18, 18, 150).getRGB());
        DrawUtils.drawRoundedOutline(x, y, x + WIDTH, y + hud.height, 5, 1f, new Color(255, 255, 255, 28).getRGB());
        DrawUtils.drawRect(x + 6, y + HEADER - 2, x + WIDTH - 6, y + HEADER - 1, (0x55 << 24) | (theme & 0xFFFFFF));
        fr.drawStringWithShadow("Kill Tracker", x + 6, y + 4, 0xFFFFFFFF);
        String unit = "KILLS/MIN";
        fr.drawString(unit, x + WIDTH - 6 - fr.getWidth(unit), y + 5, 0xFF8A8A8A);

        if (ranked.isEmpty()) {
            fr.drawString("Waiting for streaks...", x + 6, y + HEADER + 2, 0xFF7A7A7A);
            return;
        }

        float top = Math.max(1f, ranked.get(0).shownKpm);
        int i = 0;
        for (Stat s : ranked) {
            float ry = y + HEADER + i * ROW + 1;
            boolean self = s.name.equals(mc.thePlayer.getName());
            int accent = self ? theme : heat(s.shownKpm);
            int barW = WIDTH - 12;

            DrawUtils.drawRoundedRect(x + 6, ry, x + 6 + barW, ry + ROW - 2, 2, new Color(255, 255, 255, 14).getRGB());
            float fill = Math.max(3f, barW * Math.min(1f, s.shownKpm / top));
            DrawUtils.drawRoundedRect(x + 6, ry, x + 6 + fill, ry + ROW - 2, 2, (0x70 << 24) | (accent & 0xFFFFFF));

            String name = s.name;
            while (name.length() > 3 && fr.getWidth(name) > 62) name = name.substring(0, name.length() - 1);
            fr.drawStringWithShadow(name, x + 9, ry + 1.5f, self ? 0xFFFFFFFF : 0xFFE6E6E6);

            String val = s.samples.size() < 2 ? "--" : String.format("%.1f", s.shownKpm);
            fr.drawStringWithShadow(val, x + WIDTH - 9 - fr.getWidth(val), ry + 1.5f, 0xFF000000 | (accent & 0xFFFFFF));
            String streak = s.streak + " streak";
            fr.drawString(streak, x + WIDTH - 14 - fr.getWidth(val) - fr.getWidth(streak), ry + 1.5f, 0xFF9A9A9A);
            i++;
        }
    };

    /** Cool to hot colour for how fast somebody is killing. */
    private static int heat(float kpm) {
        float t = Math.min(1f, kpm / 6f);
        return Color.HSBtoRGB(0.33f * (1f - t), 0.75f, 1f);
    }

    @Override
    protected void onEnable() {
        stats.clear();
        pendingChat.clear();
        lastWorld = null;
    }

    @Override
    protected void onDisable() {
        stats.clear();
        pendingChat.clear();
    }
}
