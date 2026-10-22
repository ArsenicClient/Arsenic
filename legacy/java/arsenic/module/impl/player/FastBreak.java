package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.injection.accessor.IMixinPlayerControllerMp;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.util.MovingObjectPosition;

/** Gives block breaking a head start: skips most of the hit delay and tops up the mining progress. */
@ModuleInfo(name = "FastBreak", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class FastBreak extends Module {

    /** Share of a block that counts as already mined once breaking has begun. */
    public final DoubleProperty speed = new DoubleProperty("Speed (%)", new DoubleValue(1, 100, 15, 1));
    /** Ticks between finishing one block and starting the next (vanilla is 5). */
    public final DoubleProperty delay = new DoubleProperty("Delay (ticks)", new DoubleValue(0, 4, 0, 1));

    @Override
    public String getHudInfo() {
        return (int) speed.getValue().getInput() + "%";
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.playerController == null || mc.playerController.isInCreativeMode())
            return;
        MovingObjectPosition over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return;

        IMixinPlayerControllerMp controller = (IMixinPlayerControllerMp) mc.playerController;
        controller.setBlockHitDelay(Math.min(controller.getBlockHitDelay(), (int) delay.getValue().getInput() + 1));
        if (controller.isHittingBlock()) {
            float head = 0.3f * (float) (speed.getValue().getInput() / 100.0);
            if (controller.getCurBlockDamageMP() < head)
                controller.setCurBlockDamageMP(head);
        }
    };
}
