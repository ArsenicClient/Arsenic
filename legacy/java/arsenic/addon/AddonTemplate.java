package arsenic.addon;

import arsenic.module.ModuleCategory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Scaffolds a ready-to-edit addon so nobody has to remember the boilerplate. */
public final class AddonTemplate {

    private AddonTemplate() {}

    public static boolean isValidName(String name) {
        return name.matches("[A-Za-z][A-Za-z0-9]*");
    }

    /** @return the created file, or throws IllegalArgumentException / IOException with a user-facing message. */
    public static File create(File dir, String name, ModuleCategory category) throws IOException {
        if (!isValidName(name))
            throw new IllegalArgumentException("Addon names must be letters and digits only, starting with a letter");
        if (!dir.isDirectory() && !dir.mkdirs())
            throw new IOException("Could not create " + dir);

        File file = new File(dir, name + ".java");
        if (file.exists())
            throw new IllegalArgumentException(file.getName() + " already exists");

        Files.write(file.toPath(), source(name, category).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    static String source(String name, ModuleCategory category) {
        return "import arsenic.asm.RequiresPlayer;\n"
                + "import arsenic.event.bus.Listener;\n"
                + "import arsenic.event.bus.annotations.EventLink;\n"
                + "import arsenic.event.impl.EventTick;\n"
                + "import arsenic.module.Module;\n"
                + "import arsenic.module.ModuleCategory;\n"
                + "import arsenic.module.ModuleInfo;\n"
                + "import arsenic.module.property.impl.BooleanProperty;\n"
                + "\n"
                + "// Addons are plain modules. Write Minecraft code with the normal MCP names (mc.thePlayer, ...);\n"
                + "// Arsenic remaps them to whatever the running game uses. Save the file and run \".addon reload\".\n"
                + "@ModuleInfo(name = \"" + name + "\", description = \"Describe " + name + " here\", category = ModuleCategory." + category.name() + ")\n"
                + "public class " + name + " extends Module {\n"
                + "\n"
                + "    // Public Property fields show up in the ClickGUI and are saved in configs automatically.\n"
                + "    public final BooleanProperty example = new BooleanProperty(\"Example\", true);\n"
                + "\n"
                + "    // @RequiresPlayer skips the listener while no world is loaded.\n"
                + "    @RequiresPlayer\n"
                + "    @EventLink\n"
                + "    public final Listener<EventTick> onTick = event -> {\n"
                + "        if (example.getValue()) {\n"
                + "            // mc.thePlayer is available here\n"
                + "        }\n"
                + "    };\n"
                + "\n"
                + "    @Override\n"
                + "    protected void onEnable() {\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    protected void onDisable() {\n"
                + "    }\n"
                + "}\n";
    }
}
