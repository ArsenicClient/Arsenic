import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Alerts (chat, sound and a HUD flash) when a player whose tab-list name carries a staff rank tag joins or is already
 * listed. Each name alerts once per enable. Optionally sends /lobby when staff are seen, like the other leave options.
 * Only the rank tags are checked; the player's name is not stored.
 */
@ModuleInfo(name = "StaffAlert", description = "Alerts when a staff rank tag appears in the tab list", category = ModuleCategory.PLAYER)
public class StaffAlert extends Module {

    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final BooleanProperty leaveOnStaff = new BooleanProperty("Send /lobby", false);

    private static final String[] TAGS = {"[ADMIN]", "[GM]", "[MOD]", "[HELPER]", "[STAFF]", "[YOUTUBE]"};

    private final HudElement panel = hudElement("StaffAlert", 4, 120, 130, 16);
    private final MSTimer flash = MSTimer.expired();
    private final MSTimer scan = new MSTimer();
    private final Set<String> alerted = new HashSet<>();
    private boolean left;

    @Override
    protected void onEnable() {
        alerted.clear();
        left = false;
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer == null || mc.getNetHandler() == null || !scan.hasTimeElapsed(1000, true)) return;
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info == null || info.getGameProfile() == null || info.getGameProfile().getName() == null) continue;
            String name = info.getGameProfile().getName();
            if (name.equalsIgnoreCase(mc.thePlayer.getName()) || alerted.contains(name)) continue;
            String shown = info.getDisplayName() != null ? info.getDisplayName().getUnformattedText() : name;
            if (!hasStaffTag(shown)) continue;

            alerted.add(name);
            flash.reset();
            PlayerUtils.addWaterMarkedMessageToChat("Staff in the game: " + name);
            if (sound.getValue()) mc.thePlayer.playSound("random.anvil_land", 1f, 1f);
            if (leaveOnStaff.getValue() && !left) {
                left = true;
                mc.thePlayer.sendChatMessage("/lobby");
            }
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || flash.getTime() > 5000) return;
        String text = "Staff in game!";
        panel.setSize(Math.max(130, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0xC0AA1010);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };

    static boolean hasStaffTag(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        for (String tag : TAGS) if (upper.contains(tag)) return true;
        return false;
    }
}
