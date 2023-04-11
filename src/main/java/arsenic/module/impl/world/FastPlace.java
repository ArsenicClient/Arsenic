package arsenic.module.impl.world;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.item.ItemBlock;

@ModuleInfo(name = "Fastplace", category = ModuleCategory.WORLD)
public class FastPlace extends Module {

    public final DoubleProperty ticks = new DoubleProperty("Tick Delay", new DoubleValue(0, 3, 1, 1));
    public final BooleanProperty blocksOnly = new BooleanProperty("Blocks Only", true);

    public int getTickDelay() {
        if(!blocksOnly.getValue())
            return (int) ticks.getValue().getInput();
        if(mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock)
            return (int) ticks.getValue().getInput();
        return 4;
    }

}
