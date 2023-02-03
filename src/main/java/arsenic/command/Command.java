package arsenic.command;

import arsenic.module.ModuleInfo;
import arsenic.utils.java.JavaUtils;

import java.util.Arrays;

public class Command {

    protected String name;
    protected String help;
    protected String[] aliases;
    protected String[] args;

    public Command() {
        if (!this.getClass().isAnnotationPresent(CommandInfo.class))
            throw new IllegalArgumentException("No @ModuleInfo on class " + this.getClass().getCanonicalName());

        final CommandInfo info = this.getClass().getDeclaredAnnotation(CommandInfo.class);

        name = info.name();
        help = info.help();
        aliases = info.aliases();
        args = info.args();
    }

    public void execute(String[] args) {

    }

    public String getAutoComplete(String str, int arg) {
        return "";
    }

    public String getName() {
        return name;
    }
    public String getHelp(){return help;}
    public String[] getAliases(){return aliases;}
    public String[] getArgs(){return args;}

    public boolean isName(String name) {
        for (final String alias : JavaUtils.concat(aliases, new String[] { this.name }))
          if (alias.equalsIgnoreCase(name)) return true;
        return false;
    }
}
