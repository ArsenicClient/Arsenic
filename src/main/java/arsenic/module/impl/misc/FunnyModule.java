package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.entity.Entity;

import java.util.EventListener;

@ModuleInfo(name = "FunnyModule", category = ModuleCategory.OTHER)
public class FunnyModule extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 5, 3, 0.1));
    public final DoubleProperty hurtTicks = new DoubleProperty("Ticks", new DoubleValue(0, 10, 1, 1));


    @Override
    protected void onEnable() {
        Arsenic.getArsenic().getModuleManager().getModuleByClass(Blink.class).setEnabled(false);
        PlayerUtils.addWaterMarkedMessageToChat("Don't manually enable/disable blink pls");
    }

    @EventLink
    public Listener<EventTick> eventTickListener = event -> {
        Blink blink = Arsenic.getArsenic().getModuleManager().getModuleByClass(Blink.class);
        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());
        if(target == null) {
            blink.setEnabled(false);
            return;
        }
        blink.setEnabled(target.hurtResistantTime > hurtTicks.getValue().getInput());
    };
}
