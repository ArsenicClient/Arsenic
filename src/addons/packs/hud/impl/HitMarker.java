import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Flashes a small cross on the crosshair when one of your attacks lands. An attack counts as landed when the target
 * is hurt on one of the next ticks (its hurt time goes above zero).
 */
@ModuleInfo(name = "HitMarker", description = "Flashes a cross on the crosshair when one of your hits lands", category = ModuleCategory.RENDER)
public class HitMarker extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty duration = new DoubleProperty("Duration", new DoubleValue(50, 800, 250, 10));

    private final MSTimer flash = MSTimer.expired();
    private final MSTimer pendingTimer = new MSTimer();
    private Entity pending;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        pending = event.getTarget();
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (pending == null) return;
        if (pending instanceof EntityLivingBase && ((EntityLivingBase) pending).hurtTime > 0) {
            flash.reset();
            pending = null;
        } else if (pendingTimer.hasTimeElapsed(200)) {
            pending = null;
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!render.getValue() || mc.thePlayer == null) return;
        long age = flash.getTime();
        long length = (long) duration.getValue().getInput();
        if (age >= length) return;

        float alpha = 1f - (float) age / length;
        ScaledResolution sr = event.getSr();
        float cx = sr.getScaledWidth() / 2f;
        float cy = sr.getScaledHeight() / 2f;
        int color = ColorUtils.withAlpha(Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), alpha);

        // four arms with a gap in the middle
        DrawUtils.drawRect(cx - 7, cy - 1, cx - 3, cy + 1, color);
        DrawUtils.drawRect(cx + 3, cy - 1, cx + 7, cy + 1, color);
        DrawUtils.drawRect(cx - 1, cy - 7, cx + 1, cy - 3, color);
        DrawUtils.drawRect(cx - 1, cy + 3, cx + 1, cy + 7, color);
    };
}
