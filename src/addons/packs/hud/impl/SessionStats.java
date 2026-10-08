import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import net.minecraft.network.play.server.S02PacketChat;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kills, deaths, K/D and time played since the addon was enabled. Kills and deaths come from chat lines of the form
 * "Victim was killed by Killer", matched against your name. The chat format has not been checked on every Hypixel mode,
 * so counts can be off where the wording differs.
 */
@ModuleInfo(name = "SessionStats", description = "Kills, deaths, K/D and time played this session", category = ModuleCategory.RENDER)
public class SessionStats extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private static final String KILLED_BY = "killed by ";
    private final HudElement panel = hudElement("SessionStats", 4, 260, 150, 30);
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private int kills, deaths, ticks;

    @Override
    protected void onEnable() {
        kills = 0;
        deaths = 0;
        ticks = 0;
        lines.clear();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            lines.add(((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        ticks++;
        String me = mc.thePlayer.getName();
        String line;
        while ((line = lines.poll()) != null) {
            int at = line.toLowerCase(Locale.ROOT).indexOf(KILLED_BY);
            if (at < 0) continue;
            int was = line.toLowerCase(Locale.ROOT).indexOf(" was");
            String victim = lastWord(line.substring(0, was > 0 && was < at ? was : at));
            String killer = firstWord(line.substring(at + KILLED_BY.length()));
            if (victim.equalsIgnoreCase(me)) deaths++;
            if (killer.equalsIgnoreCase(me) && !victim.equalsIgnoreCase(me)) kills++;
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        int seconds = ticks / 20;
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        String kd = deaths == 0 ? String.valueOf(kills) : String.format("%.2f", kills / (double) deaths);
        String[] rows = {"Kills " + kills + "   Deaths " + deaths, "K/D " + kd + "   Time " + time};
        int width = 150;
        for (String r : rows) width = Math.max(width, mc.fontRendererObj.getStringWidth(r) + 12);
        panel.setSize(width, 6 + rows.length * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String r : rows) {
            mc.fontRendererObj.drawStringWithShadow(r, panel.x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
    };

    private static String lastWord(String s) {
        String t = s.trim();
        int sp = t.lastIndexOf(' ');
        return (sp < 0 ? t : t.substring(sp + 1)).replaceAll("[^A-Za-z0-9_]", "");
    }

    private static String firstWord(String s) {
        String t = s.trim();
        int sp = t.indexOf(' ');
        return (sp < 0 ? t : t.substring(0, sp)).replaceAll("[^A-Za-z0-9_]", "");
    }
}
