package arsenic.command;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import arsenic.utils.java.JavaUtils;
import arsenic.utils.minecraft.PlayerUtils;

public class CommandManager {

    private final ArrayList<Command> commands;
    private List<String> autoCompletions;
    private String latestAutoCompletion = "";

    public CommandManager() {
        commands = new ArrayList<>();
        autoCompletions = new ArrayList<>();
    }

    public final int initialize() {
        for (File file : JavaUtils.getFilesFromPackage("arsenic.command.impl")) {
            String className = file.getName().replaceAll(".class$", "");
            Class<?> cls = null;
            try {
                cls = Class.forName("arsenic.command.impl." + className);
                if (Command.class.isAssignableFrom(cls)) { add((Command) cls.newInstance()); }
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            }
        }
        return commands.size();
    }

    public void executeCommand(String str) {
        str = str.replaceFirst(".", "");
        String name = str.split(" ")[0];
        String[] args = str.length() > name.length() ? str.substring(name.length() + 1, str.length()).split(" ")
                : new String[] {};
        Command command = getCommandByName(name);

        if (command != null) {
            command.execute(args);
            return;
        }

        PlayerUtils.addWaterMarkedMessageToChat("unable to find " + name);
    }

    public void add(Command command) {
        commands.add(command);
    }

    public Set<String> getCommands() { return commands.stream().map(Command::getName).collect(Collectors.toSet()); }

    public void updateAutoCompletions(String str) {
        str = str.replaceFirst(".", "");
        String name = str.split(" ")[0];
        String[] args = str.length() > name.length() ? str.substring(name.length() + 1, str.length()).split(" ")
                : new String[] {};
        if (args.length == 0) {
            setAutoCompletions(getClosestCommandName(name));
            return;
        }
        Command command = getCommandByName(name);
        if (command == null) {
            autoCompletions.clear();
            return;
        }
        setAutoCompletions(command.getAutoComplete(args[args.length - 1], args.length - 1));
    }

    // sorts alphabetically
    private void setAutoCompletions(List<String> list) {
        list.sort(Comparator.naturalOrder());
        autoCompletions = list;
    }

    public String getAutoCompletionWithoutRotation() {
        if (autoCompletions.isEmpty()) { return ""; }
        return autoCompletions.get(0);
    }

    public String getAutoCompletion() {
        if (autoCompletions.isEmpty()) { return ""; }
        Collections.rotate(autoCompletions, -1);
        return autoCompletions.get(autoCompletions.size() - 1);
    }

    public List<String> getClosestCommandName(String name) {
        return commands.stream().filter(
                c -> c.getName().toLowerCase().startsWith(name.toLowerCase()) && c.getName().length() > name.length())
                .map(Command::getName).collect(Collectors.toList());
    }

    public Command getCommandByName(String name) {
        for (Command command : commands) { if (command.isName(name)) { return command; } }
        return null;
    }
}
