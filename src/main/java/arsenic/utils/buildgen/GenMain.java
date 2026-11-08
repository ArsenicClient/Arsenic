package arsenic.utils.buildgen;

import arsenic.utils.buildcore.Blueprint;
import arsenic.utils.buildcore.Schematic;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the generator from the command line, outside the game:
 * {@code java arsenic.utils.buildgen.GenMain provider=claude|ollama model=... effort=low think=false seconds=60
 * target=500 palette=detailed size=32 out=dir prompt="..."}. Writes each round's reply and render, the final
 * render, final.schem and example.json (a training example). {@code style=} is extra direction for the teacher that the
 * example leaves out (empty for none); {@code polish=false} stops at the first build that runs, so further rounds only
 * repair failed code. {@code GenMain montage out.png label=a.png ...} puts finished renders side by side.
 */
public final class GenMain {
    static final String DEFAULT_STYLE = "Super detailed, full blocks, use blocks relevant to the theme";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("montage")) {
            montage(args);
            return;
        }
        Map<String, String> a = new LinkedHashMap<>();
        for (String s : args) {
            String[] kv = s.split("=", 2);
            a.put(kv[0], kv.length > 1 ? kv[1] : "true");
        }
        // the mode is a bare first word, so option names (rate=, themes=) can never select one
        String mode = args.length > 0 && !args[0].contains("=") ? args[0] : "";
        switch (mode) {
            case "rate" -> Rate.serve(a);
            case "export" -> Rate.export(a);
            case "pack" -> Rate.pack(a);
            case "brief", "step" -> step(a);
            case "bulk" -> Bulk.run(a);
            case "themes" -> Themes.generate(a);
            case "" -> run(a, llm(a), a.getOrDefault("prompt", "highly detailed galaxy"), Path.of(a.getOrDefault("out", "buildgen-out")), true);
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    /** The model named by provider= (deepseek, claude or ollama) and its options. */
    static Llm llm(Map<String, String> a) throws Exception {
        if (a.getOrDefault("provider", "claude").equals("deepseek")) {
            String key = System.getenv("DEEPSEEK_API_KEY");
            if (key == null || key.isBlank()) throw new IllegalStateException("set DEEPSEEK_API_KEY");
            Llm.DeepSeek d = new Llm.DeepSeek(a.getOrDefault("model", "deepseek-flash"), a.getOrDefault("effort", "high"), key);
            d.maxTokens = Integer.parseInt(a.getOrDefault("maxtokens", "0"));
            return d;
        }
        if (a.getOrDefault("provider", "claude").equals("claude")) {
            String key = System.getenv("ANTHROPIC_API_KEY");
            if (key == null || key.isBlank()) throw new IllegalStateException("set ANTHROPIC_API_KEY");
            return new Llm.Claude(a.getOrDefault("model", "claude-sonnet-5-5"), a.getOrDefault("effort", "low"), key);
        }
        Llm.Ollama o = new Llm.Ollama(a.getOrDefault("model", "qwen3.5:9b"), Boolean.parseBoolean(a.getOrDefault("think", "false")));
        o.numCtx = Integer.parseInt(a.getOrDefault("ctx", "16384"));
        o.numPredict = Integer.parseInt(a.getOrDefault("predict", "12000"));
        o.warm();
        return o;
    }

    /**
     * One theme into one folder: the rounds, final render, final.schem and example.json. Returns the finished Apt
     * ({@code best} is null when no build ran); echo prints each round as it goes.
     */
    static Apt run(Map<String, String> a, Llm llm, String prompt, Path out, boolean echo) throws Exception {
        int seconds = Integer.parseInt(a.getOrDefault("seconds", "60"));
        int target = Integer.parseInt(a.getOrDefault("target", "500"));
        int size = Integer.parseInt(a.getOrDefault("size", "32"));
        Files.createDirectories(out);
        Palette palette = new Palette(Palette.Level.valueOf(a.getOrDefault("palette", "detailed").toUpperCase()));
        ScriptRunner runner = new ScriptRunner(palette);
        runner.sizeX = runner.sizeY = runner.sizeZ = size;
        Apt apt = new Apt(llm, palette, runner);
        apt.maxRounds = Integer.parseInt(a.getOrDefault("rounds", "0"));
        apt.polish = Boolean.parseBoolean(a.getOrDefault("polish", "true"));
        apt.style = a.getOrDefault("style", DEFAULT_STYLE);
        long start = System.nanoTime();
        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(out.resolve("log.txt")))) {
            log.printf("%s, %ds, palette %s (%d blocks), target %d, box %d%n%n", llm.name(), seconds,
                    palette.level, palette.blocks.size(), target, size);
            apt.onRound = r -> {
                double t = (r.endMs - start) / 1e9;
                String line = String.format("round %d %-9s at %5.1fs  %s  %s", r.n, r.kind, t,
                        r.reply == null ? "" : String.format("(%d in / %d out tokens, %.1fs)", r.reply.inTokens(), r.reply.outTokens(), r.reply.ms() / 1000.0),
                        r.error != null ? "ERROR " + r.error.replace('\n', ' ') : r.report.split("\n")[0]);
                if (echo) System.out.println(line);
                log.println(line);
                log.flush();
                try {
                    if (r.reply != null) Files.writeString(out.resolve("round" + r.n + ".txt"), r.reply.text());
                    if (r.render != null) ImageIO.write(r.render, "png", out.resolve("round" + r.n + ".png").toFile());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            Blueprint bp = apt.run(prompt, target, start + seconds * 1_000_000_000L);
            double took = (System.nanoTime() - start) / 1e9;
            if (bp == null) {
                log.printf("%nno build in %.1fs%n", took);
                if (echo) System.out.printf("no build in %.1fs%n", took);
                return apt;
            }
            Blueprint t = bp.trimmed();
            ImageIO.write(Render.sheet(t, palette, prompt + " - " + llm.name() + ", " + seconds + "s budget"), "png", out.resolve("final.png").toFile());
            try (FileOutputStream f = new FileOutputStream(out.resolve("final.schem").toFile())) {
                Schematic.write(t, f, 5023);
            }
            Files.writeString(out.resolve("final.java"), apt.bestCode);
            writeExample(out, prompt, target, apt, t);
            String done = String.format("%nfinal: %d blocks, %dx%dx%d, %d rounds, done at %.1fs", t.solidCount(), t.sizeX, t.sizeY, t.sizeZ, apt.rounds.size(), took);
            log.println(done);
            if (echo) System.out.println(done);
            return apt;
        }
    }

    /**
     * One training example: the student is given the task without the style line, and learns the teacher reply that
     * produced the kept build.
     */
    private static void writeExample(Path out, String prompt, int target, Apt apt, Blueprint t) throws Exception {
        JsonObject ex = new JsonObject();
        ex.addProperty("theme", prompt);
        ex.addProperty("style", apt.style);
        ex.addProperty("target", target);
        ex.addProperty("teacher", apt.llm.name());
        ex.addProperty("student_task", Apt.task(prompt, "", target));
        ex.addProperty("reply", apt.bestReply);
        ex.addProperty("reasoning", apt.bestReasoning);
        ex.addProperty("code", apt.bestCode);
        ex.addProperty("report", apt.bestReport);
        ex.addProperty("blocks", t.solidCount());
        ex.addProperty("rounds", apt.rounds.size());
        int in = 0, outTok = 0;
        for (Apt.Round r : apt.rounds) {
            if (r.reply == null) continue;
            in += r.reply.inTokens();
            outTok += r.reply.outTokens();
        }
        ex.addProperty("in_tokens", in);
        ex.addProperty("out_tokens", outTok);
        Files.writeString(out.resolve("example.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(ex));
    }

    /**
     * For a model that drives the loop itself (an agent with a shell): {@code brief} prints the system text and task;
     * {@code step code=file.java} runs one round against the clock started in out/start.txt, writes its render and
     * keeps it as the final build if it ran. Rounds after the time limit are refused.
     */
    private static void step(Map<String, String> a) throws Exception {
        int seconds = Integer.parseInt(a.getOrDefault("seconds", "60"));
        int target = Integer.parseInt(a.getOrDefault("target", "500"));
        int size = Integer.parseInt(a.getOrDefault("size", "32"));
        String prompt = a.getOrDefault("prompt", "highly detailed galaxy");
        Path out = Path.of(a.getOrDefault("out", "buildgen-out"));
        Palette palette = new Palette(Palette.Level.valueOf(a.getOrDefault("palette", "detailed").toUpperCase()));
        ScriptRunner runner = new ScriptRunner(palette);
        runner.sizeX = runner.sizeY = runner.sizeZ = size;
        Apt apt = new Apt(null, palette, runner);
        if (a.containsKey("brief")) {
            System.out.println(apt.system() + "\n" + apt.task(prompt, target));
            return;
        }
        long start = Long.parseLong(Files.readString(out.resolve("start.txt")).trim());
        double used = (System.currentTimeMillis() - start) / 1000.0;
        if (used > seconds) {
            System.out.printf("TIME UP (%.1fs of %ds used). This round is not counted; your last build that ran stands.%n", used, seconds);
            return;
        }
        String code = ScriptRunner.extractCode("```java\n" + Files.readString(Path.of(a.get("code"))) + "\n```");
        ScriptRunner.Result res = runner.run(code, target);
        int n = 1;
        while (Files.exists(out.resolve("round" + n + ".java"))) n++;
        Files.writeString(out.resolve("round" + n + ".java"), code);
        double now = (System.currentTimeMillis() - start) / 1000.0;
        String line;
        if (res.error != null) {
            line = String.format("round %d at %5.1fs  ERROR %s", n, now, res.error.replace('\n', ' '));
            System.out.println(res.error);
        } else {
            Path png = out.resolve("round" + n + ".png");
            ImageIO.write(Render.sheet(res.bp, palette, prompt + " - round " + n), "png", png.toFile());
            Blueprint t = res.bp.trimmed();
            ImageIO.write(Render.sheet(t, palette, prompt + " - " + a.getOrDefault("label", "agent") + ", " + seconds + "s budget"), "png", out.resolve("final.png").toFile());
            try (FileOutputStream f = new FileOutputStream(out.resolve("final.schem").toFile())) {
                Schematic.write(t, f, 5023);
            }
            Files.writeString(out.resolve("final.java"), code);
            line = String.format("round %d at %5.1fs  %s", n, now, res.report.split("\n")[0]);
            System.out.println("render: " + png.toAbsolutePath() + "\n" + apt.reflect(prompt, target, res.report));
        }
        Files.writeString(out.resolve("log.txt"), line + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        System.out.printf("time used: %.1fs of %ds%n", now, seconds);
    }

    private static void montage(String[] args) throws Exception {
        int n = args.length - 2;
        BufferedImage[] imgs = new BufferedImage[n];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            String[] kv = args[i + 2].split("=", 2);
            labels[i] = kv[0];
            imgs[i] = ImageIO.read(Path.of(kv[1]).toFile());
        }
        int cols = n <= 2 ? n : 2, rows = (n + cols - 1) / cols;
        int w = imgs[0].getWidth() / 2, h = imgs[0].getHeight() / 2 + 30;
        BufferedImage out = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x20262c));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        for (int i = 0; i < n; i++) {
            int x = i % cols * w, y = i / cols * h;
            g.setColor(Color.WHITE);
            g.drawString(labels[i], x + 8, y + 21);
            // the top-left (isometric) quarter of each sheet, at full size
            g.drawImage(imgs[i], x, y + 30, x + w, y + h, 0, 28, w, 28 + h - 30, null);
        }
        g.dispose();
        ImageIO.write(out, "png", Path.of(args[1]).toFile());
    }
}
