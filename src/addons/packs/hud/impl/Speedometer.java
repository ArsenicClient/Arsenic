import arsenic.asm.RequiresPlayer;
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

import java.util.Locale;

/**
 * Horizontal speed in blocks per second, measured from your position each tick (so it ignores vertical motion and
 * knockback that the server applies between two of your ticks, which shows up as a jump in the number).
 */
@ModuleInfo(name = "Speedometer", description = "Shows your horizontal speed in blocks per second", category = ModuleCategory.RENDER)
public class Speedometer extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("Speedometer", 4, 500, 110, 16);
    private double lastX, lastZ, bps;
    private boolean first = true;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (first) {
            lastX = mc.thePlayer.posX;
            lastZ = mc.thePlayer.posZ;
            first = false;
            return;
        }
        bps = Math.hypot(mc.thePlayer.posX - lastX, mc.thePlayer.posZ - lastZ) * 20;
        lastX = mc.thePlayer.posX;
        lastZ = mc.thePlayer.posZ;
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        String text = String.format(Locale.ROOT, "Speed %.2f b/s", bps);
        panel.setSize(Math.max(110, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
