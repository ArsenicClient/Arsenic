package arsenic.module.impl.blatant;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;

@ModuleInfo(name = "NoSlow", category = ModuleCategory.BLATANT)
public class NoSlow extends Module {

    public EnumProperty<sMode> slowMode = new EnumProperty<>("Mode: ", sMode.VANILLA);

    private boolean slow = true;

    public boolean shouldNotSlow() {
        return slow;
    }

    public enum sMode {
        VANILLA,
    }
}
