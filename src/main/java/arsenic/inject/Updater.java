package arsenic.inject;

import arsenic.module.BuildTiers;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the jar up to date from the latest GitHub release. On start the injector asks for the latest release and, when
 * it is newer than this jar, downloads the jar of this build's tier next to the running one. A running jar cannot be
 * replaced on Windows, so the downloaded jar is started as {@code UpdateSwap}: it waits for this process to end, moves
 * itself over the old file (same name, so Lunar's saved agent path keeps working) and opens the injector again.
 *
 * Nothing here may stop the injector from starting: every failure is logged and the current jar carries on.
 */
public final class Updater {

    private static final String LATEST = "https://api.github.com/repos/ArsenicClient/Arsenic/releases/latest";
    private static final String SWAP = "--swap";
    private static final int CONNECT_MS = 4_000;
    private static final int READ_MS = 15_000;
    private static final long MAX_BYTES = 128L * 1024 * 1024;

    private Updater() {
    }

    private static File settingsFile() {
        return new File(System.getProperty("user.home"), BuildTiers.folderName() + File.separator + "updater.properties");
    }

    /** Whether the injector updates itself on start. On unless the user switched it off in the window. */
    public static boolean isEnabled() {
        File file = settingsFile();
        if (!file.isFile())
            return true;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            return !"false".equals(p.getProperty("autoupdate"));
        } catch (Exception e) {
            return true;
        }
    }

    public static void setEnabled(boolean enabled) {
        try {
            File file = settingsFile();
            file.getParentFile().mkdirs();
            java.util.Properties p = new java.util.Properties();
            p.setProperty("autoupdate", String.valueOf(enabled));
            try (java.io.OutputStream out = Files.newOutputStream(file.toPath())) {
                p.store(out, "Arsenic updater");
            }
        } catch (Exception e) {
            log("could not save the setting: " + e);
        }
    }

    /**
     * Checks for a newer release and, if there is one, starts the swap and returns true; the caller must then exit.
     * Skipped when switched off, for development builds, a jar that is not a file (IDE run) and
     * {@code -Darsenic.noupdate=true}.
     */
    public static boolean updateAndRestart() {
        try {
            if (Boolean.getBoolean("arsenic.noupdate") || !isEnabled())
                return false;
            String local = localVersion();
            File jar = Injector.ownJar();
            if (local == null || !jar.isFile() || !jar.getName().endsWith(".jar") || !jar.canWrite())
                return false;

            String release = get(LATEST);
            String tag = string(release, "tag_name");
            if (tag == null || compare(tag, local) <= 0)
                return false;

            String classifier = classifier();
            Asset asset = findAsset(release, classifier);
            if (asset == null) {
                log("release " + tag + " has no " + classifier + " jar");
                return false;
            }

            log("updating " + local + " -> " + tag);
            File download = new File(jar.getParentFile(), jar.getName() + ".update");
            download(asset, download);
            verify(download, classifier);
            return startSwap(download, jar);
        } catch (Throwable t) {
            log("update skipped: " + t);
            return false;
        }
    }

    // ---- the swap, run from the downloaded jar ----

    /** {@code java -cp new.jar arsenic.inject.Updater --swap <new> <old>} */
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !SWAP.equals(args[0]))
            return;
        Path update = new File(args[1]).toPath();
        Path target = new File(args[2]).toPath();

        // the old process may take a moment to exit, and Windows refuses while it still has the jar open
        boolean moved = false;
        for (int attempt = 0; attempt < 120 && !moved; attempt++) {
            try {
                Files.move(update, target, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
        if (!moved) {
            log("could not replace " + target + "; it will be tried again next start");
            return;
        }
        // double-clicking needs the execute bit back on macOS and Linux
        target.toFile().setExecutable(true);
        new ProcessBuilder(javaExecutable(), "-jar", target.toString()).inheritIO().start();
    }

    private static boolean startSwap(File update, File jar) throws Exception {
        // run from a copy: the swap moves the downloaded file, which it cannot do while it is running from it
        File runner = File.createTempFile("arsenic-swap", ".jar");
        runner.deleteOnExit();
        Files.copy(update.toPath(), runner.toPath(), StandardCopyOption.REPLACE_EXISTING);
        new ProcessBuilder(javaExecutable(), "-cp", runner.getAbsolutePath(), Updater.class.getName(),
                SWAP, update.getAbsolutePath(), jar.getAbsolutePath()).start();
        return true;
    }

    private static String javaExecutable() {
        File bin = new File(System.getProperty("java.home"), "bin");
        File exe = new File(bin, System.getProperty("os.name", "").toLowerCase().contains("win") ? "javaw.exe" : "java");
        return exe.isFile() ? exe.getPath() : "java";
    }

    // ---- release lookup ----

    private static final class Asset {
        final String name;
        final String url;
        final String sha256;

        Asset(String name, String url, String sha256) {
            this.name = name;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    /** Which release jar this is: the folder name written by build.gradle maps back to the jar's classifier. */
    private static String classifier() {
        switch (BuildTiers.folderName()) {
            case "Arsenic-QOL": return "qol";
            case "Arsenic-Visual": return "visual";
            case "Arsenic-Legit": return "legit";
            case "Arsenic-Dev": return "dev";
            default: return "main";
        }
    }

    private static Asset findAsset(String release, String classifier) {
        Matcher m = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(release);
        while (m.find()) {
            String name = m.group(1);
            if (!name.endsWith("-" + classifier + ".jar"))
                continue;
            // the fields of this asset's object run up to the closing brace
            int end = release.indexOf('}', m.end());
            int start = release.lastIndexOf('{', m.start());
            if (start < 0 || end < 0)
                continue;
            String asset = release.substring(start, end);
            String url = string(asset, "browser_download_url");
            String digest = string(asset, "digest");
            if (url != null)
                return new Asset(name, url, digest != null && digest.startsWith("sha256:") ? digest.substring(7) : null);
        }
        return null;
    }

    private static String string(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ---- versions: "v3.123" ----

    private static String localVersion() {
        String version = Updater.class.getPackage() == null ? null : Updater.class.getPackage().getImplementationVersion();
        return version != null && version.matches("v?\\d+(\\.\\d+)*") ? version : null;
    }

    static int compare(String a, String b) {
        List<Integer> x = numbers(a);
        List<Integer> y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int p = i < x.size() ? x.get(i) : 0;
            int q = i < y.size() ? y.get(i) : 0;
            if (p != q)
                return Integer.compare(p, q);
        }
        return 0;
    }

    private static List<Integer> numbers(String version) {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+").matcher(version);
        while (m.find())
            out.add(Integer.parseInt(m.group()));
        return out;
    }

    // ---- network ----

    private static HttpURLConnection open(String address) throws Exception {
        URL url = new URL(address);
        String host = url.getHost();
        if (!"https".equals(url.getProtocol()) || !(host.equals("api.github.com") || host.equals("github.com")
                || host.endsWith(".githubusercontent.com")))
            throw new IllegalStateException("refusing " + address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_MS);
        connection.setReadTimeout(READ_MS);
        connection.setRequestProperty("User-Agent", "Arsenic-Updater");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static String get(String address) throws Exception {
        HttpURLConnection connection = open(address);
        try (InputStream in = connection.getInputStream()) {
            return new String(readAll(in, 4L * 1024 * 1024), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void download(Asset asset, File to) throws Exception {
        // GitHub redirects downloads to another host; follow it by hand so every hop is checked by open()
        String address = asset.url;
        HttpURLConnection connection = null;
        for (int hop = 0; hop < 4; hop++) {
            connection = open(address);
            connection.setInstanceFollowRedirects(false);
            int code = connection.getResponseCode();
            if (code / 100 == 3) {
                address = connection.getHeaderField("Location");
                connection.disconnect();
                connection = null;
                continue;
            }
            if (code != 200)
                throw new IllegalStateException("download answered " + code);
            break;
        }
        if (connection == null)
            throw new IllegalStateException("too many redirects");
        try (InputStream in = connection.getInputStream()) {
            Files.write(to.toPath(), readAll(in, MAX_BYTES));
        } finally {
            connection.disconnect();
        }
        if (asset.sha256 != null && !asset.sha256.equalsIgnoreCase(sha256(to))) {
            Files.deleteIfExists(to.toPath());
            throw new IllegalStateException("checksum of " + asset.name + " does not match");
        }
    }

    private static void verify(File file, String classifier) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            String main = jar.getManifest() == null ? null : jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (!"arsenic.inject.Injector".equals(main)) {
                Files.deleteIfExists(file.toPath());
                throw new IllegalStateException("downloaded file is not an Arsenic jar");
            }
        }
    }

    private static byte[] readAll(InputStream in, long limit) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > limit)
                throw new IllegalStateException("response too large");
        }
        return out.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(Files.readAllBytes(file.toPath())))
            hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static void log(String message) {
        System.out.println("[Arsenic updater] " + message);
    }
}
