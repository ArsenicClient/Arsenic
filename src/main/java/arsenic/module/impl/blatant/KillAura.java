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
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.player.EntityPlayer;

import java.security.SecureRandom;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {

    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));
    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 100, 20, 50,1));
    public EntityPlayer target = null;
    private final MSTimer attackTimer = new MSTimer();
    private final ServerInfo serverInfo = Arsenic.getArsenic().getServerInfo();
    private boolean wasUsingItem = false;


    @Override
    protected void onEnable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (target == null)
            return;
        float[] rots = RotationUtils.getRotationsToEntity(target); //smoothing is already done in rotation manager.
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(360f);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        boolean usingItem = mc.thePlayer.isUsingItem();

        target = TargetManager.getTarget();
        if (target != null
                && mc.thePlayer.canEntityBeSeen(target)
                && attackTimer.hasTimeElapsed(getAttackDelay())
                && !usingItem
                && !wasUsingItem // skip the tick RELEASE_USE_ITEM was sent
                && RotationUtils.getDistanceToEntityBox(target) <= 3) {
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, target);
            attackTimer.reset();
        }

        wasUsingItem = usingItem;
    };
    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(target == null)
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
