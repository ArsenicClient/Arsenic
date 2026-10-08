package arsenic.inject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What runs when the client jar is double-clicked: a small window that lists the running Minecraft games and loads
 * the client into the chosen one through the Java attach API ({@link Agent} does the rest inside the game).
 *
 * The attach API ships with JDKs only. When the Java that opened the jar has none, the injector looks for an
 * installed JDK and restarts itself with it.
 *
 * Without a window: {@code java -jar Arsenic.jar --list} and {@code java -jar Arsenic.jar --pid <pid>}.
 */
public final class Injector {

    private static final String RELAUNCHED = "--relaunched";
    private static final long TIMEOUT_MS = 60_000;

    /** A running game: its process id and what to call it. */
    static final class Game {
        final String id;
        final String label;

        Game(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> argList = new ArrayList<>();
        Collections.addAll(argList, args);
        boolean console = argList.contains("--list") || argList.contains("--pid");

        if (!attachAvailable()) {
            if (!argList.contains(RELAUNCHED) && relaunchWithJdk(args, console))
                return;
            String message = "Injecting needs a Java Development Kit (JDK), and none was found.\n"
                    + "Install one (for example Eclipse Temurin JDK 8, 17 or 21 from adoptium.net) and open the jar again.\n\n"
                    + "To use Arsenic without injecting, put the jar in .minecraft/mods instead.";
            if (console) {
                System.err.println(message);
                System.exit(2);
            }
            JOptionPane.showMessageDialog(null, message, "Arsenic Injector", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (argList.contains("--list")) {
            for (Game game : findGames(false))
                System.out.println(game.id + "  " + game.label);
            return;
        }
        int pid = argList.indexOf("--pid");
        if (pid >= 0 && pid + 1 < argList.size()) {
            String result = inject(argList.get(pid + 1), System.out::println);
            System.out.println(result);
            System.exit(result.startsWith("OK") ? 0 : 1);
        }

        SwingUtilities.invokeLater(() -> new Window().show());
    }

    // ---- attach API (reflection: it is not on the compile class path, and not in every Java) ----

    static boolean attachAvailable() {
        try {
            Class.forName("com.sun.tools.attach.VirtualMachine");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Running JVMs that look like Minecraft. Their command lines hold access tokens, so only the version is shown. */
    static List<Game> findGames(boolean all) throws Exception {
        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Class<?> descClass = Class.forName("com.sun.tools.attach.VirtualMachineDescriptor");
        Method id = descClass.getMethod("id");
        Method displayName = descClass.getMethod("displayName");
        List<Game> games = new ArrayList<>();
        String self = selfPid();
        for (Object desc : (List<?>) vmClass.getMethod("list").invoke(null)) {
            String pid = (String) id.invoke(desc);
            String name = (String) displayName.invoke(desc);
            if (pid.equals(self))
                continue;
            boolean minecraft = name.contains("net.minecraft.launchwrapper.Launch") || name.contains("net.minecraft.client.main.Main")
                    || name.toLowerCase(Locale.ROOT).contains("minecraft");
            if (!minecraft && !all)
                continue;
            games.add(new Game(pid, describe(pid, name, minecraft)));
        }
        return games;
    }

    private static String describe(String pid, String commandLine, boolean minecraft) {
        if (!minecraft) {
            String main = commandLine.split(" ")[0];
            return main.substring(main.lastIndexOf('.') + 1) + "  (pid " + pid + ")";
        }
        String version = argument(commandLine, "--version");
        boolean forge = commandLine.contains("launchwrapper") && (commandLine.contains("FMLTweaker") || (version != null && version.contains("forge")));
        String what = "Minecraft" + (version != null ? " " + version : "") + (forge || version == null ? "" : " (not Forge)");
        return what + "  (pid " + pid + ")";
    }

    private static String argument(String commandLine, String key) {
        String[] parts = commandLine.split(" ");
        for (int i = 0; i + 1 < parts.length; i++)
            if (parts[i].equals(key))
                return parts[i + 1];
        return null;
    }

    private static String selfPid() {
        String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : name;
    }

    static File ownJar() throws URISyntaxException {
        return new File(Injector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /**
     * Loads the agent into the game and waits for the client to report back.
     *
     * @return "OK" when the client started, otherwise "ERROR ..." with the reason
     */
    static String inject(String pid, java.util.function.Consumer<String> progress) {
        File status = null;
        try {
            File jar = ownJar();
            if (!jar.isFile())
                return "ERROR Run the injector from the Arsenic jar (" + jar + " is not a jar)";
            status = File.createTempFile("arsenic-inject", ".txt");

            progress.accept("Attaching to the game...");
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            // newer JDKs misread the reply of a Java 8 game ("Failed to load agent library: 0") even though the
            // agent ran, so the agent's own status file decides; the error only counts if the agent stays silent
            String loadError = null;
            try {
                progress.accept("Loading the client...");
                vmClass.getMethod("loadAgent", String.class, String.class).invoke(vm, jar.getAbsolutePath(), status.getAbsolutePath());
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                loadError = cause.getMessage() != null ? cause.getMessage() : cause.toString();
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }

            long start = System.currentTimeMillis();
            long deadline = start + TIMEOUT_MS;
            int seen = 0;
            while (System.currentTimeMillis() < deadline) {
                List<String> lines = Files.readAllLines(status.toPath(), StandardCharsets.UTF_8);
                if (lines.isEmpty() && loadError != null && System.currentTimeMillis() - start > 5_000)
                    return "ERROR " + loadError;
                for (; seen < lines.size(); seen++) {
                    String line = lines.get(seen);
                    if (line.equals("OK") || line.startsWith("ERROR"))
                        return line;
                    progress.accept(line.startsWith("LOG ") ? line.substring(4) : line + "...");
                }
                Thread.sleep(100);
            }
            return "ERROR The game did not answer. Is it frozen or minimised to a loading screen?";
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            return "ERROR " + (cause.getMessage() != null ? cause.getMessage() : cause.toString());
        } catch (Exception e) {
            return "ERROR " + e;
        } finally {
            if (status != null)
                status.deleteOnExit();
        }
    }

    // ---- finding a JDK ----

    /** Restarts the injector with a JDK's Java. @return true when it was restarted */
    private static boolean relaunchWithJdk(String[] args, boolean console) {
        File jdk = findJdk();
        if (jdk == null)
            return false;
        try {
            String classPath = ownJar().getAbsolutePath();
            File toolsJar = new File(jdk, "lib/tools.jar");
            if (toolsJar.isFile())
                classPath += File.pathSeparator + toolsJar.getAbsolutePath();
            List<String> command = new ArrayList<>();
            command.add(javaExecutable(jdk, !console).getAbsolutePath());
            command.add("-cp");
            command.add(classPath);
            command.add(Injector.class.getName());
            Collections.addAll(command, args);
            command.add(RELAUNCHED);
            ProcessBuilder builder = new ProcessBuilder(command);
            if (console) {
                builder.inheritIO();
                System.exit(builder.start().waitFor());
            }
            builder.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static File javaExecutable(File home, boolean windowed) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        return new File(home, "bin/" + (windowed && windows ? "javaw.exe" : windows ? "java.exe" : "java"));
    }

    /** A JDK home: has jcmd (JDKs only) and, for Java 8, tools.jar. */
    private static boolean isJdk(File home) {
        if (home == null || !home.isDirectory())
            return false;
        boolean jcmd = new File(home, "bin/jcmd.exe").isFile() || new File(home, "bin/jcmd").isFile();
        boolean java8 = new File(home, "jre").isDirectory();
        return jcmd && javaExecutable(home, false).isFile() && (!java8 || new File(home, "lib/tools.jar").isFile());
    }

    static File findJdk() {
        List<File> candidates = new ArrayList<>();
        File javaHome = new File(System.getProperty("java.home"));
        candidates.add(javaHome);
        candidates.add(javaHome.getParentFile());
        String env = System.getenv("JAVA_HOME");
        if (env != null)
            candidates.add(new File(env));

        List<File> roots = new ArrayList<>();
        for (String var : new String[]{"ProgramFiles", "ProgramW6432", "ProgramFiles(x86)"}) {
            String dir = System.getenv(var);
            if (dir == null)
                continue;
            for (String vendor : new String[]{"Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK", "Zulu",
                    "Microsoft", "BellSoft", "Amazon Corretto", "Semeru", "OpenJDK", "RedHat"})
                roots.add(new File(dir, vendor));
        }
        String home = System.getProperty("user.home");
        roots.add(new File(home, ".jdks"));
        roots.add(new File(home, ".gradle/jdks"));
        roots.add(new File("/usr/lib/jvm"));
        roots.add(new File("/Library/Java/JavaVirtualMachines"));
        for (File root : roots) {
            File[] children = root.listFiles(File::isDirectory);
            if (children == null)
                continue;
            for (File child : children) {
                candidates.add(child);
                candidates.add(new File(child, "Contents/Home"));
            }
        }
        for (File candidate : candidates)
            if (isJdk(candidate))
                return candidate;
        return null;
    }

    // ---- window ----

    private static final class Window {
        private final JFrame frame = new JFrame("Arsenic Injector");
        private final DefaultListModel<Game> model = new DefaultListModel<>();
        private final JList<Game> list = new JList<>(model);
        private final JCheckBox showAll = new JCheckBox("Show all Java processes");
        private final JButton injectButton = new JButton("Inject");
        private final JButton refreshButton = new JButton("Refresh");
        private final JLabel statusLabel = new JLabel(" ");
        private boolean busy;

        void show() {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                SwingUtilities.updateComponentTreeUI(frame);
            } catch (Exception ignored) {
            }

            JPanel root = new JPanel(new BorderLayout(0, 10));
            root.setBorder(new EmptyBorder(14, 14, 14, 14));

            JLabel title = new JLabel("Arsenic");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
            JLabel hint = new JLabel("Pick a running Minecraft Forge 1.8.9 game and press Inject.");
            JPanel header = new JPanel(new GridLayout(2, 1, 0, 2));
            header.add(title);
            header.add(hint);
            root.add(header, BorderLayout.NORTH);

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setVisibleRowCount(6);
            list.addListSelectionListener(e -> updateButtons());
            root.add(new JScrollPane(list), BorderLayout.CENTER);

            injectButton.addActionListener(e -> injectSelected());
            refreshButton.addActionListener(e -> refresh());
            showAll.addActionListener(e -> refresh());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.add(refreshButton);
            buttons.add(injectButton);
            JPanel controls = new JPanel(new BorderLayout());
            controls.add(showAll, BorderLayout.WEST);
            controls.add(buttons, BorderLayout.EAST);

            JPanel footer = new JPanel(new BorderLayout(0, 8));
            footer.add(controls, BorderLayout.NORTH);
            footer.add(statusLabel, BorderLayout.SOUTH);
            root.add(footer, BorderLayout.SOUTH);

            frame.setContentPane(root);
            frame.getRootPane().setDefaultButton(injectButton);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setSize(460, 320);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            refresh();
        }

        private void updateButtons() {
            injectButton.setEnabled(!busy && list.getSelectedValue() != null);
            refreshButton.setEnabled(!busy);
            showAll.setEnabled(!busy);
        }

        private void setStatus(String text) {
            statusLabel.setText(text.isEmpty() ? " " : text);
        }

        private void refresh() {
            try {
                List<Game> games = findGames(showAll.isSelected());
                model.clear();
                for (Game game : games)
                    model.addElement(game);
                if (!games.isEmpty())
                    list.setSelectedIndex(0);
                setStatus(games.isEmpty() ? "No running Minecraft found. Start the game, then press Refresh." : "");
            } catch (Exception e) {
                setStatus("Could not list Java processes: " + e);
            }
            updateButtons();
        }

        private void injectSelected() {
            Game game = list.getSelectedValue();
            if (game == null)
                return;
            busy = true;
            updateButtons();
            new SwingWorker<String, String>() {
                @Override
                protected String doInBackground() {
                    return inject(game.id, this::publish);
                }

                @Override
                protected void process(List<String> chunks) {
                    setStatus(chunks.get(chunks.size() - 1));
                }

                @Override
                protected void done() {
                    busy = false;
                    updateButtons();
                    String result;
                    try {
                        result = get();
                    } catch (Exception e) {
                        result = "ERROR " + e;
                    }
                    if (result.equals("OK")) {
                        setStatus("Injected. Press Right Shift in game for the ClickGUI.");
                        JOptionPane.showMessageDialog(frame, "Arsenic is loaded. Press Right Shift in game to open the ClickGUI.",
                                "Arsenic Injector", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        String reason = result.startsWith("ERROR") ? result.substring(5).trim() : result;
                        setStatus("Injection failed.");
                        JOptionPane.showMessageDialog(frame, "Could not inject Arsenic:\n" + reason,
                                "Arsenic Injector", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }
}
