package arsenic.command;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.minecraft.PlayerUtils;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;
import static org.reflections.scanners.Scanners.SubTypes;

public class CommandManager {

    private final ArrayList<Command> commands;
    private List<String> autoCompletions;
    public CommandManager() {
        commands = new ArrayList<>();
        autoCompletions = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public final int initialize() {
        if (!commands.isEmpty())
            throw new RuntimeException("Double initialization of Command Manager.");

        Reflections reflections = new Reflections("arsenic.command");
        reflections.get(SubTypes.of(Command.class).asClass()).forEach(command -> addCommand((Class<? extends Command>) command));

        return commands.size();
    }

    private void addCommand(Class<? extends Command> commandClass) {
        if (Modifier.isAbstract(commandClass.getModifiers()))
            return;
        try {
            commands.add(commandClass.newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCommandCount() {
        return commands.size();
    }

    public void executeCommand(String str) {
        str = str.substring(1);
        String name = str.split(" ")[0];
        String[] args = str.length() > name.length() ? str.substring(name.length() + 1, str.length()).split(" ") : new String[] {};
        Command command = getCommandByName(name);

        if (command != null) {
            if(command.getMinArgs() < args.length + 1)
                command.execute(args);
            else {
                PlayerUtils.addWaterMarkedMessageToChat(("Insufficient Arguments. Correct usage is:"));
                PlayerUtils.addWaterMarkedMessageToChat((command.getUsage()));
            }
            return;
        }

        // fallback: treat the first token as a module name so you can type
        // ".sprint" to toggle it or ".sprint multiplier 1.5" to change a setting
        if (handleModuleCommand(name, args))
            return;

        PlayerUtils.addWaterMarkedMessageToChat("unable to find " + name);
    }

    private boolean handleModuleCommand(String name, String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(name);
        if (module == null)
            return false;

        if (args.length == 0) {
            module.toggle();
            PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " is now " + (module.isEnabled() ? "§aenabled" : "§cdisabled"));
        } else {
            CommandUtils.PropMatch match = CommandUtils.matchProperty(module, args);
            if (match == null)
                PlayerUtils.addWaterMarkedMessageToChat(module.getName() + " has no setting called '" + args[0] + "'");
            else if (match.valueArgs.length == 0)
                PlayerUtils.addWaterMarkedMessageToChat(CommandUtils.displayName(match.property) + " is currently " + CommandUtils.describe(match.property));
            else
                PlayerUtils.addWaterMarkedMessageToChat(CommandUtils.applyValue(match.property, match.valueArgs));
        }

        Arsenic.getArsenic().getConfigManager().saveConfig();
        return true;
    }

    public void add(Command command) {
        commands.add(command);
    }

    public Set<String> getCommands() { return commands.stream().map(Command::getName).collect(Collectors.toSet()); }

    public void updateAutoCompletions(String str) {
        try {
            str = str.substring(1);
            String name = str.split(" ")[0];
            String[] args = str.length() > name.length() ? str.substring(name.length() + 1, str.length()).split(" ")
                    : new String[]{};
            if (args.length == 0) {
                // still completing the first token: offer command names and module names
                List<String> options = getClosestCommandName(name);
                options.addAll(autoCompleteHelper(Arsenic.getArsenic().getModuleManager().getModules().stream()
                        .map(Module::getName).collect(Collectors.toList()), name));
                setAutoCompletions(options);
                return;
            }
            Command command = getCommandByName(name);
            if (command != null) {
                setAutoCompletions(command.getAutoComplete(args));
                return;
            }
            // no command: maybe it's a module, so complete its settings / values
            setAutoCompletions(getModuleAutoComplete(name, args));
        } catch (ArrayIndexOutOfBoundsException ignored){} //crashes if you try to autocomplete a command that does not exist
    }

    private List<String> getModuleAutoComplete(String name, String[] args) {
        Module module = Arsenic.getArsenic().getModuleManager().getModuleByName(name);
        if (module == null)
            return new ArrayList<>();

        String current = args[args.length - 1];
        if (args.length == 1)
            return autoCompleteHelper(CommandUtils.getSettingNames(module), current);

        // value position: suggest enum modes / booleans for the setting named in args[0]
        CommandUtils.PropMatch match = CommandUtils.matchProperty(module, new String[] { args[0] });
        return match != null ? autoCompleteHelper(CommandUtils.valueSuggestions(match.property), current) : new ArrayList<>();
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
