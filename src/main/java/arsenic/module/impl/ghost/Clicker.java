package arsenic.module.impl.ghost;

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

@ModuleInfo(name = "Clicker", category = ModuleCategory.COMBAT)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    final MSTimer timer = new MSTimer();
    private long cps,prevCps,lastSound;
    private boolean lmbDown;
    private int lastDropTick = -1;
    
    /**
     * Only autoclick while holding a weapon. Off, the clicker runs on blocks and food too, which
     * is the tell anyone watching actually notices.
     */
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);

    /** The CPS actually being produced, which is the number people are checking a clicker for. */
    @Override
    public String getHudInfo() {
        return lmbDown ? cps + " cps" : rangeProperty.getValueString();
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

        // This listener fires per render frame, so a raw ticksExisted % 12 check is true for
        // every frame of that tick and over-subtracts at high FPS (pinning cps to 1 and killing
        // the clicker). Only apply the drop once per qualifying game tick.
        int tick = mc.player.tickCount;
        if (tick % 12 == 0 && tick != lastDropTick) {
            lastDropTick = tick;
            cps -= (long) JavaUtils.getRandom(1,3);
        }

        if (cps == prevCps) cps -= (long) JavaUtils.getRandom(1,3);

        cps = Math.max(1, cps);
        if (timer.hasTimeElapsed(1000L / cps)) {
            if (System.currentTimeMillis() > lastSound) {
                SoundUtils.playSound("click");
                lastSound = System.currentTimeMillis() + 80;
            }
            //((IMixinMinecraft) mc).leftClick();
            KeyMapping.click(((arsenic.injection.accessor.IMixinKeyMapping) mc.options.keyAttack).getBoundKey());
            prevCps = cps;
            cps = (long) rangeProperty.getValue().getRandomInRange();
            timer.reset();
        }
    };
}
