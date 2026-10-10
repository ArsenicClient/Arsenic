import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Takes a screenshot (saved in the game's screenshots folder) when you get a kill, when you die, or both. The screenshot
 * is taken on the next frame, so it shows what you see at that moment, and the file name is reported in chat.
 */
@ModuleInfo(name = "AutoScreenshot", description = "Takes a screenshot on a kill or on your death", category = ModuleCategory.RENDER)
public class AutoScreenshot extends Module {

    public final BooleanProperty onKill = new BooleanProperty("On Kill", true);
    public final BooleanProperty onDeath = new BooleanProperty("On Death", true);

    private EntityLivingBase pending;
    private final MSTimer pendingTimer = new MSTimer();
    private boolean shotPending, wasDead;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        Entity e = event.getTarget();
        if (!(e instanceof EntityLivingBase)) return;
        pending = (EntityLivingBase) e;
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean dead = mc.thePlayer.getHealth() <= 0 || mc.thePlayer.isDead;
        if (dead && !wasDead && onDeath.getValue()) shotPending = true;
        wasDead = dead;

        if (pending != null) {
            if (pending.hurtTime > 0) {
                if (onKill.getValue() && (pending.isDead || pending.getHealth() <= 0)) shotPending = true;
                pending = null;
            } else if (pendingTimer.hasTimeElapsed(200)) {
                pending = null;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!shotPending) return;
        shotPending = false;
        String result = ScreenShotHelper.saveScreenshot(mc.mcDataDir, mc.displayWidth, mc.displayHeight, mc.getFramebuffer())
                .getUnformattedText();
        PlayerUtils.addWaterMarkedMessageToChat(result);
    };
}
