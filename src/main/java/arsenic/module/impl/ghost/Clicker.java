package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventDisplayGuiScreen;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventRunTick;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.timer.MSTimer;
import arsenic.utils.timer.Timer;
import net.minecraft.block.material.Material;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;

import java.util.concurrent.ExecutorService;

@ModuleInfo(name = "Clicker", category = ModuleCategory.GHOST)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    public final BooleanProperty drop = new BooleanProperty("Drop Cps", true);
    public final BooleanProperty playSound = new BooleanProperty("Click Sound", true);
    final MSTimer timer = new MSTimer();
    private long cps,prevCps,lastSound;

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
                    new Thread(() -> SoundUtils.playSound("click")).start();
                    lastSound = System.currentTimeMillis() + 80;
                }
            }
            click();
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

    private void click() {
        mc.thePlayer.swingItem();
        switch (mc.objectMouseOver.typeOfHit) {
            case ENTITY:
                mc.playerController.attackEntity(mc.thePlayer, mc.objectMouseOver.entityHit);
                break;
            case BLOCK:
                BlockPos blockpos = mc.objectMouseOver.getBlockPos();

                if (mc.theWorld.getBlockState(blockpos).getBlock().getMaterial() != Material.air) {
                    mc.playerController.clickBlock(blockpos, mc.objectMouseOver.sideHit);
                    break;
                }
            case MISS:
            default:
        }
    }
}