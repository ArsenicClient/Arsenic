package arsenic.module.impl.ghost;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;

@ModuleInfo(name = "Reach", category = ModuleCategory.GHOST)
public class Reach extends Module {

    public final DoubleProperty reach = new DoubleProperty("Reach", new DoubleValue(3, 6, 3, 0.1));


    public double getReach() {
        return isEnabled() ? reach.getValue().getInput() : 3.0;
    }
}
