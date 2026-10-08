
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import net.minecraft.item.ItemBow;

/**
 * Shows the charge of a bow while you hold right click with it: a percentage that reaches 100 after the full draw
 * (20 ticks). Hidden otherwise.
 */
@ModuleInfo(name = "BowCharge", description = "Shows the bow charge percentage while drawing", category = ModuleCategory.RENDER)
public class BowCharge extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("BowCharge", 4, 460, 110, 16);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || !mc.thePlayer.isUsingItem()) return;
        if (!(mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)) return;
        int percent = (int) Math.min(100, mc.thePlayer.getItemInUseDuration() * 100 / 20);
        String text = "Bow " + percent + "%";
        panel.setSize(Math.max(110, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, percent == 100 ? 0xFF55FF55 : 0xFFFFFFFF);
    };
}
