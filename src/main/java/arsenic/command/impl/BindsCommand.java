package arsenic.command.impl;

import arsenic.utils.io.Keys;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

@CommandInfo(name = "binds", help = "shows all modules bound to a key")
public class BindsCommand extends Command {

    @Override
    public void execute(String[] args) {
        Arsenic.getArsenic().getModuleManager().getModules().forEach(module -> {
            if (module.getKeybind() != 0) {
                PlayerUtils.addWaterMarkedMessageToChat(
                        module.getName() + " is bound to " + Keys.getKeyName(module.getKeybind()));
            }
        });
    }

}
