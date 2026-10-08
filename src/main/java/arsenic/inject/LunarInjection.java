package arsenic.inject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Inject into Lunar": Lunar Client turns the attach mechanism off, so Arsenic has to come in as a launch argument.
 * This stops Lunar (its launcher and the game, which Lunar would otherwise rewrite its settings over), then puts
 * {@code -javaagent:<this jar>} in the JVM arguments of Lunar's launcher.json. Written for Java 8 (the injector's
 * floor), Windows first; other systems get the process stop through ps and kill.
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
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
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
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (String pid : pids) {
            log.accept("Stopping process " + pid);
            try {
                if (windows)
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
     * unless advanced mode is on, so that is switched on too. A backup is written next to the file first.
     *
     * @return true when the file changed
     */
    static boolean setAgent(File settings, String agentArg, Consumer<String> log) throws IOException {
        String text = new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8);
        String updated = text;

        Matcher args = JVM_ARGS.matcher(updated);
        if (args.find()) {
            List<String> kept = new ArrayList<>();
            for (String token : unescape(args.group(1)).trim().split("\\s+")) {
                if (token.isEmpty() || isArsenicAgent(token))
                    continue;
                kept.add(token);
            }
            kept.add(0, agentArg);
            String value = String.join(" ", kept);
            updated = updated.substring(0, args.start(1)) + escape(value) + updated.substring(args.end(1));
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
        File backup = new File(settings.getParentFile(), settings.getName() + ".arsenic-backup");
        Files.copy(settings.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.accept("Backed up the settings to " + backup.getName());
        Files.write(settings.toPath(), updated.getBytes(StandardCharsets.UTF_8));
        return true;
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
