package arsenic.module.impl.player;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import net.minecraft.world.item.BlockItem;

@ModuleInfo(name = "Fastplace", category = ModuleCategory.PLAYER)
public class FastPlace extends Module {

    public final RangeProperty ticks = new RangeProperty("Tick Delay", new RangeValue(0, 4, 0, 4,1));

    public int getTickDelay() {
        if(!true)
            return (int) ticks.getValue().getRandomInRange();
        if(mc.player.getMainHandItem() != null && mc.player.getMainHandItem().getItem() instanceof BlockItem)
            return (int) ticks.getValue().getRandomInRange();
        return 4;
    }

}
