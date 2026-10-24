package arsenic.inject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Launchers split JVM arguments on spaces, so a path like "Arsenic (1).jar" leaves a word such as "(1).jar" behind,
 * which Java takes for the main class: the game exits with "Could not find or load main class (1).jar". The agent
 * runs before that happens, so it starts the game again without the stray words and stands in for it until it exits
 * (same output, same exit code, so the launcher sees one normal game). Lunar's settings are repaired too, so the
 * next launch is clean.
 */
final class SplitArgumentFix {

    /** JVM options whose value is the next word ("-cp x"). */
    private static final List<String> VALUE_OPTIONS = Arrays.asList("-cp", "-classpath", "--class-path", "-p",
            "--module-path", "--upgrade-module-path", "--add-modules", "--limit-modules", "--add-opens", "--add-exports",
            "--add-reads", "--patch-module", "--enable-native-access", "-jar");

    private SplitArgumentFix() {}

    /** The stray word Java is about to take for the main class, or null when the launch is fine. */
    static String strayMainClass() {
        String command = System.getProperty("sun.java.command", "").trim();
        int space = command.indexOf(' ');
        String main = space < 0 ? command : command.substring(0, space);
        // "java -jar game.jar" also names a jar here, but that one exists
        if (main.isEmpty() || isClassName(main) || new File(main).isFile())
            return null;
        return main;
    }

    /** Fixes the launch. Only returns when it could not; on success this JVM exits with the game. */
    static void fix(String stray, Consumer<String> status) {
        repairLunarSettings(stray, status);
        List<String> command;
        try {
            command = ownCommandLine();
        } catch (Exception e) {
            status.accept("ERROR \"" + stray + "\" in the JVM arguments stops the game, and the command line could not be read to fix it: " + e);
            return;
        }
        List<String> fixed = withoutStrayWords(command);
        if (fixed.size() == command.size()) {
            status.accept("ERROR \"" + stray + "\" in the JVM arguments stops the game, and it could not be taken out");
            return;
        }
        String java = new File(System.getProperty("java.home"), "bin" + File.separator
                + (windows() ? "java.exe" : "java")).getAbsolutePath();
        fixed.set(0, java);
        status.accept("Removed \"" + stray + "\" from the JVM arguments (a path with a space, split by the launcher); starting the game again");
        try {
            Process game = new ProcessBuilder(fixed).inheritIO().start();
            Runtime.getRuntime().addShutdownHook(new Thread(game::destroy));
            System.exit(game.waitFor());
        } catch (Exception e) {
            status.accept("ERROR Could not start the game again: " + e);
        }
    }

    /** The command line without the words Java would take for the main class before the real one. */
    static List<String> withoutStrayWords(List<String> command) {
        List<String> kept = new ArrayList<>();
        kept.add(command.get(0));
        boolean expectsValue = false;
        for (int i = 1; i < command.size(); i++) {
            String word = command.get(i);
            if (expectsValue) {
                kept.add(word);
                expectsValue = false;
            } else if (word.startsWith("-")) {
                kept.add(word);
                expectsValue = VALUE_OPTIONS.contains(word);
                if (word.equals("-jar")) {
                    // the jar ends the options like a main class does: the rest is the game's
                    kept.addAll(command.subList(i + 1, command.size()));
                    break;
                }
            } else if (isClassName(word)) {
                // the real main class: everything after it belongs to the game
                kept.addAll(command.subList(i, command.size()));
                break;
            }
            // anything else is a stray word, dropped
        }
        return kept;
    }

    /** True for a Java class name such as com.example.Main ("(1).jar" and path pieces are not). */
    private static boolean isClassName(String word) {
        if (word.endsWith(".jar"))
            return false;
        for (String part : word.split("[./]", -1)) {
            if (part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0)))
                return false;
            for (int i = 1; i < part.length(); i++)
                if (!Character.isJavaIdentifierPart(part.charAt(i)))
                    return false;
        }
        return true;
    }

    /**
     * Takes the split argument out of Lunar's settings and puts this jar's agent argument in. Only when the stray word
     * is in Lunar's settings: another launcher's broken arguments are none of Lunar's business.
     */
    private static void repairLunarSettings(String stray, Consumer<String> status) {
        try {
            File settings = LunarInjection.settingsFile();
            if (!settings.isFile() || !new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8).contains(stray))
                return;
            if (LunarInjection.setAgent(settings, Injector.agentArg(), line -> {}))
                status.accept("Repaired the JVM arguments in Lunar's settings");
        } catch (Exception e) {
            status.accept("Could not repair Lunar's settings: " + e);
        }
    }

    private static List<String> ownCommandLine() throws Exception {
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        if (windows()) {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding = [Text.Encoding]::UTF8; (Get-CimInstance Win32_Process -Filter 'ProcessId = " + pid + "').CommandLine")
                    .redirectErrorStream(true).start();
            String line;
            try (java.io.InputStream in = p.getInputStream()) {
                line = new String(readAll(in), StandardCharsets.UTF_8).trim();
            }
            p.waitFor();
            if (line.isEmpty())
                throw new IOException("no command line for process " + pid);
            return windowsArguments(line);
        }
        // Linux: NUL-separated, exact
        if (new File("/proc/self/cmdline").isFile()) {
            byte[] raw = Files.readAllBytes(Paths.get("/proc/self/cmdline"));
            List<String> args = new ArrayList<>(Arrays.asList(new String(raw, StandardCharsets.UTF_8).split("\0")));
            if (!args.isEmpty())
                return args;
        }
        // macOS: ProcessHandle (Java 9+, which every launcher's game runs on) reads the exact arguments; by
        // reflection because the agent is built for Java 8
        Class<?> handle = Class.forName("java.lang.ProcessHandle");
        Object info = handle.getMethod("info").invoke(handle.getMethod("current").invoke(null));
        Object command = ((java.util.Optional<?>) info.getClass().getMethod("command").invoke(info)).orElse(null);
        Object arguments = ((java.util.Optional<?>) info.getClass().getMethod("arguments").invoke(info)).orElse(null);
        if (command == null || arguments == null)
            throw new IOException("no command line for process " + pid);
        List<String> args = new ArrayList<>();
        args.add((String) command);
        args.addAll(Arrays.asList((String[]) arguments));
        return args;
    }

    /** Splits a Windows command line the way the C runtime does (CommandLineToArgvW). */
    static List<String> windowsArguments(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false, any = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                int slashes = 0;
                while (i < line.length() && line.charAt(i) == '\\') {
                    slashes++;
                    i++;
                }
                if (i < line.length() && line.charAt(i) == '"') {
                    for (int s = 0; s < slashes / 2; s++)
                        current.append('\\');
                    if (slashes % 2 == 1) {
                        current.append('"');
                        i++;
                    }
                } else {
                    for (int s = 0; s < slashes; s++)
                        current.append('\\');
                }
                any = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                any = true;
            } else if ((c == ' ' || c == '\t') && !inQuotes) {
                if (any)
                    args.add(current.toString());
                current.setLength(0);
                any = false;
            } else {
                current.append(c);
                any = true;
            }
            i++;
        }
        if (any)
            args.add(current.toString());
        return args;
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0)
            out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
