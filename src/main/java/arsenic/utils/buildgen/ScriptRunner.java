package arsenic.utils.buildgen;

import arsenic.utils.buildcore.Blueprint;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles and runs a model's build script (the body of a {@link Canvas} subclass) and checks what it built. The
 * script is screened first: no imports beyond java.util, no packages named in full, none of the classes that reach
 * outside the JVM's maths. This is a guard against a model wandering off, not a sandbox, so only run scripts from
 * models you call yourself. Any {@link JavaCompiler} works: the JDK's own outside the game, the client's Eclipse
 * compiler in it.
 */
public final class ScriptRunner {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(System|Runtime|ProcessBuilder|Process|Thread|ThreadGroup|ClassLoader|Class|getClass|ServiceLoader|Unsafe"
                    + "|File|Files|Paths|Path|Socket|URL|URI|exec|native|reflect|invoke|Executors|ForkJoinPool)\\b"
                    + "|\\b(java\\s*\\.\\s*(?!util\\b)\\w+|javax|sun|jdk|com|org|net|arsenic)\\s*\\.");
    private static final String HEADER = "import java.util.*;\nimport java.util.function.*;\n"
            + "public class Gen extends arsenic.utils.buildgen.Canvas {\n";
    private static final int HEADER_LINES = 3;

    public static final class Result {
        /** The build, or null if the script did not compile or run. */
        public Blueprint bp;
        /** What went wrong, for the model, or null. */
        public String error;
        /** Counts and warnings, for the model. */
        public String report = "";
        public long ms;
    }

    private final JavaCompiler compiler;
    private final String classpath;
    private final Palette palette;
    public int sizeX = 32, sizeY = 32, sizeZ = 32;
    public long runMillis = 5000;

    public ScriptRunner(Palette palette) {
        this(palette, ToolProvider.getSystemJavaCompiler(), defaultClasspath());
    }

    public ScriptRunner(Palette palette, JavaCompiler compiler, String classpath) {
        this.palette = palette;
        this.compiler = compiler;
        this.classpath = classpath;
    }

    /** The java code in a reply: the last ```java block, or the whole reply if there is none. */
    public static String extractCode(String reply) {
        Matcher m = Pattern.compile("```(?:java)?\\s*\\n(.*?)```", Pattern.DOTALL).matcher(reply);
        String code = null;
        while (m.find()) code = m.group(1);
        if (code == null) return null;
        // a whole class instead of its body: keep the body
        Matcher c = Pattern.compile("class\\s+(\\w+)\\s+extends\\s+Canvas\\s*\\{").matcher(code);
        if (c.find()) {
            int end = code.lastIndexOf('}');
            String name = c.group(1);
            code = code.substring(c.end(), end > c.end() ? end : code.length());
            // its constructors are now Gen's
            code = code.replaceAll("\\b" + name + "\\s*\\(", "Gen(");
        }
        return code.replaceAll("(?m)^\\s*import\\s+java\\.util[\\w.]*\\*?\\s*;\\s*$", "");
    }

    public Result run(String code, int target) {
        long start = System.nanoTime();
        Result r = new Result();
        try {
            r.error = go(code, target, r);
        } catch (Throwable t) {
            r.error = "the runner failed: " + t;
        }
        r.ms = (System.nanoTime() - start) / 1_000_000;
        return r;
    }

    private String go(String code, int target, Result r) throws Exception {
        String stripped = code.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"").replaceAll("//[^\\n]*", "").replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher bad = FORBIDDEN.matcher(stripped);
        if (bad.find()) return "not allowed in a build script: '" + bad.group().trim() + "'. Use only the Canvas methods, Math and java.util.";
        if (!stripped.contains("void build()")) return "the code has no `public void build()` method.";

        Path dir = Files.createTempDirectory("buildgen");
        try {
            String src = HEADER + code + "\n}\n";
            DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
            StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8);
            fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(dir.toFile()));
            JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Gen.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignore) {
                    return src;
                }
            };
            boolean ok = compiler.getTask(null, fm, diags, List.of("-classpath", classpath, "-proc:none", "-nowarn", "-source", "21", "-target", "21"),
                    null, List.of(file)).call();
            fm.close();
            if (!ok) {
                StringBuilder sb = new StringBuilder("compile errors:\n");
                int n = 0;
                for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                    if (d.getKind() != Diagnostic.Kind.ERROR || n++ >= 8) continue;
                    sb.append("line ").append(d.getLineNumber() - HEADER_LINES).append(": ").append(d.getMessage(null)).append('\n');
                }
                return sb.toString();
            }

            Blueprint bp = new Blueprint(sizeX, sizeY, sizeZ);
            Canvas canvas;
            try (URLClassLoader cl = new URLClassLoader(new URL[]{dir.toUri().toURL()}, Canvas.class.getClassLoader())) {
                canvas = (Canvas) cl.loadClass("Gen").getDeclaredConstructor().newInstance();
                canvas.setUp(bp, palette, System.nanoTime() + runMillis * 1_000_000);
                Throwable[] thrown = new Throwable[1];
                Thread t = new Thread(() -> {
                    try {
                        canvas.build();
                    } catch (Throwable e) {
                        thrown[0] = e;
                    }
                }, "buildgen script");
                t.setDaemon(true);
                t.start();
                t.join(runMillis + 1000);
                if (t.isAlive()) return "the script ran for over " + runMillis / 1000 + "s (an endless loop?)";
                if (thrown[0] != null) return "the script threw " + describe(thrown[0]);
            }
            if (bp.solidCount() == 0) return "the script placed no blocks.";
            r.bp = bp;
            r.report = report(bp, canvas, target);
            return null;
        } finally {
            try (var s = Files.walk(dir)) {
                s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().equals("Gen")) {
                sb.append(" at line ").append(e.getLineNumber() - HEADER_LINES).append(" (").append(e.getMethodName()).append(')');
                break;
            }
        }
        return sb.toString();
    }

    /** Facts about the build for the model to check against the prompt. */
    String report(Blueprint bp, Canvas canvas, int target) {
        StringBuilder sb = new StringBuilder();
        int n = bp.solidCount();
        sb.append("blocks: ").append(n);
        if (target > 0) sb.append(" (target about ").append(target).append(n > target * 1.3 ? ", too many" : n < target * 0.7 ? ", too few" : "").append(')');
        sb.append('\n');
        int x0 = 99999, y0 = 99999, z0 = 99999, x1 = -1, y1 = -1, z1 = -1;
        Map<String, Integer> use = new TreeMap<>();
        for (int y = 0; y < bp.sizeY; y++)
            for (int z = 0; z < bp.sizeZ; z++)
                for (int x = 0; x < bp.sizeX; x++) {
                    int id = bp.get(x, y, z);
                    if (id <= 0) continue;
                    use.merge(bp.palette.get(id).replace("minecraft:", ""), 1, Integer::sum);
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    z0 = Math.min(z0, z);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                    z1 = Math.max(z1, z);
                }
        sb.append(String.format("occupies x %d..%d, y %d..%d, z %d..%d of the %dx%dx%d box%n", x0, x1, y0, y1, z0, z1, bp.sizeX, bp.sizeY, bp.sizeZ));
        List<Map.Entry<String, Integer>> list = new ArrayList<>(use.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        sb.append("blocks used: ");
        for (int i = 0; i < list.size(); i++) sb.append(i == 0 ? "" : ", ").append(list.get(i).getKey()).append(' ').append(list.get(i).getValue());
        sb.append('\n');
        int[] parts = components(bp);
        sb.append("connected parts: ").append(parts[0]).append(" (largest ").append(parts[1]).append(" blocks, ").append(parts[2])
                .append(" single blocks touching nothing)\n");
        if (canvas.outside > 0) sb.append("WARNING: ").append(canvas.outside).append(" blocks were outside the box and skipped\n");
        for (Map.Entry<String, Integer> e : canvas.unknown.entrySet())
            sb.append("WARNING: '").append(e.getKey()).append("' is not in the palette (").append(e.getValue())
                    .append(" blocks skipped); closest: ").append(String.join(", ", canvas.palette().closest(e.getKey(), 3))).append('\n');
        return sb.toString();
    }

    /** Number of 6-connected parts, the largest's size and how many blocks have no neighbour at all. */
    private static int[] components(Blueprint bp) {
        int sx = bp.sizeX, sy = bp.sizeY, sz = bp.sizeZ;
        int[] seen = new int[sx * sy * sz];
        int parts = 0, largest = 0, single = 0;
        int[] stack = new int[seen.length];
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < seen.length; i++) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            if (seen[i] != 0 || bp.get(x, y, z) <= 0) continue;
            parts++;
            int size = 0, sp = 0;
            stack[sp++] = i;
            seen[i] = 1;
            while (sp > 0) {
                int c = stack[--sp];
                size++;
                int cx = c % sx, cz = (c / sx) % sz, cy = c / (sx * sz);
                for (int[] d : dirs) {
                    int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                    if (bp.get(nx, ny, nz) <= 0) continue;
                    int j = (ny * sz + nz) * sx + nx;
                    if (seen[j] != 0) continue;
                    seen[j] = 1;
                    stack[sp++] = j;
                }
            }
            largest = Math.max(largest, size);
            if (size == 1) single++;
        }
        return new int[]{parts, largest, single};
    }

    private static String defaultClasspath() {
        List<String> cp = new ArrayList<>();
        try {
            cp.add(new File(Canvas.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
        } catch (Exception ignored) {
        }
        cp.add(System.getProperty("java.class.path"));
        return String.join(File.pathSeparator, cp);
    }
}
