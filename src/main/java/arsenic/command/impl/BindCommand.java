package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventKey;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;
import org.lwjgl.input.Keyboard;

@CommandInfo(name = "bind", args = {"name", "key"}, aliases = {"b"}, help = "binds a module to a key")
public class BindCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if(module != null) {
            PlayerUtils.addWaterMarkedMessageToChat("Binded " + module.getName() + " to " + args[1]);
            module.setKeybind(Keyboard.getKeyIndex(args[1]));
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat( args[0] + " is not a valid module");
    }
}
