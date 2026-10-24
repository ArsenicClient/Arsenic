package arsenic.inject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The injector's window: a list of running games as cards, an Inject button with progress, and a log of what the
 * agent reported. Everything is painted by hand so it looks the same on every system look and feel, in the colours of
 * the client's default (Classic) theme.
 */
final class InjectorWindow {

    // ---- Material You 3 roles (Classic accent) ----
    private static final Color BACKGROUND = InjectorTheme.SURFACE;
    private static final Color SURFACE = InjectorTheme.SURFACE_LOW;
    private static final Color CARD = InjectorTheme.SURFACE_CONTAINER;
    private static final Color SURFACE_HOVER = InjectorTheme.SURFACE_HIGH;
    private static final Color SURFACE_HIGHEST = InjectorTheme.SURFACE_HIGHEST;
    private static final Color BORDER = InjectorTheme.OUTLINE_VARIANT;
    private static final Color BORDER_HOVER = InjectorTheme.OUTLINE;
    private static final Color TEXT = InjectorTheme.ON_SURFACE;
    private static final Color TEXT_SECONDARY = InjectorTheme.ON_SURFACE_VARIANT;
    private static final Color TEXT_MUTED = InjectorTheme.OUTLINE;
    private static final Color OUTLINE = InjectorTheme.OUTLINE;
    private static final Color ACCENT = InjectorTheme.PRIMARY;
    private static final Color ACCENT_HOVER = InjectorTheme.mix(InjectorTheme.PRIMARY, InjectorTheme.ON_SURFACE, 0.12f);
    private static final Color ON_ACCENT = InjectorTheme.ON_PRIMARY;
    private static final Color ACCENT_CONTAINER = InjectorTheme.PRIMARY_CONTAINER;
    private static final Color PRIMARY_CONTAINER = InjectorTheme.PRIMARY_CONTAINER;
    private static final Color ON_ACCENT_CONTAINER = InjectorTheme.ON_PRIMARY_CONTAINER;
    private static final Color SUCCESS = InjectorTheme.SUCCESS;
    private static final Color SUCCESS_CONTAINER = InjectorTheme.SUCCESS_CONTAINER;
    private static final Color DANGER = InjectorTheme.DANGER;
    private static final Color DANGER_CONTAINER = InjectorTheme.DANGER_CONTAINER;
    private static final Color OUTLINE_VARIANT = InjectorTheme.OUTLINE_VARIANT;
    private static final Color WARNING = InjectorTheme.WARNING;
    private static final Color WARNING_CONTAINER = InjectorTheme.WARNING_CONTAINER;

    private static final int RADIUS = 12;
    private static final int WINDOW_RADIUS = 28;
    private static final int REFRESH_MS = 2500;

    private final JFrame frame = new JFrame("Arsenic Injector");
    private final JPanel cards = new JPanel();
    private final JScrollPane cardScroll;
    private final JPanel listPanel = new RoundedPanel(SURFACE, RADIUS);
    private JPanel rootPanel;
    private final ActionButton injectButton = new ActionButton("Inject", ActionButton.FILLED);
    private final ActionButton uninjectButton = new ActionButton("Uninject", ActionButton.TONAL);
    private final ActionButton injectLunarButton = new ActionButton("Inject", ActionButton.TONAL);
    private final ActionButton removeLunarButton = new ActionButton("Remove", ActionButton.TEXT);
    private final IconButton refreshButton = new IconButton(IconButton.REFRESH, "Refresh");
    private final Toggle showAll = new Toggle("Show every Java process");
    private final ProgressBar progress = new ProgressBar();
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea log = new JTextArea();
    private final JScrollPane logScroll;
    private final LinkLabel logToggle = new LinkLabel("Show details");
    private final Timer autoRefresh;
    private final InjectorFx fx = new InjectorFx(image("/assets/arsenic/logos/modern.png"), version());

    private final List<GameCard> gameCards = new ArrayList<>();
    private final java.util.Set<String> injected = new java.util.HashSet<>();
    private String selectedId;
    private boolean busy;
    private boolean refreshing;
    private boolean shaping;

    InjectorWindow() {
        cardScroll = scroll(cards);
        logScroll = scroll(log);
        autoRefresh = new Timer(REFRESH_MS, e -> {
            if (!busy && frame.isActive())
                refresh();
        });
    }

    void show() {
        rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                int w = getWidth(), h = getHeight();
                // cover the whole square window first so nothing light can flash through, then
                // draw the rounded chrome; the corners themselves are cut by the window shape
                g2.setColor(BACKGROUND);
                g2.fillRect(0, 0, w, h);
                g2.setColor(BACKGROUND);
                g2.fillRoundRect(0, 0, w, h, WINDOW_RADIUS * 2, WINDOW_RADIUS * 2);
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, WINDOW_RADIUS * 2, WINDOW_RADIUS * 2);
                g2.dispose();
            }
        };
        // keep the backing opaque: with per-pixel translucency every repaint (hover, the fx
        // timer, list changes) makes the window flicker on Java 8/21 X11, and the flash shows
        // whatever the LAF paints behind this panel. Opaque + a rounded window shape does not.
        rootPanel.setOpaque(true);
        rootPanel.setBackground(InjectorTheme.SURFACE);
        rootPanel.setBorder(new EmptyBorder(1, 1, 1, 1));
        rootPanel.add(titleBar(), BorderLayout.NORTH);

        JPanel body = transparent(new BorderLayout());
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(2, 20, 18, 20));

        JComponent header = listHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(header);
        body.add(Box.createVerticalStrut(12));

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setOpaque(false);
        listPanel.setLayout(new BorderLayout());
        listPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        listPanel.add(cardScroll, BorderLayout.CENTER);
        body.add(listPanel);
        body.add(Box.createVerticalStrut(12));

        JPanel bottom = transparent(new BorderLayout());
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent footerPanel = footer();
        footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(footerPanel);
        bottom.add(Box.createVerticalStrut(10));
        JComponent lunarPanel = launchingInLunar();
        lunarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(lunarPanel);
        body.add(bottom);

        rootPanel.add(body, BorderLayout.CENTER);

        frame.setUndecorated(true);
        frame.setBackground(InjectorTheme.SURFACE);
        frame.setContentPane(rootPanel);
        frame.setIconImages(icons());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setSize(600, 540);
        frame.setLocationRelativeTo(null);
        frame.getRootPane().setDefaultButton(null);
        updateWindowShape();
        // the effects layer sits over everything: the start-up intro plays on it, and so does the inject sequence
        frame.setGlassPane(fx);
        fx.playIntro(null);
        frame.setVisible(true);
        refresh();
        autoRefresh.start();
    }

    // ---- layout ----

    private JComponent titleBar() {
        JPanel bar = transparent(new BorderLayout());
        bar.setBorder(new EmptyBorder(15, 20, 10, 12));

        JPanel brand = transparent(new FlowLayout(FlowLayout.LEFT, 11, 0));
        BufferedImage logo = image("/assets/arsenic/logos/modern.png");
        if (logo != null) {
            int h = 28, w = logo.getWidth() * h / logo.getHeight();
            brand.add(new JLabel(new ImageIcon(logo.getScaledInstance(w, h, Image.SCALE_SMOOTH))));
        }
        JPanel names = transparent(new GridLayout(2, 1, 0, 0));
        names.add(label("Injector", 15f, Font.BOLD, TEXT));
        names.add(label("Minecraft 1.8.9", 10.5f, Font.PLAIN, TEXT_SECONDARY));
        brand.add(names);
        brand.add(new Chip(version(), ACCENT_CONTAINER, ON_ACCENT_CONTAINER));
        bar.add(brand, BorderLayout.WEST);

        JPanel controls = transparent(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        IconButton minimise = new IconButton(IconButton.MINIMISE, "Minimise");
        minimise.addActionListener(() -> frame.setState(Frame.ICONIFIED));
        IconButton close = new IconButton(IconButton.CLOSE, "Close");
        close.addActionListener(() -> System.exit(0));
        controls.add(minimise);
        controls.add(close);
        bar.add(controls, BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            private Point grab;

            @Override
            public void mousePressed(MouseEvent e) {
                grab = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point on = e.getLocationOnScreen();
                frame.setLocation(on.x - grab.x, on.y - grab.y);
            }
        };
        bar.addMouseListener(drag);
        bar.addMouseMotionListener(drag);
        brand.addMouseListener(drag);
        brand.addMouseMotionListener(drag);
        names.addMouseListener(drag);
        names.addMouseMotionListener(drag);
        return bar;
    }

    private JComponent listHeader() {
        JPanel header = transparent(new BorderLayout());
        JPanel text = transparent(new GridLayout(2, 1, 0, 2));
        text.add(label("Running games", 16f, Font.BOLD, TEXT));
        text.add(label("Forge or Vanilla 1.8.9", 11.5f, Font.PLAIN, TEXT_SECONDARY));
        header.add(text, BorderLayout.WEST);

        JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        showAll.onChange(this::refresh);
        refreshButton.addActionListener(this::refresh);
        right.add(showAll);
        right.add(refreshButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent footer() {
        JPanel footer = new RoundedPanel(SURFACE, RADIUS);
        footer.setLayout(new BorderLayout(0, 10));
        footer.setBorder(new EmptyBorder(12, 14, 12, 14));

        progress.setPreferredSize(new Dimension(10, 4));
        footer.add(progress, BorderLayout.NORTH);

        JPanel row = transparent(new BorderLayout(12, 0));
        statusLabel.setFont(font(12.5f, Font.PLAIN));
        statusLabel.setForeground(TEXT_SECONDARY);
        JPanel statusColumn = transparent(new GridLayout(2, 1, 0, 2));
        statusColumn.add(statusLabel);
        logToggle.addActionListener(this::toggleLog);
        JPanel linkRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        linkRow.add(logToggle);
        statusColumn.add(linkRow);
        row.add(statusColumn, BorderLayout.CENTER);
        injectButton.addActionListener(this::injectSelected);
        uninjectButton.addActionListener(this::uninjectSelected);
        JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(uninjectButton);
        actions.add(injectButton);
        row.add(actions, BorderLayout.EAST);
        footer.add(row, BorderLayout.CENTER);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(InjectorTheme.mono(11));
        log.setForeground(TEXT_SECONDARY);
        log.setBackground(SURFACE);
        log.setCaretColor(ACCENT);
        log.setBorder(new EmptyBorder(9, 11, 9, 11));
        logScroll.setPreferredSize(new Dimension(10, 120));
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        logScroll.setVisible(false);
        footer.add(logScroll, BorderLayout.SOUTH);
        return footer;
    }

    private void toggleLog() {
        boolean show = !logScroll.isVisible();
        logScroll.setVisible(show);
        logToggle.setText(show ? "Hide details" : "Show details");
        fitWindow();
    }

    // ---- behaviour ----

    private void refresh() {
        // the game scan can shell out (Lunar is found through the OS process list), so it runs off the event thread
        if (refreshing)
            return;
        refreshing = true;
        boolean all = showAll.isSelected();
        new SwingWorker<List<Injector.Game>, Void>() {
            @Override
            protected List<Injector.Game> doInBackground() throws Exception {
                return Injector.findGames(all);
            }

            @Override
            protected void done() {
                refreshing = false;
                try {
                    applyGames(get());
                } catch (Exception e) {
                    setStatus("Could not list Java processes: " + e, DANGER);
                }
            }
        }.execute();
    }

    private void applyGames(List<Injector.Game> games) {
        boolean same = games.size() == gameCards.size();
        for (int i = 0; same && i < games.size(); i++)
            same = games.get(i).id.equals(gameCards.get(i).game.id) && games.get(i).label().equals(gameCards.get(i).game.label());
        if (same) {
            updateButtons();
            return;
        }

        gameCards.clear();
        cards.removeAll();
        if (games.isEmpty()) {
            cards.add(new EmptyState());
            if (selectedId != null)
                selectedId = null;
        } else {
            boolean selectionAlive = false;
            for (Injector.Game game : games)
                selectionAlive |= game.id.equals(selectedId);
            if (!selectionAlive) {
                selectedId = null;
                // prefer a game the injector can actually attach to, but fall back to any supported one
                for (Injector.Game game : games)
                    if (game.supported() && game.attachable && !injected.contains(game.id)) {
                        selectedId = game.id;
                        break;
                    }
                if (selectedId == null)
                    for (Injector.Game game : games)
                        if (game.supported() && !injected.contains(game.id)) {
                            selectedId = game.id;
                            break;
                        }
            }
            for (Injector.Game game : games) {
                GameCard card = new GameCard(game);
                gameCards.add(card);
                cards.add(card);
                cards.add(Box.createVerticalStrut(8));
            }
        }
        cards.revalidate();
        cards.repaint();
        updateListHeight();
        fitWindow();
        if (!busy)
            setStatus(games.isEmpty() ? "Start Minecraft and it will show up here." : selectedHint(), TEXT_SECONDARY);
        updateButtons();
    }

    private void updateListHeight() {
        int content = Math.max(84, cards.getPreferredSize().height);
        int height = Math.min(320, content + 2);
        listPanel.setPreferredSize(new Dimension(10, height));
        listPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        listPanel.setMinimumSize(new Dimension(10, height));
        listPanel.revalidate();
    }

    /** Keeps the window as compact as its content while the game list scrolls once it gets long. */
    private void fitWindow() {
        if (rootPanel == null)
            return;
        rootPanel.revalidate();
        int width = Math.max(600, frame.getWidth());
        int height = Math.max(430, rootPanel.getPreferredSize().height + 2);
        if (frame.getWidth() != width || frame.getHeight() != height) {
            frame.setSize(width, height);
            updateWindowShape();
        }
    }

    /** Cuts the rounded corners; reentrant and only applied when the size or first paint needs it. */
    private void updateWindowShape() {
        if (shaping)
            return;
        shaping = true;
        try {
            frame.setShape(new RoundRectangle2D.Double(0, 0,
                    Math.max(1, frame.getWidth()), Math.max(1, frame.getHeight()),
                    WINDOW_RADIUS * 2, WINDOW_RADIUS * 2));
        } catch (Throwable ignored) {
            // shaped windows are not supported everywhere; square corners are fine
        } finally {
            shaping = false;
        }
    }

    private String selectedHint() {
        Injector.Game game = selected();
        if (game == null)
            return "Pick a game.";
        if (injected.contains(game.id))
            return "Arsenic is already loaded in this game.";
        if (!game.supported())
            return game.unsupportedReason();
        if (!game.attachable)
            return game.client.display + " turns off attach. Load Arsenic with -javaagent in its JVM arguments instead (steps in details).";
        return "Ready to inject into " + game.client.display + ".";
    }

    private Injector.Game selected() {
        for (GameCard card : gameCards)
            if (card.game.id.equals(selectedId))
                return card.game;
        return null;
    }

    private void select(Injector.Game game) {
        if (busy)
            return;
        if (!game.id.equals(selectedId))
            progress.setState(ProgressBar.IDLE);
        selectedId = game.id;
        for (GameCard card : gameCards)
            card.repaint();
        setStatus(selectedHint(), TEXT_SECONDARY);
        updateButtons();
        checkInjected(game);
    }

    /** Asks the game whether Arsenic is in it, so the buttons follow the game rather than this window. */
    private void checkInjected(Injector.Game game) {
        if (!game.attachable)
            return;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return Injector.loadedState(game.id);
            }

            @Override
            protected void done() {
                String state = null;
                try {
                    state = get();
                } catch (Exception ignored) {
                    // not reachable: treated as not injected
                }
                if ("injected".equals(state))
                    injected.add(game.id);
                else
                    injected.remove(game.id);
                for (GameCard card : gameCards)
                    card.repaint();
                updateButtons();
            }
        }.execute();
    }

    private void updateButtons() {
        Injector.Game game = selected();
        injectButton.setEnabled(!busy && game != null && game.supported() && !injected.contains(game.id));
        uninjectButton.setEnabled(!busy && game != null && game.attachable && injected.contains(game.id));
        refreshButton.setEnabled(!busy);
        showAll.setEnabled(!busy);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text.isEmpty() ? " " : text);
        statusLabel.setForeground(color == null ? TEXT_SECONDARY : color);
    }

    private void appendLog(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private void injectSelected() {
        Injector.Game game = selected();
        if (game == null || busy)
            return;
        busy = true;
        updateButtons();
        injectButton.setText("Injecting...");
        progress.setState(ProgressBar.RUNNING);
        appendLog("== " + game.label() + " ==");
        setStatus("Attaching to the game...", TEXT);
        fx.playInject();

        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() {
                return Injector.inject(game.id, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks)
                    appendLog(line);
                String last = chunks.get(chunks.size() - 1);
                if (!last.startsWith("hooked ") && !last.startsWith("missed ")) {
                    setStatus(last, TEXT);
                    fx.stage(last);
                }
            }

            @Override
            protected void done() {
                String outcome;
                try {
                    outcome = get();
                } catch (Exception e) {
                    outcome = "ERROR " + e;
                }
                String result = outcome;
                // the window follows the result at the moment the animation shows it
                fx.finishInject(result.equals("OK"), () -> showInjectResult(game, result));
            }
        }.execute();
    }

    private void showInjectResult(Injector.Game game, String result) {
        busy = false;
        injectButton.setText("Inject");
        appendLog(result);
        if (result.equals("OK")) {
            injected.add(game.id);
            progress.setState(ProgressBar.DONE);
            setStatus("Injected. Press Right Shift in game to open the ClickGUI.", SUCCESS);
        } else {
            String reason = result.startsWith("ERROR") ? result.substring(5).trim() : result;
            progress.setState(ProgressBar.FAILED);
            if (!game.attachable) {
                appendLog("");
                appendLaunchSteps(game);
                setStatus(game.client.display + " can't be injected -- launch it with -javaagent (see details).", WARNING);
            } else {
                setStatus("Injection failed: " + reason, DANGER);
            }
            if (!logScroll.isVisible())
                toggleLog();
        }
        for (GameCard card : gameCards)
            card.repaint();
        updateButtons();
    }

    /** Takes Arsenic out of the selected game, through the agent (games that turn attach off are done in Lunar's settings). */
    private void uninjectSelected() {
        Injector.Game game = selected();
        if (game == null || busy)
            return;
        busy = true;
        updateButtons();
        uninjectButton.setText("Removing...");
        progress.setState(ProgressBar.RUNNING);
        appendLog("== " + game.label() + " (uninject) ==");
        setStatus("Removing Arsenic...", TEXT);

        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() {
                return Injector.uninject(game.id, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks)
                    appendLog(line);
                String last = chunks.get(chunks.size() - 1);
                if (!last.startsWith("hooked ") && !last.startsWith("missed "))
                    setStatus(last, TEXT);
            }

            @Override
            protected void done() {
                busy = false;
                uninjectButton.setText("Uninject");
                String result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = "ERROR " + e;
                }
                appendLog(result);
                if (result.equals("OK")) {
                    injected.remove(game.id);
                    progress.setState(ProgressBar.DONE);
                    setStatus("Removed. Restart the game to inject Arsenic again.", SUCCESS);
                } else {
                    String reason = result.startsWith("ERROR") ? result.substring(5).trim() : result;
                    progress.setState(ProgressBar.FAILED);
                    setStatus("Uninject failed: " + reason, DANGER);
                    if (!logScroll.isVisible())
                        toggleLog();
                }
                for (GameCard card : gameCards)
                    card.repaint();
                updateButtons();
            }
        }.execute();
    }

    /** How to load Arsenic at launch into a game that turns attach off. Lunar's steps are always shown at the bottom. */
    private void appendLaunchSteps(Injector.Game game) {
        appendLog(game.client.display + " turns off the Java attach mechanism, so the injector cannot reach it.");
        if (game.client == Injector.Client.LUNAR) {
            appendLog("Launching in Lunar is covered at the bottom of this window.");
            return;
        }
        appendLog("Load Arsenic at launch instead:");
        appendLog("  Add this line to the game's Java arguments:");
        appendLog("       " + Injector.agentArg());
    }

    /** Lunar Client turns attach off, so Arsenic goes into its launch arguments instead. Always shown, under the log. */
    private JComponent launchingInLunar() {
        JPanel panel = new RoundedPanel(CARD, RADIUS);
        panel.setLayout(new BorderLayout(14, 0));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel brand = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        BufferedImage logo = image("/assets/arsenic/inject/lunar.png");
        if (logo != null)
            brand.add(new JLabel(new ImageIcon(logo.getScaledInstance(30, 30, Image.SCALE_SMOOTH))));
        JPanel names = transparent(new GridLayout(2, 1, 0, 1));
        names.add(label("Lunar Client", 14f, Font.BOLD, TEXT));
        names.add(label("Load Arsenic through Lunar's JVM arguments", 11f, Font.PLAIN, TEXT_SECONDARY));
        brand.add(names);
        panel.add(brand, BorderLayout.WEST);

        injectLunarButton.addActionListener(() -> lunarTask(LunarAction.INJECT));
        removeLunarButton.addActionListener(() -> lunarTask(LunarAction.REMOVE));
        JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(removeLunarButton);
        actions.add(injectLunarButton);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    /** What the Lunar buttons do to Lunar's JVM arguments. */
    private enum LunarAction {
        INJECT("Inject", "Inject into Lunar", "adds Arsenic to Lunar's JVM arguments"),
        REMOVE("Remove", "Remove from Lunar", "takes Arsenic out of Lunar's JVM arguments");

        final String label;
        final String title;
        final String effect;

        LunarAction(String label, String title, String effect) {
            this.label = label;
            this.title = title;
            this.effect = effect;
        }
    }

    private ActionButton lunarButton(LunarAction action) {
        return action == LunarAction.INJECT ? injectLunarButton : removeLunarButton;
    }

    private void setLunarButtonsEnabled(boolean enabled) {
        for (LunarAction action : LunarAction.values()) {
            lunarButton(action).setText(action.label);
            lunarButton(action).setEnabled(enabled);
        }
    }

    /** Stops Lunar, then changes its launch arguments. Runs off the event thread. */
    private void lunarTask(LunarAction action) {
        if (busy)
            return;
        String question = "This closes Lunar Client and any game running in it, then " + action.effect + ".\n\nContinue?";
        int answer = JOptionPane.showConfirmDialog(frame, question, action.title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION)
            return;
        busy = true;
        updateButtons();
        setLunarButtonsEnabled(false);
        lunarButton(action).setText("Working...");
        appendLog(action.title);
        setStatus("Updating Lunar Client", TEXT_SECONDARY);

        new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                String agent = Injector.agentArg();
                if (action == LunarAction.INJECT && !agent.endsWith(".jar"))
                    throw new IllegalStateException("Run the built Arsenic jar; the classes folder cannot be a launch argument");
                List<String> pids = LunarInjection.runningLunar();
                if (!pids.isEmpty()) {
                    publish("Closing Lunar Client (" + pids.size() + " processes)");
                    if (!LunarInjection.stop(pids, line -> publish(line)))
                        throw new IllegalStateException("Lunar Client is still running; close it and try again");
                }
                File settings = LunarInjection.settingsFile();
                if (!settings.isFile())
                    throw new IllegalStateException("No Lunar settings at " + settings + "; open Lunar Client once first");
                return action == LunarAction.INJECT ? LunarInjection.setAgent(settings, agent, line -> publish(line))
                        : LunarInjection.removeAgent(settings, line -> publish(line));
            }

            @Override
            protected void process(List<String> lines) {
                for (String line : lines)
                    appendLog(line);
            }

            @Override
            protected void done() {
                busy = false;
                setLunarButtonsEnabled(true);
                updateButtons();
                try {
                    boolean changed = get();
                    if (action == LunarAction.INJECT) {
                        setStatus(changed ? "Lunar will load Arsenic when you launch a game from it" : "Lunar already loads Arsenic", SUCCESS);
                        appendLog("Start Lunar Client and launch a game; Arsenic loads with it.");
                    } else {
                        setStatus(changed ? "Lunar will no longer load Arsenic" : "Arsenic was not in Lunar's settings", changed ? SUCCESS : TEXT_SECONDARY);
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    setStatus(message, DANGER);
                    appendLog("Failed: " + message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                refresh();
            }
        }.execute();
    }

    /** A rounded tonal container. */
    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int radius;

        RoundedPanel(Color fill, int radius) {
            this.fill = fill;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A compact tonal label. */
    private static final class Chip extends JComponent {
        private final String text;
        private final Color fill;
        private final Color foreground;

        Chip(String text, Color fill, Color foreground) {
            this.text = text;
            this.fill = fill;
            this.foreground = foreground;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font(10f, Font.BOLD));
            return new Dimension(fm.stringWidth(text) + 16, 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setFont(font(10f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(foreground);
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2f,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    // ---- components ----

    /** One running game. */
    private final class GameCard extends JComponent {
        final Injector.Game game;
        private boolean hover;

        GameCard(Injector.Game game) {
            this.game = game;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    select(game);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2)
                        injectSelected();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 68);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 68);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            boolean selected = game.id.equals(selectedId);
            boolean usable = game.supported() && !injected.contains(game.id);
            int w = getWidth() - 1, h = getHeight() - 1;

            g2.setColor(selected ? InjectorTheme.SECONDARY_CONTAINER
                    : hover && !busy ? InjectorTheme.mix(InjectorTheme.SURFACE_CONTAINER,
                            InjectorTheme.SURFACE_HIGH, 0.6f) : CARD);
            g2.fillRoundRect(0, 0, w, h, RADIUS * 2, RADIUS * 2);
            if (!selected) {
                g2.setColor(hover ? BORDER_HOVER : BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w, h, RADIUS * 2, RADIUS * 2);
            }

            int badge = 40, bx = 14, by = (getHeight() - badge) / 2;
            Color badgeFill = selected ? PRIMARY_CONTAINER
                    : usable ? PRIMARY_CONTAINER : InjectorTheme.SURFACE_HIGH;
            Color badgeText = usable || selected ? ON_ACCENT_CONTAINER : TEXT_MUTED;
            g2.setColor(badgeFill);
            g2.fillRoundRect(bx, by, badge, badge, 14, 14);
            g2.setColor(badgeText);
            g2.setFont(font(17f, Font.BOLD));
            String initial = game.client.display.substring(0, 1);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, bx + (badge - fm.stringWidth(initial)) / 2f,
                    by + (badge + fm.getAscent() - fm.getDescent()) / 2f);

            int tx = bx + badge + 14;
            g2.setFont(font(14f, Font.BOLD));
            g2.setColor(selected ? InjectorTheme.ON_SECONDARY_CONTAINER
                    : usable ? TEXT : TEXT_SECONDARY);
            g2.drawString(game.title(), tx, getHeight() / 2f - 3);
            g2.setFont(font(11.5f, Font.PLAIN));
            g2.setColor(selected ? InjectorTheme.alpha(InjectorTheme.ON_SECONDARY_CONTAINER, 205)
                    : TEXT_SECONDARY);
            g2.drawString(game.client.display + "  ·  pid " + game.id, tx, getHeight() / 2f + 16);

            String pill;
            Color pillFill;
            Color pillText;
            if (injected.contains(game.id)) {
                pill = "Injected";
                pillFill = SUCCESS_CONTAINER;
                pillText = SUCCESS;
            } else if (!game.supported()) {
                pill = "Unsupported";
                pillFill = SURFACE_HIGHEST;
                pillText = TEXT_SECONDARY;
            } else if (!game.attachable) {
                pill = "Attach off";
                pillFill = WARNING_CONTAINER;
                pillText = WARNING;
            } else {
                pill = null;
                pillFill = null;
                pillText = null;
            }
            if (pill != null) {
                g2.setFont(font(10.5f, Font.BOLD));
                fm = g2.getFontMetrics();
                int pw = fm.stringWidth(pill) + 18, ph = 22;
                int px = getWidth() - pw - 16, py = (getHeight() - ph) / 2;
                g2.setColor(pillFill);
                g2.fillRoundRect(px, py, pw, ph, ph, ph);
                g2.setColor(pillText);
                g2.drawString(pill, px + 9, py + (ph + fm.getAscent() - fm.getDescent()) / 2f);
            } else if (selected) {
                int d = 22, cx = getWidth() - 27, cy = getHeight() / 2;
                g2.setColor(ACCENT);
                g2.fillOval(cx - d / 2, cy - d / 2, d, d);
                g2.setColor(ON_ACCENT);
                g2.setFont(InjectorTheme.icon(14f));
                FontMetrics im = g2.getFontMetrics();
                g2.drawString(InjectorTheme.CHECK, cx - im.stringWidth(InjectorTheme.CHECK) / 2f,
                        cy + (im.getAscent() - im.getDescent()) / 2f);
            }
            g2.dispose();
        }
    }

    /** Shown when no game is running. */
    private static final class EmptyState extends JComponent {
        EmptyState() {
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 150);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 150);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1, h = getHeight() - 1;
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, w, h, RADIUS, RADIUS);
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w, h, RADIUS, RADIUS);

            String title = "No game detected";
            String hint = "Start Minecraft 1.8.9 and it will appear here.";
            g2.setFont(font(14f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(TEXT);
            g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2f, getHeight() / 2f - 4);
            g2.setFont(font(12f, Font.PLAIN));
            fm = g2.getFontMetrics();
            g2.setColor(TEXT_SECONDARY);
            g2.drawString(hint, Math.max(8, (getWidth() - fm.stringWidth(hint)) / 2f), getHeight() / 2f + 16);
            g2.dispose();
        }
    }

    /** A Material You button: filled, tonal or text. */
    private static final class ActionButton extends JComponent {
        static final int FILLED = 0, TONAL = 1, TEXT = 2;
        private String text;
        private final int style;
        private boolean hover, pressed;
        private Runnable action;

        ActionButton(String text, int style) {
            this.text = text;
            this.style = style;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    boolean fire = pressed && hover && isEnabled();
                    pressed = false;
                    repaint();
                    if (fire && action != null)
                        action.run();
                }
            });
        }

        void addActionListener(Runnable action) {
            this.action = action;
        }

        void setText(String text) {
            this.text = text;
            repaint();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(style == TEXT ? 92 : 124, 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            Color fill = null;
            Color fg;
            if (!isEnabled()) {
                fill = style == TEXT ? null : InjectorTheme.alpha(InjectorTheme.ON_SURFACE, 28);
                fg = InjectorTheme.alpha(InjectorTheme.ON_SURFACE, 97);
            } else if (style == FILLED) {
                // MD3 filled: primary fill, onPrimary label, onPrimary state layer at 8% / 12%
                fill = InjectorTheme.mix(InjectorTheme.PRIMARY, InjectorTheme.ON_PRIMARY,
                        pressed ? 0.12f : hover ? 0.08f : 0f);
                fg = ON_ACCENT;
            } else if (style == TONAL) {
                // MD3 tonal: secondaryContainer fill with an onSecondaryContainer state layer
                fill = InjectorTheme.mix(InjectorTheme.SECONDARY_CONTAINER, InjectorTheme.ON_SECONDARY_CONTAINER,
                        pressed ? 0.12f : hover ? 0.08f : 0f);
                fg = InjectorTheme.ON_SECONDARY_CONTAINER;
            } else {
                if (hover || pressed)
                    fill = InjectorTheme.alpha(InjectorTheme.PRIMARY, pressed ? 31 : 20);
                fg = ACCENT;
            }
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w, h, h, h);
            }
            g2.setFont(font(13f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(fg);
            g2.drawString(text, (w - fm.stringWidth(text)) / 2f,
                    (h + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    /** A Material icon button. */
    private static final class IconButton extends JComponent {
        static final int REFRESH = 0, CLOSE = 1, MINIMISE = 2;
        private final int icon;
        private boolean hover;
        private Runnable action;

        IconButton(int icon, String tooltip) {
            this.icon = icon;
            setToolTipText(tooltip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled() && action != null)
                        action.run();
                }
            });
        }

        void addActionListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(36, 36);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            if (hover && isEnabled()) {
                g2.setColor(icon == CLOSE ? InjectorTheme.alpha(InjectorTheme.DANGER, 28)
                        : InjectorTheme.alpha(InjectorTheme.ON_SURFACE_VARIANT, 26));
                g2.fillRoundRect(0, 0, w, h, w, h);
            }
            g2.setColor(!isEnabled() ? TEXT_MUTED : icon == CLOSE && hover ? DANGER : TEXT_SECONDARY);
            int cx = w / 2, cy = h / 2;
            if (icon == REFRESH) {
                g2.setFont(InjectorTheme.icon(18f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(InjectorTheme.REFRESH, cx - fm.stringWidth(InjectorTheme.REFRESH) / 2f,
                        cy + (fm.getAscent() - fm.getDescent()) / 2f);
            } else {
                g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (icon == CLOSE) {
                    g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
                } else {
                    g2.drawLine(cx - 6, cy, cx + 6, cy);
                }
            }
            g2.dispose();
        }
    }

    /** A Material You switch with a label. */
    private static final class Toggle extends JComponent {
        private final String text;
        private boolean selected;
        private Runnable onChange;

        Toggle(String text) {
            this.text = text;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!isEnabled())
                        return;
                    selected = !selected;
                    repaint();
                    if (onChange != null)
                        onChange.run();
                }
            });
        }

        boolean isSelected() {
            return selected;
        }

        void onChange(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font(12f, Font.PLAIN));
            return new Dimension(52 + 12 + fm.stringWidth(text), 32);
        }

        protected void paintSwitch(Graphics2D g2) {
            boolean on = selected && isEnabled();
            int tw = 52, th = 32, ty = (getHeight() - th) / 2;
            // track: primary when selected, surfaceContainerHighest when not
            g2.setColor(!isEnabled() ? SURFACE_HIGHEST : on ? ACCENT : SURFACE_HIGHEST);
            g2.fillRoundRect(0, ty, tw, th, th, th);
            g2.setColor(OUTLINE_VARIANT);
            if (!on)
                g2.drawRoundRect(0, ty, tw - 1, th - 1, th - 1, th - 1);
            // handle: 16dp off / 24dp on, outline-coloured while off, onPrimary while on
            int knob = on ? 24 : 16;
            g2.setColor(on ? ON_ACCENT : OUTLINE);
            int kx = on ? tw - 4 - knob : 6;
            g2.fillOval(kx, ty + (th - knob) / 2, knob, knob);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            paintSwitch(g2);
            g2.setColor(isEnabled() ? TEXT_SECONDARY : TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics(g2.getFont());
            g2.drawString(text, 52 + 12, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }

    /** Underlined-on-hover text that runs an action. */
    private static final class LinkLabel extends JLabel {
        private Runnable action;

        LinkLabel(String text) {
            super(text);
            setFont(font(11.5f, Font.PLAIN));
            setForeground(ACCENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setForeground(ACCENT_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setForeground(ACCENT);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (action != null)
                        action.run();
                }
            });
        }

        void addActionListener(Runnable action) {
            this.action = action;
        }
    }

    /** A thin Material You linear progress indicator. */
    private static final class ProgressBar extends JComponent {
        static final int IDLE = 0, RUNNING = 1, DONE = 2, FAILED = 3;
        private int state = IDLE;
        private float phase;
        private final Timer timer = new Timer(16, e -> {
            phase = (phase + 0.012f) % 1f;
            repaint();
        });

        void setState(int state) {
            this.state = state;
            if (state == RUNNING) {
                phase = 0;
                timer.start();
            } else {
                timer.stop();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = Math.max(4, getHeight());
            int y = Math.max(0, (getHeight() - 4) / 2);
            g2.setColor(SURFACE_HIGHEST);
            g2.fillRoundRect(0, y, w, 4, 4, 4);
            if (state == RUNNING) {
                // MD3 linear indeterminate: a 4dp dot at the track start, then a round-capped
                // active indicator gliding along the track with a 4dp gap between the two
                g2.setColor(ACCENT);
                g2.fillOval(0, y, 4, 4);
                int len = Math.max(24, w / 3);
                float swing = Math.abs(((phase + 0.18f) % 1f) - 0.5f) * 2f; // 0..1..0 glide
                int x = Math.round(8 + swing * (w - 8 - len));
                int x1 = Math.max(8, x), x2 = Math.min(w, x + len);
                if (x2 > x1)
                    g2.fillRoundRect(x1, y, x2 - x1, 4, 4, 4);
            } else if (state == DONE || state == FAILED) {
                g2.setColor(state == DONE ? SUCCESS : DANGER);
                g2.fillRoundRect(0, y, w, 4, 4, 4);
            }
            g2.dispose();
        }
    }

    // ---- helpers ----

    private static JScrollPane scroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = BORDER_HOVER;
                trackColor = new Color(0, 0, 0, 0);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = smooth(g);
                g2.setColor(thumbColor);
                g2.fillRoundRect(r.x + 2, r.y, r.width - 4, r.height, 4, 4);
                g2.dispose();
            }

            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
        return scroll;
    }

    private static String version() {
        String ver = InjectorWindow.class.getPackage().getImplementationVersion();
        return ver != null ? ver : "dev";
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, float size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(size, style));
        label.setForeground(color);
        return label;
    }

    /** Inter, matching the client's Material You type scale. */
    static Font font(float size, int style) {
        return style == Font.BOLD ? InjectorTheme.semibold(size) : InjectorTheme.regular(size);
    }

    static Graphics2D smooth(Graphics g) {
        return InjectorTheme.smooth(g);
    }

    private static BufferedImage image(String path) {
        try (InputStream in = InjectorWindow.class.getResourceAsStream(path)) {
            return in != null ? ImageIO.read(in) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** A plain accent "A" icon for the taskbar, drawn at a few sizes. */
    private static List<Image> icons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 64}) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = smooth(img.getGraphics());
            g2.setColor(ACCENT);
            g2.fillRoundRect(0, 0, size, size, size / 3, size / 3);
            g2.setColor(ON_ACCENT);
            g2.setFont(InjectorTheme.bold(size * 0.72f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("A", (size - fm.stringWidth("A")) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
            icons.add(img);
        }
        return icons;
    }

    /** Opens the window on the event thread. */
    static void open() {
        SwingUtilities.invokeLater(() -> new InjectorWindow().show());
    }
}
