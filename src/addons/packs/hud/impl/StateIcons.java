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

import java.util.ArrayList;
import java.util.List;

/**
 * A row of small labels for what you are doing right now: Sprint, Sneak, Block, Use, Air. Each label is only shown while
 * the state is on. Text labels rather than icons, so they need no textures.
 */
@ModuleInfo(name = "StateIcons", description = "Small labels for sprinting, sneaking, blocking, using and airborne", category = ModuleCategory.RENDER)
public class StateIcons extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("StateIcons", 4, 520, 150, 16);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        List<String> labels = new ArrayList<>();
        if (mc.thePlayer.isSprinting()) labels.add("Sprint");
        if (mc.thePlayer.isSneaking()) labels.add("Sneak");
        if (mc.thePlayer.isBlocking()) labels.add("Block");
        if (mc.thePlayer.isUsingItem()) labels.add("Use");
        if (!mc.thePlayer.onGround) labels.add("Air");
        if (labels.isEmpty()) return;

        StringBuilder text = new StringBuilder();
        for (String l : labels) text.append(l).append("  ");
        String shown = text.toString().trim();
        panel.setSize(Math.max(60, mc.fontRendererObj.getStringWidth(shown) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(shown, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
