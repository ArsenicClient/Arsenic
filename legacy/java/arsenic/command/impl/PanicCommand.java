package arsenic.command.impl;

import java.util.Collection;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "panic", aliases = { "off" }, help = "disables every enabled module")
public class PanicCommand extends Command {

    @Override
    public void execute(String[] args) {
        // getEnabledModules() returns a fresh list, so disabling while iterating is safe
        Collection<Module> enabled = Arsenic.getArsenic().getModuleManager().getEnabledModules();
        int count = enabled.size();
        enabled.forEach(module -> module.setEnabled(false));
        PlayerUtils.addWaterMarkedMessageToChat("Panic! Disabled " + count + " module" + (count == 1 ? "" : "s"));
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }
}
