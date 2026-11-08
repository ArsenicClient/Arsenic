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
    /** Stops after this many rounds when above 0, whatever time is left. */
    public int maxRounds;
    /** When false, stops after the first build that runs, so later rounds only repair code that failed. */
    public boolean polish = true;
    /** Extra direction for the teacher model ("Super detailed, ..."), left out of {@link #task(String, String, int)} when empty. */
    public String style = "";
    public Blueprint best;
    public String bestCode, bestReply, bestReport, bestReasoning;
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
                  hollowCylinder(cx, y, cz, r, h, t, b)   upright shell with floor and roof, wall t thick
                  hollowEllipsoid(cx, cy, cz, rx, ry, rz, t, b)   shell t thick
                  hollowOut() / hollowOut(t)              call LAST: clears everything more than t (default 1) cells
                                                          from air, glass or the box edge, leaving a shell
                  stairs(x, y, z, b, facing, half)        facing north/south/east/west is the way it climbs (the tall
                                                          back is on that side; north is -z, east is +x); half is
                                                          "bottom" (normal) or "top" (upside down)
                  slab(x, y, z, b, type)                  type "bottom" or "top" half of the cell
                  gableRoof(x1, z1, x2, z2, y, stairsB, fillB, ridgeAlongX)   stair roof, eaves row at y, climbing to
                                                          a ridge; two blocks thick, empty underneath, gable ends open
                  mix(b1, b2, ...)                        a blend: pass it as the block of ANY call above or below and
                                                          each cell gets one at random (repeat a name to weight it)
                  cone(cx, y, cz, baseR, topR, h, b)      gapless cone/frustum shell on y (disk at the foot)
                  flow(cx, y, cz, baseR, topR, h, angle, width, sway, b)   strip running down a cone() with the same
                                                          numbers, top to foot, at angle in radians (0 = +x, PI/2 = +z)
                  mound(cx, y, cz, r, h, b)               dome shell on y
                  patch(cx, y, cz, r, b)                  ragged flat blotch (ash, grass, sand, puddle)
                  rocks(n, x1, z1, x2, z2, y, rmin, rmax, b)   n boulders on the floor at y, random in the area
                  sprinkle(n, x1, y1, z1, x2, y2, z2, b)  n single blocks in a region, only on a supported empty
                                                          cell (embers, rubble, flowers, snow): no floating specks
                  pillar(x, y, z, h, b, capB)             column, capB on top or null
                  tree(x, y, z, h, trunkB, leavesB)       trunk with a round crown; deadTree(x, y, z, h, logB, leavesB)
                                                          is bare with branches (leavesB may be null)
                  plume(x, y, z, dx, dy, dz, n, r0, r1, b)   n hollow puffs, each moved (dx,dy,dz) from the last,
                                                          radius growing r0 to r1 (smoke, steam, clouds)
                  get(x, y, z) -> block name or "air"; count() -> blocks so far
                  random() -> seeded double in [0,1); pick(b1, b2, ...) -> one at random
                  W, H, D: the box size. Math and java.util are available; nothing else (no System, no files).
                Later calls overwrite earlier ones, so draw big shapes first and details on top.

                BUILD HOLLOW. The player flies round the outside and can never stand inside a block, and judges only see \
                surfaces, so a block with no exposed face is wasted time. Use hollowBox, shell, hollowCylinder and \
                hollowEllipsoid, never box, sphere or cylinder for anything bigger than 2-3 cells across (a filled \
                one is fine for small details like a pillar or a ball under 3 cells wide). Walls, floors and roofs \
                are one block thick (two at most for big builds). Anything still buried is removed automatically \
                and reported, so count only the blocks that show when you plan the block total. Interiors can hold \
                furniture, but only if the theme needs it.

                Stairs and slabs give shape: use them for sloped roofs, rounded edges, steps and eaves instead of \
                stepped full blocks. Name them in full ("oak_stairs", "stone_brick_slab") from the shaped list below; \
                the bot clicks each one against a neighbour to get your facing and half. A stair cannot be attached \
                only to the block on its low (open) side, so each needs a neighbour on its tall side, either side, \
                above or below (a roof row resting on a wall or on the row under it is fine).

                %s""".formatted(runner.sizeX, runner.sizeY, runner.sizeZ, blocksSection(palette));
    }

    /** The palette part of the system prompt: the full list, or for every block the rules for naming them. */
    public static String blocksSection(Palette palette) {
        if (palette.level == Palette.Level.ALL) return """
                Blocks: anything in the creative menu (%d blocks), by its game ID without "minecraft:": every stone, \
                wood, wool, concrete, glass and pane, plus fences, walls, trapdoors, lanterns, flowers, chains, \
                buttons and so on. A name that is not a block is skipped and reported with the closest real ones. \
                Give a state in brackets where it matters, e.g. "oak_log[axis=x]", "spruce_trapdoor[half=top]", \
                "lantern[hanging=true]"; stairs and slabs take theirs through stairs() and slab(). Fences, walls and \
                panes join up with their neighbours by themselves.
                """.formatted(palette.blocks.size());
        return """
                Allowed blocks (%s palette):
                %s

                Allowed stairs and slabs (use with stairs(), slab(), gableRoof()):
                %s
                """.formatted(palette.level.name().toLowerCase(), String.join(", ", palette.blocks), String.join(", ", palette.shaped));
    }

    public String task(String prompt, int target) {
        return task(prompt, style, target);
    }

    /** The first message; with an empty style it is what a trained model is given for the theme alone. */
    public static String task(String prompt, String style, int target) {
        return """
                Theme: %s
                %sSize: about %d blocks.

                Answer in two parts.
                ## Plan
                The build's parts as a short list: what each is, where (centre and size in cells), which shape calls, \
                which blocks, and roughly how many blocks it takes. The counts must add up to about %d. Think about \
                what makes the theme read at a glance from a judge's view: silhouette, colour contrast, a focal point.
                ## Code
                One ```java block with `public void build()` and any helpers.""".formatted(prompt,
                style.isBlank() ? "" : "Style: " + style + "\n", target, target);
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
            if (maxRounds > 0 && n > maxRounds || !polish && best != null) break;
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
                    bestReply = r.reply.text();
                    bestReasoning = r.reply.reasoning();
                    bestReport = res.report;
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
