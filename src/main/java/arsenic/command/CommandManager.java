package arsenic.command;

import arsenic.module.Module;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class CommandManager {

    private final ArrayList<Command> commands;

    public CommandManager() {
        commands = new ArrayList<>();
    }

    public final int initialize() {
        for (File file : JavaUtils.getFilesFromPackage("arsenic.command.impl")) {
                String className = file.getName().replaceAll(".class$", "");
                Class<?> cls = null;
                try {
                    cls = Class.forName("arsenic.command.impl." + className);
                    if (Command.class.isAssignableFrom(cls)) {
                        add((Command) cls.newInstance());
                    }
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {}
        }
        return commands.size();
    }


    public void executeCommand(String str) {
        PlayerUtils.addWaterMarkedMessageToChat("sent command " + str);
        str.replaceFirst(".", "");
        String name = str.split(" ")[0];
        String[] args = str.substring(name.length() + 1, str.length()).split(" ");
        for(Command command : commands) {
            if(command.isName(name)) {
                command.execute(args);
                break;
            }
        }
    }

    public void add(Command command) {
        commands.add(command);
    }

    public Set<String> getCommands() {
        return commands.stream().map(Command::getName).collect(Collectors.toSet());
    }

    public ArrayList<String> getAutoCompletions(String str) {
        return new ArrayList<String>();
    }

}
