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
import arsenic.utils.minecraft.BedwarsTracker;
import arsenic.utils.render.DrawUtils;
import net.minecraft.network.play.server.S02PacketChat;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Counts final kills in BedWars from chat: all final kills in the game, and yours. Hypixel puts the victim first and
 * the killer after the last " by ": "Garrettwc21 was killed by KingZeenx. FINAL KILL!", "X was knocked into the void
 * by Y. FINAL KILL!", or "X fell into the void. FINAL KILL!" with no killer. Only lines ending in "FINAL KILL!" count,
 * not reward lines such as "+2 tokens! (Final Kill)". Counts reset when a new game starts (BedwarsTracker).
 */
@ModuleInfo(name = "FinalKillCounter", description = "Counts final kills in BedWars from chat", category = ModuleCategory.RENDER)
public class FinalKillCounter extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("FinalKills", 4, 80, 150, 16);
    private final ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<>();
    private int total, mine, game = -1;

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
        if (game != BedwarsTracker.gameId()) {
            game = BedwarsTracker.gameId();
            total = 0;
            mine = 0;
        }
        String line;
        while ((line = lines.poll()) != null) {
            line = line.replaceAll("\u00a7.", "").trim();
            if (!line.endsWith("FINAL KILL!")) continue;
            total++;
            int by = line.lastIndexOf(" by ");
            if (by < 0 || mc.thePlayer == null) continue;
            String killer = line.substring(by + 4, line.length() - "FINAL KILL!".length()).trim();
            if (killer.endsWith(".")) killer = killer.substring(0, killer.length() - 1);
            if (killer.equals(mc.thePlayer.getName())) mine++;
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
