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

import java.util.Locale;

/**
 * Shows how hard the last hit you took pushed you and the last one you gave pushed the target, as the horizontal speed
 * the victim had on the tick after the hit (blocks per tick). Your own movement adds to the taken value, so jumping or
 * sprinting during a hit makes it bigger than the knockback alone.
 */
@ModuleInfo(name = "KnockbackMeter", description = "Shows how hard the last hit you took and the last one you gave pushed", category = ModuleCategory.RENDER)
public class KnockbackMeter extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("KnockbackMeter", 4, 420, 150, 30);
    private int lastHurt;
    private boolean takenPending;
    private EntityLivingBase target;
    private boolean givenPending;
    private double taken = -1, given = -1;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity e = event.getTarget();
        if (e instanceof EntityLivingBase) {
            target = (EntityLivingBase) e;
            givenPending = true;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        int hurt = mc.thePlayer.hurtTime;
        if (hurt > lastHurt) takenPending = true;
        lastHurt = hurt;
        if (takenPending) {
            taken = Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
            takenPending = false;
        }
        if (givenPending && target != null) {
            if (target.hurtTime > 0) {
                given = Math.hypot(target.motionX, target.motionZ);
                givenPending = false;
                target = null;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        String[] rows = {
                String.format(Locale.ROOT, "Taken  %s", taken < 0 ? "-" : String.format(Locale.ROOT, "%.3f", taken)),
                String.format(Locale.ROOT, "Given  %s", given < 0 ? "-" : String.format(Locale.ROOT, "%.3f", given))
        };
        int width = 150;
        for (String r : rows) width = Math.max(width, mc.fontRendererObj.getStringWidth(r) + 12);
        panel.setSize(width, 6 + rows.length * 10);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int y = panel.y + 4;
        for (String r : rows) {
            mc.fontRendererObj.drawStringWithShadow(r, panel.x + 6, y, 0xFFFFFFFF);
            y += 10;
        }
    };
}
