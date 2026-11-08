package arsenic.utils.buildgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Training data in bulk: {@code GenMain bulk themes=training/themes/train.txt out=training/batch1 from=0 count=1000
 * jobs=24 budget=20 rate=peak} plus the usual run options (provider, effort, target, palette, size, seconds, rounds,
 * polish, style, maxtokens). Each theme gets its own folder as a single run would; folders that already hold
 * example.json or failed.txt are skipped, so a stopped batch picks up where it left off. Every finished build adds a
 * line to out/examples.jsonl. New themes stop being started once the estimated spend reaches budget dollars, priced at
 * DeepSeek's peak rate unless rate=offpeak (half), so the estimate errs high.
 */
public final class Bulk {
    public static void run(Map<String, String> a) throws Exception {
        List<String> themes = Files.readAllLines(Path.of(a.getOrDefault("themes", "training/themes/train.txt"))).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        int from = Integer.parseInt(a.getOrDefault("from", "0"));
        int count = Integer.parseInt(a.getOrDefault("count", String.valueOf(themes.size())));
        List<String> slice = themes.subList(Math.min(from, themes.size()), Math.min(from + count, themes.size()));
        Path out = Path.of(a.getOrDefault("out", "training/batch1"));
        Files.createDirectories(out);
        int jobs = Integer.parseInt(a.getOrDefault("jobs", "24"));
        double budget = Double.parseDouble(a.getOrDefault("budget", "20"));
        double scale = a.getOrDefault("rate", "peak").equals("offpeak") ? 1 : 2;
        double inPrice = 0.15e-6 * scale, outPrice = 0.60e-6 * scale;
        Llm llm = GenMain.llm(a);

        double[] spent = {0};
        AtomicInteger done = new AtomicInteger(), built = new AtomicInteger(), failed = new AtomicInteger(), skipped = new AtomicInteger();
        // what earlier runs into this folder already spent counts against the budget
        for (Path ex : listExamples(out)) spent[0] += cost(ex, inPrice, outPrice);
        System.out.printf("%d themes, %d at a time, budget $%.2f (%s prices), $%.3f already spent in %s%n",
                slice.size(), jobs, budget, scale == 1 ? "off-peak" : "peak", spent[0], out);

        ExecutorService pool = Executors.newFixedThreadPool(jobs);
        long start = System.nanoTime();
        for (String theme : slice) {
            Path dir = out.resolve(slug(theme));
            if (Files.exists(dir.resolve("example.json")) || Files.exists(dir.resolve("failed.txt"))) {
                skipped.incrementAndGet();
                continue;
            }
            pool.submit(() -> {
                synchronized (spent) {
                    if (spent[0] >= budget) return;
                }
                try {
                    Apt apt = GenMain.run(a, llm, theme, dir, false);
                    int in = 0, outTok = 0;
                    for (Apt.Round r : apt.rounds)
                        if (r.reply != null) {
                            in += r.reply.inTokens();
                            outTok += r.reply.outTokens();
                        }
                    double c = in * inPrice + outTok * outPrice, total;
                    synchronized (spent) {
                        spent[0] += c;
                        total = spent[0];
                    }
                    String line;
                    if (apt.best == null) {
                        failed.incrementAndGet();
                        String why = apt.rounds.isEmpty() ? "no rounds" : String.valueOf(apt.rounds.getLast().error);
                        Files.writeString(dir.resolve("failed.txt"), why + "\n");
                        line = "FAILED " + why.split("\n")[0];
                    } else {
                        built.incrementAndGet();
                        String ex = Files.readString(dir.resolve("example.json"));
                        JsonObject o = JsonParser.parseString(ex).getAsJsonObject();
                        o.addProperty("dir", dir.getFileName().toString());
                        synchronized (Bulk.class) {
                            Files.writeString(out.resolve("examples.jsonl"), o + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        }
                        line = o.get("blocks").getAsInt() + " blocks";
                    }
                    System.out.printf("[%d/%d] %-28s %s, %d rounds, %dk out, $%.4f, total $%.3f%n", done.incrementAndGet(),
                            slice.size() - skipped.get(), theme, line, apt.rounds.size(), outTok / 1000, c, total);
                } catch (Throwable e) {
                    failed.incrementAndGet();
                    System.out.printf("[%d] %s ERROR %s%n", done.incrementAndGet(), theme, e);
                    try {
                        Files.createDirectories(dir);
                        Files.writeString(dir.resolve("failed.txt"), String.valueOf(e) + "\n");
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(7, TimeUnit.DAYS);
        System.out.printf("%nbuilt %d, failed %d, skipped %d (done before), spent about $%.3f (%s prices), %.1f min%n",
                built.get(), failed.get(), skipped.get(), spent[0], scale == 1 ? "off-peak" : "peak", (System.nanoTime() - start) / 60e9);
        if (spent[0] >= budget) System.out.println("stopped at the budget; run again with a higher budget= to continue");
    }

    private static List<Path> listExamples(Path out) throws Exception {
        try (var s = Files.list(out)) {
            return s.map(d -> d.resolve("example.json")).filter(Files::exists).toList();
        }
    }

    private static double cost(Path example, double inPrice, double outPrice) throws Exception {
        JsonObject o = JsonParser.parseString(Files.readString(example)).getAsJsonObject();
        return o.get("in_tokens").getAsInt() * inPrice + o.get("out_tokens").getAsInt() * outPrice;
    }

    static String slug(String theme) {
        String s = theme.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "theme" : s;
    }
}
