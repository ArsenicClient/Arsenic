package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.event.bus.EventErrors;
import arsenic.gui.ErrorOverlay;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

/**
 * Reads back what the error overlay is showing, including the full stack trace of the first
 * occurrence - the one the JIT has not yet optimised the trace off.
 */
@CommandInfo(name = "errors", args = { "list/trace/hud/clear", "module name" }, aliases = { "err" },
        help = "shows event listener errors and their stack traces")
public class ErrorsCommand extends Command {

    private final List<String> subCommands = new ArrayList<>(Arrays.asList("list", "trace", "hud", "clear"));

    @Override
    public void execute(String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase();

        switch (sub) {
            case "clear":
                EventErrors.clear();
                PlayerUtils.addWaterMarkedMessageToChat("§acleared");
                break;
            case "hud":
                hud(args.length > 1 ? args[1].toLowerCase() : "");
                break;
            case "trace": {
                if (args.length == 1) {
                    PlayerUtils.addWaterMarkedMessageToChat("§cI need the name of a module");
                    break;
                }
                trace(args[1]);
                break;
            }
            default:
                list();
                break;
        }
    }

    /** Errors are always collected; this only controls whether they are drawn over the game. */
    private void hud(String state) {
        switch (state) {
            case "enable":
            case "on":
                ErrorOverlay.setEnabled(true);
                break;
            case "disable":
            case "off":
                ErrorOverlay.setEnabled(false);
                break;
            case "toggle":
                ErrorOverlay.setEnabled(!ErrorOverlay.isEnabled());
                break;
            default:
                PlayerUtils.addWaterMarkedMessageToChat("error hud is "
                        + (ErrorOverlay.isEnabled() ? "§aenabled" : "§cdisabled")
                        + " §7- use .errors hud enable/disable");
                return;
        }

        Arsenic.getArsenic().getConfigManager().saveClientConfig();
        PlayerUtils.addWaterMarkedMessageToChat("error hud "
                + (ErrorOverlay.isEnabled() ? "§aenabled" : "§cdisabled"));
    }

    private void list() {
        List<EventErrors.Entry> errors = EventErrors.getActive();
        if (errors.isEmpty()) {
            PlayerUtils.addWaterMarkedMessageToChat("no active errors");
            return;
        }

        PlayerUtils.addWaterMarkedMessageToChat("Active errors:");
        for (EventErrors.Entry error : errors) {
            PlayerUtils.addWaterMarkedMessageToChat(" §c" + error.getOwner() + " §7" + error.getEvent()
                    + " §8x" + error.getCount());
            PlayerUtils.addWaterMarkedMessageToChat("   §f" + error.getMessage());
            PlayerUtils.addWaterMarkedMessageToChat("   §8"
                    + (error.getTrace().isEmpty() ? "no trace captured" : ".errors trace " + error.getOwner()));
        }
    }

    private void trace(String owner) {
        for (EventErrors.Entry error : EventErrors.getActive()) {
            if (!error.getOwner().equalsIgnoreCase(owner))
                continue;

            PlayerUtils.addWaterMarkedMessageToChat("§c" + error.getOwner() + " §7- §f" + error.getMessage());
            if (error.getTrace().isEmpty()) {
                PlayerUtils.addWaterMarkedMessageToChat(
                        "§7no trace was captured - every throw so far had one optimised away.");
                PlayerUtils.addWaterMarkedMessageToChat(
                        "§7relaunch with §f-XX:-OmitStackTraceInFastThrow§7 and reproduce it.");
                return;
            }

            for (String frame : error.getTrace())
                PlayerUtils.addWaterMarkedMessageToChat("  §8at §7" + frame);
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat("§cnothing erroring called " + owner);
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        String current = args[args.length - 1];
        if (args.length <= 1)
            return autoCompleteHelper(subCommands, current);

        if (args[0].equalsIgnoreCase("trace")) {
            List<String> owners = new ArrayList<>();
            for (EventErrors.Entry error : EventErrors.getActive())
                owners.add(error.getOwner());
            return autoCompleteHelper(owners, current);
        }
        if (args[0].equalsIgnoreCase("hud"))
            return autoCompleteHelper(new ArrayList<>(Arrays.asList("enable", "disable", "toggle")), current);
        return new ArrayList<>();
    }
}
