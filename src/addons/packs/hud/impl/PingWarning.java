import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.render.DrawUtils;

/**
 * HUD line with your ping, red while it is above the limit. Uses the client's own ping measurement (LagManager).
 */
@ModuleInfo(name = "PingWarning", description = "Shows your ping and warns when it goes over a limit", category = ModuleCategory.RENDER)
public class PingWarning extends Module {

    public final DoubleProperty limit = new DoubleProperty("Limit (ms)", new DoubleValue(50, 500, 150, 10));
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("PingWarning", 4, 240, 110, 16);

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        int ping = LagManager.getPing();
        boolean high = ping > limit.getValue().getInput();
        String text = "Ping " + ping + " ms";
        panel.setSize(Math.max(110, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, high ? 0xC0AA1010 : 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
