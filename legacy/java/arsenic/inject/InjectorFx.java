package arsenic.inject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The injector's effects layer, used as the window's glass pane. Two scenes, both painted by hand:
 * <ul>
 * <li>the start-up intro: a scanline wipes the wordmark in over a spray of sparks while two comets orbit it, a
 * loading line fills, then the whole thing blooms outward and dissolves into the window;</li>
 * <li>the inject sequence: a reactor of spinning rings drinks particles in while the injection runs, then the
 * result detonates: shockwaves and a particle burst around a drawn check mark (or a shaking red cross).</li>
 * </ul>
 * While a scene plays the layer swallows the mouse, so nothing behind it can be clicked.
 */
final class InjectorFx extends JComponent {

    private static final Color BACKGROUND = new Color(0x0D0D0F);
    private static final Color ACCENT = new Color(0xDD425E);
    private static final Color ACCENT_HOT = new Color(0xFF7A92);
    private static final Color SUCCESS = new Color(0x3FB950);
    private static final Color DANGER = new Color(0xE24B4A);
    private static final Color WHITE = Color.WHITE;

    private static final int NONE = 0, INTRO = 1, INJECT = 2;
    private static final int IN = 0, RUN = 1, RESULT = 2, OUT = 3;

    /** The intro's timeline is written in ms at 1x; this plays it faster (2500 ms of timeline takes about 1.6 s). */
    private static final float INTRO_SPEED = 1.55f;
    private static final float INTRO_MS = 2500f;
    /** Timeline ms at which the loading line reaches 100% (it fills from 300 over 1450). */
    private static final float INTRO_BAR_FULL = 1750f;
    private static final float INTRO_EXIT_AT = 2000f;
    /** The reactor always spins at least this long, so a quick injection still gets its show. */
    private static final float MIN_RUN_MS = 2000f;

    private final BufferedImage logo;
    private final String version;
    private final Timer timer = new Timer(8, e -> tick());
    private final Random rnd = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Swirl> swirls = new ArrayList<>();

    private int scene = NONE;
    private boolean started;
    private long sceneStart, lastTick;
    private float ms, dt;
    private Runnable introDone;

    // inject scene
    private int phase;
    private float phaseStart, resultStart, spin, pulse;
    private boolean resulted, ok, pending, pendingOk;
    private String stage = "Attaching to the game...";
    private Runnable atResult;

    // logo, scaled once and composited through a mask each frame
    private BufferedImage buf;
    private Image scaledLogo;

    InjectorFx(BufferedImage logo, String version) {
        this.logo = logo;
        this.version = version;
        setOpaque(false);
        setVisible(false);
        setCursor(Cursor.getDefaultCursor());
        MouseAdapter swallow = new MouseAdapter() {
        };
        addMouseListener(swallow);
        addMouseMotionListener(swallow);
        addMouseWheelListener(swallow);
    }

    // ---- control ----

    void playIntro(Runnable done) {
        introDone = done;
        begin(INTRO);
    }

    void playInject() {
        phase = IN;
        phaseStart = 0;
        resultStart = 0;
        spin = 0;
        pulse = 0;
        resulted = false;
        pending = false;
        atResult = null;
        stage = "Attaching to the game...";
        begin(INJECT);
    }

    void stage(String text) {
        if (text != null && !text.isEmpty())
            stage = text;
    }

    /** Ends the reactor with a result once it has spun long enough. {@code onResult} runs the moment the result shows. */
    void finishInject(boolean success, Runnable onResult) {
        if (scene != INJECT) {
            if (onResult != null)
                onResult.run();
            return;
        }
        pending = true;
        pendingOk = success;
        atResult = onResult;
    }

    private void begin(int newScene) {
        particles.clear();
        swirls.clear();
        scene = newScene;
        started = false;
        ms = 0;
        setVisible(true);
        timer.start();
    }

    private void end() {
        timer.stop();
        int was = scene;
        scene = NONE;
        particles.clear();
        swirls.clear();
        setVisible(false);
        if (was == INTRO && introDone != null) {
            Runnable r = introDone;
            introDone = null;
            r.run();
        }
    }

    // ---- simulation ----

    private void tick() {
        if (scene == NONE)
            return;
        long now = System.nanoTime();
        if (!started) {
            // the clock starts when the layer is first on screen, so a slow window start does not eat the intro
            if (!isShowing() || getWidth() == 0)
                return;
            started = true;
            sceneStart = lastTick = now;
        }
        dt = Math.min(0.05f, (now - lastTick) / 1e9f);
        lastTick = now;
        ms = (now - sceneStart) / 1e6f;
        if (scene == INTRO) {
            ms *= INTRO_SPEED;
            dt *= INTRO_SPEED;
            // once the loading line is full the ending runs twice as fast
            if (ms > INTRO_BAR_FULL) {
                ms = INTRO_BAR_FULL + (ms - INTRO_BAR_FULL) * 2f;
                dt *= 2f;
            }
        }

        if (scene == INTRO)
            tickIntro();
        else
            tickInject();
        if (scene == NONE)
            return;
        updateParticles();
        repaint();
    }

    private float[] introLayout() {
        float w = getWidth(), h = getHeight();
        float lw = Math.min(320f, w - 140f);
        float lh = logo != null ? lw * logo.getHeight() / logo.getWidth() : 70f;
        return new float[]{w / 2f, h / 2f - 14f, lw, lh};
    }

    private static float revealProgress(float ms) {
        return easeInOut((ms - 150f) / 1000f);
    }

    private void tickIntro() {
        if (ms >= INTRO_MS) {
            end();
            return;
        }
        float[] l = introLayout();
        float cx = l[0], cy = l[1], lw = l[2], lh = l[3];
        // ambient embers rising from the bottom
        int embers = (int) (dt * 46f + rnd.nextFloat());
        for (int i = 0; i < embers; i++) {
            Particle p = new Particle();
            p.x = rnd.nextFloat() * getWidth();
            p.y = getHeight() + 4;
            p.vx = (rnd.nextFloat() - 0.5f) * 26f;
            p.vy = -(40f + rnd.nextFloat() * 100f);
            p.max = p.life = 2.2f + rnd.nextFloat() * 1.6f;
            p.size = 0.8f + rnd.nextFloat() * 1.4f;
            p.color = rnd.nextInt(3) == 0 ? WHITE : ACCENT;
            p.alpha = 0.55f;
            particles.add(p);
        }
        // sparks thrown off the scanline while it wipes the wordmark in
        float p = revealProgress(ms);
        if (p > 0f && p < 1f) {
            float edge = cx - lw / 2f + p * (lw + 40f);
            int sparks = (int) (dt * 260f + rnd.nextFloat());
            for (int i = 0; i < sparks; i++) {
                Particle s = new Particle();
                s.x = edge;
                s.y = cy + (rnd.nextFloat() - 0.5f) * lh * 1.2f;
                s.vx = -40f + rnd.nextFloat() * 220f;
                s.vy = (rnd.nextFloat() - 0.6f) * 180f;
                s.gravity = 260f;
                s.drag = 1.4f;
                s.max = s.life = 0.45f + rnd.nextFloat() * 0.5f;
                s.size = 1f + rnd.nextFloat() * 1.6f;
                s.color = rnd.nextInt(3) == 0 ? WHITE : ACCENT_HOT;
                s.alpha = 1f;
                particles.add(s);
            }
        }
    }

    private void tickInject() {
        float pm = ms - phaseStart;
        float cx = getWidth() / 2f, cy = getHeight() / 2f - 14f;
        spin += dt * reactorSpeed();
        pulse = Math.max(0f, pulse - dt * 3f);
        switch (phase) {
            case IN:
                spawnSwirls();
                if (pm >= 350f) {
                    phase = RUN;
                    phaseStart = ms;
                }
                break;
            case RUN:
                spawnSwirls();
                if (pending && ms >= MIN_RUN_MS)
                    beginResult(cx, cy);
                break;
            case RESULT:
                if (ms - resultStart >= (ok ? 1900f : 1500f)) {
                    phase = OUT;
                    phaseStart = ms;
                }
                break;
            default:
                if (pm >= 380f)
                    end();
        }
        for (Iterator<Swirl> it = swirls.iterator(); it.hasNext(); ) {
            Swirl s = it.next();
            s.vr += 240f * dt;
            s.radius -= s.vr * dt;
            s.angle += s.spin * (160f / (s.radius + 40f)) * dt;
            if (s.radius < 10f) {
                pulse = Math.min(1f, pulse + 0.06f);
                it.remove();
            }
        }
    }

    private float reactorSpeed() {
        return 1f + 2.2f * easeOut(ms / 2200f);
    }

    private void spawnSwirls() {
        int n = (int) (dt * (70f + 40f * easeOut(ms / 2200f)) + rnd.nextFloat());
        for (int i = 0; i < n; i++) {
            Swirl s = new Swirl();
            s.angle = rnd.nextFloat() * (float) (Math.PI * 2);
            s.r0 = s.radius = 220f + rnd.nextFloat() * 60f;
            s.vr = 30f + rnd.nextFloat() * 50f;
            s.spin = 1.2f + rnd.nextFloat() * 1.1f;
            s.color = rnd.nextInt(3) == 0 ? WHITE : rnd.nextBoolean() ? ACCENT : ACCENT_HOT;
            swirls.add(s);
        }
    }

    private void beginResult(float cx, float cy) {
        ok = pendingOk;
        resulted = true;
        phase = RESULT;
        phaseStart = resultStart = ms;
        swirls.clear();
        int n = ok ? 130 : 46;
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            double a = rnd.nextDouble() * Math.PI * 2;
            float speed = (ok ? 120f : 80f) + rnd.nextFloat() * (ok ? 360f : 230f);
            p.x = cx;
            p.y = cy;
            p.vx = (float) Math.cos(a) * speed;
            p.vy = (float) Math.sin(a) * speed - (ok ? 40f : 0f);
            p.gravity = ok ? 360f : 640f;
            p.drag = 1.1f;
            p.max = p.life = 0.7f + rnd.nextFloat() * 0.8f;
            p.size = 1.4f + rnd.nextFloat() * 2f;
            if (ok)
                p.color = new Color[]{SUCCESS, WHITE, ACCENT_HOT, ACCENT, SUCCESS}[rnd.nextInt(5)];
            else
                p.color = rnd.nextBoolean() ? DANGER : ACCENT;
            p.alpha = 1f;
            particles.add(p);
        }
        if (atResult != null) {
            Runnable r = atResult;
            atResult = null;
            r.run();
        }
    }

    private void updateParticles() {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.vy += p.gravity * dt;
            float keep = Math.max(0f, 1f - p.drag * dt);
            p.vx *= keep;
            p.vy *= keep;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0f)
                it.remove();
        }
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics g) {
        if (scene == NONE)
            return;
        Graphics2D g2 = InjectorWindow.smooth(g);
        int w = getWidth(), h = getHeight();
        if (scene == INTRO)
            paintIntro(g2, w, h);
        else
            paintInject(g2, w, h);
        g2.dispose();
    }

    private void paintIntro(Graphics2D g2, int w, int h) {
        float[] l = introLayout();
        float cx = l[0], cy = l[1], lw = l[2], lh = l[3];
        float exit = clamp01((ms - INTRO_EXIT_AT) / (INTRO_MS - INTRO_EXIT_AT));
        float bg = 1f - easeInOut(exit);

        setAlpha(g2, bg);
        g2.setColor(BACKGROUND);
        g2.fillRect(0, 0, w, h);
        float breathe = 0.85f + 0.15f * (float) Math.sin(ms / 260f);
        glow(g2, cx, cy, Math.max(w, h) * 0.62f, ACCENT, 0.22f * breathe * clamp01(ms / 500f));
        paintParticles(g2);

        setAlpha(g2, 1f - easeIn(exit));
        java.awt.geom.AffineTransform base = g2.getTransform();
        float sc = 1f + 0.14f * easeIn(exit);
        g2.translate(cx, cy);
        g2.scale(sc, sc);
        g2.translate(-cx, -cy);

        // wordmark, wiped in behind the scanline, then lit by a sweeping shine
        float p = revealProgress(ms);
        float lx = cx - lw / 2f, ly = cy - lh / 2f;
        float edge = lx + p * (lw + 40f);
        glow(g2, cx, cy, lw * 0.75f, ACCENT, 0.16f * easeOut((ms - 150f) / 900f));
        drawWordmark(g2, lx, ly, lw, lh, edge - lx, (ms - 1150f) / 520f);

        // the scanline
        if (p > 0f && p < 1f) {
            float la = Math.min(1f, p * 8f) * Math.min(1f, (1f - p) * 8f);
            float half = lh * 0.95f;
            scanLine(g2, edge, cy, half, 18f, rgba(ACCENT, 0.12f * la), rgba(ACCENT, 0.30f * la));
            scanLine(g2, edge, cy, half, 7f, rgba(ACCENT_HOT, 0.25f * la), rgba(ACCENT_HOT, 0.55f * la));
            scanLine(g2, edge, cy, half, 2f, rgba(WHITE, 0f), rgba(WHITE, la));
        }

        // two comets on an ellipse around the wordmark
        float oa = easeOut((ms - 250f) / 600f);
        if (oa > 0f) {
            float rx = lw / 2f + 62f, ry = lh / 2f + 46f;
            for (int k = 0; k < 2; k++) {
                double head = ms * 0.0042 + k * Math.PI;
                Color col = k == 0 ? ACCENT_HOT : WHITE;
                int n = 46;
                for (int i = 0; i < n; i++) {
                    double a0 = head - i * 0.032, a1 = head - (i + 1) * 0.032;
                    float f = 1f - i / (float) n;
                    g2.setStroke(new BasicStroke(0.6f + 3f * f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(rgba(col, oa * (float) Math.pow(f, 1.8) * (k == 0 ? 0.95f : 0.55f)));
                    g2.draw(new Line2D.Double(cx + rx * Math.cos(a0), cy + ry * Math.sin(a0), cx + rx * Math.cos(a1), cy + ry * Math.sin(a1)));
                }
                glow(g2, (float) (cx + rx * Math.cos(head)), (float) (cy + ry * Math.sin(head)), 18f, col, oa * 0.85f);
            }
        }

        // version, loading line, stage text
        float va = easeOut((ms - 900f) / 400f);
        g2.setFont(InjectorWindow.font(13f, Font.BOLD));
        g2.setColor(rgba(WHITE, va));
        spaced(g2, version, cx, cy + lh / 2f + 28f, 3f);

        float pp = easeInOut((ms - 300f) / 1450f);
        float barW = 240f, bx = cx - barW / 2f, by = cy + lh / 2f + 52f;
        g2.setColor(rgba(WHITE, 0.11f));
        g2.fill(new RoundRectangle2D.Float(bx, by, barW, 3f, 3f, 3f));
        if (pp > 0f) {
            float fw = Math.max(3f, barW * pp);
            g2.setPaint(new GradientPaint(bx, 0, ACCENT, bx + fw, 0, ACCENT_HOT));
            g2.fill(new RoundRectangle2D.Float(bx, by, fw, 3f, 3f, 3f));
            glow(g2, bx + fw, by + 1.5f, 16f, ACCENT_HOT, 0.8f * (1f - 0.6f * clamp01((ms - 1750f) / 250f)));
        }
        String text = ms < 650f ? "INITIALISING" : ms < 1150f ? "LOADING MODULES" : ms < 1650f ? "SCANNING FOR GAMES" : "READY";
        g2.setFont(InjectorWindow.font(11.5f, Font.BOLD));
        g2.setColor(WHITE);
        spaced(g2, text, cx, by + 26f, 3.5f);

        g2.setTransform(base);
        // a shockwave leaves as the intro hands over to the window
        if (exit > 0f && exit < 1f) {
            setAlpha(g2, 1f);
            float r = 40f + 380f * easeOut(exit);
            g2.setStroke(new BasicStroke(2.5f * (1f - exit) + 0.5f));
            g2.setColor(rgba(ACCENT_HOT, 0.6f * (1f - exit)));
            g2.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
        }
    }

    /** The wordmark, cut off at {@code revealX} with a soft edge, with a shine of accent colour at {@code shine} (0..1). */
    private void drawWordmark(Graphics2D g2, float lx, float ly, float lw, float lh, float revealX, float shine) {
        int bw = (int) Math.ceil(lw), bh = (int) Math.ceil(lh);
        if (buf == null || buf.getWidth() != bw || buf.getHeight() != bh) {
            buf = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
            scaledLogo = logo != null ? logo.getScaledInstance(bw, bh, Image.SCALE_SMOOTH) : null;
        }
        Graphics2D b = buf.createGraphics();
        b.setComposite(AlphaComposite.Clear);
        b.fillRect(0, 0, bw, bh);
        b.setComposite(AlphaComposite.SrcOver);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (scaledLogo != null) {
            b.drawImage(scaledLogo, 0, 0, null);
        } else {
            b.setColor(WHITE);
            b.setFont(InjectorWindow.font(46f, Font.BOLD));
            FontMetrics fm = b.getFontMetrics();
            b.drawString("ARSENIC", (bw - fm.stringWidth("ARSENIC")) / 2, (bh + fm.getAscent() - fm.getDescent()) / 2);
        }
        b.setComposite(AlphaComposite.DstIn);
        b.setPaint(new LinearGradientPaint(revealX - 56f, 0, revealX, 0, new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 0)}));
        b.fillRect(0, 0, bw, bh);
        if (shine > 0f && shine < 1f) {
            float sx = -70f + (bw + 140f) * easeInOut(shine);
            b.setComposite(AlphaComposite.SrcAtop);
            b.setPaint(new LinearGradientPaint(sx - 50f, 0, sx + 50f, 0, new float[]{0f, 0.5f, 1f},
                    new Color[]{rgba(ACCENT_HOT, 0f), ACCENT_HOT, rgba(ACCENT_HOT, 0f)}));
            b.fillRect(0, 0, bw, bh);
        }
        b.dispose();
        g2.drawImage(buf, Math.round(lx), Math.round(ly), null);
    }

    private static void scanLine(Graphics2D g2, float x, float cy, float half, float width, Color edge, Color mid) {
        g2.setPaint(new LinearGradientPaint(x, cy - half, x, cy + half, new float[]{0f, 0.5f, 1f},
                new Color[]{rgba(edge, 0f), mid, rgba(edge, 0f)}));
        g2.fill(new java.awt.geom.Rectangle2D.Float(x - width / 2f, cy - half, width, half * 2f));
    }

    private void paintInject(Graphics2D g2, int w, int h) {
        float cx = w / 2f, cy = h / 2f - 14f;
        float pm = ms - phaseStart;
        float rm = resulted ? ms - resultStart : 0f;
        float backdrop = 0.94f, content = 1f;
        if (phase == IN) {
            backdrop *= easeOut(pm / 350f);
            content = easeOut(pm / 350f);
        } else if (phase == OUT) {
            float o = easeInOut(pm / 380f);
            backdrop *= 1f - o;
            content = 1f - o;
        }
        Color tone = !resulted ? ACCENT : ok ? SUCCESS : DANGER;

        setAlpha(g2, backdrop);
        g2.setColor(BACKGROUND);
        g2.fillRect(0, 0, w, h);
        setAlpha(g2, content);
        glow(g2, cx, cy, 320f, tone, 0.17f + 0.05f * (float) Math.sin(ms / 240f));
        if (resulted && rm < 400f)
            glow(g2, cx, cy, 420f, WHITE, 0.16f * (1f - rm / 400f));

        java.awt.geom.AffineTransform base = g2.getTransform();
        if (resulted && !ok && rm < 520f)
            g2.translate(Math.sin(rm * 0.065) * 11.0 * (1.0 - rm / 520f), 0);

        // the reactor: three dashed rings, a core and the particles being drawn into it
        float collapse = resulted ? clamp01(rm / 240f) : 0f;
        float ringScale = (1f - 0.85f * easeIn(collapse)) * (ms < 500f ? easeOutBack(ms / 500f) : 1f);
        float ringAlpha = 1f - collapse;
        if (ringAlpha > 0f && ringScale > 0.05f) {
            ring(g2, cx, cy, 44f * ringScale, 3, 0.55f, 3.5f, rgba(ACCENT, ringAlpha), 120f);
            ring(g2, cx, cy, 64f * ringScale, 12, 0.5f, 2f, rgba(WHITE, 0.7f * ringAlpha), -80f);
            ring(g2, cx, cy, 86f * ringScale, 2, 0.8f, 1.5f, rgba(ACCENT_HOT, 0.55f * ringAlpha), 50f);
        }
        for (Swirl s : swirls) {
            float f = clamp01(1f - s.radius / s.r0);
            float rate = s.spin * (160f / (s.radius + 40f));
            float tr = s.radius + s.vr * 0.05f;
            double ta = s.angle - rate * 0.05;
            g2.setStroke(new BasicStroke(1.2f + 1.2f * f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(rgba(s.color, clamp01(0.15f + f * 1.2f)));
            g2.draw(new Line2D.Double(cx + Math.cos(ta) * tr, cy + Math.sin(ta) * tr,
                    cx + Math.cos(s.angle) * s.radius, cy + Math.sin(s.angle) * s.radius));
        }
        float coreFade = resulted ? 1f - easeIn(clamp01(rm / 200f)) : 1f;
        float coreR = (10f + 2.5f * (float) Math.sin(ms / 110f) + pulse * 7f) * coreFade * (ms < 400f ? easeOutBack(ms / 400f) : 1f);
        if (coreR > 0.5f) {
            glow(g2, cx, cy, coreR * 7f, ACCENT, Math.min(1f, 0.5f + pulse * 0.5f));
            g2.setPaint(new RadialGradientPaint(cx, cy, coreR, new float[]{0f, 1f}, new Color[]{WHITE, ACCENT_HOT}));
            g2.fill(new Ellipse2D.Float(cx - coreR, cy - coreR, coreR * 2f, coreR * 2f));
        }

        if (resulted)
            paintResult(g2, cx, cy, rm, content, tone);
        else {
            g2.setFont(InjectorWindow.font(14f, Font.BOLD));
            g2.setColor(WHITE);
            spaced(g2, "INJECTING", cx, cy + 120f, 5f);
            g2.setFont(InjectorWindow.font(12.5f, Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            String line = fit(fm, stage, w - 80);
            g2.drawString(line, cx - fm.stringWidth(line) / 2f, cy + 144f);
        }

        g2.setTransform(base);
        setAlpha(g2, content);
        paintParticles(g2);
    }

    private void paintResult(Graphics2D g2, float cx, float cy, float rm, float content, Color tone) {
        // shockwaves
        float[] delays = ok ? new float[]{0f, 90f, 180f} : new float[]{0f, 90f};
        Color[] cols = ok ? new Color[]{WHITE, tone, ACCENT_HOT} : new Color[]{WHITE, tone};
        for (int i = 0; i < delays.length; i++) {
            float p = clamp01((rm - delays[i]) / 750f);
            if (p <= 0f || p >= 1f)
                continue;
            float r = 24f + 310f * easeOut(p);
            g2.setStroke(new BasicStroke(3.2f * (1f - p) + 0.6f));
            g2.setColor(rgba(cols[i], (1f - p) * 0.85f));
            g2.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
        }
        float fp = clamp01(rm / 340f);
        if (fp < 1f)
            glow(g2, cx, cy, 30f + 180f * easeOut(fp), WHITE, (1f - fp) * 0.9f);

        // the result ring draws itself, with a glow under it
        float rp = clamp01((rm - 150f) / 500f);
        if (rp > 0f) {
            float rr = 54f * (0.9f + 0.1f * easeOutBack(rp));
            g2.setColor(rgba(tone, 0.14f * easeOut(rp)));
            g2.fill(new Ellipse2D.Float(cx - rr, cy - rr, rr * 2f, rr * 2f));
            double sweep = -360.0 * easeOut(rp);
            Arc2D arc = new Arc2D.Float(cx - rr, cy - rr, rr * 2f, rr * 2f, 90f, (float) sweep, Arc2D.OPEN);
            g2.setStroke(new BasicStroke(13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(rgba(tone, 0.2f));
            g2.draw(arc);
            g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(tone);
            g2.draw(arc);
        }

        // the glyph is drawn stroke by stroke
        float gp = easeOut((rm - 420f) / 420f);
        if (gp > 0f) {
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(WHITE);
            if (ok) {
                polyline(g2, new float[][]{{cx - 20f, cy + 2f}, {cx - 6f, cy + 16f}, {cx + 22f, cy - 14f}}, gp);
            } else {
                polyline(g2, new float[][]{{cx - 15f, cy - 15f}, {cx + 15f, cy + 15f}}, clamp01(gp * 2f));
                polyline(g2, new float[][]{{cx + 15f, cy - 15f}, {cx - 15f, cy + 15f}}, clamp01(gp * 2f - 1f));
            }
        }

        float tp = easeOut((rm - 620f) / 350f);
        if (tp > 0f) {
            setAlpha(g2, content * tp);
            g2.setFont(InjectorWindow.font(20f, Font.BOLD));
            g2.setColor(WHITE);
            spaced(g2, ok ? "INJECTED" : "INJECTION FAILED", cx, cy + 122f + 8f * (1f - tp), 5f);
            g2.setFont(InjectorWindow.font(12.5f, Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            String note = ok ? "Press Right Shift in game to open the ClickGUI" : "Details below say what went wrong";
            g2.drawString(note, cx - fm.stringWidth(note) / 2f, cy + 148f + 8f * (1f - tp));
            setAlpha(g2, content);
        }
    }

    private void ring(Graphics2D g2, float cx, float cy, float r, int dashes, float fill, float width, Color color, float speed) {
        if (r < 4f)
            return;
        float c = (float) (2 * Math.PI * r);
        float on = c / dashes * fill, off = c / dashes * (1f - fill);
        float phase = (spin * speed) % c;
        if (phase < 0f)
            phase += c;
        Ellipse2D circle = new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(rgba(WHITE, 0.08f * color.getAlpha() / 255f));
        g2.draw(circle);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{on, off}, phase));
        g2.setColor(color);
        g2.draw(circle);
    }

    private void paintParticles(Graphics2D g2) {
        for (Particle p : particles) {
            float a = clamp01(p.life / p.max) * p.alpha;
            g2.setStroke(new BasicStroke(p.size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(rgba(p.color, a));
            g2.draw(new Line2D.Float(p.x - p.vx * 0.035f - 0.01f, p.y - p.vy * 0.035f - 0.01f, p.x, p.y));
        }
    }

    // ---- bits ----

    private static final class Particle {
        float x, y, vx, vy, gravity, drag, life, max, size, alpha;
        Color color;
    }

    /** A particle on a spiral into the reactor's core, in polar coordinates around it. */
    private static final class Swirl {
        float angle, radius, r0, vr, spin;
        Color color;
    }

    private static void setAlpha(Graphics2D g2, float a) {
        g2.setComposite(AlphaComposite.SrcOver.derive(clamp01(a)));
    }

    private static Color rgba(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(clamp01(a) * 255f));
    }

    private static void glow(Graphics2D g2, float cx, float cy, float r, Color c, float a) {
        if (a <= 0.004f || r < 1f)
            return;
        g2.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 1f}, new Color[]{rgba(c, a), rgba(c, 0f)}));
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
    }

    /** Draws the first {@code frac} (by length) of a polyline. */
    private static void polyline(Graphics2D g2, float[][] pts, float frac) {
        if (frac <= 0f)
            return;
        float total = 0f;
        float[] seg = new float[pts.length - 1];
        for (int i = 0; i < seg.length; i++) {
            seg[i] = (float) Math.hypot(pts[i + 1][0] - pts[i][0], pts[i + 1][1] - pts[i][1]);
            total += seg[i];
        }
        float left = total * frac;
        Path2D.Float path = new Path2D.Float();
        path.moveTo(pts[0][0], pts[0][1]);
        for (int i = 0; i < seg.length && left > 0f; i++) {
            float f = Math.min(1f, left / seg[i]);
            path.lineTo(pts[i][0] + (pts[i + 1][0] - pts[i][0]) * f, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * f);
            left -= seg[i];
        }
        g2.draw(path);
    }

    /** Centred text with extra space between the letters. */
    private static void spaced(Graphics2D g2, String s, float cx, float y, float tracking) {
        FontMetrics fm = g2.getFontMetrics();
        float total = -tracking;
        for (int i = 0; i < s.length(); i++)
            total += fm.charWidth(s.charAt(i)) + tracking;
        float x = cx - total / 2f;
        for (int i = 0; i < s.length(); i++) {
            g2.drawString(String.valueOf(s.charAt(i)), x, y);
            x += fm.charWidth(s.charAt(i)) + tracking;
        }
    }

    private static String fit(FontMetrics fm, String s, int max) {
        if (fm.stringWidth(s) <= max)
            return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > max)
            s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static float easeOut(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    private static float easeIn(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    private static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float easeOutBack(float t) {
        float u = clamp01(t) - 1f;
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
    }
}
