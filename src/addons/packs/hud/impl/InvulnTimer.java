import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Shows how long the entity you last hit stays invulnerable: the hurt-resistant ticks left, in seconds. Hidden when it
 * is zero, and when the target is gone. In 1.8 a target cannot take another hit until this runs out, so this is the
 * time to wait for the next hit to count.
 */
@ModuleInfo(name = "InvulnTimer", description = "Shows the invulnerability time left on the entity you last hit", category = ModuleCategory.RENDER)
public class InvulnTimer extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("InvulnTimer", 4, 200, 120, 16);
    private EntityLivingBase target;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity e = event.getTarget();
        target = e instanceof EntityLivingBase ? (EntityLivingBase) e : null;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (target != null && (target.isDead || target.worldObj != mc.theWorld)) target = null;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || target == null || target.hurtResistantTime <= 0) return;
        String text = String.format("Invuln %.2fs", target.hurtResistantTime / 20.0);
        panel.setSize(Math.max(120, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
