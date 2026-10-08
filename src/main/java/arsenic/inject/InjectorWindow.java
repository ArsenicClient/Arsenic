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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The injector's window: a list of running games as cards, an Inject button with progress, and a log of what the
 * agent reported. Everything is painted by hand so it looks the same on every system look and feel, in the colours of
 * the client's default (Classic) theme.
 */
final class InjectorWindow {

    // ---- palette ----
    private static final Color BACKGROUND = new Color(0x0D0D0F);
    private static final Color SURFACE = new Color(0x111114);
    private static final Color SURFACE_HOVER = new Color(0x16161B);
    private static final Color BORDER = new Color(0x1E1E24);
    private static final Color BORDER_HOVER = new Color(0x2A2A35);
    private static final Color TEXT = new Color(0xF2F2F5);
    private static final Color TEXT_SECONDARY = new Color(0x8A8A96);
    private static final Color TEXT_MUTED = new Color(0x55555F);
    private static final Color ACCENT = new Color(0xDD425E);
    private static final Color ACCENT_HOVER = new Color(0xE85A74);
    private static final Color SUCCESS = new Color(0x3FB950);
    private static final Color DANGER = new Color(0xE24B4A);
    private static final Color WARNING = new Color(0xE0A030);

    private static final int RADIUS = 12;
    private static final int REFRESH_MS = 2500;

    private final JFrame frame = new JFrame("Arsenic Injector");
    private final JPanel cards = new JPanel();
    private final JScrollPane cardScroll;
    private final ActionButton injectButton = new ActionButton("Inject");
    private final IconButton refreshButton = new IconButton(IconButton.REFRESH, "Refresh");
    private final Toggle showAll = new Toggle("Show every Java process");
    private final ProgressBar progress = new ProgressBar();
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea log = new JTextArea();
    private final JScrollPane logScroll;
    private final LinkLabel logToggle = new LinkLabel("Show details");
    private final Timer autoRefresh;

    private final List<GameCard> gameCards = new ArrayList<>();
    private final java.util.Set<String> injected = new java.util.HashSet<>();
    private String selectedId;
    private boolean busy;
    private boolean refreshing;

    InjectorWindow() {
        cardScroll = scroll(cards);
        logScroll = scroll(log);
        autoRefresh = new Timer(REFRESH_MS, e -> {
            if (!busy && frame.isActive())
                refresh();
        });
    }

    void show() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setColor(BACKGROUND);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // a faint accent glow behind the title bar
                g2.setPaint(new RadialGradientPaint(new Point(getWidth() / 5, 0), getWidth() * 0.7f,
                        new float[]{0f, 1f}, new Color[]{new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 34), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        root.setBorder(BorderFactory.createLineBorder(BORDER));
        root.add(titleBar(), BorderLayout.NORTH);

        JPanel body = transparent(new BorderLayout(0, 12));
        body.setBorder(new EmptyBorder(6, 20, 18, 20));
        body.add(listHeader(), BorderLayout.NORTH);

        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setOpaque(false);
        body.add(cardScroll, BorderLayout.CENTER);
        body.add(footer(), BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        frame.setUndecorated(true);
        frame.setContentPane(root);
        frame.setIconImages(icons());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(560, 500);
        frame.setMinimumSize(new Dimension(460, 420));
        frame.setLocationRelativeTo(null);
        frame.getRootPane().setDefaultButton(null);
        try {
            frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 14, 14));
        } catch (Throwable ignored) {
            // shaped windows are not supported everywhere; square corners are fine
        }
        frame.setVisible(true);
        refresh();
        autoRefresh.start();
    }

    // ---- layout ----

    private JComponent titleBar() {
        JPanel bar = transparent(new BorderLayout());
        bar.setBorder(new EmptyBorder(14, 20, 10, 12));

        JPanel brand = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        BufferedImage logo = image("/assets/arsenic/logos/modern.png");
        if (logo != null) {
            int h = 26, w = logo.getWidth() * h / logo.getHeight();
            brand.add(new JLabel(new ImageIcon(logo.getScaledInstance(w, h, Image.SCALE_SMOOTH))));
        } else {
            brand.add(label("Arsenic", 20f, Font.BOLD, TEXT));
        }
        JLabel product = label("  Injector", 13f, Font.PLAIN, TEXT_SECONDARY);
        product.setBorder(new EmptyBorder(6, 4, 0, 0));
        brand.add(product);
        bar.add(brand, BorderLayout.WEST);

        JPanel controls = transparent(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        IconButton minimise = new IconButton(IconButton.MINIMISE, "Minimise");
        minimise.addActionListener(() -> frame.setState(Frame.ICONIFIED));
        IconButton close = new IconButton(IconButton.CLOSE, "Close");
        close.addActionListener(() -> System.exit(0));
        controls.add(minimise);
        controls.add(close);
        bar.add(controls, BorderLayout.EAST);

        // dragging the title bar moves the undecorated window
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
        return bar;
    }

    private JComponent listHeader() {
        JPanel header = transparent(new BorderLayout());
        JPanel text = transparent(new GridLayout(2, 1, 0, 2));
        text.add(label("Running games", 15f, Font.BOLD, TEXT));
        text.add(label("Forge, Vanilla and Lunar Client 1.8.9", 12f, Font.PLAIN, TEXT_SECONDARY));
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
        JPanel footer = transparent(new BorderLayout(0, 10));

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
        row.add(injectButton, BorderLayout.EAST);
        footer.add(row, BorderLayout.CENTER);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        log.setForeground(TEXT_SECONDARY);
        log.setBackground(SURFACE);
        log.setCaretColor(TEXT_SECONDARY);
        log.setBorder(new EmptyBorder(8, 10, 8, 10));
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
        frame.setSize(frame.getWidth(), frame.getHeight() + (show ? 130 : -130));
        try {
            frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 14, 14));
        } catch (Throwable ignored) {
        }
        frame.revalidate();
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
        if (!busy)
            setStatus(games.isEmpty() ? "Start Minecraft and it will show up here." : selectedHint(), TEXT_SECONDARY);
        updateButtons();
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
    }

    private void updateButtons() {
        Injector.Game game = selected();
        injectButton.setEnabled(!busy && game != null && game.supported() && !injected.contains(game.id));
        refreshButton.setEnabled(!busy);
        showAll.setEnabled(!busy);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text.isEmpty() ? " " : text);
        statusLabel.setForeground(color);
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
                if (!last.startsWith("hooked ") && !last.startsWith("missed "))
                    setStatus(last, TEXT);
            }

            @Override
            protected void done() {
                busy = false;
                injectButton.setText("Inject");
                String result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = "ERROR " + e;
                }
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
        }.execute();
    }

    /** How to load Arsenic at launch into a game that turns attach off, written out for the game's own settings. */
    private void appendLaunchSteps(Injector.Game game) {
        appendLog(game.client.display + " turns off the Java attach mechanism, so the injector cannot reach it.");
        appendLog("Load Arsenic at launch instead:");
        if (game.client == Injector.Client.LUNAR) {
            appendLog("  1. In Lunar, open Settings (bottom left) -> Game.");
            appendLog("  2. Turn on advanced mode (the shield icon left of \"Game Settings\").");
            appendLog("  3. Under JVM arguments, add this line (keep any others already there):");
        } else {
            appendLog("  Add this line to the game's Java arguments:");
        }
        appendLog("       " + Injector.agentArg());
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
            return new Dimension(100, 64);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 64);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            boolean selected = game.id.equals(selectedId);
            boolean usable = game.supported() && !injected.contains(game.id);
            int w = getWidth() - 1, h = getHeight() - 1;

            g2.setColor(hover && !busy ? SURFACE_HOVER : SURFACE);
            g2.fillRoundRect(0, 0, w, h, RADIUS, RADIUS);
            g2.setColor(selected ? ACCENT : hover ? BORDER_HOVER : BORDER);
            g2.setStroke(new BasicStroke(selected ? 1.5f : 1f));
            g2.drawRoundRect(0, 0, w, h, RADIUS, RADIUS);

            // client badge: a rounded square with the client's initial
            int badge = 38, bx = 14, by = (getHeight() - badge) / 2;
            Color clientColor = game.client.color;
            g2.setColor(usable ? clientColor : desaturate(clientColor));
            g2.fillRoundRect(bx, by, badge, badge, 10, 10);
            g2.setColor(Color.WHITE);
            g2.setFont(font(17f, Font.BOLD));
            String initial = game.client.display.substring(0, 1);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, bx + (badge - fm.stringWidth(initial)) / 2, by + (badge + fm.getAscent() - fm.getDescent()) / 2);

            int tx = bx + badge + 14;
            g2.setFont(font(14f, Font.BOLD));
            g2.setColor(usable ? TEXT : TEXT_SECONDARY);
            g2.drawString(game.title(), tx, getHeight() / 2 - 3);
            g2.setFont(font(12f, Font.PLAIN));
            g2.setColor(TEXT_SECONDARY);
            g2.drawString(game.client.display + "  \u00B7  pid " + game.id, tx, getHeight() / 2 + 15);

            // right side: what state the game is in
            String pill;
            Color pillColor;
            if (injected.contains(game.id)) {
                pill = "Injected";
                pillColor = SUCCESS;
            } else if (!game.supported()) {
                pill = "Unsupported";
                pillColor = TEXT_SECONDARY;
            } else if (!game.attachable) {
                // visible but the attach API cannot reach it (Lunar turns attach off) - still selectable, inject will try
                pill = "Attach off";
                pillColor = WARNING;
            } else {
                pill = null;
                pillColor = null;
            }
            if (pill != null) {
                g2.setFont(font(11f, Font.BOLD));
                fm = g2.getFontMetrics();
                int pw = fm.stringWidth(pill) + 18, ph = 22;
                int px = getWidth() - pw - 16, py = (getHeight() - ph) / 2;
                g2.setColor(new Color(pillColor.getRed(), pillColor.getGreen(), pillColor.getBlue(), 40));
                g2.fillRoundRect(px, py, pw, ph, ph, ph);
                g2.setColor(pillColor);
                g2.drawString(pill, px + 9, py + (ph + fm.getAscent() - fm.getDescent()) / 2);
            } else if (selected) {
                int r = 9, cx = getWidth() - 26, cy = getHeight() / 2;
                g2.setColor(ACCENT);
                g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{cx - 4, cx - 1, cx + 4}, new int[]{cy, cy + 3, cy - 3}, 3);
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
            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
            g2.drawRoundRect(0, 0, w, h, RADIUS, RADIUS);

            String title = "No Minecraft running";
            String hint = "Start a 1.8.9 game (Forge, Vanilla or Lunar). It shows up here on its own.";
            g2.setFont(font(14f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(TEXT);
            g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, getHeight() / 2 - 4);
            g2.setFont(font(12f, Font.PLAIN));
            fm = g2.getFontMetrics();
            g2.setColor(TEXT_SECONDARY);
            g2.drawString(hint, Math.max(8, (getWidth() - fm.stringWidth(hint)) / 2), getHeight() / 2 + 16);
            g2.dispose();
        }
    }

    /** The big accent button. */
    private static final class ActionButton extends JComponent {
        private String text;
        private boolean hover, pressed;
        private Runnable action;

        ActionButton(String text) {
            this.text = text;
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
            return new Dimension(132, 42);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            Color fill = !isEnabled() ? new Color(0x2A2A31) : pressed ? ACCENT.darker() : hover ? ACCENT_HOVER : ACCENT;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setFont(font(14f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(isEnabled() ? Color.WHITE : TEXT_MUTED);
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** A small square button with a drawn icon. */
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
            return new Dimension(30, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (hover && isEnabled()) {
                g2.setColor(icon == CLOSE ? new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), 60) : SURFACE_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            g2.setColor(!isEnabled() ? TEXT_MUTED : hover ? TEXT : TEXT_SECONDARY);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2, cy = getHeight() / 2;
            switch (icon) {
                case CLOSE:
                    g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
                    break;
                case MINIMISE:
                    g2.drawLine(cx - 5, cy, cx + 5, cy);
                    break;
                default:
                    g2.drawArc(cx - 6, cy - 6, 12, 12, 60, 290);
                    // arrow head at the end of the arc
                    g2.fillPolygon(new int[]{cx + 7, cx + 1, cx + 7}, new int[]{cy - 9, cy - 5, cy - 2}, 3);
            }
            g2.dispose();
        }
    }

    /** A small switch with a label. */
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
            return new Dimension(30 + 8 + fm.stringWidth(text), 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int tw = 28, th = 16, ty = (getHeight() - th) / 2;
            g2.setColor(selected ? ACCENT : BORDER_HOVER);
            g2.fillRoundRect(0, ty, tw, th, th, th);
            g2.setColor(Color.WHITE);
            int knob = th - 4;
            g2.fillOval(selected ? tw - knob - 2 : 2, ty + 2, knob, knob);
            g2.setFont(font(12f, Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(isEnabled() ? TEXT_SECONDARY : TEXT_MUTED);
            g2.drawString(text, tw + 8, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Underlined-on-hover text that runs an action. */
    private static final class LinkLabel extends JLabel {
        private Runnable action;

        LinkLabel(String text) {
            super(text);
            setFont(font(11.5f, Font.PLAIN));
            setForeground(TEXT_MUTED);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setForeground(TEXT_SECONDARY);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setForeground(TEXT_MUTED);
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

    /** A thin bar: an accent stripe that sweeps while injecting, then green or red. */
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
            int w = getWidth(), h = getHeight();
            g2.setColor(SURFACE);
            g2.fillRoundRect(0, 0, w, h, h, h);
            if (state == RUNNING) {
                int len = w / 3;
                int x = (int) (phase * (w + len)) - len;
                g2.setPaint(new GradientPaint(x, 0, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 0), x + len / 2f, 0, ACCENT, true));
                g2.fillRoundRect(Math.max(0, x), 0, Math.min(len, w - Math.max(0, x)) + Math.min(0, x), h, h, h);
            } else if (state == DONE || state == FAILED) {
                g2.setColor(state == DONE ? SUCCESS : DANGER);
                g2.fillRoundRect(0, 0, w, h, h, h);
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

    private static Font baseFont;

    /** Segoe UI / SF / the system sans font when present, Java's sans otherwise. */
    static Font font(float size, int style) {
        if (baseFont == null) {
            java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            String family = Font.SANS_SERIF;
            for (String candidate : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Ubuntu", "Cantarell", "DejaVu Sans"}) {
                if (available.contains(candidate)) {
                    family = candidate;
                    break;
                }
            }
            baseFont = new Font(family, Font.PLAIN, 12);
        }
        return baseFont.deriveFont(style, size);
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    private static Color desaturate(Color c) {
        int grey = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
        return new Color((grey + 0x2A) / 2, (grey + 0x2A) / 2, (grey + 0x31) / 2);
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
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size * 3 / 4));
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
