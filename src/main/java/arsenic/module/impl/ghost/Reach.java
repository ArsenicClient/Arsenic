package arsenic.module.impl.ghost;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;

@ModuleInfo(name = "Reach", category = ModuleCategory.GHOST)
public class Reach extends Module {

    public final RangeProperty reach = new RangeProperty("Reach", new RangeValue(3, 6, 3, 3.1, 0.05));


    public double getReach() {
        return isEnabled() ? reach.getValue().getRandomInRange() : 3.0;
    }
}