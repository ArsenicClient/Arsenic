import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.BedwarsTracker;
import arsenic.utils.render.DrawUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Team panel for BedWars, read from the sidebar: each team with whether its bed is still up, and how many players of
 * that team are left when the sidebar shows a number. Hypixel's team lines look like "R Red: ✔" or "B Blue: 2 YOU";
 * only the eight team colours are read, so lines such as "Kills: 3" are ignored. Beds and teams that chat reported as
 * destroyed or eliminated (BedwarsTracker) are shown that way whatever the sidebar says. Your own team is marked.
 * Only active while GameDetector reports BedWars; needs the hypixel pack's GameDetector (pack.json requires).
 */
@ModuleInfo(name = "BedwarsStatus", description = "Team panel from the BedWars sidebar: beds and players left", category = ModuleCategory.PLAYER)
public class BedwarsStatus extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private static final Pattern TEAM_LINE = Pattern.compile("^(?:[A-Z] )?(Red|Blue|Green|Yellow|Aqua|White|Pink|Gray|Grey):\\s*(.*)$");
    private final HudElement panel = hudElement("BedwarsStatus", 4, 40, 150, 16);
    private final List<String> rows = new ArrayList<>();

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || GameDetector.game() != GameDetector.Game.BEDWARS) return;

        rows.clear();
        for (String line : GameDetector.sidebar()) {
            Matcher m = TEAM_LINE.matcher(line.trim());
            if (!m.matches()) continue;
            String team = m.group(1);
            String value = m.group(2);
            String bed = value.contains("✔") || value.contains("✓") ? "bed up"
                    : value.contains("✖") || value.contains("✗") || value.contains("✘") ? "bed down" : null;
            // chat is authoritative: "BED DESTRUCTION > Red Bed was destroyed", "TEAM ELIMINATED > Red Team ..."
            if (containsIgnoreCase(BedwarsTracker.destroyedBeds(), team)) bed = "bed down";
            if (containsIgnoreCase(BedwarsTracker.eliminatedTeams(), team)) bed = "eliminated";
            String alive = value.replaceAll("[^0-9]", "");
            StringBuilder row = new StringBuilder(team);
            if (bed != null) row.append("  ").append(bed);
            if (!alive.isEmpty()) row.append("  ").append(alive).append(" left");
            if (value.toUpperCase().contains("YOU")) row.append("  (you)");
            if (bed != null || !alive.isEmpty()) rows.add(row.toString());
        }
        if (rows.isEmpty()) return;

        int width = 150;
        for (String r : rows) width = Math.max(width, mc.fontRendererObj.getStringWidth(r) + 12);
        panel.setSize(width, 6 + rows.size() * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String r : rows) {
            int color = r.contains("eliminated") ? 0xFF888888 : r.contains("bed down") ? 0xFFFF5555 : r.contains("bed up") ? 0xFF55FF55 : 0xFFFFFFFF;
            mc.fontRendererObj.drawStringWithShadow(r, panel.x + 6, y, color);
            y += 10;
        }
    };

    private static boolean containsIgnoreCase(java.util.Set<String> names, String name) {
        for (String n : names)
            if (n.equalsIgnoreCase(name)) return true;
        return false;
    }
}
