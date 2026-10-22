package arsenic.inject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lunar Client turns the attach mechanism off, so Arsenic comes in as a launch argument instead: {@code
 * -javaagent:<this jar>} in the JVM arguments of Lunar's launcher.json. This stops Lunar (its launcher and the game,
 * which would otherwise rewrite the settings when they exit) and then adds or removes that argument. Written for Java 8
 * (the injector's floor); process handling is Windows first, other systems use ps and kill.
 */
final class LunarInjection {

    private static final long STOP_TIMEOUT_MS = 15_000;
    private static final Pattern JVM_ARGS = Pattern.compile("\"jvmArgs\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SETTINGS_OPEN = Pattern.compile("\"settings\"\\s*:\\s*\\{");
    private static final Pattern ADVANCED_OFF = Pattern.compile("\"advancedMode\"\\s*:\\s*false");

    private LunarInjection() {}

    /** Lunar's settings file for the current user. */
    static File settingsFile() {
        return new File(System.getProperty("user.home"), ".lunarclient" + File.separator + "settings" + File.separator + "launcher.json");
    }

    /** Process ids of Lunar's launcher and game. The injector itself is never in the list. */
    static List<String> runningLunar() {
        List<String> pids = new ArrayList<>();
        String self = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        try {
            if (windows()) {
                // $PID is the PowerShell itself, whose command line holds this script and so mentions lunar too
                String script = "Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and "
                        + "($_.Name -match 'lunar' -or $_.CommandLine -match 'lunar') } | "
                        + "ForEach-Object { $_.ProcessId }";
                for (String line : run("powershell", "-NoProfile", "-NonInteractive", "-Command", script).split("\\r?\\n")) {
                    String pid = line.trim();
                    if (pid.matches("\\d+") && !pid.equals(self))
                        pids.add(pid);
                }
            } else {
                for (String line : run("ps", "-eo", "pid=,args=").split("\n")) {
                    String t = line.trim();
                    int space = t.indexOf(' ');
                    if (space <= 0)
                        continue;
                    String pid = t.substring(0, space);
                    if (t.toLowerCase(Locale.ROOT).contains("lunar") && pid.matches("\\d+") && !pid.equals(self))
                        pids.add(pid);
                }
            }
        } catch (Exception ignored) {
            // no process list: nothing found, which the caller reports
        }
        return pids;
    }

    /** Stops the processes and waits until they are gone. Returns false when some are still running after the wait. */
    static boolean stop(List<String> pids, Consumer<String> log) throws InterruptedException {
        for (String pid : pids) {
            log.accept("Stopping process " + pid);
            try {
                if (windows())
                    run("taskkill", "/F", "/T", "/PID", pid);
                else
                    run("kill", "-9", pid);
            } catch (Exception e) {
                log.accept("Could not stop " + pid + ": " + e.getMessage());
            }
        }
        long deadline = System.currentTimeMillis() + STOP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (runningLunar().isEmpty())
                return true;
            Thread.sleep(500);
        }
        return runningLunar().isEmpty();
    }

    /**
     * Puts the agent argument into the JVM arguments in launcher.json, replacing any earlier Arsenic agent argument
     * (an old jar in .minecraft/mods, for example) and keeping every other argument. Lunar ignores JVM arguments
     * unless advanced mode is on, so that is switched on too.
     *
     * @return true when the file changed
     */
    static boolean setAgent(File settings, String agentArg, Consumer<String> log) throws IOException {
        String text = read(settings);
        String updated = text;
        Matcher args = JVM_ARGS.matcher(updated);
        if (args.find()) {
            List<String> kept = withoutArsenic(args.group(1));
            kept.add(0, agentArg);
            updated = replaceJvmArgs(updated, args, String.join(" ", kept));
        } else {
            Matcher open = SETTINGS_OPEN.matcher(updated);
            if (!open.find())
                throw new IOException("no \"settings\" object in " + settings);
            updated = updated.substring(0, open.end()) + "\n\t\t\"jvmArgs\": \"" + escape(agentArg) + "\","
                    + updated.substring(open.end());
        }

        Matcher advanced = ADVANCED_OFF.matcher(updated);
        if (advanced.find()) {
            updated = updated.substring(0, advanced.start()) + "\"advancedMode\": true" + updated.substring(advanced.end());
            log.accept("Turned on advanced mode, which Lunar needs for JVM arguments");
        }

        if (updated.equals(text)) {
            log.accept("launcher.json already loads Arsenic from this jar");
            return false;
        }
        save(settings, updated);
        return true;
    }

    /**
     * Takes Arsenic's agent argument out of the JVM arguments in launcher.json, keeping the others.
     *
     * @return true when the file changed
     */
    static boolean removeAgent(File settings, Consumer<String> log) throws IOException {
        String text = read(settings);
        Matcher args = JVM_ARGS.matcher(text);
        if (!args.find()) {
            log.accept("Lunar has no JVM arguments, so Arsenic is not in them");
            return false;
        }
        List<String> kept = withoutArsenic(args.group(1));
        if (String.join(" ", kept).equals(written(args.group(1)))) {
            log.accept("Arsenic is not in Lunar's JVM arguments");
            return false;
        }
        save(settings, replaceJvmArgs(text, args, String.join(" ", kept)));
        return true;
    }

    private static List<String> withoutArsenic(String jsonValue) {
        List<String> kept = new ArrayList<>();
        for (String argument : arguments(jsonValue)) {
            if (!isArsenicAgent(argument))
                kept.add(argument);
        }
        return kept;
    }

    /** JVM options whose value is the next word ("-cp x"). Any other word without a "-" ends the JVM's options. */
    private static final List<String> VALUE_OPTIONS = Arrays.asList("-cp", "-classpath", "--class-path", "-p",
            "--module-path", "--upgrade-module-path", "--add-modules", "--limit-modules", "--add-opens", "--add-exports",
            "--add-reads", "--patch-module", "--enable-native-access");

    /**
     * The JVM arguments, without stray words. Lunar splits on spaces, so a path with a space in it ("Arsenic (1).jar",
     * or a user folder like "John Smith") ends up as several words, and Java takes the first word that is neither an
     * option nor an option's value for the main class: the game exits with "Could not find or load main class
     * (1).jar". Such words can only break the launch, so they go. An Arsenic agent argument keeps its stray words
     * (they are part of its path), so it is recognised and removed as a whole.
     */
    private static List<String> arguments(String jsonValue) {
        List<String> arguments = new ArrayList<>();
        List<Boolean> split = new ArrayList<>();
        boolean expectsValue = false;
        for (String token : unescape(jsonValue).trim().split("\\s+")) {
            if (token.isEmpty())
                continue;
            int last = arguments.size() - 1;
            if (token.startsWith("-")) {
                arguments.add(token);
                split.add(false);
                expectsValue = VALUE_OPTIONS.contains(token);
            } else if (expectsValue) {
                arguments.set(last, arguments.get(last) + " " + token);
                expectsValue = false;
            } else if (last >= 0) {
                arguments.set(last, arguments.get(last) + " " + token);
                split.set(last, true);
            }
        }
        for (int i = 0; i < arguments.size(); i++) {
            // a split argument that is not Arsenic's keeps its first word: the rest would be the main class
            if (split.get(i) && !isArsenicAgent(arguments.get(i)))
                arguments.set(i, arguments.get(i).split(" ")[0]);
        }
        return arguments;
    }

    /** The JVM arguments exactly as written, one space apart, to tell whether anything changed. */
    private static String written(String jsonValue) {
        return String.join(" ", unescape(jsonValue).trim().split("\\s+"));
    }

    private static String replaceJvmArgs(String text, Matcher args, String value) {
        return text.substring(0, args.start(1)) + escape(value) + text.substring(args.end(1));
    }

    private static void save(File settings, String after) throws IOException {
        Files.write(settings.toPath(), after.getBytes(StandardCharsets.UTF_8));
        // backups written by older injectors
        Files.deleteIfExists(new File(settings.getParentFile(), settings.getName() + ".arsenic-backup").toPath());
    }

    private static String read(File settings) throws IOException {
        return new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8);
    }

    private static boolean isArsenicAgent(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.startsWith("-javaagent:") && lower.contains("arsenic");
    }

    /** The JSON string escapes a JVM argument can carry: backslash and quote (Windows paths use backslashes). */
    private static String unescape(String json) {
        return json.replaceAll("\\\\(.)", "$1");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String run(String... command) throws IOException {
        Process p = new ProcessBuilder(Arrays.asList(command)).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (java.io.Reader r = new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = r.read(buf)) > 0)
                out.append(buf, 0, n);
        }
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out.toString();
    }
}
