package arsenic.module.impl.visual;

import arsenic.module.ModuleTier;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.java.MathUtils;
import arsenic.utils.keystrokes.SyntheticKeys;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.event.impl.EventTick;
import arsenic.gui.click.UITheme;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.lag.PingTracker;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.TextUtils;
import arsenic.utils.render.RenderContext;
import arsenic.utils.render.ScaledResolution;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.network.chat.numbers.StyledFormat;

import java.util.function.BinaryOperator;

@ModuleInfo(name = "HUD", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.QOL)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.RAINBOW);
    public final EnumProperty<WatermarkMode> watermarkMode = new EnumProperty<>("Watermark: ", WatermarkMode.TEXT);
    public final EnumProperty<ArrayListSort> arraylistSort = new EnumProperty<>("Sort: ", ArrayListSort.LENGTH);
    public final EnumProperty<ArrayListBackground> arraylistBackground = new EnumProperty<>("Background: ", ArrayListBackground.PANEL);
    public final BooleanProperty showInfo = new BooleanProperty("Module Info", true);
    public final BooleanProperty showCoords = new BooleanProperty("Show Coords", false);
    public final BooleanProperty showKeybinds = new BooleanProperty("Show Keybinds", false);
    public final BooleanProperty showPing = new BooleanProperty("Show Ping", false);
    public final BooleanProperty showPotions = new BooleanProperty("Show Potions", false);
    public final BooleanProperty showKeystrokes = new BooleanProperty("Show Keystrokes", false);
    public final BooleanProperty showScoreboard = new BooleanProperty("Show Scoreboard", false);
    public final DoubleProperty backgroundOpacity = new DoubleProperty("Opacity", new DoubleValue(0, 100, 62, 1));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);

    public static final int KEYSTROKES_W = 64, KEYSTROKES_H = 92;

    private final HudElement arrayList = hudElement("Module List", 0, 0, 92, 70, true);
    private final HudElement watermark = hudElement("Watermark", 4, 4, 78, 16);
    private final HudElement coords = hudElement("Coordinates", 4, 60, 96, 16);
    private final HudElement keybinds = hudElement("Keybinds", 4, 80, 104, 46);
    private final HudElement pingElement = hudElement("Ping", 4, 40, 70, 16);
    private final HudElement potions = hudElement("Potions", 76, 140, 100, 16);
    private final HudElement keystrokes = hudElement("Keystrokes", 4, 140, KEYSTROKES_W, KEYSTROKES_H);
    private final HudElement scoreboard = hudElement("Scoreboard", 0, 120, 120, 16, true);

    {
        coords.active = showCoords::getValue;
        keybinds.active = showKeybinds::getValue;
        pingElement.active = showPing::getValue;
        potions.active = showPotions::getValue;
        keystrokes.active = showKeystrokes::getValue;
        scoreboard.active = () -> showScoreboard.getValue() && sidebarObjective() != null;
    }

    private static final float ROW_HEIGHT = 13f;
    private static final float PANEL_PAD_X = 6f;
    private static final float ACCENT_WIDTH = 1.6f;
    private static final float RADIUS = 3.5f;

    private static final float SORT_TOLERANCE = 2f;
    private static final long SORT_SETTLE_MS = 500L;

    private final Map<Module, Entry> entries = new LinkedHashMap<>();
    private List<Entry> visible = new ArrayList<>();
    private final FrameClock clock = new FrameClock();

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (editPosition.getValue() && !(mc.gui.screen() instanceof HudEditorScreen)) {
            mc.gui.setScreen(new HudEditorScreen());
            editPosition.setValue(false);
        }
    };

    private final java.util.ArrayDeque<Long> clicks = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Long> rightClicks = new java.util.ArrayDeque<>();

    @EventLink
    public final Listener<EventMouse.Down> onMouseDown = event -> {
        if (event.button == 0)
            clicks.addLast(System.currentTimeMillis());
        else if (event.button == 1)
            rightClicks.addLast(System.currentTimeMillis());
    };

    private static int cps(java.util.ArrayDeque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!q.isEmpty() && q.peekFirst() < cutoff)
            q.pollFirst();
        return q.size();
    }

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!shouldRender())
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null)
            return;

        int accent = colorMode.getValue().getColor(4, 0);

        renderWatermark(fr, accent);
        if (showCoords.getValue() && mc.player != null)
            renderCoords(fr, accent);
        if (showKeybinds.getValue())
            renderKeybinds(fr);
        if (showPing.getValue())
            renderPing(fr, accent);
        if (showPotions.getValue() && mc.player != null)
            renderPotions(fr, accent);
        if (showKeystrokes.getValue())
            renderKeystrokes(fr, accent);
        if (showScoreboard.getValue())
            renderScoreboard(fr, sr, accent);

        buildEntries(fr);
        drawArrayList(fr, sr, Pass.NORMAL);
    };

    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (!shouldRender())
            return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null || visible.isEmpty())
            return;
        drawArrayList(fr, new ScaledResolution(mc), Pass.BLOOM);
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!shouldRender())
            return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null || visible.isEmpty())
            return;
        drawArrayList(fr, new ScaledResolution(mc), Pass.BLUR);
    };

    private boolean shouldRender() {
        return mc.gui.screen() == null
                || mc.gui.screen() instanceof ChatScreen
                || mc.gui.screen() instanceof HudEditorScreen;
    }

    private enum Pass { NORMAL, BLOOM, BLUR }


    private void buildEntries(FontRendererExtension<?> fr) {
        long now = System.currentTimeMillis();
        float dt = clock.tick();

        Set<Module> enabled = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                .stream().filter(m -> !m.isHidden()).collect(Collectors.toSet());

        for (Module m : enabled)
            entries.computeIfAbsent(m, Entry::new);

        for (Map.Entry<Module, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            entry.alive = enabled.contains(e.getKey());
            entry.info = showInfo.getValue() ? safeInfo(e.getKey()) : null;
            entry.nameWidth = fr.getWidth(entry.name);
            entry.width = entry.nameWidth
                    + (entry.info == null ? 0 : fr.getWidth(" " + entry.info));
            entry.settleSortWidth(now);
        }

        List<Entry> ordered = new ArrayList<>(entries.values());
        ordered.sort(arraylistSort.getValue().getComparator());

        float y = 0;
        visible = new ArrayList<>(ordered.size());
        for (Entry entry : ordered) {
            float target = entry.alive ? 1f : 0f;
            entry.opacity += Math.signum(target - entry.opacity) * dt * 5.5f;
            entry.opacity = MathUtils.clamp01(entry.opacity);

            if (entry.y < 0)
                entry.y = y;
            entry.y = FrameClock.approach(entry.y, y, 14f, dt);

            if (entry.opacity > 0.004f) {
                visible.add(entry);
                y += ROW_HEIGHT * entry.opacity;
            }
        }

        entries.values().removeIf(entry -> !entry.alive && entry.opacity <= 0.004f);

    }

    private String safeInfo(Module m) {
        try {
            String s = m.getHudInfo();
            return s == null || s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private void drawArrayList(FontRendererExtension<?> fr, ScaledResolution sr, Pass pass) {
        if (visible.isEmpty())
            return;

        ArrayListBackground bg = arraylistBackground.getValue();
        float right = sr.getScaledWidth() + arrayList.x;
        float top = arrayList.y;
        int panelAlpha = (int) (255 * (backgroundOpacity.getValue().getInput() / 100.0));

        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            float rowY = top + entry.y;
            float rowH = ROW_HEIGHT * entry.opacity;
            float rowMid = rowY + ROW_HEIGHT / 2f;
            int color = colorMode.getValue().getColor(4, i * 20);

            float slide = (1f - entry.opacity) * 10f;
            float rowRight = right + slide;
            float rowLeft = rowRight - (entry.width + PANEL_PAD_X * 2f);
            float alpha = pass == Pass.BLOOM ? 1f : entry.opacity;

            if (pass == Pass.BLUR) {
                DrawUtils.drawRoundedRect(rowLeft, rowY, rowRight, rowY + rowH, RADIUS, 0xFFFFFFFF);
                continue;
            }

            if (pass == Pass.NORMAL
                    && (bg == ArrayListBackground.PANEL || bg == ArrayListBackground.RECTANGLE)) {
                DrawUtils.drawRoundedRect(rowLeft, rowY, rowRight, rowY + rowH, RADIUS,
                        UITheme.alpha(0x000000, (int) (panelAlpha * entry.opacity)));
            }

            if (bg != ArrayListBackground.NONE)
                DrawUtils.drawRoundedRect(rowRight - ACCENT_WIDTH, rowY, rowRight, rowY + rowH,
                        ACCENT_WIDTH / 2f, UITheme.alpha(color, alpha));

            float cursor = rowRight - PANEL_PAD_X;
            if (entry.info != null) {
                float infoW = fr.getWidth(entry.info);
                fr.drawStringWithShadow(entry.info, cursor - infoW, rowMid,
                        UITheme.alpha(pass == Pass.BLOOM ? color : ThemeManager.getTextMuted(), alpha), fr.CENTREY);
                cursor -= infoW + fr.getWidth(" ");
            }
            fr.drawStringWithShadow(entry.name, cursor - entry.nameWidth,
                    rowMid, UITheme.alpha(color, alpha), fr.CENTREY);
        }
    }


    private void renderWatermark(FontRendererExtension<?> fr, int color) {
        String suffix = null;
        switch (watermarkMode.getValue()) {
            case FPS:
                suffix = mc.getFps() + " fps";
                break;
            case COORDS:
                if (mc.player != null)
                    suffix = String.format("%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
                break;
            case IP:
                suffix = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
                break;
            case TEXT:
            default:
                break;
        }

        String title = "Arsenic";
        float pad = 4f;
        float h = fr.getHeight(title) + pad * 1.5f;
        float w = fr.getWidth(title) + (suffix == null ? 0 : fr.getWidth("  " + suffix)) + pad * 2f;
        float mid = watermark.y + h / 2f;
        watermark.width = (int) Math.ceil(w);
        watermark.height = (int) Math.ceil(h);

        chipBackground(watermark.x, watermark.y, watermark.x + w, watermark.y + h);
        DrawUtils.drawRoundedRect(watermark.x, watermark.y + pad * 0.6f,
                watermark.x + 1.6f, watermark.y + h - pad * 0.6f, 0.8f, color);

        fr.drawStringWithShadow(title, watermark.x + pad, mid, color, fr.CENTREY);
        if (suffix != null)
            fr.drawStringWithShadow(suffix, watermark.x + pad + fr.getWidth(title + "  "),
                    mid, ThemeManager.getTextMuted(), fr.CENTREY);
    }

    private void renderCoords(FontRendererExtension<?> fr, int color) {
        String text = String.format("%.0f  %.0f  %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float pad = 4f;
        float h = fr.getHeight(text) + pad * 1.5f;
        float w = fr.getWidth("XYZ  " + text) + pad * 2f;
        coords.width = (int) Math.ceil(w);
        coords.height = (int) Math.ceil(h);
        chipBackground(coords.x, coords.y, coords.x + w, coords.y + h);
        float coordsMid = coords.y + h / 2f;
        fr.drawStringWithShadow("XYZ", coords.x + pad, coordsMid, ThemeManager.getTextMuted(), fr.CENTREY);
        fr.drawStringWithShadow(text, coords.x + pad + fr.getWidth("XYZ  "), coordsMid, color, fr.CENTREY);
    }

    /** Keybinds keep one colour for the enabled names, the theme's main colour, instead of the cycling rainbow. */
    private void renderKeybinds(FontRendererExtension<?> fr) {
        List<Module> binds = Arsenic.getArsenic().getModuleManager().getModules().stream()
                .filter(m -> m.getKeybind() != 0)
                .sorted(Comparator.comparing(Module::getName))
                .collect(Collectors.toList());
        if (binds.isEmpty())
            return;

        float pad = 4f;
        float textH = fr.getHeight("Ag");
        float rowH = textH + 3f;
        float w = 0;
        for (Module m : binds)
            w = Math.max(w, fr.getWidth(m.getName() + "   " + Keys.getKeyName(m.getKeybind())));
        w += pad * 2f;
        float h = binds.size() * rowH + pad * 1.5f;
        keybinds.width = (int) Math.ceil(w);
        keybinds.height = (int) Math.ceil(h);

        chipBackground(keybinds.x, keybinds.y, keybinds.x + w, keybinds.y + h);

        float y = keybinds.y + pad * 0.75f + rowH / 2f;
        for (Module m : binds) {
            String key = Keys.getKeyName(m.getKeybind());
            fr.drawStringWithShadow(m.getName(), keybinds.x + pad, y, m.isEnabled() ? ThemeManager.getMainColor() : ThemeManager.getTextMuted(), fr.CENTREY);
            fr.drawStringWithShadow(key, keybinds.x + w - pad - fr.getWidth(key), y, ThemeManager.getTextMuted(), fr.CENTREY);
            y += rowH;
        }
    }

    private void renderPing(FontRendererExtension<?> fr, int color) {
        PingTracker.Source source = PingTracker.getSource();
        int ping = PingTracker.getPing();
        String value = source == PingTracker.Source.NONE ? "--" : ping + " ms";
        String tag = source == PingTracker.Source.TAB_LIST ? " ~" : source == PingTracker.Source.LOCKED ? " *" : "";
        int valueColor = source == PingTracker.Source.NONE ? ThemeManager.getTextMuted()
                : ping < 80 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : 0xFFFF5555;

        float pad = 4f;
        float h = fr.getHeight("Ping") + pad * 1.5f;
        float w = fr.getWidth("Ping  " + value + tag) + pad * 2f;
        pingElement.width = (int) Math.ceil(w);
        pingElement.height = (int) Math.ceil(h);
        chipBackground(pingElement.x, pingElement.y, pingElement.x + w, pingElement.y + h);
        float mid = pingElement.y + h / 2f;
        fr.drawStringWithShadow("Ping", pingElement.x + pad, mid, ThemeManager.getTextMuted(), fr.CENTREY);
        float x = pingElement.x + pad + fr.getWidth("Ping  ");
        fr.drawStringWithShadow(value, x, mid, valueColor, fr.CENTREY);
        if (!tag.isEmpty())
            fr.drawStringWithShadow(tag, x + fr.getWidth(value), mid, ThemeManager.getTextMuted(), fr.CENTREY);
    }

    private void renderPotions(FontRendererExtension<?> fr, int color) {
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        if (effects.isEmpty()) {
            potions.width = 100;
            potions.height = 16;
            return;
        }
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());

        float pad = 4f;
        float icon = 12f;
        float rowH = 15f;
        float tickRate = mc.level != null ? mc.level.tickRateManager().tickrate() : 20f;
        List<String[]> rows = new ArrayList<>();
        float w = 0;
        for (MobEffectInstance effect : effects) {
            String name = effect.getEffect().value().getDisplayName().getString();
            int amp = effect.getAmplifier();
            if (amp > 0)
                name += " " + (amp < 9 ? net.minecraft.network.chat.Component.translatable("enchantment.level." + (amp + 1)).getString()
                        : String.valueOf(amp + 1));
            String time = MobEffectUtil.formatDuration(effect, 1.0F, tickRate).getString();
            rows.add(new String[]{name, time});
            w = Math.max(w, fr.getWidth(name + "   " + time));
        }
        w += pad * 2f + icon + 4f;
        float h = rows.size() * rowH + pad * 1.2f;
        potions.width = (int) Math.ceil(w);
        potions.height = (int) Math.ceil(h);
        chipBackground(potions.x, potions.y, potions.x + w, potions.y + h);

        float y = potions.y + pad * 0.6f;
        for (MobEffectInstance effect : effects) {
            RenderContext.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()),
                    (int) (potions.x + pad), (int) (y + (rowH - icon) / 2f), (int) icon, (int) icon);
            y += rowH;
        }

        y = potions.y + pad * 0.6f + rowH / 2f;
        for (int i = 0; i < rows.size(); i++) {
            MobEffectInstance effect = effects.get(i);
            boolean expiring = !effect.isInfiniteDuration() && effect.getDuration() < 200 && !effect.isAmbient();
            int nameColor = expiring && (System.currentTimeMillis() / 250 % 2 == 0) ? 0xFFFF5555 : color;
            fr.drawStringWithShadow(rows.get(i)[0], potions.x + pad + icon + 4f, y, nameColor, fr.CENTREY);
            String time = rows.get(i)[1];
            fr.drawStringWithShadow(time, potions.x + w - pad - fr.getWidth(time), y,
                    ThemeManager.getTextMuted(), fr.CENTREY);
            y += rowH;
        }
    }

    private void renderKeystrokes(FontRendererExtension<?> fr, int color) {
        float k = 20f, gap = 2f;
        float x0 = keystrokes.x, y0 = keystrokes.y;

        keyBox(fr, "W", mc.options.keyUp.isDown(), x0 + k + gap, y0, k, k, color);
        float row2 = y0 + k + gap;
        keyBox(fr, "A", mc.options.keyLeft.isDown(), x0, row2, k, k, color);
        keyBox(fr, "S", mc.options.keyDown.isDown(), x0 + k + gap, row2, k, k, color);
        keyBox(fr, "D", mc.options.keyRight.isDown(), x0 + (k + gap) * 2f, row2, k, k, color);

        float row3 = row2 + k + gap;
        float half = (k * 3f + gap * 2f - gap) / 2f;
        boolean lmb = Keys.isMouseDown(0) || SyntheticKeys.flashing(SyntheticKeys.Key.LMB);
        int lmbCps = cps(clicks) + SyntheticKeys.countLastSecond(SyntheticKeys.Key.LMB);
        keyBox(fr, "LMB " + lmbCps, lmb, x0, row3, half, k, color);
        keyBox(fr, "RMB " + cps(rightClicks), Keys.isMouseDown(1), x0 + half + gap, row3, half, k, color);

        float row4 = row3 + k + gap;
        float spaceH = 12f;
        boolean jump = mc.options.keyJump.isDown() || SyntheticKeys.flashing(SyntheticKeys.Key.JUMP);
        float rowW = k * 3f + gap * 2f;
        chipBackground(x0, row4, x0 + rowW, row4 + spaceH, jump, color);
        float barW = 22f;
        float cx = x0 + rowW / 2f;
        DrawUtils.drawRect(cx - barW / 2f, row4 + spaceH / 2f - 0.5f, cx + barW / 2f, row4 + spaceH / 2f + 0.5f,
                jump ? 0xFFFFFFFF : ThemeManager.getTextMuted());

        float row5 = row4 + spaceH + gap;
        boolean sprint = mc.options.keySprint.isDown() || SyntheticKeys.flashing(SyntheticKeys.Key.SPRINT);
        keyBox(fr, "SPRINT", sprint, x0, row5, rowW, spaceH, color);
    }

    /** The objective shown in the sidebar slot, or null when no scoreboard sidebar is up. */
    private Objective sidebarObjective() {
        if (mc.level == null || mc.player == null)
            return null;
        Scoreboard board = mc.level.getScoreboard();
        PlayerTeam team = board.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null && team.getColor().isPresent()) {
            Objective teamObjective = board.getDisplayObjective(team.getColor().get().displaySlot());
            if (teamObjective != null)
                return teamObjective;
        }
        return board.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    /** True while this HUD draws the scoreboard, so the vanilla sidebar should stay hidden. */
    public boolean replacesVanillaScoreboard() {
        return isEnabled() && showScoreboard.getValue();
    }

    private void renderScoreboard(FontRendererExtension<?> fr, ScaledResolution sr, int color) {
        Objective objective = sidebarObjective();
        if (objective == null)
            return;

        Scoreboard board = objective.getScoreboard();
        List<PlayerScoreEntry> scores = board.listPlayerScores(objective).stream()
                .filter(score -> !score.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15)
                .collect(Collectors.toList());

        float pad = 4f;
        float rowH = fr.getHeight("Ag") + 2f;
        String title = TextUtils.toLegacy(objective.getDisplayName());
        List<String> names = new ArrayList<>();
        List<String> points = new ArrayList<>();
        float w = fr.getWidth(title);
        for (PlayerScoreEntry score : scores) {
            PlayerTeam team = board.getPlayersTeam(score.owner());
            String name = TextUtils.toLegacy(PlayerTeam.formatNameForTeam(team, score.ownerName()));
            String value = TextUtils.toLegacy(score.formatValue(objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)));
            names.add(name);
            points.add(value);
            w = Math.max(w, fr.getWidth(name) + fr.getWidth("  ") + fr.getWidth(value));
        }
        w += pad * 2f;
        float titleH = fr.getHeight(title) + pad * 1.2f;
        float h = titleH + names.size() * rowH + pad * 0.6f;
        scoreboard.width = (int) Math.ceil(w);
        scoreboard.height = (int) Math.ceil(h);

        float x1 = sr.getScaledWidth() + scoreboard.x - scoreboard.width;
        float y1 = scoreboard.y;
        chipBackground(x1, y1, x1 + scoreboard.width, y1 + scoreboard.height);
        fr.drawStringWithShadow(title, x1 + scoreboard.width / 2f, y1 + titleH / 2f, color, fr.CENTREX, fr.CENTREY);

        float y = y1 + titleH + rowH / 2f;
        for (int i = 0; i < names.size(); i++) {
            fr.drawStringWithShadow(names.get(i), x1 + pad, y, ThemeManager.getTextMuted(), fr.CENTREY);
            fr.drawStringWithShadow(points.get(i), x1 + scoreboard.width - pad - fr.getWidth(points.get(i)), y,
                    0xFFFF5555, fr.CENTREY);
            y += rowH;
        }
    }

    private void keyBox(FontRendererExtension<?> fr, String label, boolean down, float x, float y, float w, float h, int color) {
        chipBackground(x, y, x + w, y + h, down, color);
        fr.drawStringWithShadow(label, x + w / 2f, y + h / 2f, down ? 0xFFFFFFFF : ThemeManager.getTextMuted(),
                fr.CENTREX, fr.CENTREY);
    }

    private void chipBackground(float x1, float y1, float x2, float y2, boolean pressed, int color) {
        int a = (int) (255 * (backgroundOpacity.getValue().getInput() / 100.0));
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, RADIUS, UITheme.alpha(0x000000, a));
        if (pressed)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, RADIUS, UITheme.alpha(color, 130));
    }

    private void chipBackground(float x1, float y1, float x2, float y2) {
        int a = (int) (255 * (backgroundOpacity.getValue().getInput() / 100.0));
        DrawUtils.drawShadow(x1, y1, x2, y2, RADIUS, 3f, (int) (70 * (a / 255f)), 3);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, RADIUS, UITheme.alpha(0x000000, a));
    }


    /** Configs written before HUD elements moved into their modules kept every position in HUD's "positions". */
    @Override
    public void loadFromJson(JsonObject obj) {
        JsonObject legacy = obj.has("positions") && obj.get("positions").isJsonObject() ? obj.getAsJsonObject("positions") : null;
        if (legacy != null && !obj.has("hud")) {
            legacyPosition(legacy, "watermark", watermark);
            legacyPosition(legacy, "arrayList", arrayList);
            legacyPosition(legacy, "coords", coords);
            legacyPosition(legacy, "keybinds", keybinds);
            legacyPosition(legacy, "ping", pingElement);
            legacyPosition(legacy, "potions", potions);
            legacyPosition(legacy, "keystrokes", keystrokes);
        }
        super.loadFromJson(obj);
    }

    private static void legacyPosition(JsonObject legacy, String key, HudElement element) {
        if (legacy.has(key + "X")) element.x = legacy.get(key + "X").getAsInt();
        if (legacy.has(key + "Y")) element.y = legacy.get(key + "Y").getAsInt();
    }

    private static class Entry {
        final String name;
        String info;
        float nameWidth;
        float width;
        float sortWidth = -1f;
        private float candidateWidth;
        private long candidateSince;
        float opacity;
        float y = -1f;
        boolean alive;

        Entry(Module module) {
            this.name = module.getName();
        }

        void settleSortWidth(long now) {
            if (sortWidth < 0f) {
                sortWidth = width;
                candidateWidth = width;
                candidateSince = now;
                return;
            }

            if (Math.abs(width - sortWidth) <= SORT_TOLERANCE) {
                candidateWidth = sortWidth;
                candidateSince = now;
                return;
            }

            if (Math.abs(width - candidateWidth) > SORT_TOLERANCE) {
                candidateWidth = width;
                candidateSince = now;
                return;
            }

            if (now - candidateSince >= SORT_SETTLE_MS)
                sortWidth = width;
        }
    }

    public enum hMode {
        THEME(ColorUtils::getThemeRainbowColor),
        RAINBOW(ColorUtils::getRainbow);

        private final BinaryOperator<Integer> f;

        hMode(BinaryOperator<Integer> f) {
            this.f = f;
        }

        public int getColor(int speed, int delay) {
            return f.apply(speed, delay);
        }
    }

    public enum WatermarkMode {
        TEXT, FPS, COORDS, IP
    }

    public enum ArrayListSort {
        LENGTH(Comparator.comparingDouble((Entry e) -> -e.sortWidth)),
        ABC(Comparator.comparing((Entry e) -> e.name.toLowerCase()));

        private final Comparator<Entry> comparator;

        ArrayListSort(Comparator<Entry> comparator) {
            this.comparator = comparator;
        }

        Comparator<Entry> getComparator() {
            return comparator;
        }
    }

    public enum ArrayListBackground {
        PANEL, RECTANGLE, BAR, NONE
    }
}
