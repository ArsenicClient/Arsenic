package arsenic.utils.buildgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A local page for scoring builds by eye: {@code GenMain rate dir=... [port=8417] [host=127.0.0.1]}. Shows each
 * {@code *-ingame.png} under dir (from BuildShots in the client) with its theme; keys 1-9 score it and move to the
 * next unscored one. Scores are saved to dir/scores.json on every key, keyed by the schematic's path under dir.
 */
public final class Rate {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir, scoresFile;
    private final Map<String, Integer> scores = new TreeMap<>();

    private Rate(Path dir) throws IOException {
        this.dir = dir.toAbsolutePath().normalize();
        this.scoresFile = this.dir.resolve("scores.json");
        if (Files.exists(scoresFile)) {
            JsonObject o = JsonParser.parseString(Files.readString(scoresFile)).getAsJsonObject();
            for (String k : o.keySet()) scores.put(k, o.get(k).getAsInt());
        }
    }

    public static void serve(Map<String, String> a) throws Exception {
        Rate rate = new Rate(Path.of(a.getOrDefault("dir", ".")));
        int port = Integer.parseInt(a.getOrDefault("port", "8417"));
        HttpServer http = HttpServer.create(new InetSocketAddress(a.getOrDefault("host", "127.0.0.1"), port), 0);
        http.createContext("/", rate::handle);
        http.start();
        System.out.println("scoring page: http://localhost:" + port + "/  (" + rate.items().size() + " builds, Ctrl+C to stop)");
    }

    /**
     * For the hosted scoring page: {@code GenMain export dir=training/lockin out=training/web}. Adds every screenshot
     * under dir to out/items.json (keeping entries from earlier exports) as a JPEG in out/shots/, keyed
     * "batch~path~to~final.schem" with batch = dir's folder name, the same key the page stores scores under.
     */
    public static void export(Map<String, String> a) throws Exception {
        Rate rate = new Rate(Path.of(a.getOrDefault("dir", ".")));
        Path out = Path.of(a.getOrDefault("out", "training/web"));
        Files.createDirectories(out.resolve("shots"));
        String batch = rate.dir.getFileName().toString();
        Map<String, JsonObject> all = new TreeMap<>();
        Path itemsFile = out.resolve("items.json");
        if (Files.exists(itemsFile))
            for (var e : JsonParser.parseString(Files.readString(itemsFile)).getAsJsonArray())
                all.put(e.getAsJsonObject().get("key").getAsString(), e.getAsJsonObject());
        int added = 0;
        for (var e : rate.items()) {
            JsonObject it = e.getAsJsonObject();
            String key = (batch + "/" + it.get("id").getAsString()).replace('/', '~');
            Path jpg = out.resolve("shots").resolve(key.replace(".schem", ".jpg"));
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(rate.dir.resolve(it.get("img").getAsString()).toFile());
            int w = Math.min(1200, src.getWidth()), h = src.getHeight() * w / src.getWidth();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.85f);
            Files.deleteIfExists(jpg);
            try (var ios = javax.imageio.ImageIO.createImageOutputStream(jpg.toFile())) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(img, null, null), p);
            }
            writer.dispose();
            it.addProperty("key", key);
            it.addProperty("batch", batch);
            it.addProperty("img", "shots/" + jpg.getFileName());
            it.remove("score");
            if (all.put(key, it) == null) added++;
        }
        JsonArray arr = new JsonArray();
        all.values().forEach(arr::add);
        Files.writeString(itemsFile, arr.toString());
        System.out.println("exported " + rate.items().size() + " (" + added + " new), " + all.size() + " in " + itemsFile);
    }

    /**
     * Stages the hosted page for publishing: {@code GenMain pack web=training/web out=<dir> [mb=4]}. An artifact
     * version holds at most 511 files, so the JPEGs go into out/packs/NNN.txt (concatenated, about mb each, base64 because artifacts serve no raw binary type) and
     * out/items.json gives each item {pack, off, len} instead of img; the page slices its picture out of the pack.
     */
    public static void pack(Map<String, String> a) throws Exception {
        Path web = Path.of(a.getOrDefault("web", "training/web")), out = Path.of(a.getOrDefault("out", "web-packed"));
        long max = (long) (Double.parseDouble(a.getOrDefault("mb", "4")) * 1_000_000);
        Files.createDirectories(out.resolve("packs"));
        try (Stream<Path> s = Files.list(out.resolve("packs"))) {
            for (Path p : s.toList()) Files.delete(p);
        }
        Files.copy(web.resolve("index.html"), out.resolve("index.html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        JsonArray items = JsonParser.parseString(Files.readString(web.resolve("items.json"))).getAsJsonArray();
        var buf = new java.io.ByteArrayOutputStream();
        int n = 0;
        for (var e : items) {
            JsonObject it = e.getAsJsonObject();
            byte[] jpg = Files.readAllBytes(web.resolve(it.remove("img").getAsString()));
            if (buf.size() > 0 && buf.size() + jpg.length > max) {
                Files.write(out.resolve("packs").resolve("%03d.txt".formatted(n++)), java.util.Base64.getEncoder().encode(buf.toByteArray()));
                buf.reset();
            }
            it.addProperty("pack", "packs/%03d.txt".formatted(n));
            it.addProperty("off", buf.size());
            it.addProperty("len", jpg.length);
            buf.write(jpg);
        }
        if (buf.size() > 0) Files.write(out.resolve("packs").resolve("%03d.txt".formatted(n++)), java.util.Base64.getEncoder().encode(buf.toByteArray()));
        Files.writeString(out.resolve("items.json"), items.toString());
        System.out.println("packed " + items.size() + " pictures into " + n + " packs in " + out);
    }

    private List<Path> shots() throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith("-ingame.png")).sorted().toList();
        }
    }

    /** One entry per screenshot: id (schematic path under dir), theme and block count from example.json if present. */
    private JsonArray items() throws IOException {
        JsonArray arr = new JsonArray();
        for (Path png : shots()) {
            String rel = dir.relativize(png).toString().replace('\\', '/');
            String id = rel.substring(0, rel.length() - "-ingame.png".length()) + ".schem";
            JsonObject it = new JsonObject();
            it.addProperty("id", id);
            it.addProperty("img", rel);
            Path parent = png.getParent();
            String theme = parent.equals(dir) ? id : parent.getFileName().toString().replace('-', ' ');
            Path ex = parent.resolve("example.json");
            if (Files.exists(ex)) {
                JsonObject o = JsonParser.parseString(Files.readString(ex)).getAsJsonObject();
                if (o.has("theme")) theme = o.get("theme").getAsString();
                if (o.has("blocks")) it.addProperty("blocks", o.get("blocks").getAsInt());
            }
            it.addProperty("theme", theme);
            Integer s = scores.get(id);
            if (s != null) it.addProperty("score", s);
            arr.add(it);
        }
        return arr;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) {
                send(ex, 200, "text/html; charset=utf-8", PAGE.getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/items")) {
                send(ex, 200, "application/json", items().toString().getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/score") && ex.getRequestMethod().equals("POST")) {
                JsonObject o = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                String id = o.get("id").getAsString();
                synchronized (this) {
                    if (o.has("score") && !o.get("score").isJsonNull()) scores.put(id, o.get("score").getAsInt());
                    else scores.remove(id);
                    Files.writeString(scoresFile, GSON.toJson(scores));
                }
                send(ex, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
            } else if (path.startsWith("/img/")) {
                Path f = dir.resolve(URLDecoder.decode(path.substring(5), StandardCharsets.UTF_8)).normalize();
                if (!f.startsWith(dir) || !f.toString().endsWith(".png") || !Files.exists(f)) send(ex, 404, "text/plain", new byte[0]);
                else send(ex, 200, "image/png", Files.readAllBytes(f));
            } else {
                send(ex, 404, "text/plain", new byte[0]);
            }
        } catch (Exception e) {
            send(ex, 500, "text/plain", String.valueOf(e).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("content-type", type);
        ex.getResponseHeaders().set("cache-control", "no-store");
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Build scoring</title><style>
            :root{color-scheme:dark}body{margin:0;background:#16191d;color:#e8eaed;font:15px system-ui,sans-serif;display:flex;flex-direction:column;height:100vh}
            header{display:flex;gap:16px;align-items:baseline;padding:10px 16px;flex-wrap:wrap}
            h1{font-size:22px;margin:0}#meta,#progress{color:#9aa0a6}#score{font-size:22px;font-weight:700;color:#8ab4f8}
            main{flex:1;min-height:0;display:flex;justify-content:center;align-items:center;padding:0 16px}
            img{max-width:100%;max-height:100%;object-fit:contain;border-radius:6px}
            footer{display:flex;gap:6px;justify-content:center;padding:12px 16px;flex-wrap:wrap}
            button{font:inherit;font-size:18px;min-width:44px;height:44px;border-radius:8px;border:1px solid #3c4043;background:#24282d;color:#e8eaed;cursor:pointer}
            button.on{background:#8ab4f8;color:#16191d;border-color:#8ab4f8}button.nav{min-width:60px}
            #help{color:#9aa0a6;text-align:center;padding-bottom:10px;font-size:13px}label{color:#9aa0a6;margin-left:auto}
            </style></head><body>
            <header><h1 id="theme">loading</h1><span id="meta"></span><span id="score"></span><span id="progress"></span>
            <label><input type="checkbox" id="unrated" checked> skip scored</label></header>
            <main><img id="img" alt=""></main>
            <footer id="buttons"></footer>
            <div id="help">1-9 score and go to the next &middot; &larr; &rarr; move &middot; 0 clears &middot; the two views are opposite corners</div>
            <script>
            let items=[],i=0;
            const $=id=>document.getElementById(id);
            const b=$('buttons');
            const prev=document.createElement('button');prev.textContent='\\u2190';prev.className='nav';prev.onclick=()=>move(-1);b.append(prev);
            for(let s=1;s<=9;s++){const x=document.createElement('button');x.textContent=s;x.onclick=()=>score(s);b.append(x);}
            const next=document.createElement('button');next.textContent='\\u2192';next.className='nav';next.onclick=()=>move(1);b.append(next);
            function show(){
              const it=items[i];const done=items.filter(x=>x.score).length;
              $('progress').textContent=done+' / '+items.length+' scored';
              if(!it){$('theme').textContent='nothing to score';return;}
              $('theme').textContent=it.theme;$('meta').textContent=(it.blocks?it.blocks+' blocks \\u00b7 ':'')+it.id+' \\u00b7 '+(i+1)+'/'+items.length;
              $('score').textContent=it.score?'scored '+it.score:'';
              $('img').src='/img/'+encodeURI(it.img);
              [...b.children].forEach((x,k)=>x.classList.toggle('on',k>0&&k<10&&it.score===k));
            }
            function move(d){
              const skip=$('unrated').checked;
              for(let k=i+d;k>=0&&k<items.length;k+=d){if(!skip||!items[k].score||d<0){i=k;show();return;}}
              show();
            }
            async function score(s){
              const it=items[i];if(!it)return;it.score=s||undefined;show();
              await fetch('/score',{method:'POST',body:JSON.stringify({id:it.id,score:s||null})});
              if(s)move(1);
            }
            document.onkeydown=e=>{
              if(e.key>='1'&&e.key<='9')score(+e.key);else if(e.key==='0')score(0);
              else if(e.key==='ArrowRight')move(1);else if(e.key==='ArrowLeft'||e.key==='Backspace')move(-1);
            };
            fetch('/items').then(r=>r.json()).then(j=>{items=j;const k=items.findIndex(x=>!x.score);i=k<0?0:k;show();});
            </script></body></html>
            """;
}
