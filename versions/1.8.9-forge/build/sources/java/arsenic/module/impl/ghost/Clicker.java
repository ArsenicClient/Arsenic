package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;

@ModuleInfo(name = "Clicker", category = ModuleCategory.GHOST)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    public final BooleanProperty drop = new BooleanProperty("Drop Cps", true);
    public final BooleanProperty playSound = new BooleanProperty("Click Sound", true);
    final MSTimer timer = new MSTimer();
    private long cps,prevCps,lastSound;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventRunTickListener = e -> {
        BlockHit blockHit = Arsenic.getArsenic().getModuleManager().getModuleByClass(BlockHit.class);
        if (!mc.gameSettings.keyBindAttack.isKeyDown() ||
                (mc.gameSettings.keyBindUseItem.isKeyDown() &&
                        (!blockHit.isEnabled() || !blockHit.isAutoblock()))) return;

        if (drop.getValue()) {
            if (mc.thePlayer.ticksExisted % 12 == 0) {
                cps -= (long) JavaUtils.getRandom(1,3);
            }
        }
        if (cps == prevCps) cps -= (long) JavaUtils.getRandom(1,3);

        if (timer.hasTimeElapsed(1000L / cps)) {
            if (playSound.getValue()){
                if (System.currentTimeMillis() > lastSound) {
                    new Thread(() -> SoundUtils.playSound("click")).start();
                    lastSound = System.currentTimeMillis() + 80;
                }
            }
            PlayerUtils.click();
            prevCps = cps;
            randomize();
            timer.reset();
        }
    };

    private void randomize() {
        cps = (long) JavaUtils.getRandom(rangeProperty.getValue().getMin(),rangeProperty.getValue().getMax()) + 6;
    }

    @Override
    protected void onEnable() {
        randomize();
    }
}