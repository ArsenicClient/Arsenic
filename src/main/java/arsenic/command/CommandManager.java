package arsenic.command;

import arsenic.utils.java.JavaUtils;
import arsenic.utils.minecraft.PlayerUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
        str = str.replaceFirst(".", "");
        String name = str.split(" ")[0];
        String[] args =  str.length() > name.length() ? str.substring(name.length() + 1, str.length()).split(" ") : new String[]{};
        Command command = getCommandByName(name);

        if(command != null) {
            command.execute(args);
            return;
        }

        PlayerUtils.addWaterMarkedMessageToChat("unable to find " + name);
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
    public String getAutoCompletion(String str) {
        str = str.replaceFirst(".", "");
        String name = str.split(" ")[0];
        String[] args =  str.length() > name.length() ? str.substring(name.length() + 1, str.length()).split(" ") : new String[]{};
        if(args.length == 0) {
            return getClosestCommandName(name).replaceFirst(str, "");
        }
        Command command = getCommandByName(name);
        return command == null ? "" : command.getAutoComplete(args[args.length-1], args.length - 1).replaceFirst(args[args.length-1], "");
    }

    public String getClosestCommandName(String name) {
        List<Command> commands = this.commands.stream().filter(c -> c.getName().toLowerCase().startsWith(name.toLowerCase())).collect(Collectors.toList());
        return commands.isEmpty() ? "" : commands.get(0).getName();
    }

    public Command getCommandByName(String name) {
        for(Command command : commands) {
            if(command.isName(name)) {
                return command;
            }
        }
        return null;
    }

}
