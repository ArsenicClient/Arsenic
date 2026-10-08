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
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Counts the hits you land and the hits you take in the current fight. The counts reset after a quiet period with no
 * hits either way. A landed hit is an attack whose target is hurt on one of the next ticks; a hit taken is a rise in
 * your own hurt time.
 */
@ModuleInfo(name = "HitCounter", description = "Counts the hits you land and take in the current fight", category = ModuleCategory.RENDER)
public class HitCounter extends Module {

    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    public final DoubleProperty resetAfter = new DoubleProperty("Reset After (s)", new DoubleValue(5, 60, 10, 1));

    private final HudElement panel = hudElement("HitCounter", 4, 60, 110, 16);
    private final MSTimer quiet = new MSTimer();
    private final MSTimer pendingTimer = new MSTimer();
    private Entity pending;
    private int hitsLanded, hitsTaken, lastHurtTime;

    @Override
    protected void onEnable() {
        hitsLanded = 0;
        hitsTaken = 0;
        pending = null;
        lastHurtTime = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        pending = event.getTarget();
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (pending != null) {
            if (pending instanceof EntityLivingBase && ((EntityLivingBase) pending).hurtTime > 0) {
                hitsLanded++;
                quiet.reset();
                pending = null;
            } else if (pendingTimer.hasTimeElapsed(200)) {
                pending = null;
            }
        }

        int hurt = mc.thePlayer.hurtTime;
        if (hurt > lastHurtTime) {
            hitsTaken++;
            quiet.reset();
        }
        lastHurtTime = hurt;

        if ((hitsLanded > 0 || hitsTaken > 0) && quiet.hasTimeElapsed((long) (resetAfter.getValue().getInput() * 1000))) {
            hitsLanded = 0;
            hitsTaken = 0;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        String text = "Hits " + hitsLanded + "   Taken " + hitsTaken;
        panel.setSize(Math.max(90, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
