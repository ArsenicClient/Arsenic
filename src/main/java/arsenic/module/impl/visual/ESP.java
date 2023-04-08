package arsenic.module.impl.visual;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.ColourProperty;

@ModuleInfo(name = "Esp", category = ModuleCategory.WORLD)
public class ESP extends Module {

    public ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);

}
