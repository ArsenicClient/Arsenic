package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.*;
import java.security.SecureRandom;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {

    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));
    public final RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 100, 20, 50,1));

    public EntityPlayer target = null;
    private final MSTimer attackTimer = new MSTimer();

    @Override
    protected void onEnable() {
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (!canAura()) return;
        float[] rots = RotationUtils.getRotationsToEntity(target); //smoothing is already done in rotation manager.
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed((float) speed.getValue().getRandomInRange());
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        target = TargetManager.getTarget();
        if (!canAura()) return;

        if (target != null) {
            if (mc.thePlayer.canEntityBeSeen(target)) {
                if (attackTimer.hasTimeElapsed(getAttackDelay())) {
                    attack();
                    attackTimer.reset();
                }
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (!canAura()) {
            return;
        }
        RenderUtils.drawCircle(target, event.partialTicks, 0.7, Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor(), 255);
    };

    public void attack() {
        if (!canAura()) return;
        if (RotationUtils.getDistanceToEntityBox(target) <= 3) {
            swing();
            mc.playerController.attackEntity(mc.thePlayer, target);
        }
    }

    private void swing() {
        mc.thePlayer.swingItem();
    }


    private long getAttackDelay() {
        double x = aps.getValue().getMax();
        double y = aps.getValue().getMin();
        float finalValue = getRandom((float) x, (float) y) + 6;
        return (long) (1000L / finalValue);
    }

    private boolean canAura() {
        return target != null && !Arsenic.getInstance().getModuleManager().getModuleByClass(Scaffold.class).isEnabled();
    }

    public static float getRandom(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }
}
