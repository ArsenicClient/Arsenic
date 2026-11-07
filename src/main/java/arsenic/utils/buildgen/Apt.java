package arsenic.utils.buildgen;

import arsenic.utils.buildcore.Blueprint;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Prompt to build in a fixed time, the way APT does it (arXiv 2411.17255): the model plans the build's parts, writes
 * code that places them, the code runs, and the model sees a render and a check of the result and rewrites the code,
 * round after round until the time is up. The last build that ran is the result, so running out of time mid-round
 * only loses that round.
 */
public final class Apt {
    public static final class Round {
        public int n;
        public String kind;
        public Llm.Reply reply;
        public String error, report;
        public BufferedImage render;
        public long endMs;
    }

    public final Llm llm;
    public final ScriptRunner runner;
    public final Palette palette;
    public final List<Round> rounds = new ArrayList<>();
    public Blueprint best;
    public String bestCode;
    public Consumer<Round> onRound = r -> {
    };

    public Apt(Llm llm, Palette palette, ScriptRunner runner) {
        this.llm = llm;
        this.palette = palette;
        this.runner = runner;
    }

    public String system() {
        return """
                You design builds for Minecraft Build Battle, where players have a few minutes to build a theme and \
                judges vote on how well it reads. A bot places your build block by block, so you write Java that \
                draws it into a box of %d (x) by %d (y, up) by %d (z) cells; the plot floor is y = 0 and floating \
                parts are fine, but each block touching no other costs the bot a scaffold, so keep lone specks few \
                (group them into small clusters).

                Write the body of a class that extends Canvas: `public void build() { ... }` plus any helper methods \
                or fields you want. Available (all coordinates are doubles, rounded down to cells; both corners \
                inclusive; block names without "minecraft:"; "air" clears):
                  block(x, y, z, b)                       one block
                  box(x1, y1, z1, x2, y2, z2, b)          filled box
                  hollowBox(x1, y1, z1, x2, y2, z2, b)    box walls, floor and roof
                  line(x1, y1, z1, x2, y2, z2, b)         gapless line
                  sphere(cx, cy, cz, r, b)                solid ball
                  ellipsoid(cx, cy, cz, rx, ry, rz, b)
                  shell(cx, cy, cz, r, thickness, b)      hollow ball
                  disk(cx, y, cz, r, b)                   flat horizontal circle
                  ring(cx, y, cz, r, thickness, b)        flat horizontal ring
                  cylinder(cx, y, cz, r, h, b)            upright, from y up h cells
                  get(x, y, z) -> block name or "air"; count() -> blocks so far
                  random() -> seeded double in [0,1); pick(b1, b2, ...) -> one at random
                  W, H, D: the box size. Math and java.util are available; nothing else (no System, no files).
                Later calls overwrite earlier ones, so draw big shapes first and details on top.

                Allowed blocks (%s palette; plain cubes only):
                %s
                """.formatted(runner.sizeX, runner.sizeY, runner.sizeZ, palette.level.name().toLowerCase(), String.join(", ", palette.blocks));
    }

    String task(String prompt, int target) {
        return """
                Theme: %s
                Size: about %d blocks.

                Answer in two parts.
                ## Plan
                The build's parts as a short list: what each is, where (centre and size in cells), which shape calls, \
                which blocks, and roughly how many blocks it takes. The counts must add up to about %d. Think about \
                what makes the theme read at a glance from a judge's view: silhouette, colour contrast, a focal point.
                ## Code
                One ```java block with `public void build()` and any helpers.""".formatted(prompt, target, target);
    }

    String reflect(String prompt, int target, String report) {
        return """
                That code ran. The render shows two isometric views, the top and the front; the check says:
                %s
                Theme: %s, about %d blocks.
                Judge it as a Build Battle voter would: does it read as the theme, are the shape, proportions and \
                colours right, is anything noisy, missing or wasted, is the block count right? Give at most five \
                short points, then the complete improved code in one ```java block. Keep what works.""".formatted(report, prompt, target);
    }

    /** Runs rounds until the deadline (System.nanoTime()) and returns the last build that ran, or null. */
    public Blueprint run(String prompt, int target, long deadline) {
        String system = system();
        Llm.Msg first = new Llm.Msg("user", task(prompt, target), null);
        List<Llm.Msg> msgs = new ArrayList<>(List.of(first));
        long longest = 0;
        for (int n = 1; ; n++) {
            long left = (deadline - System.nanoTime()) / 1_000_000;
            // a round that cannot finish in time only burns the clock
            if (left < 3000 || best != null && left < longest * 0.6) break;
            Round r = new Round();
            r.n = n;
            r.kind = n == 1 ? "plan+code" : bestCode == null ? "repair" : "reflect";
            try {
                r.reply = llm.chat(system, msgs, left);
            } catch (HttpTimeoutException e) {
                r.error = "out of time waiting for the model";
                r.endMs = System.nanoTime();
                rounds.add(r);
                onRound.accept(r);
                break;
            } catch (Exception e) {
                r.error = "model call failed: " + e.getMessage();
                r.endMs = System.nanoTime();
                rounds.add(r);
                onRound.accept(r);
                break;
            }
            longest = Math.max(longest, r.reply.ms());
            String code = ScriptRunner.extractCode(r.reply.text());
            String feedback;
            byte[] png = null;
            if (code == null) {
                r.error = "no ```java block in the reply";
                feedback = "Your reply had no ```java code block. Output the complete code in one ```java block.";
            } else {
                ScriptRunner.Result res = runner.run(code, target);
                if (res.error != null) {
                    r.error = res.error;
                    feedback = "That code failed: " + res.error + "\nOutput the complete fixed code in one ```java block.";
                } else {
                    best = res.bp;
                    bestCode = code;
                    r.report = res.report;
                    r.render = Render.sheet(res.bp, palette, prompt + " - round " + n);
                    png = png(r.render);
                    feedback = reflect(prompt, target, res.report);
                }
            }
            r.endMs = System.nanoTime();
            rounds.add(r);
            onRound.accept(r);
            // the model sees its task, its latest answer and what came of it; older rounds are dropped
            msgs = new ArrayList<>(List.of(first, new Llm.Msg("assistant", r.reply.text(), null), new Llm.Msg("user", feedback, png)));
        }
        return best;
    }

    public static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
