package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRunTick;
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
import arsenic.injection.accessor.IMixinMinecraft;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;

@ModuleInfo(name = "Clicker", category = ModuleCategory.GHOST)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    public final BooleanProperty drop = new BooleanProperty("Drop Cps", true);
    public final BooleanProperty playSound = new BooleanProperty("Click Sound", true);
    final MSTimer timer = new MSTimer();
    private long cps,prevCps,lastSound;
    private boolean breakHeld;

    @RequiresPlayer
    @EventLink
    public final Listener<EventRunTick> eventRunTickListener = e -> {
        if (!mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown()) return;

        if (drop.getValue()) {
            if (mc.thePlayer.ticksExisted % 12 == 0) {
                cps -= (long) JavaUtils.getRandom(1,3);
            }
        }
        if (cps == prevCps) cps -= (long) JavaUtils.getRandom(1,3);

        if (timer.hasTimeElapsed(1000L / cps)) {
            if (playSound.getValue()){
                if (System.currentTimeMillis() > lastSound) {
                    SoundUtils.playSound("click");
                    lastSound = System.currentTimeMillis() + 80;
                }
            }
            ((IMixinMinecraft) mc).leftClick();
            prevCps = cps;
            randomize();
            timer.reset();
        }
    };

    private void randomize() {
        cps = (long) JavaUtils.getRandom(rangeProperty.getValue().getMin(),rangeProperty.getValue().getMax());
    }

    @Override
    protected void onEnable() {
        randomize();
    }
}
