package arsenic.inject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A quiet Material You overlay for the injector: a short branded splash and a simple
 * status card while the agent attaches. It deliberately avoids particle effects and
 * glow; the only motion is a fade and a standard indeterminate progress line.
 */
final class InjectorFx extends JComponent {

    private static final int NONE = 0, INTRO = 1, INJECT = 2;
    private static final int IN = 0, RUN = 1, RESULT = 2, OUT = 3;

    private static final float INTRO_IN = 180f;
    private static final float INTRO_HOLD = 900f;
    private static final float INTRO_OUT = 180f;
    private static final float INJECT_IN = 160f;
    private static final float MIN_RUN = 550f;
    private static final float RESULT_HOLD = 1100f;
    private static final float OUT_MS = 180f;

    private final BufferedImage logo;
    private final String version;
    private final Timer timer = new Timer(16, e -> tick());

    private int scene = NONE;
    private boolean started;
    private long sceneStart, lastTick;
    private float ms, dt;

    private Runnable introDone;
    private int phase;
    private float phaseStart, resultStart, progressPhase;
    private boolean pending, pendingOk, ok, resulted;
    private String stage = "Attaching to the game...";
    private Runnable atResult;

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
        phaseStart = 0f;
        resultStart = 0f;
        progressPhase = 0f;
        pending = false;
        pendingOk = false;
        ok = false;
        resulted = false;
        atResult = null;
        stage = "Attaching to the game...";
        begin(INJECT);
    }

    void stage(String text) {
        if (text != null && !text.isEmpty())
            stage = text;
    }

    /** Shows the result once the card has been visible for a moment. */
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
        scene = newScene;
        started = false;
        ms = 0f;
        setVisible(true);
        timer.start();
    }

    private void end() {
        timer.stop();
        int was = scene;
        scene = NONE;
        setVisible(false);
        if (was == INTRO && introDone != null) {
            Runnable r = introDone;
            introDone = null;
            r.run();
        }
    }

    // ---- timeline ----

    private void tick() {
        if (scene == NONE)
            return;
        long now = System.nanoTime();
        if (!started) {
            if (!isShowing() || getWidth() == 0)
                return;
            started = true;
            sceneStart = lastTick = now;
        }
        dt = Math.min(0.05f, (now - lastTick) / 1e9f);
        lastTick = now;
        ms = (now - sceneStart) / 1e6f;

        if (scene == INTRO)
            tickIntro();
        else
            tickInject();
        if (scene == NONE)
            return;
        repaint();
    }

    private void tickIntro() {
        if (ms >= INTRO_IN + INTRO_HOLD + INTRO_OUT)
            end();
    }

    private void tickInject() {
        float pm = ms - phaseStart;
        if (phase == IN && pm >= INJECT_IN) {
            phase = RUN;
            phaseStart = ms;
        } else if (phase == RUN) {
            progressPhase = (progressPhase + dt * 0.45f) % 1f;
            if (pending && ms - phaseStart >= MIN_RUN)
                beginResult();
        } else if (phase == RESULT) {
            if (ms - resultStart >= RESULT_HOLD) {
                phase = OUT;
                phaseStart = ms;
            }
        } else if (phase == OUT && pm >= OUT_MS) {
            end();
        }
    }

    private void beginResult() {
        ok = pendingOk;
        resulted = true;
        phase = RESULT;
        phaseStart = resultStart = ms;
        if (atResult != null) {
            Runnable r = atResult;
            atResult = null;
            r.run();
        }
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics g) {
        if (scene == NONE)
            return;
        Graphics2D g2 = InjectorTheme.smooth(g);
        // the window is a rounded translucent surface: never paint outside its corners, or the
        // intro/inject scenes would show square edges over the desktop
        g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 56, 56));
        if (scene == INTRO)
            paintIntro(g2, getWidth(), getHeight());
        else
            paintInject(g2, getWidth(), getHeight());
        g2.dispose();
    }

    private void paintIntro(Graphics2D g2, int w, int h) {
        float fadeIn = easeOut(ms / INTRO_IN);
        float fadeOut = 1f - easeInOut((ms - INTRO_IN - INTRO_HOLD) / INTRO_OUT);
        float alpha = clamp01(Math.min(fadeIn, fadeOut));

        setAlpha(g2, alpha);
        g2.setColor(InjectorTheme.SURFACE);
        g2.fillRect(0, 0, w, h);

        float cx = w / 2f;
        float cy = h / 2f - 24f;
        float lw = Math.min(190f, w - 120f);
        float lh = logo != null ? lw * logo.getHeight() / logo.getWidth() : 54f;
        if (logo != null) {
            g2.drawImage(logo, Math.round(cx - lw / 2f), Math.round(cy - lh / 2f),
                    Math.round(lw), Math.round(lh), null);
        } else {
            g2.setFont(InjectorTheme.semibold(28f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(InjectorTheme.ON_SURFACE);
            g2.drawString("ARSENIC", cx - fm.stringWidth("ARSENIC") / 2f,
                    cy + (fm.getAscent() - fm.getDescent()) / 2f);
        }

        g2.setFont(InjectorTheme.medium(11f));
        FontMetrics fm = g2.getFontMetrics();
        String v = "v" + version;
        g2.setColor(InjectorTheme.ON_SURFACE_VARIANT);
        g2.drawString(v, cx - fm.stringWidth(v) / 2f, cy + lh / 2f + 22f);

        float barW = 220f;
        float bx = cx - barW / 2f;
        float by = cy + lh / 2f + 42f;
        g2.setColor(InjectorTheme.SURFACE_HIGH);
        g2.fillRoundRect(Math.round(bx), Math.round(by), Math.round(barW), 4, 4, 4);
        float progress = easeInOut((ms - 160f) / 820f);
        if (progress > 0f) {
            g2.setColor(InjectorTheme.PRIMARY);
            g2.fillRoundRect(Math.round(bx), Math.round(by),
                    Math.max(4, Math.round(barW * progress)), 4, 4, 4);
        }

        g2.setFont(InjectorTheme.regular(11.5f));
        fm = g2.getFontMetrics();
        String label = "Starting Arsenic";
        g2.setColor(InjectorTheme.ON_SURFACE_VARIANT);
        g2.drawString(label, cx - fm.stringWidth(label) / 2f, by + 24f);
    }

    private void paintInject(Graphics2D g2, int w, int h) {
        float fadeIn = easeOut(ms / INJECT_IN);
        float fadeOut = phase == OUT ? easeInOut((ms - phaseStart) / OUT_MS) : 0f;
        float alpha = clamp01(fadeIn * (1f - fadeOut));

        setAlpha(g2, alpha);
        g2.setColor(InjectorTheme.alpha(InjectorTheme.SCRIM, 0.62f));
        g2.fillRect(0, 0, w, h);

        float cw = Math.min(380f, w - 72f);
        float ch = 178f;
        float x = (w - cw) / 2f;
        float y = (h - ch) / 2f;
        float radius = 24f;

        g2.setColor(InjectorTheme.SURFACE_HIGH);
        g2.fill(new RoundRectangle2D.Float(x, y, cw, ch, radius, radius));
        g2.setColor(InjectorTheme.OUTLINE_VARIANT);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(x, y, cw, ch, radius, radius));

        float iconCx = x + 44f;
        float iconCy = y + 46f;
        float iconR = 22f;
        Color iconFill = resulted
                ? (ok ? InjectorTheme.SUCCESS_CONTAINER : InjectorTheme.DANGER_CONTAINER)
                : InjectorTheme.PRIMARY_CONTAINER;
        g2.setColor(iconFill);
        g2.fill(new Ellipse2D.Float(iconCx - iconR, iconCy - iconR, iconR * 2f, iconR * 2f));

        if (resulted) {
            Color iconColor = ok ? InjectorTheme.SUCCESS : InjectorTheme.DANGER;
            g2.setColor(iconColor);
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (ok) {
                g2.drawPolyline(new int[]{
                                Math.round(iconCx - 8f), Math.round(iconCx - 2f), Math.round(iconCx + 9f)},
                        new int[]{
                                Math.round(iconCy), Math.round(iconCy + 7f), Math.round(iconCy - 7f)}, 3);
            } else {
                g2.drawLine(Math.round(iconCx - 7f), Math.round(iconCy - 7f),
                        Math.round(iconCx + 7f), Math.round(iconCy + 7f));
                g2.drawLine(Math.round(iconCx + 7f), Math.round(iconCy - 7f),
                        Math.round(iconCx - 7f), Math.round(iconCy + 7f));
            }
        } else {
            g2.setColor(InjectorTheme.OUTLINE_VARIANT);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Arc2D.Float(iconCx - 9f, iconCy - 9f, 18f, 18f, 0f, 360f, Arc2D.OPEN));
            g2.setColor(InjectorTheme.PRIMARY);
            g2.draw(new Arc2D.Float(iconCx - 9f, iconCy - 9f, 18f, 18f,
                    (float) (ms * 0.12) % 360f, 110f, Arc2D.OPEN));
        }

        String title = resulted ? (ok ? "Injected" : "Injection failed") : "Injecting Arsenic";
        String note = resulted
                ? (ok ? "Arsenic is ready. Press Right Shift in game."
                : "Check the details in the injector window.")
                : stage;

        g2.setFont(InjectorTheme.semibold(17f));
        g2.setColor(InjectorTheme.ON_SURFACE);
        g2.drawString(title, x + 80f, y + 42f);

        g2.setFont(InjectorTheme.regular(11.5f));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(InjectorTheme.ON_SURFACE_VARIANT);
        g2.drawString(fit(fm, note, Math.round(cw - 104f)), x + 80f, y + 64f);

        float barX = x + 24f;
        float barY = y + ch - 34f;
        float barW = cw - 48f;
        g2.setColor(InjectorTheme.SURFACE_HIGHEST);
        g2.fillRoundRect(Math.round(barX), Math.round(barY), Math.round(barW), 4, 4, 4);
        if (resulted) {
            g2.setColor(ok ? InjectorTheme.SUCCESS : InjectorTheme.DANGER);
            g2.fillRoundRect(Math.round(barX), Math.round(barY), Math.round(barW), 4, 4, 4);
        } else {
            float len = Math.max(42f, barW / 3f);
            float travel = barW + len;
            float sx = barX - len + progressPhase * travel;
            float x1 = Math.max(barX, sx);
            float x2 = Math.min(barX + barW, sx + len);
            if (x2 > x1) {
                g2.setColor(InjectorTheme.PRIMARY);
                g2.fillRoundRect(Math.round(x1), Math.round(barY), Math.round(x2 - x1), 4, 4, 4);
            }
        }
    }

    // ---- helpers ----

    private static void setAlpha(Graphics2D g2, float a) {
        g2.setComposite(AlphaComposite.SrcOver.derive(clamp01(a)));
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

    private static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }
}
