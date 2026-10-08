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
 * Counts final kills in BedWars from chat: all final kills in the game, and the ones where your name comes before
 * "FINAL KILL" in the line. The chat format is assumed to put the killer first, which has not been checked against a
 * live server. Counts reset when you enable the addon, not when a game starts.
 */
@ModuleInfo(name = "FinalKillCounter", description = "Counts final kills in BedWars from chat", category = ModuleCategory.RENDER)
public class FinalKillCounter extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("FinalKills", 4, 80, 150, 16);
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private int total, mine;

    @Override
    protected void onEnable() {
        total = 0;
        mine = 0;
        lines.clear();
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            lines.add(((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText());
        }
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        String line;
        while ((line = lines.poll()) != null) {
            int at = line.toUpperCase(Locale.ROOT).indexOf("FINAL KILL");
            if (at < 0) continue;
            total++;
            String name = mc.thePlayer == null ? "" : mc.thePlayer.getName();
            int nameAt = name.isEmpty() ? -1 : line.indexOf(name);
            if (nameAt >= 0 && nameAt < at) mine++;
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        String text = "Final kills: " + mine + " you, " + total + " total";
        panel.setSize(Math.max(150, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
