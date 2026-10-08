package arsenic.inject;

import javax.swing.*;
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
 * What runs when the client jar is double-clicked: a window ({@link InjectorWindow}) that lists the running Minecraft
 * games (Forge, vanilla, Lunar Client) and loads the client into the chosen one through the Java attach API
 * ({@link Agent} does the rest inside the game).
 *
 * The attach API ships with JDKs only. When the Java that opened the jar has none, the injector looks for an
 * installed JDK and restarts itself with it.
 *
 * Without a window: {@code java -jar Arsenic.jar --list} and {@code java -jar Arsenic.jar --pid <pid>}.
 */
public final class Injector {

    private static final String RELAUNCHED = "--relaunched";
    private static final long TIMEOUT_MS = 60_000;

    /** Which launcher or loader a game runs under, from its command line. */
    enum Client {
        FORGE("Forge", new Color(0xE0812F)),
        LUNAR("Lunar Client", new Color(0x3B82F6)),
        VANILLA("Vanilla", new Color(0x3FA34D)),
        UNKNOWN("Minecraft", new Color(0x7C3AED)),
        OTHER("Java", new Color(0x55555F));

        final String display;
        final Color color;

        Client(String display, Color color) {
            this.display = display;
            this.color = color;
        }
    }

    /** A running game: its process id, what runs it and which Minecraft version (null when unknown). */
    static final class Game {
        final String id;
        final Client client;
        final String version;
        final String mainClass;

        Game(String id, Client client, String version, String mainClass) {
            this.id = id;
            this.client = client;
            this.version = version;
            this.mainClass = mainClass;
        }

        /** Unknown versions are allowed; the agent checks the game itself. */
        boolean supported() {
            return client != Client.OTHER && (version == null || version.contains("1.8.9"));
        }

        String unsupportedReason() {
            if (client == Client.OTHER)
                return "This is not a Minecraft game.";
            return "Only Minecraft 1.8.9 is supported (this is " + version + ").";
        }

        String title() {
            if (client == Client.OTHER)
                return mainClass.substring(mainClass.lastIndexOf('.') + 1);
            return "Minecraft " + (version == null ? "" : shortVersion(version));
        }

        /** Stable text for the game, to tell whether the list changed. Command lines hold access tokens, so only this is shown. */
        String label() {
            return title() + " (" + client.display + ", pid " + id + ")";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** "1.8.9-forge1.8.9-11.15.1.2318-1.8.9" -> "1.8.9" */
    private static String shortVersion(String version) {
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
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
                System.out.println(game.id + "  " + game.label() + (game.supported() ? "" : "  [unsupported]"));
            return;
        }
        int pid = argList.indexOf("--pid");
        if (pid >= 0 && pid + 1 < argList.size()) {
            String result = inject(argList.get(pid + 1), System.out::println);
            System.out.println(result);
            System.exit(result.startsWith("OK") ? 0 : 1);
        }

        InjectorWindow.open();
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
            Client client = classify(name);
            if (client == Client.OTHER && !all)
                continue;
            String main = name.split(" ")[0];
            games.add(new Game(pid, client, argument(name, "--version"), main));
        }
        return games;
    }

    /** Works out the client from the main class and arguments the JVM reports for the process. */
    static Client classify(String commandLine) {
        String lower = commandLine.toLowerCase(Locale.ROOT);
        String version = argument(commandLine, "--version");
        if (lower.contains("com.moonsworth.lunar") || lower.contains("lunarclient"))
            return Client.LUNAR;
        if (commandLine.contains("net.minecraft.launchwrapper.Launch")) {
            boolean forge = commandLine.contains("FMLTweaker") || version != null && version.toLowerCase(Locale.ROOT).contains("forge");
            return forge ? Client.FORGE : Client.VANILLA;
        }
        if (commandLine.contains("net.minecraft.client.main.Main"))
            return Client.VANILLA;
        if (lower.contains("minecraft"))
            return Client.UNKNOWN;
        return Client.OTHER;
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
}
