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
        /** Whether the attach API lists it. Lunar turns attach off, so it only shows up in the OS process scan. */
        final boolean attachable;

        Game(String id, Client client, String version, String mainClass, boolean attachable) {
            this.id = id;
            this.client = client;
            this.version = version;
            this.mainClass = mainClass;
            this.attachable = attachable;
        }

        /** Unknown versions are allowed; the agent checks the game itself. */
        boolean supported() {
            if (client == Client.OTHER)
                return false;
            // Lunar's command line does not carry a reliable Minecraft version; the agent checks it is 1.8.9 inside
            if (client == Client.LUNAR)
                return true;
            return version == null || version.contains("1.8.9");
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
                    + "Or start the game with -javaagent:Arsenic.jar in its JVM arguments, which needs no JDK.";
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

    /**
     * Running games. Command lines hold access tokens, so only the version is ever shown. The attach API lists the
     * games the injector can attach to; an OS process scan adds the ones it can't (Lunar turns the attach mechanism
     * off), so they still appear in the window even though injecting them will fail.
     */
    static List<Game> findGames(boolean all) throws Exception {
        java.util.Map<String, Game> byPid = new java.util.LinkedHashMap<>();
        String self = selfPid();

        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Class<?> descClass = Class.forName("com.sun.tools.attach.VirtualMachineDescriptor");
        Method id = descClass.getMethod("id");
        Method displayName = descClass.getMethod("displayName");
        for (Object desc : (List<?>) vmClass.getMethod("list").invoke(null)) {
            String pid = (String) id.invoke(desc);
            String name = (String) displayName.invoke(desc);
            if (pid.equals(self))
                continue;
            Client client = classify(name);
            if (client == Client.OTHER && !all)
                continue;
            String main = name.split(" ")[0];
            byPid.put(pid, new Game(pid, client, argument(name, "--version"), main, true));
        }

        // games the attach API does not list (attach turned off, or no perf data): keep the attach entry when both find it
        for (Game game : osGames(all))
            byPid.putIfAbsent(game.id, game);

        return new ArrayList<>(byPid.values());
    }

    /** The {@code -javaagent} argument that loads the client at launch, where the attach API cannot reach a game. */
    static String agentArg() {
        try {
            return "-javaagent:" + ownJar().getAbsolutePath();
        } catch (Exception e) {
            return "-javaagent:Arsenic.jar";
        }
    }

    // ---- OS process scan (for games the attach API misses, e.g. Lunar) ----

    /** Java games from the OS process list, marked not-attachable. */
    private static List<Game> osGames(boolean all) {
        List<Game> games = new ArrayList<>();
        String self = selfPid();
        for (String[] proc : osProcesses()) {
            String pid = proc[0], cmd = proc[1];
            if (pid.equals(self) || cmd == null || cmd.isEmpty())
                continue;
            Client client = classify(cmd);
            if (client == Client.OTHER && !all)
                continue;
            games.add(new Game(pid, client, argument(cmd, "--version"), mainClassOf(cmd), false));
        }
        return games;
    }

    /** {pid, commandLine} for running processes; empty when the scan is unavailable. */
    private static List<String[]> osProcesses() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win"))
                return windowsProcesses();
            if (os.contains("mac"))
                return macProcesses();
            return linuxProcesses();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<String[]> windowsProcesses() throws Exception {
        // WMIC first (fast); PowerShell's CIM query is the fallback where WMIC is gone (Windows 11 24H2+)
        String out = run("wmic", "process", "where", "name like 'java%.exe'", "get", "CommandLine,ProcessId", "/format:list");
        List<String[]> games = parseWmic(out);
        if (!games.isEmpty())
            return games;
        String script = "Get-CimInstance Win32_Process -Filter \"Name like 'java%'\" | "
                + "ForEach-Object { \"$($_.ProcessId)`t$($_.CommandLine)\" }";
        return parseTabbed(run("powershell", "-NoProfile", "-NonInteractive", "-Command", script));
    }

    private static List<String[]> parseWmic(String out) {
        List<String[]> games = new ArrayList<>();
        String cmd = null;
        for (String line : out.split("\r?\n")) {
            if (line.startsWith("CommandLine="))
                cmd = line.substring("CommandLine=".length()).trim();
            else if (line.startsWith("ProcessId=")) {
                String pid = line.substring("ProcessId=".length()).trim();
                if (isPid(pid))
                    games.add(new String[]{pid, cmd == null ? "" : cmd});
                cmd = null;
            }
        }
        return games;
    }

    private static List<String[]> parseTabbed(String out) {
        List<String[]> games = new ArrayList<>();
        for (String line : out.split("\r?\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0)
                continue;
            String pid = line.substring(0, tab).trim();
            if (isPid(pid))
                games.add(new String[]{pid, line.substring(tab + 1).trim()});
        }
        return games;
    }

    private static List<String[]> macProcesses() throws Exception {
        List<String[]> games = new ArrayList<>();
        for (String line : run("ps", "-ax", "-o", "pid=,command=").split("\n")) {
            line = line.trim();
            int sp = line.indexOf(' ');
            if (sp <= 0)
                continue;
            String pid = line.substring(0, sp);
            String cmd = line.substring(sp + 1);
            if (isPid(pid) && cmd.contains("java"))
                games.add(new String[]{pid, cmd});
        }
        return games;
    }

    private static List<String[]> linuxProcesses() {
        List<String[]> games = new ArrayList<>();
        File[] dirs = new File("/proc").listFiles();
        if (dirs == null)
            return games;
        for (File dir : dirs) {
            String pid = dir.getName();
            if (!isPid(pid))
                continue;
            try {
                byte[] raw = Files.readAllBytes(new File(dir, "cmdline").toPath());
                if (raw.length == 0)
                    continue;
                String cmd = new String(raw, StandardCharsets.UTF_8).replace('\0', ' ').trim();
                if (cmd.contains("java"))
                    games.add(new String[]{pid, cmd});
            } catch (Exception ignored) {
            }
        }
        return games;
    }

    private static boolean isPid(String s) {
        if (s.isEmpty())
            return false;
        for (int i = 0; i < s.length(); i++)
            if (!Character.isDigit(s.charAt(i)))
                return false;
        return true;
    }

    /** The main class in a command line, for naming a non-Minecraft Java process; "Java" when none is found. */
    private static String mainClassOf(String commandLine) {
        for (String part : commandLine.split(" "))
            if (part.matches("[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)+") && part.contains("."))
                return part;
        return "Java";
    }

    /** Runs a short-lived command and returns its stdout, or "" on failure or timeout. */
    private static String run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
            StringBuilder sb = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null)
                        sb.append(line).append('\n');
                } catch (Exception ignored) {
                }
            });
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            reader.join(1000);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
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
