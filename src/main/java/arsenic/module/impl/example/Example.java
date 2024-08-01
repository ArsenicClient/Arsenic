package arsenic.module.impl.example;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.StringProperty;


@ModuleInfo(name = "Example", category = ModuleCategory.SETTINGS)
public class Example extends Module {


    @Override
    public void registerProperties() throws IllegalAccessException {
        super.registerProperties();
        for(int i = 0; i < 5; i++)
            registerProperty(new StringProperty("Property:" + i));

    }
}
