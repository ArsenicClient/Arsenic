package arsenic.module.impl.misc;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;

import static net.minecraftforge.fml.common.Loader.instance;

import arsenic.module.property.impl.StringProperty;
import net.minecraftforge.fml.common.ModContainer;

import java.util.HashSet;
import java.util.Set;

@ModuleInfo(name = "Mod Spoofer", category = ModuleCategory.OTHER)
public class ModSpoofer extends Module {
    public final BooleanProperty cancel = new BooleanProperty("Stop Forge from sending Mod List", true);
    @PropertyInfo(reliesOn = "Stop Forge from sending Mod List", value = "false")
    public final StringProperty description = new StringProperty("Toggle mods you want to send to the server.");
    public final Set<BooleanProperty> mods = new HashSet<>();
    @Override
    public void registerProperties() throws IllegalAccessException {
        super.registerProperties();
        for (ModContainer modContainer : instance().getActiveModList()) {
            BooleanProperty setting = new BooleanProperty(modContainer.getModId(), true);
            setting.setVisible(() -> !cancel.getValue());
            registerProperty(setting);
            mods.add(setting);
        }
    }
}
