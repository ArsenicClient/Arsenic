package arsenic.module.impl.ghost;

import arsenic.utils.timer.MSTimer;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRunTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;

@ModuleInfo(name = "Clicker", category = ModuleCategory.COMBAT)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    final MSTimer timer = new MSTimer();
    private long cps,prevCps;
    private final MSTimer soundTimer = new MSTimer();
    private boolean lmbDown;
    private int lastDropTick = -1;
    
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);

    @Override
    public String getHudInfo() {
        return lmbDown ? cps + " cps" : rangeProperty.getValueString();
    }

    private boolean shouldPlayClickSound() {
        if (!mc.gameSettings.keyBindAttack.isKeyDown() || mc.currentScreen != null)
            return false;
        if (mc.playerController != null && ((arsenic.injection.accessor.IMixinPlayerControllerMp) mc.playerController).isHittingBlock())
            return false;
        return mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    @EventLink
    public final Listener<EventLiving> eventLivingListener = e -> {
        lmbDown = mc.gameSettings.keyBindAttack.isKeyDown();
    };
    
    @RequiresPlayer
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventRender2D> eventRunTickListener = e -> {
        if (!lmbDown)
            return;

        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return;

        int tick = mc.thePlayer.ticksExisted;
        if (tick % 12 == 0 && tick != lastDropTick) {
            lastDropTick = tick;
            cps -= (long) JavaUtils.getRandom(1,3);
        }

        if (cps == prevCps) cps -= (long) JavaUtils.getRandom(1,3);

        cps = Math.max(1, cps);
        if (timer.hasTimeElapsed(1000L / cps)) {
            if (shouldPlayClickSound() && soundTimer.finished(80)) {
                SoundUtils.playSound("click");
                soundTimer.reset();
            }
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            KeyBinding.onTick(key);
            prevCps = cps;
            cps = (long) rangeProperty.getValue().getRandomInRange();
            timer.reset();
        }
    };
}
