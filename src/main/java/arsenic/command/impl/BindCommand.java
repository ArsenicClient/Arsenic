package arsenic.command.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.input.Keyboard;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "bind", args = { "name", "key" }, aliases = { "b" }, help = "binds a module to a key", minArgs = 2)
public class BindCommand extends Command {

    @Override
    public void execute(String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(args[0]);
        if (module == null) {
            PlayerUtils.addWaterMarkedMessageToChat(args[0] + " is not a valid module");
            return;
        }
        int bind = Keyboard.getKeyIndex(args[1].toUpperCase());
        PlayerUtils.addWaterMarkedMessageToChat("Bound " + module.getName() + " to " + Keyboard.getKeyName(bind));
        module.setKeybind(Keyboard.getKeyIndex(args[1].toUpperCase()));

        Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    @Override
    public List<String> getAutoComplete(String str, int arg, List<String> list) {
        if (arg == 0)
            return autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getModules().stream().map(Module::getName).collect(Collectors.toList()), str);
        if (arg == 1)
            return autoCompleteHelper(keyNames(), str);
        return list;
    }

    private static List<String> keyNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Keyboard.KEYBOARD_SIZE; i++) {
            String name = Keyboard.getKeyName(i);
            if (name != null)
                names.add(name);
        }
        return names;
    }
}
