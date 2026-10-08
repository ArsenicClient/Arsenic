import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;

import java.util.Locale;

/**
 * Shows the distance to the last entity you attacked, measured to its hitbox when you attacked it. Shows the last
 * distance until you attack something else.
 */
@ModuleInfo(name = "ReachDisplay", description = "Shows the distance to the last entity you attacked", category = ModuleCategory.RENDER)
public class ReachDisplay extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("ReachDisplay", 4, 580, 130, 16);
    private double last = -1;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity target = event.getTarget();
        if (target != null) last = RotationUtils.getDistanceToEntityBox(target);
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || last < 0) return;
        String text = String.format(Locale.ROOT, "Reach %.2f", last);
        panel.setSize(Math.max(130, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
