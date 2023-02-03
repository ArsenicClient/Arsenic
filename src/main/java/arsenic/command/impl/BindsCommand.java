package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;
import org.lwjgl.input.Keyboard;

@CommandInfo(name = "binds", help = "shows all modules bound to a key")
public class BindsCommand extends Command {

    @Override
    public void execute(String[] args) {
        Arsenic.getArsenic().getModuleManager().getModulesMap().values().forEach(module -> {
            if(module.getKeybind() != 0 ) {
                PlayerUtils.addWaterMarkedMessageToChat( module.getName()  + " is bound to " + Keyboard.getKeyName(module.getKeybind()));
            }
        });
    }

}
