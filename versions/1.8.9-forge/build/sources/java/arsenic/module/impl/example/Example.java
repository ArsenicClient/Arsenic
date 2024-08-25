package arsenic.module.impl.example;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.ButtonProperty;
import arsenic.module.property.impl.StringProperty;


@ModuleInfo(name = "Example", category = ModuleCategory.SETTINGS)
public class Example extends Module {


    ButtonProperty button = new ButtonProperty("Click me");

}
