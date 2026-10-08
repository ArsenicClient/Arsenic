import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Plays the client's hit confirm sound when one of your attacks lands, and a level-up sound when the target dies from
 * it. Uses the same landed-hit check as HitMarker: the target is hurt on one of the next ticks.
 */
@ModuleInfo(name = "HitSound", description = "Plays a sound when one of your hits lands, and another on a kill", category = ModuleCategory.RENDER)
public class HitSound extends Module {

    public final BooleanProperty killSound = new BooleanProperty("Kill Sound", true);

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
            EntityLivingBase target = (EntityLivingBase) pending;
            SoundUtils.hitConfirm();
            if (killSound.getValue() && (target.isDead || target.getHealth() <= 0)) {
                mc.thePlayer.playSound("random.levelup", 0.5f, 1.4f);
            }
            pending = null;
        } else if (pendingTimer.hasTimeElapsed(200)) {
            pending = null;
        }
    };
}
