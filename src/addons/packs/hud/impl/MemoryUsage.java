import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;

/**
 * Memory the Java process is using out of the most it may use, in megabytes. A rising number over a long session can
 * show a leak in the client or an addon.
 */
@ModuleInfo(name = "MemoryUsage", description = "Shows the memory the game is using, in megabytes", category = ModuleCategory.RENDER)
public class MemoryUsage extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("MemoryUsage", 4, 600, 150, 16);

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        String text = "RAM " + used + " / " + max + " MB";
        panel.setSize(Math.max(150, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
