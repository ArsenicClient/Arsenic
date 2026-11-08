package arsenic.inject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Polyfrost's OneClient blocks the attach mechanism too, so, as with Lunar, Arsenic comes in as a launch argument:
 * {@code -javaagent:<this jar>} in the launch arguments of the setting profiles used by OneClient's Minecraft 1.8.9
 * instances. OneClient keeps those in an SQLite database (user_data.db, table setting_profiles, column launch_args),
 * which is edited with the sqlite3 command line tool since Java 8 has no SQLite driver. OneClient (launcher and game)
 * is stopped first so it cannot hold or rewrite the database.
 */
final class OneClientInjection {

    private static final long STOP_TIMEOUT_MS = 15_000;
    private static final String MC_VERSION = "1.8.9";
    private static final String FIELD = "\u001f";
    private static final String ROW = "\u001e";

    private OneClientInjection() {}

    /** OneClient's data folder for the current user. */
    static File dataDir() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        File base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = appData != null && !appData.isEmpty() ? new File(appData) : new File(home, "AppData" + File.separator + "Roaming");
        } else if (os.contains("mac")) {
            base = new File(home, "Library" + File.separator + "Application Support");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            base = xdg != null && !xdg.isEmpty() ? new File(xdg) : new File(home, ".local" + File.separator + "share");
        }
        return new File(new File(base, "Polyfrost"), "OneClient");
    }

    /** The database holding OneClient's setting profiles. */
    static File database() {
        return new File(dataDir(), "user_data.db");
    }

    /** Process ids of OneClient's launcher and of games it started. The injector itself is never in the list. */
    static List<String> runningOneClient() {
        List<String> pids = new ArrayList<>();
        String self = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        try {
            if (windows()) {
                String script = "Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and "
                        + "($_.Name -match 'oneclient' -or $_.CommandLine -match 'oneclient') } | "
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
                    if (t.toLowerCase(Locale.ROOT).contains("oneclient") && pid.matches("\\d+") && !pid.equals(self))
                        pids.add(pid);
                }
            }
        } catch (Exception ignored) {
            // no process list: nothing found
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
            if (runningOneClient().isEmpty())
                return true;
            Thread.sleep(500);
        }
        return runningOneClient().isEmpty();
    }

    /**
     * Puts the agent argument first in the launch arguments of every 1.8.9 instance's setting profile, replacing any
     * earlier Arsenic agent argument and keeping the other arguments.
     *
     * @return true when the database changed
     */
    static boolean setAgent(File db, String agentArg, Consumer<String> log) throws IOException {
        if (agentArg.contains(" "))
            log.accept("Warning: the jar path contains spaces, OneClient may split it; move the jar to a path without spaces if the game does not start");
        Map<String, String> profiles = profiles(db, log);
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> profile : profiles.entrySet()) {
            List<String> kept = withoutArsenic(profile.getValue());
            kept.add(0, agentArg);
            String after = String.join(" ", kept);
            if (after.equals(normalised(profile.getValue())))
                log.accept("Profile '" + profile.getKey() + "' already loads Arsenic from this jar");
            else
                changes.put(profile.getKey(), after);
        }
        return write(db, changes, log);
    }

    /**
     * Takes Arsenic's agent argument out of the launch arguments of every 1.8.9 instance's setting profile.
     *
     * @return true when the database changed
     */
    static boolean removeAgent(File db, Consumer<String> log) throws IOException {
        Map<String, String> profiles = profiles(db, log);
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> profile : profiles.entrySet()) {
            String after = String.join(" ", withoutArsenic(profile.getValue()));
            if (after.equals(normalised(profile.getValue())))
                log.accept("Arsenic is not in profile '" + profile.getKey() + "'");
            else
                changes.put(profile.getKey(), after.isEmpty() ? null : after);
        }
        return write(db, changes, log);
    }

    /** Setting profile name to launch arguments ("" when none), for the profiles used by 1.8.9 instances. */
    private static Map<String, String> profiles(File db, Consumer<String> log) throws IOException {
        for (String[] row : rows(sqlite(db, "SELECT name FROM clusters WHERE mc_version = " + quote(MC_VERSION)
                + " AND (setting_profile_name IS NULL OR setting_profile_name NOT IN (SELECT name FROM setting_profiles));")))
            log.accept("Instance '" + row[0] + "' has no setting profile of its own, skipped");

        Map<String, String> profiles = new LinkedHashMap<>();
        for (String[] row : rows(sqlite(db, "SELECT name, IFNULL(launch_args, '') FROM setting_profiles WHERE name IN "
                + "(SELECT setting_profile_name FROM clusters WHERE mc_version = " + quote(MC_VERSION) + ") ORDER BY name;"))) {
            if (row.length >= 2)
                profiles.put(row[0], row[1]);
        }
        if (profiles.isEmpty())
            throw new IOException("OneClient has no Minecraft " + MC_VERSION + " instance with a setting profile: create one in OneClient first");
        return profiles;
    }

    /** Writes the new launch arguments in one transaction, after a copy of the database next to it. */
    private static boolean write(File db, Map<String, String> changes, Consumer<String> log) throws IOException {
        if (changes.isEmpty())
            return false;
        File backup = new File(db.getParentFile(), db.getName() + ".arsenic-backup");
        Files.deleteIfExists(backup.toPath());
        sqlite(db, "VACUUM INTO " + quote(backup.getPath()) + ";");

        StringBuilder sql = new StringBuilder("BEGIN IMMEDIATE;");
        for (Map.Entry<String, String> change : changes.entrySet()) {
            sql.append(" UPDATE setting_profiles SET launch_args = ")
                    .append(change.getValue() == null ? "NULL" : quote(change.getValue()))
                    .append(" WHERE name = ").append(quote(change.getKey())).append(';');
        }
        sql.append(" COMMIT;");
        sqlite(db, sql.toString());
        for (Map.Entry<String, String> change : changes.entrySet())
            log.accept("Profile '" + change.getKey() + "': launch arguments now "
                    + (change.getValue() == null ? "empty" : change.getValue()));
        return true;
    }

    /** The arguments without Arsenic's agent (whose path may have been split on spaces). */
    private static List<String> withoutArsenic(String value) {
        List<String> tokens = tokens(value);
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (stripQuotes(token).toLowerCase(Locale.ROOT).startsWith("-javaagent:")) {
                StringBuilder whole = new StringBuilder(token);
                int end = i;
                while (end + 1 < tokens.size() && !stripQuotes(tokens.get(end + 1)).startsWith("-"))
                    whole.append(' ').append(tokens.get(++end));
                if (whole.toString().toLowerCase(Locale.ROOT).contains("arsenic")) {
                    i = end;
                    continue;
                }
            }
            kept.add(token);
        }
        return kept;
    }

    private static List<String> tokens(String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : value.trim().split("\\s+"))
            if (!token.isEmpty())
                tokens.add(token);
        return tokens;
    }

    private static String normalised(String value) {
        return String.join(" ", tokens(value));
    }

    private static String stripQuotes(String token) {
        return token.replaceFirst("^[\"']+", "");
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static List<String[]> rows(String output) {
        List<String[]> rows = new ArrayList<>();
        for (String row : output.split(ROW, -1)) {
            String r = row.replaceAll("^[\\r\\n]+", "");
            if (!r.isEmpty())
                rows.add(r.split(FIELD, -1));
        }
        return rows;
    }

    /** Runs one SQL script with the sqlite3 tool and returns its output; fails when sqlite3 reports an error. */
    private static String sqlite(File db, String sql) throws IOException {
        String tool = new File("/usr/bin/sqlite3").canExecute() ? "/usr/bin/sqlite3" : "sqlite3";
        Process p;
        try {
            p = new ProcessBuilder(Arrays.asList(tool, "-batch", "-bail", "-noheader", "-cmd", ".timeout 5000",
                    "-separator", FIELD, "-newline", ROW, db.getPath(), sql)).start();
        } catch (IOException e) {
            throw new IOException("the sqlite3 tool is needed to edit OneClient's settings and was not found", e);
        }
        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());
        int code;
        try {
            code = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while editing OneClient's settings");
        }
        if (code != 0)
            throw new IOException("sqlite3 failed: " + (err.trim().isEmpty() ? "exit code " + code : err.trim()));
        return out;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0)
            bytes.write(buf, 0, n);
        in.close();
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String run(String... command) throws IOException {
        Process p = new ProcessBuilder(Arrays.asList(command)).redirectErrorStream(true).start();
        String out = readAll(p.getInputStream());
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out;
    }
}
