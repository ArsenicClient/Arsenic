import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Counts the players within a radius, split into teammates and everyone else. Bots the built-in AntiBot filters are not
 * counted, and players on your friend list count as teammates. Team is whatever PlayerUtils considers the same team
 * (on BedWars, the same bed colour).
 */
@ModuleInfo(name = "NearbyCount", description = "Counts players nearby, split into teammates and enemies", category = ModuleCategory.RENDER)
public class NearbyCount extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(4, 64, 24, 1));

    private final HudElement panel = hudElement("NearbyCount", 4, 540, 150, 16);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        int team = 0, enemies = 0;
        for (EntityPlayer p : PlayerUtils.getPlayersWithin(radius.getValue().getInput())) {
            if (p.isDead || AntiBot.isBot(p)) continue;
            if (PlayerUtils.isEntityTeamSameAsPlayer(p) || Arsenic.getArsenic().getFriendManager().isFriend(p)) team++;
            else enemies++;
        }
        String text = "Enemies " + enemies + "   Team " + team;
        panel.setSize(Math.max(150, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
