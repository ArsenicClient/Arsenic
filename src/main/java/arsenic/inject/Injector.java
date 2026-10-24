package arsenic.inject;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        /** Launcher instance the game runs from, when the system exposes it (Prism and MultiMC set it). */
        final String instance;

        Game(String id, Client client, String version, String mainClass, boolean attachable) {
            this(id, client, version, mainClass, attachable, null);
        }

        Game(String id, Client client, String version, String mainClass, boolean attachable, String instance) {
            this.id = id;
            this.client = client;
            this.version = version;
            this.mainClass = mainClass;
            this.attachable = attachable;
            this.instance = instance;
        }

        /** Unknown versions are allowed; the agent checks the game itself. */
        boolean supported() {
            // Lunar's command line does not carry a reliable Minecraft version; the agent checks it is 1.8.9 inside
            if (client == Client.LUNAR)
                return true;
            if (client != Client.VANILLA && client != Client.FORGE)
                return false;
            return version == null || version.contains("1.8.9");
        }

        String unsupportedReason() {
            if (client == Client.OTHER || client == Client.UNKNOWN)
                return "This is not a Minecraft game the injector recognises.";
            return "Only Minecraft 1.8.9 is supported (this is " + version + ").";
        }

        String title() {
            if (client == Client.OTHER)
                return mainClass.substring(mainClass.lastIndexOf('.') + 1);
            return "Minecraft" + (version == null ? "" : " " + shortVersion(version));
        }

        /** Stable text for the game, to tell whether the list changed. Command lines hold access tokens, so only this is shown. */
        String label() {
            String where = instance == null || instance.isEmpty() ? "" : " [" + instance + "]";
            return title() + where + " (" + client.display + ", pid " + id + ")";
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
        sweepStaleStatusFiles();
        List<String> argList = new ArrayList<>();
        Collections.addAll(argList, args);
        boolean headless = GraphicsEnvironment.isHeadless();
        boolean console = argList.contains("--list") || argList.contains("--pid") || headless;

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

        if (headless) {
            System.err.println("No display is available. Use --list to see running games, or --pid <id> to inject.");
            System.exit(2);
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

        // The OS scan has the full command line (version, FMLTweaker, launcher arguments); the attach API often only
        // reports the main class, so its entry is used only where the OS scan found nothing.
        java.util.Map<String, Game> fromOs = new java.util.LinkedHashMap<>();
        for (Game game : osGames(all))
            fromOs.put(game.id, game);

        Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        Class<?> descClass = Class.forName("com.sun.tools.attach.VirtualMachineDescriptor");
        Method id = descClass.getMethod("id");
        Method displayName = descClass.getMethod("displayName");
        for (Object desc : (List<?>) vmClass.getMethod("list").invoke(null)) {
            String pid = (String) id.invoke(desc);
            String name = (String) displayName.invoke(desc);
            if (pid.equals(self))
                continue;

            Game seen = fromOs.remove(pid);
            if (seen != null) {
                byPid.put(pid, new Game(pid, seen.client, seen.version, seen.mainClass, true, seen.instance));
                continue;
            }

            String command = name;
            Client client = classify(name);
            if ((client == Client.OTHER || client == Client.UNKNOWN) && mayBeMinecraft(name)) {
                // launchers rename the game's process (Prism shows "Entrypoint"), so ask the process what it runs
                String real = systemProperty(pid, "sun.java.command");
                String classPath = systemProperty(pid, "java.class.path");
                if (real != null) {
                    command = real;
                    client = classify(real);
                    boolean minecraftJar = classPath != null && classPath.toLowerCase(Locale.ROOT).contains("minecraft");
                    if (client != Client.VANILLA && client != Client.FORGE && minecraftJar)
                        client = real.contains("FMLTweaker") ? Client.FORGE : Client.VANILLA;
                }
            }
            if (client == Client.OTHER && !all)
                continue;
            String main = command.split(" ")[0];
            byPid.put(pid, new Game(pid, client, versionOf(command), main, true, null));
        }

        for (Game game : fromOs.values())
            byPid.put(game.id, game);

        return new ArrayList<>(byPid.values());
    }

    /** The {@code -javaagent} argument that loads the client at launch, where the attach API cannot reach a game. */
    static String agentArg() {
        try {
            return "-javaagent:" + spaceFreeJar(ownJar()).getAbsolutePath();
        } catch (Exception e) {
            return "-javaagent:Arsenic.jar";
        }
    }

    /**
     * Launchers split JVM arguments on spaces, so a jar at "Downloads\Arsenic (1).jar" turns into a main class named
     * "(1).jar" and the game won't start. Such a jar is copied to a folder without spaces and that copy is named
     * instead (copied on every call, so it follows the jar that is run).
     */
    private static File spaceFreeJar(File jar) {
        if (!hasSpace(jar.getAbsolutePath()))
            return jar;
        List<File> dirs = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null)
            dirs.add(new File(localAppData, "Arsenic"));
        dirs.add(new File(System.getProperty("user.home"), ".arsenic"));
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null)
            dirs.add(new File(tmp, "arsenic-" + System.getProperty("user.name", "user")));
        String systemDrive = System.getenv("SystemDrive");
        if (systemDrive != null)
            dirs.add(new File(systemDrive + File.separator, "Arsenic"));
        for (File dir : dirs) {
            File copy = new File(dir, "Arsenic.jar");
            if (hasSpace(copy.getAbsolutePath()))
                continue;
            try {
                Files.createDirectories(dir.toPath());
                Files.copy(jar.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return copy;
            } catch (Exception ignored) {
            }
        }
        return jar;
    }

    private static boolean hasSpace(String path) {
        for (int i = 0; i < path.length(); i++)
            if (Character.isWhitespace(path.charAt(i)))
                return true;
        return false;
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
            games.add(new Game(pid, client, versionOf(cmd), mainClassOf(cmd), false, instanceName(pid)));
        }
        return games;
    }

    /** {pid, commandLine} for running processes; empty when the scan is unavailable. */
    private static List<String[]> osProcesses() {
        try {
            if (windows())
                return windowsProcesses();
            if (mac())
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
        int readable = 0;
        if (dirs != null) {
            for (File dir : dirs) {
                String pid = dir.getName();
                if (!isPid(pid))
                    continue;
                try {
                    // launcher wrapper scripts mention java and minecraft too, but are not the game itself
                    Path exe = Files.readSymbolicLink(new File(dir, "exe").toPath());
                    if (!exe.getFileName().toString().startsWith("java"))
                        continue;
                } catch (Exception ignored) {
                    // no exe link (permissions): the command line still has to mention java
                }
                try {
                    byte[] raw = Files.readAllBytes(new File(dir, "cmdline").toPath());
                    readable++;
                    if (raw.length == 0)
                        continue;
                    String cmd = new String(raw, StandardCharsets.UTF_8).replace('\0', ' ').trim();
                    if (cmd.contains("java"))
                        games.add(new String[]{pid, cmd});
                } catch (Exception ignored) {
                }
            }
        }
        // /proc unreadable (hidepid, a container): ps still lists this user's own processes
        return readable == 0 ? psProcesses() : games;
    }

    private static List<String[]> psProcesses() {
        List<String[]> games = new ArrayList<>();
        for (String line : run("ps", "-eww", "-o", "pid=,args=").split("\n")) {
            String t = line.trim();
            int space = t.indexOf(' ');
            if (space <= 0)
                continue;
            String pid = t.substring(0, space);
            String cmd = t.substring(space + 1);
            if (isPid(pid) && cmd.contains("java"))
                games.add(new String[]{pid, cmd});
        }
        return games;
    }

    /** The launcher instance a game runs from, when the environment exposes it (Prism and MultiMC set these). */
    private static String instanceName(String pid) {
        if (!linux())
            return null;
        try {
            byte[] raw = Files.readAllBytes(new File("/proc", pid + "/environ").toPath());
            String instance = null, id = null, name = null, dir = null;
            for (String entry : new String(raw, StandardCharsets.UTF_8).split("\0")) {
                if (entry.startsWith("INST_INSTANCE_NAME="))
                    instance = entry.substring("INST_INSTANCE_NAME=".length());
                else if (entry.startsWith("INST_ID="))
                    id = entry.substring("INST_ID=".length());
                else if (entry.startsWith("INST_NAME="))
                    name = entry.substring("INST_NAME=".length());
                else if (entry.startsWith("INST_MC_DIR="))
                    dir = entry.substring("INST_MC_DIR=".length());
            }
            if (instance != null && !instance.isEmpty())
                return instance;
            if (id != null && !id.isEmpty())
                return id;
            if (name != null && !name.isEmpty())
                return name;
            if (dir != null) {
                while (dir.endsWith("/"))
                    dir = dir.substring(0, dir.length() - 1);
                if (dir.endsWith("/.minecraft"))
                    dir = dir.substring(0, dir.length() - "/.minecraft".length());
                int slash = dir.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < dir.length())
                    return dir.substring(slash + 1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean linux() {
        return !windows() && !mac();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
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
        String version = versionOf(commandLine);
        if (lower.contains("com.moonsworth.lunar") || lower.contains("lunarclient"))
            return Client.LUNAR;
        if (lower.contains("org.polymc.entrypoint") || lower.contains("org.multimc.entrypoint")
                || lower.contains("org.prismlauncher.entrypoint")) {
            // these launchers start the game from their own main class; the class path says which loader it uses
            boolean forge = lower.contains("fmltweaker") || lower.contains("net.minecraftforge") || lower.contains("forge-");
            return forge ? Client.FORGE : Client.VANILLA;
        }
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

    /** The Minecraft version, from the launcher argument or from the Minecraft/Forge jar on the class path. */
    private static String versionOf(String commandLine) {
        String version = argument(commandLine, "--version");
        if (version != null)
            return version;
        Matcher matcher = Pattern.compile("(?:minecraft|forge)-(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(commandLine.toLowerCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : null;
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
        // run from an IDE the code runs from a classes folder, so the IDE run configuration names the jar to load
        String jar = System.getProperty("arsenic.jar");
        if (jar != null)
            return new File(jar);
        return new File(Injector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /**
     * Loads the agent into the game and waits for the client to report back.
     *
     * @return "OK" when the client started, otherwise "ERROR ..." with the reason
     */
    static String inject(String pid, java.util.function.Consumer<String> progress) {
        return runAgent(pid, "", progress);
    }

    /** Takes Arsenic out of a running game (the agent's uninject); see {@link Agent}. */
    static String uninject(String pid, java.util.function.Consumer<String> progress) {
        return runAgent(pid, "uninject:", progress);
    }

    /**
     * "injected" or "uninjected" when Arsenic has been loaded into the game, null when it has not or the game cannot be
     * reached. Reads the game's own system property, so it is right whichever window injected it.
     */
    static String loadedState(String pid) {
        return systemProperty(pid, "arsenic.loaded");
    }

    /** A system property of a running Java process through the attach API, or null when it cannot be read. */
    static String systemProperty(String pid, String key) {
        try {
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            try {
                java.util.Map<?, ?> props = (java.util.Map<?, ?>) vmClass.getMethod("getSystemProperties").invoke(vm);
                Object value = props.get(key);
                return value == null ? null : value.toString();
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Names a launcher gives its game's process, which the name alone does not show as Minecraft. */
    private static boolean mayBeMinecraft(String displayName) {
        String lower = displayName.toLowerCase(Locale.ROOT);
        for (String hint : new String[]{"minecraft", "entrypoint", "prism", "multimc", "launchwrapper", "client.main"})
            if (lower.contains(hint))
                return true;
        return false;
    }

    /** Loads the agent into the game with {@code argPrefix} before the status file, and follows the status file. */
    private static String runAgent(String pid, String argPrefix, java.util.function.Consumer<String> progress) {
        File status = null;
        try {
            File jar = ownJar();
            if (!jar.isFile())
                return "ERROR Run the injector from the Arsenic jar (" + jar + " is not a jar)";
            status = createStatusFile();

            progress.accept("Attaching to the game...");
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            // newer JDKs misread the reply of a Java 8 game ("Failed to load agent library: 0") even though the
            // agent ran, so the agent's own status file decides; the error only counts if the agent stays silent
            Throwable loadFailure = null;
            try {
                progress.accept("Loading the client...");
                vmClass.getMethod("loadAgent", String.class, String.class).invoke(vm, jar.getAbsolutePath(), argPrefix + status.getAbsolutePath());
            } catch (java.lang.reflect.InvocationTargetException e) {
                loadFailure = e.getCause();
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }

            StatusLog log = new StatusLog(status);
            long start = System.currentTimeMillis();
            long deadline = start + TIMEOUT_MS;
            boolean anyOutput = false;
            while (System.currentTimeMillis() < deadline) {
                List<String> lines;
                try {
                    lines = log.read();
                } catch (java.io.IOException e) {
                    Thread.sleep(100);
                    continue;
                }
                if (loadFailure != null && lines.isEmpty() && !anyOutput && System.currentTimeMillis() - start > 5_000)
                    return "ERROR " + attachFailure(loadFailure, pid);
                for (String line : lines) {
                    if (line.equals("OK") || line.startsWith("ERROR"))
                        return line;
                    progress.accept(line.startsWith("LOG ") ? line.substring(4) : line + "...");
                }
                if (!lines.isEmpty())
                    anyOutput = true;
                Thread.sleep(100);
            }
            String message = loadFailure != null
                    ? attachFailure(loadFailure, pid)
                    : "The game did not answer. Is it frozen or minimised to a loading screen?";
            String sandbox = sandboxHint(pid);
            if (sandbox != null)
                message += " " + sandbox + ".";
            return "ERROR " + message;
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "ERROR " + attachFailure(e.getCause(), pid);
        } catch (Exception e) {
            return "ERROR " + e;
        } finally {
            if (status != null)
                status.deleteOnExit();
        }
    }

    /** Creates the file the agent reports progress into, readable and writable by this user only. */
    private static File createStatusFile() throws java.io.IOException {
        File status = File.createTempFile("arsenic-inject", ".txt");
        try {
            Files.setPosixFilePermissions(status.toPath(), PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {
            // not a POSIX file system (Windows)
        }
        return status;
    }

    /** Removes status files left behind by an injector that never finished. */
    private static void sweepStaleStatusFiles() {
        File[] files = new File(System.getProperty("java.io.tmpdir", ".")).listFiles();
        if (files == null)
            return;
        long cutoff = System.currentTimeMillis() - 3_600_000L;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("arsenic-inject") && name.endsWith(".txt") && file.isFile() && file.lastModified() < cutoff)
                file.delete();
        }
    }

    /**
     * The lines the agent has written in full. A line is only taken once its newline has arrived, so a half-written
     * "OK" is never read as something else.
     */
    private static final class StatusLog {
        private final File file;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private long offset;

        StatusLog(File file) {
            this.file = file;
        }

        List<String> read() throws java.io.IOException {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                byte[] buffer = new byte[2048];
                int n;
                while ((n = raf.read(buffer)) > 0) {
                    pending.write(buffer, 0, n);
                    offset += n;
                }
            }
            byte[] bytes = pending.toByteArray();
            List<String> lines = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] != '\n')
                    continue;
                int end = i;
                if (end > start && bytes[end - 1] == '\r')
                    end--;
                lines.add(new String(bytes, start, end - start, StandardCharsets.UTF_8));
                start = i + 1;
            }
            pending.reset();
            if (start < bytes.length)
                pending.write(bytes, start, bytes.length - start);
            return lines;
        }
    }

    /** Turns an attach failure into something the user can act on. */
    private static String attachFailure(Throwable cause, String pid) {
        String message = cause == null ? "Unknown error"
                : cause.getMessage() != null ? cause.getMessage() : cause.toString();
        String lower = message.toLowerCase(Locale.ROOT);
        String hint;
        if (lower.contains("invalid process identifier") || lower.contains("no such process"))
            hint = "The game exited, or the process belongs to another user.";
        else if (lower.contains("permission denied"))
            hint = "Arsenic and the game have to run as the same user.";
        else if (lower.contains("not responding") || lower.contains("not loaded"))
            hint = "The game is still starting; wait for the main menu and try again.";
        else if (lower.contains("failed to load agent") || lower.contains("disableattach"))
            hint = "The game did not accept the agent; if it is still loading, wait for the main menu, otherwise launch it with -javaagent instead.";
        else if (lower.contains("attachnotsupported") || lower.contains("no providers installed"))
            hint = "This Java cannot attach to other processes; run the injector with a JDK.";
        else
            hint = message;
        String sandbox = sandboxHint(pid);
        if (sandbox != null)
            hint += " " + sandbox + ".";
        return hint;
    }

    /** Why attach cannot work here, or null when nothing suggests a sandbox. */
    private static String sandboxHint(String pid) {
        if (new File("/.flatpak-info").isFile() || System.getenv("FLATPAK_ID") != null)
            return "Arsenic is running inside a Flatpak sandbox; launch the game with -javaagent in its JVM arguments instead";
        if (System.getenv("SNAP") != null || System.getenv("SNAP_NAME") != null)
            return "Arsenic is running inside a Snap sandbox; launch the game with -javaagent in its JVM arguments instead";
        if (linux() && sandboxedTarget(pid))
            return "The game runs in a different mount namespace (a sandbox); launch it with -javaagent in its JVM arguments instead";
        return null;
    }

    /** True when the game has a different root (mount namespace), which hides the attach socket it listens on. */
    private static boolean sandboxedTarget(String pid) {
        try {
            Path root = Files.readSymbolicLink(new File("/proc", pid + "/root").toPath());
            return !"/".equals(root.toString());
        } catch (Exception e) {
            return false;
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
        boolean win = windows();
        return new File(home, "bin/" + (windowed && win ? "javaw.exe" : win ? "java.exe" : "java"));
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
        roots.add(new File(home, ".sdkman/candidates/java"));
        roots.add(new File(home, ".asdf/installs/java"));
        String sdkman = System.getenv("SDKMAN_DIR");
        if (sdkman != null)
            roots.add(new File(sdkman, "candidates/java"));
        roots.add(new File("/usr/lib/jvm"));
        roots.add(new File("/usr/lib64/jvm"));
        roots.add(new File("/usr/java"));
        roots.add(new File("/opt/java"));
        roots.add(new File("/Library/Java/JavaVirtualMachines"));
        for (File root : roots) {
            candidates.add(root);
            candidates.add(new File(root, "Contents/Home"));
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
