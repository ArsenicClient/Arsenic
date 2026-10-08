import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Works out which Hypixel game you are in from the sidebar title, and shares that with other addons:
 * GameDetector.current is the game, and GameDetector.sidebar() gives the sidebar lines, top to bottom, with colour codes
 * removed. The game is one of BEDWARS, SKYWARS, PIT, DUELS, MURDER, ZOMBIES, UNKNOWN (a sidebar we do not recognise,
 * usually the lobby), or NONE (not in a world).
 *
 * The title strings are matched loosely and have not been checked against every Hypixel mode. If a game is detected as
 * UNKNOWN, read the title from the sidebar and add its text to detect().
 */
@ModuleInfo(name = "GameDetector", description = "Detects the current Hypixel game from the sidebar and shares it with other addons", category = ModuleCategory.PLAYER)
public class GameDetector extends Module {

    public enum Game { NONE, UNKNOWN, BEDWARS, SKYWARS, PIT, DUELS, MURDER, ZOMBIES }

    /** The game the player is in, updated every tick. */
    public static volatile Game current = Game.NONE;

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("GameDetector", 4, 4, 130, 16);

    @Override
    protected void onDisable() {
        current = Game.NONE;
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            current = Game.NONE;
            return;
        }
        current = detect(title());
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        String text = "Game: " + current.name();
        panel.setSize(Math.max(110, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };

    /** Sidebar title with colour codes removed, or null when there is no sidebar. */
    public static String title() {
        Scoreboard sb = Minecraft.getMinecraft().theWorld == null ? null : Minecraft.getMinecraft().theWorld.getScoreboard();
        ScoreObjective obj = sb == null ? null : sb.getObjectiveInDisplaySlot(1);
        return obj == null ? null : strip(obj.getDisplayName());
    }

    /** Sidebar lines from the top down, with colour codes removed. Empty when there is no sidebar. */
    public static List<String> sidebar() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return out;
        Scoreboard sb = mc.theWorld.getScoreboard();
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) return out;
        List<Score> scores = new ArrayList<>(sb.getSortedScores(obj));
        Collections.reverse(scores); // the sidebar lists the highest score first
        for (Score s : scores) {
            ScorePlayerTeam team = sb.getPlayersTeam(s.getPlayerName());
            String line = strip(ScorePlayerTeam.formatPlayerName(team, s.getPlayerName()));
            if (!line.startsWith("#")) out.add(line);
        }
        return out;
    }

    static Game detect(String title) {
        if (title == null) return Game.UNKNOWN;
        String t = title.toUpperCase(Locale.ROOT);
        if (t.contains("BED WARS")) return Game.BEDWARS;
        if (t.contains("SKYWARS")) return Game.SKYWARS;
        if (t.contains("THE HYPIXEL PIT") || t.equals("PIT")) return Game.PIT;
        if (t.contains("DUELS")) return Game.DUELS;
        if (t.contains("MURDER")) return Game.MURDER;
        if (t.contains("ZOMBIES")) return Game.ZOMBIES;
        return Game.UNKNOWN;
    }

    static String strip(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }
}
