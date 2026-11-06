package arsenic.module.impl.player;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.ItemUtils;

/** Shortens the right-click use cooldown so blocks (or any item) can be placed faster. */
@ModuleInfo(name = "Fastplace", description = "Allows you to place blocks faster", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class FastPlace extends Module {

    /** Vanilla right-click delay in ticks. */
    private static final int VANILLA_DELAY = 4;

    public final RangeProperty ticks = new RangeProperty("Tick Delay", new RangeValue(0, 4, 0, 4, 1));
    public final BooleanProperty blocksOnly = new BooleanProperty("Blocks Only", true);

    /** The use cooldown to apply after a right-click; rolled per use, never longer than vanilla. */
    public int getTickDelay() {
        if (blocksOnly.getValue() && !isHoldingBlock())
            return VANILLA_DELAY;
        int delay = (int) Math.round(ticks.getValue().getRandomInRange());
        return Math.max(0, Math.min(VANILLA_DELAY, delay));
    }

    private boolean isHoldingBlock() {
        if (mc.player == null)
            return false;
        return ItemUtils.isBlock(mc.player.getMainHandItem()) || ItemUtils.isBlock(mc.player.getOffhandItem());
    }

}
