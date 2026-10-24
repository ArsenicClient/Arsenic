package arsenic.module.impl.visual;

import arsenic.module.ModuleTier;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.java.MathUtils;
import arsenic.utils.keystrokes.SyntheticKeys;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.function.BinaryOperator;

@ModuleInfo(name = "HUD", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.QOL)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.THEME);
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

    public static final int KEYSTROKES_W = 82, KEYSTROKES_H = 129;

    private final HudElement arrayList = hudElement("Module List", 0, 0, 92, 70, true);
    private final HudElement watermark = hudElement("Watermark", 4, 4, 108, 26);
    private final HudElement coords = hudElement("Coordinates", 4, 68, 160, 34);
    private final HudElement keybinds = hudElement("Keybinds", 4, 108, 140, 54);
    private final HudElement pingElement = hudElement("Ping", 4, 36, 112, 30);
    private final HudElement potions = hudElement("Potions", 4, 170, 146, 40);
    private final HudElement keystrokes = hudElement("Keystrokes", 4, 150, KEYSTROKES_W, KEYSTROKES_H);
    private final HudElement scoreboard = hudElement("Scoreboard", 0, 120, 120, 40, true);

    {
        coords.active = showCoords::getValue;
        keybinds.active = showKeybinds::getValue;
        pingElement.active = showPing::getValue;
        potions.active = showPotions::getValue;
        keystrokes.active = showKeystrokes::getValue;
        scoreboard.active = () -> showScoreboard.getValue() && sidebarObjective() != null;
    }

    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static final float ROW_HEIGHT = 16f;
    private static final float PANEL_PAD_X = 8f;
    private static final float INFO_GAP = 6f;
    private static final float ACCENT_WIDTH = 3f;
    private static final float LIST_RADIUS = 11f;

    private static final float SORT_TOLERANCE = 2f;
    private static final long SORT_SETTLE_MS = 500L;

    private final Map<Module, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Float> keyPress = new HashMap<>();
    private List<Entry> visible = new ArrayList<>();
    private final FrameClock clock = new FrameClock();
    private final FrameClock keyClock = new FrameClock();

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (editPosition.getValue() && !(mc.currentScreen instanceof HudEditorScreen)) {
            mc.displayGuiScreen(new HudEditorScreen());
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
        int accent = colorMode.getValue() == hMode.THEME ? M3.primary() : colorMode.getValue().getColor(4, 0);

        renderWatermark(accent);
        if (showCoords.getValue() && mc.thePlayer != null)
            renderCoords();
        if (showKeybinds.getValue())
            renderKeybinds();
        if (showPing.getValue())
            renderPing();
        if (showPotions.getValue() && mc.thePlayer != null)
            renderPotions(accent);
        if (showKeystrokes.getValue())
            renderKeystrokes(accent);
        if (showScoreboard.getValue())
            renderScoreboard(sr, accent);

        buildEntries();
        drawArrayList(sr, Pass.NORMAL);
        HudStyle.reset();
    };

    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (!shouldRender() || visible.isEmpty())
            return;
        drawArrayList(new ScaledResolution(mc), Pass.BLOOM);
        HudStyle.reset();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!shouldRender() || visible.isEmpty())
            return;
        drawArrayList(new ScaledResolution(mc), Pass.BLUR);
        HudStyle.reset();
    };

    private boolean shouldRender() {
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
    }

    private enum Pass { NORMAL, BLOOM, BLUR }

    private int panelAlpha() {
        return (int) (255 * (backgroundOpacity.getValue().getInput() / 100.0));
    }

    private int panelFill(float multiplier) {
        return M3.alpha(M3.surfaceContainerHigh(), (int) (panelAlpha() * multiplier));
    }

    private void buildEntries() {
        long now = System.currentTimeMillis();
        float dt = clock.tick();

        Set<Module> enabled = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                .stream().filter(m -> !m.isHidden()).collect(Collectors.toSet());

        for (Module m : enabled)
            entries.computeIfAbsent(m, Entry::new);

        FontRendererExtension<?> nameFont = HudStyle.title();
        FontRendererExtension<?> infoFont = HudStyle.label();
        for (Map.Entry<Module, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            entry.alive = enabled.contains(e.getKey());
            entry.info = showInfo.getValue() ? safeInfo(e.getKey()) : null;
            entry.nameWidth = nameFont.getWidth(entry.name);
            entry.infoWidth = entry.info == null ? 0 : infoFont.getWidth(entry.info);
            entry.width = entry.nameWidth
                    + (entry.info == null ? 0 : INFO_GAP + entry.infoWidth);
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

    private void drawArrayList(ScaledResolution sr, Pass pass) {
        if (visible.isEmpty())
            return;

        ArrayListBackground bg = arraylistBackground.getValue();
        float right = sr.getScaledWidth() + arrayList.x;
        float top = arrayList.y;
        int panelAlpha = (int) (panelAlpha() * 0.90f);

        float listWidth = 0f;
        float listHeight = 0f;
        for (Entry entry : visible) {
            listWidth = Math.max(listWidth, entry.width + PANEL_PAD_X * 2f);
            listHeight = Math.max(listHeight, entry.y + ROW_HEIGHT * entry.opacity);
        }
        arrayList.width = (int) Math.ceil(listWidth);
        arrayList.height = (int) Math.ceil(listHeight);

        HudStyle.pushScale(right, top, arrayList.scale);

        if ((pass == Pass.BLUR || pass == Pass.NORMAL)
                && (bg == ArrayListBackground.PANEL || bg == ArrayListBackground.RECTANGLE)) {
            int colour = pass == Pass.BLUR ? 0xFFFFFFFF
                    : M3.alpha(M3.surfaceContainerHigh(), panelAlpha);
            boolean[] round = {false, false, false, false};
            for (int i = 0; i < visible.size(); i++) {
                Entry entry = visible.get(i);
                float rowRight = right + (1f - entry.opacity) * 10f;
                float rowW = entry.width + PANEL_PAD_X * 2f;
                float rowY = top + entry.y;
                float rowH = ROW_HEIGHT * entry.opacity;
                if (rowH <= 0.004f)
                    continue;
                if (bg == ArrayListBackground.PANEL) {
                    round[0] = i == 0;
                    round[1] = i == visible.size() - 1;
                }
                DrawUtils.drawRoundedRect(rowRight - rowW, rowY, rowRight, rowY + rowH,
                        bg == ArrayListBackground.PANEL ? LIST_RADIUS : 0f, colour, round);
            }
            if (pass == Pass.BLUR) {
                HudStyle.popScale();
                return;
            }
        }

        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            float rowY = top + entry.y;
            float rowMid = rowY + ROW_HEIGHT / 2f;
            float alpha = entry.opacity;
            int color = colorMode.getValue() == hMode.THEME
                    ? M3.primary() : colorMode.getValue().getColor(4, i * 20);

            float slide = (1f - entry.opacity) * 10f;
            float rowRight = right + slide;
            float rowW = entry.width + PANEL_PAD_X * 2f;

            if (pass == Pass.NORMAL && bg == ArrayListBackground.BAR) {
                float accentX = rowRight - ACCENT_WIDTH - 1f;
                DrawUtils.drawRoundedRect(accentX, rowY + 3f, rowRight - 1f, rowY + ROW_HEIGHT - 3f,
                        ACCENT_WIDTH / 2f, M3.alpha(color, (int) (alpha * 225)));
            }

            float pad = PANEL_PAD_X;
            float textRight = rowRight - pad;
            FontRendererExtension<?> nameFont = HudStyle.title();
            FontRendererExtension<?> infoFont = HudStyle.label();
            boolean shadow = bg == ArrayListBackground.NONE;
            int nameColor = M3.alpha(M3.onSurface(), (int) (alpha * 255));
            int infoColor = M3.alpha(M3.onSurfaceVariant(), (int) (alpha * 235));

            float nameX = textRight - entry.nameWidth;
            if (entry.info != null) {
                float infoX = textRight - entry.infoWidth;
                nameX = infoX - INFO_GAP - entry.nameWidth;
                if (shadow)
                    infoFont.drawStringWithShadow(entry.info, infoX, rowMid, infoColor, infoFont.CENTREY);
                else
                    infoFont.drawString(entry.info, infoX, rowMid, infoColor, infoFont.CENTREY);
            }
            if (shadow)
                nameFont.drawStringWithShadow(entry.name, nameX, rowMid, nameColor, nameFont.CENTREY);
            else
                nameFont.drawString(entry.name, nameX, rowMid, nameColor, nameFont.CENTREY);
        }

        HudStyle.popScale();
    }

    private void renderWatermark(int color) {
        FontRendererExtension<?> title = HudStyle.title();
        FontRendererExtension<?> label = HudStyle.label();

        WatermarkMode mode = watermarkMode.getValue();
        String suffix = suffixFor(mode);
        String glyph = glyphFor(mode);
        boolean strong = mode == WatermarkMode.FPS && mc.thePlayer != null;

        float h = 26f, pad = 5f, icon = 18f;
        float contentX = watermark.x + pad + icon + 7f;
        // the suffix pill must be sized for its full contents: text plus its leading glyph
        float suffixW = suffix == null ? 0
                : glyph == null ? HudStyle.pillWidth(label, suffix, 15f)
                : HudStyle.iconPillWidth(label, suffix, 15f);
        float w = pad + icon + 7f + title.getWidth("Arsenic")
                + (suffix == null ? 0 : 9f + suffixW) + pad;
        watermark.width = (int) Math.ceil(w);
        watermark.height = (int) Math.ceil(h);

        float mid = watermark.y + h / 2f;
        HudStyle.pushScale(watermark.x, watermark.y, watermark.scale);
        HudStyle.card(watermark.x, watermark.y, watermark.x + w, watermark.y + h, h / 2f, panelFill(0.96f));
        HudStyle.iconBadge(M3Icons.BRAND, watermark.x + pad + icon / 2f, mid, icon / 2f,
                M3.alpha(M3.primary(), 235), M3.onPrimary());
        title.drawString("Arsenic", contentX, mid, M3.onSurface(), title.CENTREY);

        if (suffix != null) {
            float pillX = watermark.x + w - pad - suffixW;
            int fg = M3.onSecondaryContainer();
            int fill = M3.alpha(M3.secondaryContainer(), 220);
            switch (mode) {
                case FPS:
                    int quality = strong ? M3.primary() : M3.outline();
                    fg = M3.onPrimaryContainer();
                    fill = M3.alpha(M3.primaryContainer(), strong ? 240 : 150);
                    if (strong)
                        fg = M3.primary();
                    break;
                case COORDS:
                    fill = M3.alpha(M3.surfaceContainerHighest(), 235);
                    fg = M3.onSurfaceVariant();
                    break;
                case IP:
                    fill = M3.alpha(M3.tertiaryContainer(), 230);
                    fg = M3.onTertiaryContainer();
                    break;
                default:
                    fill = M3.alpha(M3.surfaceContainerHighest(), 235);
                    fg = M3.onSurfaceVariant();
                    break;
            }
            if (glyph != null)
                HudStyle.iconPill(label, glyph, suffix, pillX, mid, 15f, fill, fg);
            else
                HudStyle.pill(label, suffix, pillX, mid, 15f, fill, fg);
        }
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private String suffixFor(WatermarkMode mode) {
        switch (mode) {
            case FPS:
                return Minecraft.getDebugFPS() + " FPS";
            case COORDS:
                if (mc.thePlayer != null)
                    return String.format("%.0f, %.0f, %.0f",
                            mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                return null;
            case IP:
                return mc.getCurrentServerData() != null
                        ? mc.getCurrentServerData().serverIP : "Offline";
            default:
                return null;
        }
    }

    private String glyphFor(WatermarkMode mode) {
        switch (mode) {
            case FPS: return M3Icons.TIMER;
            case COORDS: return M3Icons.LOCATION;
            case IP: return M3Icons.ONLINE;
            default: return null;
        }
    }

    private void renderCoords() {
        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> valueFont = HudStyle.title();
        String[] values = {
                String.format("%.0f", mc.thePlayer.posX),
                String.format("%.0f", mc.thePlayer.posY),
                String.format("%.0f", mc.thePlayer.posZ)
        };
        String[] axes = {"X", "Y", "Z"};

        float h = 34f;
        float pad = 6f;
        float icon = 20f;
        float textX = coords.x + pad + icon + 8f;
        float cursor = textX;
        for (int i = 0; i < axes.length; i++) {
            float colW = Math.max(label.getWidth(axes[i]), valueFont.getWidth(values[i]));
            cursor += colW + (i == axes.length - 1 ? 0 : 10f);
        }
        float w = cursor - coords.x + pad;
        coords.width = (int) Math.ceil(w);
        coords.height = (int) Math.ceil(h);

        float mid = coords.y + h / 2f;
        HudStyle.pushScale(coords.x, coords.y, coords.scale);
        HudStyle.card(coords.x, coords.y, coords.x + w, coords.y + h, 13f, panelFill(0.96f));
        HudStyle.iconBadge(M3Icons.LOCATION, coords.x + pad + icon / 2f, mid, icon / 2f,
                M3.alpha(M3.primaryContainer(), 230), M3.onPrimaryContainer());

        cursor = textX;
        for (int i = 0; i < axes.length; i++) {
            float colW = Math.max(label.getWidth(axes[i]), valueFont.getWidth(values[i]));
            label.drawString(axes[i], cursor, coords.y + 10.5f,
                    M3.onSurfaceVariant(), label.CENTREY);
            valueFont.drawString(values[i], cursor, coords.y + 23f,
                    i == 1 ? M3.primary() : M3.onSurface(), valueFont.CENTREY);
            cursor += colW + 10f;
        }
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private void renderPing() {
        PingTracker.Source source = PingTracker.getSource();
        int ping = PingTracker.getPing();
        String value = source == PingTracker.Source.NONE ? "--" : ping + " ms";
        String sourceText = source == PingTracker.Source.TAB_LIST ? "TAB"
                : source == PingTracker.Source.CONFIRMATION ? "ACK"
                : source == PingTracker.Source.LOCKED ? "LOCK" : "";
        int quality = source == PingTracker.Source.NONE ? M3.outline()
                : ping < 80 ? M3.primary() : ping < 150 ? M3.tertiary() : M3.error();

        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> valueFont = HudStyle.title();
        float h = 30f;
        float pad = 5f;
        float icon = 18f;
        float textX = pingElement.x + pad + icon + 7f;
        float sourceW = sourceText.isEmpty() ? 0 : label.getWidth(sourceText) + 12f;
        float w = pad + icon + 7f + Math.max(label.getWidth("PING"),
                valueFont.getWidth(value) + (sourceText.isEmpty() ? 0 : sourceW + 6f)) + pad;
        pingElement.width = (int) Math.ceil(w);
        pingElement.height = (int) Math.ceil(h);

        float mid = pingElement.y + h / 2f;
        HudStyle.pushScale(pingElement.x, pingElement.y, pingElement.scale);
        HudStyle.card(pingElement.x, pingElement.y, pingElement.x + w, pingElement.y + h, 13f, panelFill(0.96f));
        HudStyle.iconBadge(M3Icons.WIFI, pingElement.x + pad + icon / 2f, mid, icon / 2f,
                M3.alpha(quality, 42), quality);

        label.drawString("PING", textX, pingElement.y + 10.5f, M3.onSurfaceVariant(), label.CENTREY);
        valueFont.drawString(value, textX, pingElement.y + 21f, quality, valueFont.CENTREY);
        if (!sourceText.isEmpty()) {
            float pillX = textX + valueFont.getWidth(value) + 6f;
            HudStyle.pill(label, sourceText, pillX, pingElement.y + 21f, 13f,
                    M3.alpha(M3.surfaceContainerHighest(), 210), M3.onSurfaceVariant());
        }
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private void renderKeybinds() {
        List<Module> binds = Arsenic.getArsenic().getModuleManager().getModules().stream()
                .filter(m -> m.getKeybind() != 0)
                .sorted(Comparator.comparing(Module::getName))
                .collect(Collectors.toList());
        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        if (binds.isEmpty()) {
            keybinds.width = 110;
            keybinds.height = 26;
            HudStyle.resetColor();
            return;
        }

        float pad = 6f;
        float headerH = 23f;
        float rowH = 25f;
        float keySize = 20f;
        float w = pad + 18f + 7f + title.getWidth("Keybinds") + pad;
        for (Module m : binds)
            w = Math.max(w, pad + keySize + 9f + title.getWidth(m.getName()) + pad);
        w = Math.max(w, 150f);
        float h = pad + headerH + binds.size() * rowH + pad;

        keybinds.width = (int) Math.ceil(w);
        keybinds.height = (int) Math.ceil(h);
        HudStyle.pushScale(keybinds.x, keybinds.y, keybinds.scale);
        HudStyle.card(keybinds.x, keybinds.y, keybinds.x + w, keybinds.y + h, 15f, panelFill(0.96f));

        float headerMid = keybinds.y + pad + headerH / 2f;
        M3Icons.drawCentered(M3Icons.KEYBOARD, keybinds.x + pad + 8f, headerMid, M3.primary(), 0.46f);
        title.drawString("Keybinds", keybinds.x + pad + 23f, headerMid, M3.onSurface(), title.CENTREY);
        HudStyle.divider(keybinds.x + pad, keybinds.y + pad + headerH, keybinds.x + w - pad);

        float y = keybinds.y + pad + headerH + rowH / 2f;
        for (Module m : binds) {
            float keyX = keybinds.x + pad;
            DrawUtils.drawRoundedRect(keyX, y - keySize / 2f, keyX + keySize, y + keySize / 2f, 6f,
                    M3.alpha(M3.secondaryContainer(), 235));
            String key = GameSettings.getKeyDisplayString(m.getKeybind());
            label.drawString(key, keyX + keySize / 2f, y, M3.onSecondaryContainer(),
                    label.CENTREX, label.CENTREY);
            title.drawString(m.getName(), keyX + keySize + 9f, y,
                    m.isEnabled() ? M3.onSurface() : M3.onSurfaceVariant(), title.CENTREY);
            y += rowH;
        }
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private void renderPotions(int color) {
        List<PotionEffect> effects = new ArrayList<>(mc.thePlayer.getActivePotionEffects());
        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        if (effects.isEmpty()) {
            potions.width = 110;
            potions.height = 26;
            HudStyle.resetColor();
            return;
        }
        effects.sort(Comparator.comparingInt(PotionEffect::getDuration).reversed());

        float pad = 5f;
        float headerH = 22f;
        float icon = 16f;
        float rowH = 21f;
        List<String[]> rows = new ArrayList<>();
        float w = 0;
        for (PotionEffect effect : effects) {
            String name = I18n.format(effect.getEffectName());
            String amp = effect.getAmplifier() > 0 ? roman(effect.getAmplifier() + 1) : "";
            String time = effect.getIsAmbient() ? "∞" : Potion.getDurationString(effect);
            rows.add(new String[]{name, amp, time});
            float rowW = pad + icon + 7f + title.getWidth(name)
                    + (amp.isEmpty() ? 0 : 6f + label.getWidth(amp) + 10f)
                    + 10f + label.getWidth(time) + 10f + pad;
            w = Math.max(w, rowW);
        }
        w = Math.max(w, pad + 16f + 6f + label.getWidth("EFFECTS") + 8f
                + label.getWidth("00") + 12f + pad);
        float h = pad + headerH + effects.size() * rowH + pad;

        potions.width = (int) Math.ceil(w);
        potions.height = (int) Math.ceil(h);
        HudStyle.pushScale(potions.x, potions.y, potions.scale);
        HudStyle.card(potions.x, potions.y, potions.x + w, potions.y + h, 14f, panelFill(0.96f));

        float headerMid = potions.y + pad + headerH / 2f;
        M3Icons.drawCentered(M3Icons.EFFECTS, potions.x + pad + 6f, headerMid, M3.primary(), 0.42f);
        label.drawString("EFFECTS", potions.x + pad + 17f, headerMid, M3.onSurfaceVariant(), label.CENTREY);
        String count = String.valueOf(effects.size());
        float countW = label.getWidth(count) + 12f;
        HudStyle.pill(label, count, potions.x + w - pad - countW, headerMid, 15f,
                M3.alpha(M3.secondaryContainer(), 210), M3.onSecondaryContainer());
        HudStyle.divider(potions.x + pad, potions.y + pad + headerH, potions.x + w - pad);

        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        float y = potions.y + pad + headerH;
        for (int i = 0; i < effects.size(); i++) {
            PotionEffect effect = effects.get(i);
            float mid = y + rowH / 2f;
            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion != null && potion.hasStatusIcon()) {
                int idx = potion.getStatusIconIndex();
                GlStateManager.enableBlend();
                GlStateManager.color(1f, 1f, 1f, 1f);
                Gui.drawScaledCustomSizeModalRect((int) (potions.x + pad),
                        (int) (mid - icon / 2f),
                        idx % 8 * 18, 198 + idx / 8 * 18, 18, 18,
                        (int) icon, (int) icon, 256, 256);
            }

            boolean expiring = effect.getDuration() < 200 && !effect.getIsAmbient();
            boolean flash = expiring && (System.currentTimeMillis() / 250 % 2 == 0);
            int nameColor = flash ? M3.error() : M3.onSurface();
            title.drawString(rows.get(i)[0], potions.x + pad + icon + 6f, mid - 3.5f,
                    nameColor, title.CENTREY);

            String amp = rows.get(i)[1];
            if (!amp.isEmpty()) {
                float ampX = potions.x + pad + icon + 6f + title.getWidth(rows.get(i)[0]) + 5f;
                HudStyle.pill(label, amp, ampX, mid - 3.5f, 12f,
                        M3.alpha(M3.surfaceContainerHighest(), 210), M3.onSurfaceVariant());
            }

            String time = rows.get(i)[2];
            float timeW = label.getWidth(time) + 10f;
            float timeX = potions.x + w - pad - timeW;
            DrawUtils.drawRoundedRect(timeX, mid - 7f, timeX + timeW, mid + 7f, 7f,
                    M3.alpha(flash ? M3.errorContainer() : M3.surfaceContainerHighest(), 200));
            label.drawString(time, timeX + timeW / 2f, mid,
                    flash ? M3.error() : M3.onSurfaceVariant(), label.CENTREX, label.CENTREY);
            y += rowH;
        }
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private void renderKeystrokes(int color) {
        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        float pad = 6f, gap = 3.5f, key = 21f, spaceH = 12f, sprintH = 18f, headerH = 20f;
        float x0 = keystrokes.x, y0 = keystrokes.y;
        float w = key * 3f + gap * 2f + pad * 2f;
        float h = pad + headerH + key + gap + key + gap + key + gap + spaceH + gap + sprintH + pad;
        keystrokes.width = (int) Math.ceil(w);
        keystrokes.height = (int) Math.ceil(h);
        HudStyle.pushScale(x0, y0, keystrokes.scale);
        HudStyle.card(x0, y0, x0 + w, y0 + h, 16f, panelFill(0.96f));

        float dt = keyClock.tick();
        int lmbCps = cps(clicks) + SyntheticKeys.countLastSecond(SyntheticKeys.Key.LMB);
        int rmbCps = cps(rightClicks);
        float headerMid = y0 + pad + headerH / 2f;
        HudStyle.iconBadge(M3Icons.KEYBOARD, x0 + pad + 7f, headerMid, 7.5f,
                M3.alpha(M3.primaryContainer(), 235), M3.onPrimaryContainer());
        title.drawString("Keystrokes", x0 + pad + 20f, headerMid, M3.onSurface(), title.CENTREY);
        String cpsText = lmbCps + " / " + rmbCps;
        float cpsW = label.getWidth(cpsText) + 9f;
        HudStyle.pill(label, cpsText, x0 + w - pad - cpsW, headerMid, 14f,
                M3.alpha(M3.surfaceContainerHighest(), 235), M3.onSurfaceVariant());

        float ky = y0 + pad + headerH;
        float rowW = key * 3f + gap * 2f;
        keyBox(label, "W", mc.gameSettings.keyBindForward.isKeyDown(),
                x0 + pad + key + gap, ky, key, key, dt);
        ky += key + gap;
        keyBox(label, "A", mc.gameSettings.keyBindLeft.isKeyDown(), x0 + pad, ky, key, key, dt);
        keyBox(label, "S", mc.gameSettings.keyBindBack.isKeyDown(), x0 + pad + key + gap, ky, key, key, dt);
        keyBox(label, "D", mc.gameSettings.keyBindRight.isKeyDown(),
                x0 + pad + (key + gap) * 2f, ky, key, key, dt);
        ky += key + gap;

        float half = (rowW - gap) / 2f;
        boolean lmb = Mouse.isButtonDown(0) || SyntheticKeys.flashing(SyntheticKeys.Key.LMB);
        keyBox(label, lmbCps + " LMB", lmb, x0 + pad, ky, half, key, dt);
        keyBox(label, rmbCps + " RMB", Mouse.isButtonDown(1), x0 + pad + half + gap, ky, half, key, dt);
        ky += key + gap;

        boolean jump = mc.gameSettings.keyBindJump.isKeyDown() || SyntheticKeys.flashing(SyntheticKeys.Key.JUMP);
        keyBox(label, "SPACE", jump, x0 + pad, ky, rowW, spaceH, dt);
        ky += spaceH + gap;

        boolean sprint = mc.gameSettings.keyBindSprint.isKeyDown() || SyntheticKeys.flashing(SyntheticKeys.Key.SPRINT);
        keyBox(label, "SPRINT", sprint, x0 + pad, ky, rowW, sprintH, dt);
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private void keyBox(FontRendererExtension<?> fr, String text, boolean down,
                        float x, float y, float w, float h, float dt) {
        String id = text.split(" ")[0];
        float press = keyPress.getOrDefault(id, 0f);
        press = FrameClock.approach(press, down ? 1f : 0f, down ? 30f : 20f, dt);
        keyPress.put(id, press);

        float inset = press * 1f;

        // M3 shape morph: corners round into a pill as the key goes down, with a pressed inset
        float radius = MathUtils.lerp(Math.min(7f, h * 0.36f), Math.min(11f, h / 2f), press);
        int fill = M3.mix(M3.alpha(M3.surfaceContainerHighest(), 230),
                M3.alpha(M3.primaryContainer(), 255), press);
        int outline = M3.mix(M3.alpha(M3.outlineVariant(), 110),
                M3.alpha(M3.primary(), 220), press);
        DrawUtils.drawRoundedRect(x + inset, y + inset, x + w - inset, y + h - inset, radius, fill);
        DrawUtils.drawRoundedOutline(x + inset, y + inset, x + w - inset, y + h - inset, radius, 1f, outline);

        int fg = M3.mix(M3.onSurfaceVariant(), M3.onPrimaryContainer(), press);
        fr.drawString(text, x + w / 2f, y + h / 2f, fg, fr.CENTREX, fr.CENTREY);
    }

    private String sidebarObjectiveName() {
        ScoreObjective objective = sidebarObjective();
        return objective == null ? null : objective.getDisplayName();
    }

    /** The objective shown in the sidebar slot, or null when no scoreboard sidebar is up. */
    private ScoreObjective sidebarObjective() {
        if (mc.theWorld == null)
            return null;
        return mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
    }

    /** True while this HUD draws the scoreboard, so the vanilla sidebar should stay hidden. */
    public boolean replacesVanillaScoreboard() {
        return isEnabled() && showScoreboard.getValue();
    }

    private void renderScoreboard(ScaledResolution sr, int color) {
        ScoreObjective objective = sidebarObjective();
        if (objective == null)
            return;

        Scoreboard board = objective.getScoreboard();
        List<Score> scores = new ArrayList<>();
        for (Score score : board.getSortedScores(objective))
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#"))
                scores.add(score);
        if (scores.size() > 15)
            scores = new ArrayList<>(scores.subList(scores.size() - 15, scores.size()));

        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        float pad = 6f;
        float headerH = 25f;
        float rowH = 17f;
        String titleText = objective.getDisplayName();
        List<String> names = new ArrayList<>();
        List<String> points = new ArrayList<>();
        float w = pad + 18f + 7f + title.getWidth(titleText) + pad;
        for (Score score : scores) {
            ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String value = String.valueOf(score.getScorePoints());
            names.add(name);
            points.add(value);
            w = Math.max(w, pad + title.getWidth(name) + 10f + label.getWidth(value) + 10f + pad);
        }
        float h = pad + headerH + scores.size() * rowH + pad;

        scoreboard.width = (int) Math.ceil(w);
        scoreboard.height = (int) Math.ceil(h);
        float x1 = sr.getScaledWidth() + scoreboard.x - scoreboard.width;
        HudStyle.pushScale(sr.getScaledWidth() + scoreboard.x, scoreboard.y, scoreboard.scale);
        float y1 = scoreboard.y;
        HudStyle.card(x1, y1, x1 + scoreboard.width, y1 + h, 15f, panelFill(0.96f));

        float headerMid = y1 + pad + headerH / 2f;
        HudStyle.iconBadge(M3Icons.TROPHY, x1 + pad + 8f, headerMid, 8f,
                M3.alpha(M3.primaryContainer(), 230), M3.onPrimaryContainer());
        title.drawString(titleText, x1 + pad + 21f, headerMid, M3.onSurface(), title.CENTREY);
        HudStyle.divider(x1 + pad, y1 + pad + headerH, x1 + scoreboard.width - pad);

        float y = y1 + pad + headerH + rowH / 2f;
        for (int i = 0; i < names.size(); i++) {
            title.drawString(names.get(i), x1 + pad + 2f, y, M3.onSurfaceVariant(), title.CENTREY);

            String pts = points.get(i);
            float ptsW = label.getWidth(pts) + 10f;
            float ptsX = x1 + scoreboard.width - pad - ptsW;
            int fill = i == 0 ? M3.alpha(M3.primaryContainer(), 210)
                    : M3.alpha(M3.surfaceContainerHighest(), 185);
            int fg = i == 0 ? M3.onPrimaryContainer() : M3.onSurfaceVariant();
            DrawUtils.drawRoundedRect(ptsX, y - 6.5f, ptsX + ptsW, y + 6.5f, 6.5f, fill);
            label.drawString(pts, ptsX + ptsW / 2f, y, fg, label.CENTREX, label.CENTREY);

            if (i < names.size() - 1)
                HudStyle.divider(x1 + pad, y + rowH / 2f - 0.5f, x1 + scoreboard.width - pad);
            y += rowH;
        }
        HudStyle.resetColor();
        HudStyle.popScale();
    }

    private static String roman(int n) {
        if (n <= 0)
            return "";
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L"};
        return tens[Math.min(5, n / 10)] + ones[Math.min(9, n % 10)];
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
        float infoWidth;
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
