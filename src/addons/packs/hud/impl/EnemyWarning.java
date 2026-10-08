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
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Warns when a player who is not on your friend list comes within the chosen distance. Plays a sound each time someone
 * enters the range, and lists the closest players (name and distance) in a HUD panel while anyone is in range.
 * Players the built-in AntiBot filters (NPCs, lobby bots) are ignored.
 */
@ModuleInfo(name = "EnemyWarning", description = "Warns when a non-friend player comes within a chosen distance", category = ModuleCategory.RENDER)
public class EnemyWarning extends Module {

    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(4, 64, 20, 1));
    public final BooleanProperty ignoreFriends = new BooleanProperty("Ignore Friends", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("EnemyWarning", 4, 100, 120, 16);
    private final Set<Integer> inRange = new HashSet<>();
    private final List<String> lines = new ArrayList<>();

    @Override
    protected void onEnable() {
        inRange.clear();
        lines.clear();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        double range = distance.getValue().getInput();
        Set<Integer> now = new HashSet<>();
        List<EntityPlayer> close = new ArrayList<>();

        for (EntityPlayer p : PlayerUtils.getPlayersWithin(range)) {
            if (p.isDead || AntiBot.isBot(p)) continue;
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
