import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;

import java.util.Locale;

/**
 * Shows how far you have fallen since you last touched the ground, in blocks. Hidden when you are not falling. Useful
 * for judging a landing; it reads the client's own fall counter and does not change anything.
 */
@ModuleInfo(name = "FallDistance", description = "Shows how far you have fallen since you last touched the ground", category = ModuleCategory.RENDER)
public class FallDistance extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("FallDistance", 4, 480, 110, 16);

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || mc.thePlayer == null || mc.thePlayer.fallDistance < 0.5f) return;
        String text = String.format(Locale.ROOT, "Fall %.1f", mc.thePlayer.fallDistance);
        panel.setSize(Math.max(110, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
