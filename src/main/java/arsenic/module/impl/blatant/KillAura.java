package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

import java.security.SecureRandom;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50, 1));
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));

    public EntityPlayer target = null;
    private boolean wasUsingItem;
    private Entity pendingAttack;       // entity to swing at on the next movement update

    private final MSTimer attackTimer = new MSTimer();

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    private void resetState() {
        target = null;
        pendingAttack = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        target = TargetManager.getTarget();
        if (target == null)
            return;
        float[] rots = RotationUtils.getRotationsToEntity(target); // smoothing is handled by the rotation manager
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed((float) speed.getValue().getRandomInRange());
    };

    /**
     * Detection. Runs after the silent rotation has settled, so the raytrace reflects where we are
     * actually looking this tick. The swing itself is deferred to {@link EventUpdate.Pre} so the
     * attack packet trails the committed look rotation.
     */
    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventPostListener = event -> {
        boolean usingItem = mc.thePlayer.isUsingItem();
        MovingObjectPosition raytrace = event.getRayTraceEntity();
        boolean inRange = target != null
                && raytrace != null
                && raytrace.entityHit != null
                && RotationUtils.getDistanceToEntityBox(raytrace.entityHit) <= 3;

        if (inRange && attackTimer.hasTimeElapsed(getAttackDelay()) && !usingItem && !wasUsingItem)
            pendingAttack = raytrace.entityHit;

        wasUsingItem = usingItem;
    };

    /**
     * The swing fires here, at the movement-update pre-hook: this is the point the tick's outgoing
     * rotation is committed, so the attack lands with the same angle the server sees rather than
     * a sub-tick early.
     */
    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdatePre = event -> {
        if (pendingAttack == null)
            return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, pendingAttack);
        pendingAttack = null;
        attackTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (target == null)
            return;
        RenderUtils.drawCircle(target, event.partialTicks, 0.7, Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor(), 255);
        RenderUtils.resetColor();
    };

    private long getAttackDelay() {
        double x = aps.getValue().getMax();
        double y = aps.getValue().getMin();
        float finalValue = getRandom((float) x, (float) y) + 6;
        return (long) (1000L / finalValue);
    }

    public static float getRandom(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }
}
