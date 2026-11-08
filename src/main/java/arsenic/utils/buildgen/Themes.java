package arsenic.utils.buildgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Build Battle themes for training data: {@code GenMain themes [per=60] [holdout=50] [out=training/themes]}. Asks
 * DeepSeek (no thinking) for themes in each domain at three levels (1 one object, 2 an object in a scene or doing
 * something, 3 an abstract word or idea), removes near-duplicates, shuffles with a fixed seed and holds some out as a
 * test set. Writes themes.jsonl (theme, domain, level, split), train.txt and holdout.txt.
 */
public final class Themes {
    static final String[] DOMAINS = {
            "farm and pet animals", "wild animals", "sea creatures", "insects and small creatures", "birds",
            "dinosaurs and prehistoric life", "meals and fast food", "desserts and sweets", "drinks", "fruit and vegetables",
            "famous kinds of buildings and landmarks", "homes and rooms", "cars, trucks and trains", "aircraft and spaceflight",
            "boats and ships", "landscapes and natural features", "weather and the sky", "plants, trees and flowers",
            "fantasy creatures", "myths and legends", "fairy tales and storybook scenes", "holidays and celebrations",
            "sports and games", "musical instruments and music", "household objects", "tools and machines",
            "technology and electronics", "clothing and accessories", "jobs and workplaces", "places in a town",
            "ancient and medieval history", "science and outer space", "emotions and abstract ideas", "seasons and nature events",
            "toys", "art and crafts", "arcade and board game ideas (no brands)", "the human body and health",
            "magic and wizardry", "spooky and horror", "everyday activities", "cities and transport"
    };

    public static void generate(Map<String, String> a) throws Exception {
        int per = Integer.parseInt(a.getOrDefault("per", "60"));
        int holdout = Integer.parseInt(a.getOrDefault("holdout", "50"));
        Path out = Path.of(a.getOrDefault("out", "training/themes"));
        Files.createDirectories(out);
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("set DEEPSEEK_API_KEY");
        Llm llm = new Llm.DeepSeek(a.getOrDefault("model", "deepseek-flash"), "none", key);
        String system = "You write themes for Minecraft Build Battle, where players get a theme and a few minutes to build it. "
                + "Reply with a JSON array only, no prose and no code fence.";

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<JsonArray>> jobs = new ArrayList<>();
        int[] tokens = new int[2];
        for (String domain : DOMAINS) {
            jobs.add(pool.submit(() -> {
                String ask = """
                        Domain: %s
                        Write %d different Build Battle themes in this domain, as a JSON array of objects \
                        {"theme": "...", "level": 1|2|3}. Level 1: one clear object or creature ("snail", "teapot"). \
                        Level 2: an object in a setting or doing something ("snail race", "teapot tea party", \
                        "lighthouse in a storm"). Level 3: an abstract word, idea or idiom that needs a clever build \
                        ("patience", "time flies"). About half level 1, a third level 2, the rest level 3. \
                        Themes are 1-4 words, lower case, the way a game would print them. Nothing that needs words or \
                        letters to read, no real people, brands, trademarked characters, films or games.""".formatted(domain, per);
                Llm.Reply r = llm.chat(system, List.of(new Llm.Msg("user", ask, null)), 120_000);
                synchronized (tokens) {
                    tokens[0] += r.inTokens();
                    tokens[1] += r.outTokens();
                }
                String text = r.text().trim().replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
                JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
                for (JsonElement e : arr) e.getAsJsonObject().addProperty("domain", domain);
                System.out.println(domain + ": " + arr.size());
                return arr;
            }));
        }
        Map<String, JsonObject> unique = new LinkedHashMap<>();
        int raw = 0;
        for (Future<JsonArray> f : jobs) {
            JsonArray arr;
            try {
                arr = f.get();
            } catch (Exception e) {
                System.out.println("a domain failed: " + e.getCause());
                continue;
            }
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("theme")) continue;
                String theme = o.get("theme").getAsString().trim().toLowerCase().replaceAll("\\s+", " ");
                if (theme.isEmpty() || theme.split(" ").length > 5) continue;
                o.addProperty("theme", theme);
                raw++;
                unique.putIfAbsent(norm(theme), o);
            }
        }
        pool.shutdown();

        List<JsonObject> all = new ArrayList<>(unique.values());
        Collections.shuffle(all, new Random(20261108));
        StringBuilder jsonl = new StringBuilder(), train = new StringBuilder(), held = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            JsonObject o = all.get(i);
            boolean h = i < holdout;
            o.addProperty("split", h ? "holdout" : "train");
            jsonl.append(o).append('\n');
            (h ? held : train).append(o.get("theme").getAsString()).append('\n');
        }
        Files.writeString(out.resolve("themes.jsonl"), jsonl);
        Files.writeString(out.resolve("train.txt"), train);
        Files.writeString(out.resolve("holdout.txt"), held);
        System.out.printf("%d raw, %d unique (%d train, %d holdout), %d in / %d out tokens, about $%.3f%n",
                raw, all.size(), all.size() - Math.min(holdout, all.size()), Math.min(holdout, all.size()),
                tokens[0], tokens[1], tokens[0] * 0.15e-6 + tokens[1] * 0.60e-6);
    }

    /** Key for spotting the same theme twice: no articles, no plural s, letters only. */
    static String norm(String theme) {
        StringBuilder sb = new StringBuilder();
        for (String w : theme.split(" ")) {
            if (w.equals("a") || w.equals("an") || w.equals("the")) continue;
            w = w.replaceAll("[^a-z0-9]", "");
            if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) w = w.substring(0, w.length() - 1);
            sb.append(w).append(' ');
        }
        return sb.toString().trim();
    }
}
