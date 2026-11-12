import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.BedwarsTracker;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warns about nearby enemies, excluding friends and teammates. In BedWars, only players with validated tab teams
 * are considered; validated teams are retained for the match. AntiBot-filtered players are ignored.
 */
@ModuleInfo(name = "EnemyWarning", description = "Warns when an enemy comes within a chosen distance", category = ModuleCategory.RENDER, tier = ModuleTier.VISUAL)
public class EnemyWarning extends Module {

    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(4, 64, 20, 1));
    public final BooleanProperty ignoreFriends = new BooleanProperty("Ignore Friends", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private static final Pattern TAB_TEAM = Pattern.compile("(?i)§([0-9a-f])(?:§[k-o])*([RBGYAWPS])\\s+");
    private final Map<UUID, Character> teams = new HashMap<>();
    private int game = -1;
    private final HudElement panel = hudElement("EnemyWarning", 4, 100, 120, 16);
    private final Set<Integer> inRange = new HashSet<>();
    private final List<String> lines = new ArrayList<>();

    @Override
    protected void onEnable() {
        inRange.clear();
        lines.clear();
        teams.clear();
        game = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean bedwars = BedwarsTracker.inGame();
        if ((bedwars && game != BedwarsTracker.gameId()) || (!bedwars && game != -1)) {
            teams.clear();
            inRange.clear();
            game = bedwars ? BedwarsTracker.gameId() : -1;
        }
        if (bedwars && mc.getNetHandler() != null) {
            for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
                if (info.getGameProfile() == null) continue;
                char team = readTabTeam(info, info.getGameProfile().getName());
                if (team != 0) teams.put(info.getGameProfile().getId(), team);
            }
        }
        double range = distance.getValue().getInput();
        Set<Integer> now = new HashSet<>();
        List<EntityPlayer> close = new ArrayList<>();

        for (EntityPlayer p : PlayerUtils.getPlayersWithin(range)) {
            if (p.isDead || AntiBot.isBot(p)) continue;
            if (bedwars) {
                Character ownTeam = teams.get(mc.thePlayer.getUniqueID());
                Character targetTeam = teams.get(p.getUniqueID());
                if (ownTeam == null || targetTeam == null || ownTeam.equals(targetTeam)) continue;
            } else if (mc.thePlayer.isOnSameTeam(p)) continue;
            if (ignoreFriends.getValue() && Arsenic.getArsenic().getFriendManager().isFriend(p)) continue;
            now.add(p.getEntityId());
            close.add(p);
            if (!inRange.contains(p.getEntityId()) && sound.getValue()) {
                mc.thePlayer.playSound("random.orb", 0.5f, 1f);
            }
        }
        inRange.clear();
        inRange.addAll(now);

        close.sort((a, b) -> Double.compare(mc.thePlayer.getDistanceToEntity(a), mc.thePlayer.getDistanceToEntity(b)));
        lines.clear();
        for (int i = 0; i < close.size() && i < 3; i++) {
            EntityPlayer p = close.get(i);
            lines.add(p.getName() + "  " + String.format("%.1fm", mc.thePlayer.getDistanceToEntity(p)));
        }
    };

    private char readTabTeam(NetworkPlayerInfo info, String name) {
        String text = info.getDisplayName() != null ? info.getDisplayName().getFormattedText()
                : ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(), info.getGameProfile().getName());
        if (text == null) return 0;
        Matcher match = TAB_TEAM.matcher(text);
        if (!match.find() || !StringUtils.stripControlCodes(text.substring(0, match.start())).trim().isEmpty()) return 0;
        if (!StringUtils.stripControlCodes(text.substring(match.end())).trim().equalsIgnoreCase(name)) return 0;
        char color = Character.toLowerCase(match.group(1).charAt(0));
        char initial = Character.toUpperCase(match.group(2).charAt(0));
        switch (color) {
            case 'c': return initial == 'R' ? color : 0;
            case '9': return initial == 'B' ? color : 0;
            case 'a': return initial == 'G' ? color : 0;
            case 'e': return initial == 'Y' ? color : 0;
            case 'b': return initial == 'A' ? color : 0;
            case 'f': return initial == 'W' ? color : 0;
            case 'd': return initial == 'P' ? color : 0;
            case '7':
            case '8': return initial == 'S' ? '8' : 0;
            default: return 0;
        }
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || lines.isEmpty()) return;
        int width = 120;
        for (String line : lines) width = Math.max(width, mc.fontRendererObj.getStringWidth(line) + 12);
        int height = 6 + lines.size() * 10;
        panel.setSize(width, height);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String line : lines) {
            mc.fontRendererObj.drawStringWithShadow(line, panel.x + 6, y, 0xFFFF5555);
            y += 10;
        }
    };
}
