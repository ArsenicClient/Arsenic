package arsenic.utils.buildgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * A chat model the generator can talk to: Claude over the Messages API, or a local model served by Ollama. Plain
 * HTTP with Gson, since addons cannot pull in SDK jars.
 */
public abstract class Llm {
    public record Msg(String role, String text, byte[] png) {
    }

    public record Reply(String text, int inTokens, int outTokens, long ms) {
    }

    protected final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public abstract String name();

    /** Sends the conversation; throws java.net.http.HttpTimeoutException when the reply takes over timeoutMs. */
    public abstract Reply chat(String system, List<Msg> msgs, long timeoutMs) throws Exception;

    protected JsonObject post(String url, JsonObject body, long timeoutMs, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException(name() + " HTTP " + res.statusCode() + ": " + res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /** Claude through the Messages API, with adaptive thinking at the given effort (low, medium, high). */
    public static final class Claude extends Llm {
        private final String model, effort, key;

        public Claude(String model, String effort, String key) {
            this.model = model;
            this.effort = effort;
            this.key = key;
        }

        @Override
        public String name() {
            return model + " (effort " + effort + ")";
        }

        @Override
        public Reply chat(String system, List<Msg> msgs, long timeoutMs) throws Exception {
            long start = System.nanoTime();
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 16000);
            body.addProperty("system", system);
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "adaptive");
            body.add("thinking", thinking);
            JsonObject out = new JsonObject();
            out.addProperty("effort", effort);
            body.add("output_config", out);
            JsonArray messages = new JsonArray();
            for (Msg m : msgs) {
                JsonObject jm = new JsonObject();
                jm.addProperty("role", m.role());
                JsonArray content = new JsonArray();
                if (m.png() != null) {
                    JsonObject src = new JsonObject();
                    src.addProperty("type", "base64");
                    src.addProperty("media_type", "image/png");
                    src.addProperty("data", Base64.getEncoder().encodeToString(m.png()));
                    JsonObject img = new JsonObject();
                    img.addProperty("type", "image");
                    img.add("source", src);
                    content.add(img);
                }
                JsonObject text = new JsonObject();
                text.addProperty("type", "text");
                text.addProperty("text", m.text());
                content.add(text);
                jm.add("content", content);
                messages.add(jm);
            }
            body.add("messages", messages);
            JsonObject res = post("https://api.anthropic.com/v1/messages", body, timeoutMs,
                    "x-api-key", key, "anthropic-version", "2026-10-21");
            StringBuilder sb = new StringBuilder();
            for (JsonElement e : res.getAsJsonArray("content")) {
                JsonObject block = e.getAsJsonObject();
                if (block.get("type").getAsString().equals("text")) sb.append(block.get("text").getAsString());
            }
            JsonObject usage = res.getAsJsonObject("usage");
            return new Reply(sb.toString(), usage.get("input_tokens").getAsInt(), usage.get("output_tokens").getAsInt(),
                    (System.nanoTime() - start) / 1_000_000);
        }
    }

    /** A model served by a local Ollama, thinking on or off. */
    public static final class Ollama extends Llm {
        private final String model, url;
        private final boolean think;

        public Ollama(String model, boolean think) {
            this(model, think, "http://localhost:11434");
        }

        public Ollama(String model, boolean think, String url) {
            this.model = model;
            this.think = think;
            this.url = url;
        }

        @Override
        public String name() {
            return model + " (thinking " + (think ? "on" : "off") + ")";
        }

        /** Loads the model into VRAM so the first timed call does not pay for it. */
        public void warm() throws Exception {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("keep_alive", "30m");
            post(url + "/api/generate", body, 120_000);
        }

        @Override
        public Reply chat(String system, List<Msg> msgs, long timeoutMs) throws Exception {
            long start = System.nanoTime();
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("stream", false);
            body.addProperty("think", think);
            body.addProperty("keep_alive", "30m");
            JsonObject options = new JsonObject();
            options.addProperty("num_ctx", 16384);
            options.addProperty("num_predict", 12000);
            body.add("options", options);
            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", system);
            messages.add(sys);
            for (Msg m : msgs) {
                JsonObject jm = new JsonObject();
                jm.addProperty("role", m.role());
                jm.addProperty("content", m.text());
                if (m.png() != null) {
                    JsonArray imgs = new JsonArray();
                    imgs.add(Base64.getEncoder().encodeToString(m.png()));
                    jm.add("images", imgs);
                }
                messages.add(jm);
            }
            body.add("messages", messages);
            JsonObject res = post(url + "/api/chat", body, timeoutMs);
            String text = res.getAsJsonObject("message").get("content").getAsString();
            int in = res.has("prompt_eval_count") ? res.get("prompt_eval_count").getAsInt() : 0;
            int out = res.has("eval_count") ? res.get("eval_count").getAsInt() : 0;
            return new Reply(text, in, out, (System.nanoTime() - start) / 1_000_000);
        }
    }
}
