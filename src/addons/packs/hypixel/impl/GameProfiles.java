import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.TextProperty;

/**
 * Switches modules when GameDetector reports a different game. For each game, the "On" list is enabled and the "Off"
 * list is disabled, using module names (comma separated, the names shown in the ClickGUI). Modules not in either list are
 * left alone. Profiles apply when the game changes, not every tick, so you can still toggle things by hand.
 */
@ModuleInfo(name = "GameProfiles", description = "Turns modules on or off for each Hypixel game", category = ModuleCategory.PLAYER)
public class GameProfiles extends Module {

    public final TextProperty bedwarsOn = new TextProperty("BedWars On", "KillAura,BedAlarm", 300);
    public final TextProperty bedwarsOff = new TextProperty("BedWars Off", "", 300);
    public final TextProperty skywarsOn = new TextProperty("SkyWars On", "", 300);
    public final TextProperty skywarsOff = new TextProperty("SkyWars Off", "KillAura", 300);
    public final TextProperty pitOn = new TextProperty("Pit On", "AutoSoup", 300);
    public final TextProperty pitOff = new TextProperty("Pit Off", "", 300);
    public final TextProperty duelsOn = new TextProperty("Duels On", "", 300);
    public final TextProperty duelsOff = new TextProperty("Duels Off", "", 300);

    private GameDetector.Game applied;

    @Override
    protected void onEnable() {
        applied = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        GameDetector.Game now = GameDetector.game();
        if (now == applied) return;
        applied = now;
        switch (now) {
            case BEDWARS: apply(bedwarsOn, bedwarsOff); break;
            case SKYWARS: apply(skywarsOn, skywarsOff); break;
            case PIT: apply(pitOn, pitOff); break;
            case DUELS: apply(duelsOn, duelsOff); break;
            default: break;
        }
    };

    private static void apply(TextProperty on, TextProperty off) {
        for (String name : on.getValue().split(",")) setIf(name, true);
        for (String name : off.getValue().split(",")) setIf(name, false);
    }

    private static void setIf(String name, boolean enabled) {
        String n = name.trim();
        if (n.isEmpty()) return;
        arsenic.module.Module m = Arsenic.getArsenic().getModuleManager().getModuleByName(n);
        if (m != null && m.isEnabled() != enabled) m.setEnabled(enabled);
    }
}
