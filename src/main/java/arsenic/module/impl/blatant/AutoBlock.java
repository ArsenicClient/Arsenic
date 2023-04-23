package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;

@ModuleInfo(name = "Autoblock", category = ModuleCategory.BLATANT)
public class AutoBlock extends Module {

    public EnumProperty<bMode> blockMode = new EnumProperty<>("Mode: ", bMode.HYPIXEL);

    public boolean block;


    @EventLink
    public final Listener<EventUpdate.Post> eventUpdateListener =  event -> {
        if(blockMode.getValue() == bMode.HYPIXEL) {

        }
    };

    public boolean shouldBlock() {
        return block;
    }

    public enum bMode {
        VANILLA,
        HYPIXEL,

    }
}
