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
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.HitResult;

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
        if (!mc.options.keyAttack.isDown() || mc.gui.screen() != null)
            return false;
        if (mc.gameMode != null && mc.gameMode.isDestroying())
            return false;
        return mc.hitResult == null
                || mc.hitResult.getType() != HitResult.Type.BLOCK;
    }

    @EventLink
    public final Listener<EventLiving> eventLivingListener = e -> {
        lmbDown = mc.options.keyAttack.isDown();
    };
    
    @RequiresPlayer
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventRender2D> eventRunTickListener = e -> {
        if (!lmbDown)
            return;

        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return;

        int tick = mc.player.tickCount;
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
            KeyMapping.click(((arsenic.injection.accessor.IMixinKeyMapping) mc.options.keyAttack).getBoundKey());
            prevCps = cps;
            cps = (long) rangeProperty.getValue().getRandomInRange();
            timer.reset();
        }
    };
}
