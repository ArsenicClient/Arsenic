package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMouse;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.event.impl.EventTick;
import arsenic.gui.design.M3;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.MathUtils;
import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.utils.lag.PingTracker;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.FrameClock;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@ModuleInfo(name = "HUD", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.QOL)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.THEME);
    public final EnumProperty<WatermarkMode> watermarkMode = new EnumProperty<>("Watermark: ", WatermarkMode.FPS);
    public final EnumProperty<ArrayListSort> arraylistSort = new EnumProperty<>("Sort: ", ArrayListSort.LENGTH);
    public final EnumProperty<ArrayListBackground> arraylistBackground = new EnumProperty<>("Background: ", ArrayListBackground.PANEL);
    public final BooleanProperty showInfo = new BooleanProperty("Module Info", true);
    public final BooleanProperty showCoords = new BooleanProperty("Show Coords", false);
    public final BooleanProperty showKeybinds = new BooleanProperty("Show Keybinds", false);
    public final BooleanProperty showPing = new BooleanProperty("Show Ping", false);
    public final BooleanProperty showPotions = new BooleanProperty("Show Potions", false);
    public final BooleanProperty showKeystrokes = new BooleanProperty("Show Keystrokes", false);
    public final BooleanProperty showScoreboard = new BooleanProperty("Show Scoreboard", false);
    public final DoubleProperty backgroundOpacity = new DoubleProperty("Opacity", new DoubleValue(0, 100, 58, 1));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);

    public static final int KEYSTROKES_W = 64, KEYSTROKES_H = 92;

    private final HudElement arrayList = hudElement("Module List", -6, 6, 96, 70, true);
    private final HudElement watermark = hudElement("Watermark", 6, 6, 84, 18);
    private final HudElement coords = hudElement("Coordinates", 6, 50, 98, 18);
    private final HudElement keybinds = hudElement("Keybinds", 6, 72, 106, 36);
    private final HudElement pingElement = hudElement("Ping", 6, 28, 54, 18);
    private final HudElement potions = hudElement("Potions", 6, 112, 108, 20);
    private final HudElement keystrokes = hudElement("Keystrokes", 6, 136, KEYSTROKES_W, KEYSTROKES_H);
    private final HudElement scoreboard = hudElement("Scoreboard", -6, 120, 124, 60, true);

    {
        coords.active = showCoords::getValue;
        keybinds.active = showKeybinds::getValue;
        pingElement.active = showPing::getValue;
        potions.active = showPotions::getValue;
        keystrokes.active = showKeystrokes::getValue;
        scoreboard.active = () -> showScoreboard.getValue() && sidebarObjective() != null;
    }

    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static final float ROW_HEIGHT = 13.5f;
    private static final float PANEL_PAD_L = 5.5f;
    private static final float PANEL_PAD_R = 6.5f;
    private static final float ACCENT_BAR_W = 2f;

    private static final float SORT_TOLERANCE = 2f;
    private static final long SORT_SETTLE_MS = 500L;

    private final Map<Module, Entry> entries = new LinkedHashMap<>();
    private List<Entry> visible = new ArrayList<>();
    private final FrameClock clock = new FrameClock();
    private final FrameClock keystrokeClock = new FrameClock();
    private final float[] keyPress = new float[8];

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (editPosition.getValue() && !(mc.currentScreen instanceof HudEditorScreen)) {
            mc.displayGuiScreen(new HudEditorScreen());
            editPosition.setValue(false);
        }
    };

    private final ArrayDeque<Long> clicks = new ArrayDeque<>();
    private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();

    @EventLink
    public final Listener<EventMouse.Down> onMouseDown = event -> {
        if (event.button == 0)
            clicks.addLast(System.currentTimeMillis());
        else if (event.button == 1)
            rightClicks.addLast(System.currentTimeMillis());
    };

    private static int cps(ArrayDeque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!q.isEmpty() && q.peekFirst() < cutoff)
            q.pollFirst();
        return q.size();
    }

    private int surfaceAlpha() {
        return (int) Math.round(255.0 * (backgroundOpacity.getValue().getInput() / 100.0));
    }

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!shouldRender())
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = HudStyle.body();
        if (fr == null)
            return;

        int accent = colorMode.getValue().getColor(4, 0);

        renderWatermark(fr, accent);
        if (showCoords.getValue() && mc.thePlayer != null)
            renderCoords(fr, accent);
        if (showKeybinds.getValue())
            renderKeybinds(fr);
        if (showPing.getValue())
            renderPing(fr, accent);
        if (showPotions.getValue() && mc.thePlayer != null)
            renderPotions(fr, accent);
        if (showKeystrokes.getValue())
            renderKeystrokes(fr, accent);
        if (showScoreboard.getValue())
            renderScoreboard(fr, sr, accent);

        buildEntries(fr);
        drawArrayList(fr, sr, Pass.NORMAL);
        HudStyle.resetGl();
    };

    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (!shouldRender())
            return;
        FontRendererExtension<?> fr = HudStyle.body();
        if (fr == null || visible.isEmpty())
            return;
        drawArrayList(fr, new ScaledResolution(mc), Pass.BLOOM);
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!shouldRender())
            return;
        FontRendererExtension<?> fr = HudStyle.body();
        if (fr == null)
            return;
        ScaledResolution sr = new ScaledResolution(mc);
        drawHudBlurMasks(sr);
        if (!visible.isEmpty())
            drawArrayList(fr, sr, Pass.BLUR);
    };

    private void drawHudBlurMasks(ScaledResolution sr) {
        if (surfaceAlpha() <= 4)
            return;
        DrawUtils.drawRoundedRect(watermark.x, watermark.y, watermark.x + watermark.width, watermark.y + watermark.height,
                HudStyle.RADIUS_COMPACT, 0xFFFFFFFF);
        if (showCoords.getValue() && mc.thePlayer != null) {
            DrawUtils.drawRoundedRect(coords.x, coords.y, coords.x + coords.width, coords.y + coords.height,
                    HudStyle.RADIUS_COMPACT, 0xFFFFFFFF);
        }
        if (showKeybinds.getValue() && keybinds.width > 0 && keybinds.height > 0) {
            boolean hasBinds = Arsenic.getArsenic().getModuleManager().getModules().stream().anyMatch(m -> m.getKeybind() != 0);
            if (hasBinds || mc.currentScreen instanceof HudEditorScreen) {
                DrawUtils.drawRoundedRect(keybinds.x, keybinds.y, keybinds.x + keybinds.width, keybinds.y + keybinds.height,
                        HudStyle.RADIUS_CARD, 0xFFFFFFFF);
            }
        }
        if (showPing.getValue()) {
            DrawUtils.drawRoundedRect(pingElement.x, pingElement.y, pingElement.x + pingElement.width, pingElement.y + pingElement.height,
                    HudStyle.RADIUS_COMPACT, 0xFFFFFFFF);
        }
        if (showPotions.getValue() && mc.thePlayer != null
                && (!mc.thePlayer.getActivePotionEffects().isEmpty() || mc.currentScreen instanceof HudEditorScreen)) {
            DrawUtils.drawRoundedRect(potions.x, potions.y, potions.x + potions.width, potions.y + potions.height,
                    HudStyle.RADIUS_CARD, 0xFFFFFFFF);
        }
        if (showKeystrokes.getValue()) {
            float k = 20f, gap = 2f;
            float x0 = HudStyle.snap(keystrokes.x);
            float y0 = HudStyle.snap(keystrokes.y);
            DrawUtils.drawRoundedRect(x0 + k + gap, y0, x0 + k * 2f + gap, y0 + k, 5.5f, 0xFFFFFFFF);
            float row2 = y0 + k + gap;
            DrawUtils.drawRoundedRect(x0, row2, x0 + k, row2 + k, 5.5f, 0xFFFFFFFF);
            DrawUtils.drawRoundedRect(x0 + k + gap, row2, x0 + k * 2f + gap, row2 + k, 5.5f, 0xFFFFFFFF);
            DrawUtils.drawRoundedRect(x0 + (k + gap) * 2f, row2, x0 + k * 3f + gap * 2f, row2 + k, 5.5f, 0xFFFFFFFF);
            float row3 = row2 + k + gap;
            float half = (k * 3f + gap * 2f - gap) / 2f;
            DrawUtils.drawRoundedRect(x0, row3, x0 + half, row3 + k, 5.5f, 0xFFFFFFFF);
            DrawUtils.drawRoundedRect(x0 + half + gap, row3, x0 + half * 2f + gap, row3 + k, 5.5f, 0xFFFFFFFF);
            float row4 = row3 + k + gap;
            float spaceH = 12f;
            float rowW = k * 3f + gap * 2f;
            DrawUtils.drawRoundedRect(x0, row4, x0 + rowW, row4 + spaceH, 4.5f, 0xFFFFFFFF);
            float row5 = row4 + spaceH + gap;
            DrawUtils.drawRoundedRect(x0, row5, x0 + rowW, row5 + spaceH, 4.5f, 0xFFFFFFFF);
        }
        if (showScoreboard.getValue() && sidebarObjective() != null) {
            float x1 = HudStyle.snap(sr.getScaledWidth() + scoreboard.x - scoreboard.width);
            float y1 = HudStyle.snap(scoreboard.y);
            DrawUtils.drawRoundedRect(x1, y1, x1 + scoreboard.width, y1 + scoreboard.height,
                    HudStyle.RADIUS_CARD, 0xFFFFFFFF);
        }
    }

    private boolean shouldRender() {
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
    }

    private enum Pass { NORMAL, BLOOM, BLUR }

    private void buildEntries(FontRendererExtension<?> fr) {
        long now = System.currentTimeMillis();
        float dt = clock.tick();
        FontRendererExtension<?> labelFr = HudStyle.label();

        Set<Module> enabled = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                .stream().filter(m -> !m.isHidden()).collect(Collectors.toSet());

        for (Module m : enabled)
            entries.computeIfAbsent(m, Entry::new);

        for (Map.Entry<Module, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            entry.alive = enabled.contains(e.getKey());
            entry.info = showInfo.getValue() ? safeInfo(e.getKey()) : null;
            entry.nameWidth = labelFr.getWidth(entry.name);
            entry.infoWidth = entry.info == null ? 0f : fr.getWidth(entry.info);
            entry.width = entry.nameWidth + (entry.info == null ? 0f : 4.5f + entry.infoWidth);
            entry.settleSortWidth(now);
        }

        List<Entry> ordered = new ArrayList<>(entries.values());
        ordered.sort(arraylistSort.getValue().getComparator());

        float y = 0f;
        visible = new ArrayList<>(ordered.size());
        for (Entry entry : ordered) {
            float target = entry.alive ? 1f : 0f;
            entry.opacity += Math.signum(target - entry.opacity) * dt * 6.0f;
            entry.opacity = MathUtils.clamp01(entry.opacity);

            if (entry.y < 0f)
                entry.y = y;
            entry.y = FrameClock.approach(entry.y, y, 15f, dt);

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

        FontRendererExtension<?> labelFr = HudStyle.label();
        ArrayListBackground bg = arraylistBackground.getValue();
        float rightAnchor = HudStyle.snap(sr.getScaledWidth() + arrayList.x);
        float topAnchor = HudStyle.snap(arrayList.y);
        int baseAlpha = surfaceAlpha();

        float maxRowW = 64f;
        float totalBottom = topAnchor + ROW_HEIGHT;

        for (int i = 0; i < visible.size(); i++) {
            Entry entry = visible.get(i);
            float rowY = HudStyle.snap(topAnchor + entry.y);
            float rowH = HudStyle.snap(ROW_HEIGHT);
            float rowMid = rowY + rowH / 2f;
            int color = colorMode.getValue().getColor(4, i * 160);

            float slide = (1f - entry.opacity) * 14f;
            float rowW = HudStyle.snap(entry.width + PANEL_PAD_L + PANEL_PAD_R);
            maxRowW = Math.max(maxRowW, rowW);
            totalBottom = Math.max(totalBottom, rowY + rowH);

            float rowRight = HudStyle.snap(rightAnchor + slide);
            float rowLeft = HudStyle.snap(rowRight - rowW);
            float alphaPct = pass == Pass.BLOOM ? 1f : entry.opacity;
            int rowPanelAlpha = (int) (baseAlpha * entry.opacity);
            float pillGap = 1.5f;
            float boxY1 = bg == ArrayListBackground.PANEL ? HudStyle.snap(rowY + pillGap * 0.5f) : rowY;
            float boxY2 = bg == ArrayListBackground.PANEL ? HudStyle.snap(rowY + rowH - pillGap * 0.5f) : HudStyle.snap(rowY + rowH);
            float boxR = Math.min(3f, (boxY2 - boxY1) / 2f);

            if (pass == Pass.BLUR) {
                if (bg == ArrayListBackground.PANEL) {
                    DrawUtils.drawRoundedRect(rowLeft, boxY1, rowRight, boxY2, boxR, 0xFFFFFFFF);
                } else if (bg == ArrayListBackground.RECTANGLE) {
                    DrawUtils.drawRect(rowLeft, boxY1, rowRight, boxY2, 0xFFFFFFFF);
                }
                continue;
            }

            if (pass == Pass.NORMAL && rowPanelAlpha > 0) {
                int fill = M3.alpha(M3.surfaceContainerLowest(), rowPanelAlpha);
                if (bg == ArrayListBackground.PANEL) {
                    DrawUtils.drawRoundedRect(rowLeft, boxY1, rowRight, boxY2, boxR, fill);
                } else if (bg == ArrayListBackground.RECTANGLE) {
                    DrawUtils.drawRect(rowLeft, boxY1, rowRight, boxY2, fill);
                }
            }

            if (bg != ArrayListBackground.NONE) {
                int barColor = M3.alpha(color, (int) (255 * alphaPct));
                if (bg == ArrayListBackground.PANEL) {
                    DrawUtils.drawRoundedRect(rowRight - ACCENT_BAR_W, boxY1, rowRight, boxY2, 1f, barColor);
                } else {
                    DrawUtils.drawRect(rowRight - ACCENT_BAR_W, boxY1, rowRight, boxY2, barColor);
                }
            }

            float textLeft = HudStyle.snap(rowLeft + PANEL_PAD_L);
            int nameCol = M3.alpha(color, (int) (255 * alphaPct));
            labelFr.drawString(entry.name, textLeft, rowMid, nameCol, labelFr.CENTREY);

            if (entry.info != null) {
                float infoX = textLeft + entry.nameWidth + 4.5f;
                int infoCol = pass == Pass.BLOOM
                        ? nameCol
                        : M3.alpha(M3.onSurfaceVariant(), (int) (235 * alphaPct));
                fr.drawString(entry.info, infoX, rowMid, infoCol, fr.CENTREY);
            }
        }

        if (pass == Pass.NORMAL) {
            arrayList.width = Math.max(72, (int) Math.ceil(maxRowW));
            arrayList.height = Math.max(18, (int) Math.ceil(totalBottom - topAnchor));
        }
    }

    private void renderWatermark(FontRendererExtension<?> fr, int color) {
        FontRendererExtension<?> labelFr = HudStyle.label();
        String suffix = null;
        switch (watermarkMode.getValue()) {
            case FPS:
                suffix = Minecraft.getDebugFPS() + " FPS";
                break;
            case COORDS:
                if (mc.thePlayer != null)
                    suffix = String.format("%.0f, %.0f, %.0f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                break;
            case IP:
                suffix = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "Singleplayer";
                break;
            case TEXT:
            default:
                break;
        }

        String title = "Arsenic";
        float h = 18f;
        float padX = 6.5f;
        float titleW = labelFr.getWidth(title);
        float chipPadX = 4.5f;
        float chipW = suffix == null ? 0f : HudStyle.snap(fr.getWidth(suffix) + chipPadX * 2f);
        float w = HudStyle.snap(padX + 4.5f + titleW + (suffix == null ? padX : 5.5f + chipW + 3f));

        float x = HudStyle.snap(watermark.x);
        float y = HudStyle.snap(watermark.y);
        float cy = y + h / 2f;
        watermark.width = (int) Math.ceil(w);
        watermark.height = (int) Math.ceil(h);

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_COMPACT, surfaceAlpha());

        float barH = 9f;
        float barX = HudStyle.snap(x + 4.5f);
        DrawUtils.drawRoundedRect(barX, HudStyle.snap(cy - barH / 2f), barX + 2f, HudStyle.snap(cy + barH / 2f), 1f, color);

        float titleX = HudStyle.snap(barX + 5.5f);
        labelFr.drawString(title, titleX, cy, M3.onSurface(), labelFr.CENTREY);

        if (suffix != null) {
            HudStyle.rightChip(fr, suffix, x + w - 3f, cy, 12.5f, chipPadX,
                    M3.alpha(M3.surfaceContainerHigh(), 215), M3.onSurfaceVariant());
        }
        HudStyle.resetGl();
    }

    private void renderCoords(FontRendererExtension<?> fr, int color) {
        FontRendererExtension<?> labelFr = HudStyle.label();
        String sx = String.format("%.0f", mc.thePlayer.posX);
        String sy = String.format("%.0f", mc.thePlayer.posY);
        String sz = String.format("%.0f", mc.thePlayer.posZ);

        float h = 18f;
        float padX = 7f;
        float gap = 6f;
        float subGap = 2.5f;

        float xLabelW = labelFr.getWidth("X");
        float yLabelW = labelFr.getWidth("Y");
        float zLabelW = labelFr.getWidth("Z");
        float sxW = fr.getWidth(sx);
        float syW = fr.getWidth(sy);
        float szW = fr.getWidth(sz);

        float contentW = (xLabelW + subGap + sxW) + gap + (yLabelW + subGap + syW) + gap + (zLabelW + subGap + szW);
        float w = HudStyle.snap(padX * 2f + contentW);

        float x = HudStyle.snap(coords.x);
        float y = HudStyle.snap(coords.y);
        float cy = y + h / 2f;
        coords.width = (int) Math.ceil(w);
        coords.height = (int) Math.ceil(h);

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_COMPACT, surfaceAlpha());

        float cursor = HudStyle.snap(x + padX);
        labelFr.drawString("X", cursor, cy, M3.primary(), labelFr.CENTREY);
        cursor += xLabelW + subGap;
        fr.drawString(sx, cursor, cy, M3.onSurface(), fr.CENTREY);
        cursor += sxW + gap;

        labelFr.drawString("Y", cursor, cy, M3.primary(), labelFr.CENTREY);
        cursor += yLabelW + subGap;
        fr.drawString(sy, cursor, cy, M3.onSurface(), fr.CENTREY);
        cursor += syW + gap;

        labelFr.drawString("Z", cursor, cy, M3.primary(), labelFr.CENTREY);
        cursor += zLabelW + subGap;
        fr.drawString(sz, cursor, cy, M3.onSurface(), fr.CENTREY);
        HudStyle.resetGl();
    }

    private void renderKeybinds(FontRendererExtension<?> fr) {
        List<Module> binds = Arsenic.getArsenic().getModuleManager().getModules().stream()
                .filter(m -> m.getKeybind() != 0)
                .sorted(Comparator.comparing(Module::getName))
                .collect(Collectors.toList());
        if (binds.isEmpty() && !(mc.currentScreen instanceof HudEditorScreen))
            return;

        FontRendererExtension<?> labelFr = HudStyle.label();
        float padX = 6f;
        float padY = 4f;
        float rowH = 14.5f;

        if (binds.isEmpty()) {
            float sampleW = 7.5f + fr.getWidth("KillAura") + 10f + labelFr.getWidth("R") + 9f;
            float w = HudStyle.snap(sampleW + padX * 2f);
            float h = HudStyle.snap(rowH + padY * 2f);
            float x = HudStyle.snap(keybinds.x);
            float y = HudStyle.snap(keybinds.y);
            keybinds.width = (int) Math.ceil(w);
            keybinds.height = (int) Math.ceil(h);
            HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_CARD, surfaceAlpha());
            float rowCy = y + h / 2f;
            DrawUtils.drawCircle(x + padX + 2f, rowCy, 2f, M3.outlineVariant());
            fr.drawString("KillAura", x + padX + 7.5f, rowCy, M3.onSurfaceVariant(), fr.CENTREY);
            HudStyle.rightChip(labelFr, "R", x + w - padX, rowCy, 11.5f, 4.5f,
                    M3.alpha(M3.surfaceContainerHigh(), 215), M3.onSurfaceVariant());
            HudStyle.resetGl();
            return;
        }

        float maxRowW = 68f;
        for (Module m : binds) {
            String key = GameSettings.getKeyDisplayString(m.getKeybind());
            float rw = 7.5f + fr.getWidth(m.getName()) + 10f + labelFr.getWidth(key) + 9f;
            maxRowW = Math.max(maxRowW, rw);
        }

        float w = HudStyle.snap(maxRowW + padX * 2f);
        float h = HudStyle.snap(binds.size() * rowH + padY * 2f);
        float x = HudStyle.snap(keybinds.x);
        float y = HudStyle.snap(keybinds.y);
        keybinds.width = (int) Math.ceil(w);
        keybinds.height = (int) Math.ceil(h);

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_CARD, surfaceAlpha());

        float rowTop = y + padY;
        for (Module m : binds) {
            float rowCy = rowTop + rowH / 2f;
            boolean on = m.isEnabled();
            int dotCol = on ? M3.primary() : M3.outlineVariant();
            DrawUtils.drawCircle(x + padX + 2f, rowCy, 2f, dotCol);

            int nameCol = on ? M3.onSurface() : M3.onSurfaceVariant();
            fr.drawString(m.getName(), x + padX + 7.5f, rowCy, nameCol, fr.CENTREY);

            String key = GameSettings.getKeyDisplayString(m.getKeybind());
            int chipBg = on ? M3.primaryContainer() : M3.alpha(M3.surfaceContainerHigh(), 215);
            int chipFg = on ? M3.onPrimaryContainer() : M3.onSurfaceVariant();
            HudStyle.rightChip(labelFr, key, x + w - padX, rowCy, 11.5f, 4.5f, chipBg, chipFg);

            rowTop += rowH;
        }
        HudStyle.resetGl();
    }

    private void renderPing(FontRendererExtension<?> fr, int color) {
        FontRendererExtension<?> labelFr = HudStyle.label();
        PingTracker.Source source = PingTracker.getSource();
        int ping = PingTracker.getPing();
        String value = source == PingTracker.Source.NONE ? "-- ms" : ping + " ms";
        if (source == PingTracker.Source.TAB_LIST)
            value += " ~";
        else if (source == PingTracker.Source.LOCKED)
            value += " *";

        int dotCol, textCol;
        if (source == PingTracker.Source.NONE || ping < 100) {
            dotCol = M3.primary();
            textCol = M3.onSurface();
        } else if (ping < 180) {
            dotCol = M3.tertiary();
            textCol = M3.onSurface();
        } else {
            dotCol = M3.error();
            textCol = M3.error();
        }

        float h = 18f;
        float padX = 6.5f;
        float valW = labelFr.getWidth(value);
        float w = HudStyle.snap(padX + 6.5f + valW + padX);

        float x = HudStyle.snap(pingElement.x);
        float y = HudStyle.snap(pingElement.y);
        float cy = y + h / 2f;
        pingElement.width = (int) Math.ceil(w);
        pingElement.height = (int) Math.ceil(h);

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_COMPACT, surfaceAlpha());
        DrawUtils.drawCircle(x + padX + 1.5f, cy, 2f, dotCol);
        labelFr.drawString(value, x + padX + 6.5f, cy, textCol, labelFr.CENTREY);
        HudStyle.resetGl();
    }

    private void renderPotions(FontRendererExtension<?> fr, int color) {
        List<PotionEffect> effects = new ArrayList<>(mc.thePlayer.getActivePotionEffects());
        FontRendererExtension<?> labelFr = HudStyle.label();
        if (effects.isEmpty()) {
            potions.width = 96;
            potions.height = 18;
            if (mc.currentScreen instanceof HudEditorScreen) {
                float x = HudStyle.snap(potions.x);
                float y = HudStyle.snap(potions.y);
                float cy = y + 9f;
                HudStyle.card(x, y, x + 96f, y + 18f, HudStyle.RADIUS_COMPACT, surfaceAlpha());
                fr.drawString("Potions", x + 48f, cy, M3.onSurfaceVariant(), fr.CENTREX, fr.CENTREY);
            }
            return;
        }
        effects.sort(Comparator.comparingInt(PotionEffect::getDuration).reversed());

        float pad = 4f;
        float iconBox = 14f;
        float iconDraw = 12f;
        float rowH = 17f;
        float chipPadX = 4.5f;

        List<String[]> rows = new ArrayList<>(effects.size());
        float maxContentW = 0f;
        for (PotionEffect effect : effects) {
            String name = I18n.format(effect.getEffectName());
            int amp = effect.getAmplifier();
            if (amp > 0)
                name += " " + (amp < 9 ? I18n.format("enchantment.level." + (amp + 1)) : String.valueOf(amp + 1));
            String time = Potion.getDurationString(effect);
            rows.add(new String[]{name, time});
            float rowW = iconBox + 5f + fr.getWidth(name) + 8f + labelFr.getWidth(time) + chipPadX * 2f;
            maxContentW = Math.max(maxContentW, rowW);
        }

        float w = HudStyle.snap(maxContentW + pad * 2f);
        float h = HudStyle.snap(rows.size() * rowH + pad * 2f - 2f);
        float x = HudStyle.snap(potions.x);
        float y = HudStyle.snap(potions.y);
        potions.width = (int) Math.ceil(w);
        potions.height = (int) Math.ceil(h);

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_CARD, surfaceAlpha());

        float rowTop = y + pad - 1f;
        for (int i = 0; i < effects.size(); i++) {
            PotionEffect effect = effects.get(i);
            float rowCy = rowTop + rowH / 2f;
            float ibX = HudStyle.snap(x + pad);
            float ibY = HudStyle.snap(rowCy - iconBox / 2f);

            DrawUtils.drawRoundedRect(ibX, ibY, ibX + iconBox, ibY + iconBox, 4f,
                    M3.alpha(M3.surfaceContainerHigh(), 210));

            Potion potion = Potion.potionTypes[effect.getPotionID()];
            if (potion != null && potion.hasStatusIcon()) {
                int idx = potion.getStatusIconIndex();
                mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
                GlStateManager.enableBlend();
                GlStateManager.color(1f, 1f, 1f, 1f);
                int ix = Math.round(ibX + (iconBox - iconDraw) / 2f);
                int iy = Math.round(ibY + (iconBox - iconDraw) / 2f);
                Gui.drawScaledCustomSizeModalRect(ix, iy,
                        idx % 8 * 18, 198 + idx / 8 * 18, 18, 18, (int) iconDraw, (int) iconDraw, 256, 256);
            }

            boolean expiring = effect.getDuration() < 200 && !effect.getIsAmbient();
            boolean flash = expiring && (System.currentTimeMillis() / 250 % 2 == 0);
            int nameColor = flash ? M3.error() : M3.onSurface();
            fr.drawString(rows.get(i)[0], ibX + iconBox + 5f, rowCy, nameColor, fr.CENTREY);

            String time = rows.get(i)[1];
            int chipBg = expiring ? M3.alpha(M3.errorContainer(), 210) : M3.alpha(M3.surfaceContainerHigh(), 215);
            int chipFg = expiring ? M3.onErrorContainer() : M3.onSurfaceVariant();
            HudStyle.rightChip(labelFr, time, x + w - pad, rowCy, 12f, chipPadX, chipBg, chipFg);

            rowTop += rowH;
        }
        HudStyle.resetGl();
    }

    private void renderKeystrokes(FontRendererExtension<?> fr, int color) {
        float dt = keystrokeClock.tick();
        FontRendererExtension<?> labelFr = HudStyle.label();
        float k = 20f, gap = 2f;
        float x0 = HudStyle.snap(keystrokes.x);
        float y0 = HudStyle.snap(keystrokes.y);

        boolean wDown = mc.gameSettings.keyBindForward.isKeyDown();
        boolean aDown = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean sDown = mc.gameSettings.keyBindBack.isKeyDown();
        boolean dDown = mc.gameSettings.keyBindRight.isKeyDown();
        boolean lmb = Mouse.isButtonDown(0) || SyntheticKeys.flashing(SyntheticKeys.Key.LMB);
        boolean rmb = Mouse.isButtonDown(1);
        boolean jump = mc.gameSettings.keyBindJump.isKeyDown() || SyntheticKeys.flashing(SyntheticKeys.Key.JUMP);
        boolean sprint = mc.gameSettings.keyBindSprint.isKeyDown() || SyntheticKeys.flashing(SyntheticKeys.Key.SPRINT);

        boolean[] states = {wDown, aDown, sDown, dDown, lmb, rmb, jump, sprint};
        for (int i = 0; i < states.length; i++) {
            keyPress[i] = FrameClock.approach(keyPress[i], states[i] ? 1f : 0f, 18f, dt);
        }

        keyCap(labelFr, "W", null, keyPress[0], x0 + k + gap, y0, k, k);
        float row2 = y0 + k + gap;
        keyCap(labelFr, "A", null, keyPress[1], x0, row2, k, k);
        keyCap(labelFr, "S", null, keyPress[2], x0 + k + gap, row2, k, k);
        keyCap(labelFr, "D", null, keyPress[3], x0 + (k + gap) * 2f, row2, k, k);

        float row3 = row2 + k + gap;
        float half = (k * 3f + gap * 2f - gap) / 2f;
        int lmbCps = cps(clicks) + SyntheticKeys.countLastSecond(SyntheticKeys.Key.LMB);
        int rmbCps = cps(rightClicks);
        keyCap(labelFr, "LMB", lmbCps > 0 ? lmbCps + " CPS" : null, keyPress[4], x0, row3, half, k);
        keyCap(labelFr, "RMB", rmbCps > 0 ? rmbCps + " CPS" : null, keyPress[5], x0 + half + gap, row3, half, k);

        float row4 = row3 + k + gap;
        float spaceH = 12f;
        float rowW = k * 3f + gap * 2f;
        drawKeySurface(x0, row4, rowW, spaceH, 4.5f, keyPress[6]);
        float barW = 22f;
        float barH = 2f;
        float cx = x0 + rowW / 2f;
        float cy = row4 + spaceH / 2f;
        int barCol = M3.mix(M3.onSurfaceVariant(), M3.onPrimaryContainer(), keyPress[6]);
        DrawUtils.drawRoundedRect(HudStyle.snap(cx - barW / 2f), HudStyle.snap(cy - barH / 2f),
                HudStyle.snap(cx + barW / 2f), HudStyle.snap(cy + barH / 2f), barH / 2f, barCol);

        float row5 = row4 + spaceH + gap;
        keyCap(labelFr, "SPRINT", null, keyPress[7], x0, row5, rowW, spaceH);
        HudStyle.resetGl();
    }

    private void drawKeySurface(float x, float y, float w, float h, float radius, float press) {
        float x1 = HudStyle.snap(x);
        float y1 = HudStyle.snap(y);
        float x2 = HudStyle.snap(x + w);
        float y2 = HudStyle.snap(y + h);
        int a = surfaceAlpha();
        int restFill = M3.alpha(M3.mix(M3.surfaceContainerLow(), M3.surfaceContainer(), 0.45f), a);
        int activeFill = M3.alpha(M3.primaryContainer(), Math.min(255, a + 25));
        int fill = M3.mix(restFill, activeFill, press);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, fill);

        int restOutline = M3.alpha(M3.outlineVariant(), (int) (125 * (a / 255f)));
        int activeOutline = M3.alpha(M3.primary(), 200);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f, M3.mix(restOutline, activeOutline, press));
    }

    private void keyCap(FontRendererExtension<?> labelFr, String label, String subLabel,
                        float press, float x, float y, float w, float h) {
        float r = h <= 13f ? 4.5f : 5.5f;
        drawKeySurface(x, y, w, h, r, press);
        float cx = HudStyle.snap(x) + HudStyle.snap(w) / 2f;
        float cy = HudStyle.snap(y) + HudStyle.snap(h) / 2f;
        int fg = M3.mix(M3.onSurface(), M3.onPrimaryContainer(), press);

        if (subLabel == null) {
            float sc = h <= 13f ? 0.85f : 1f;
            labelFr.setScale(sc);
            labelFr.drawString(label, cx, cy, fg, labelFr.CENTREX, labelFr.CENTREY);
            labelFr.resetScale();
        } else {
            labelFr.setScale(0.92f);
            labelFr.drawString(label, cx, cy - 3.2f, fg, labelFr.CENTREX, labelFr.CENTREY);
            labelFr.resetScale();

            FontRendererExtension<?> bodyFr = HudStyle.body();
            int subFg = M3.mix(M3.onSurfaceVariant(), M3.onPrimaryContainer(), press);
            bodyFr.setScale(0.76f);
            bodyFr.drawString(subLabel, cx, cy + 3.8f, subFg, bodyFr.CENTREX, bodyFr.CENTREY);
            bodyFr.resetScale();
        }
        HudStyle.resetGl();
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

    private void renderScoreboard(FontRendererExtension<?> fr, ScaledResolution sr, int color) {
        ScoreObjective objective = sidebarObjective();
        if (objective == null)
            return;

        FontRendererExtension<?> labelFr = HudStyle.label();
        Scoreboard board = objective.getScoreboard();
        List<Score> scores = new ArrayList<>();
        for (Score score : board.getSortedScores(objective))
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#"))
                scores.add(score);
        if (scores.size() > 15)
            scores = new ArrayList<>(scores.subList(scores.size() - 15, scores.size()));

        float pad = 6f;
        float rowH = 12f;
        String title = objective.getDisplayName();
        List<String> names = new ArrayList<>(scores.size());
        List<String> points = new ArrayList<>(scores.size());
        float w = labelFr.getWidth(title) + 12f;
        for (Score score : scores) {
            ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String value = String.valueOf(score.getScorePoints());
            names.add(name);
            points.add(value);
            w = Math.max(w, fr.getWidth(name) + 10f + labelFr.getWidth(value));
        }
        w = HudStyle.snap(w + pad * 2f);
        float titleH = 18f;
        float bodyPad = 3f;
        float h = HudStyle.snap(titleH + bodyPad + names.size() * rowH + bodyPad);
        scoreboard.width = (int) Math.ceil(w);
        scoreboard.height = (int) Math.ceil(h);

        float x1 = HudStyle.snap(sr.getScaledWidth() + scoreboard.x - scoreboard.width);
        float y1 = HudStyle.snap(scoreboard.y);
        HudStyle.card(x1, y1, x1 + w, y1 + h, HudStyle.RADIUS_CARD, surfaceAlpha());

        float headerCy = y1 + titleH / 2f;
        labelFr.drawString(title, x1 + w / 2f, headerCy, M3.onSurface(), labelFr.CENTREX, labelFr.CENTREY);
        HudStyle.divider(x1 + pad, x1 + w - pad, y1 + titleH);

        float rowTop = y1 + titleH + bodyPad;
        for (int i = 0; i < names.size(); i++) {
            float rowCy = rowTop + rowH / 2f;
            fr.drawString(names.get(i), x1 + pad, rowCy, M3.onSurface(), fr.CENTREY);
            String pt = points.get(i);
            labelFr.drawString(pt, x1 + w - pad - labelFr.getWidth(pt), rowCy, M3.primary(), labelFr.CENTREY);
            rowTop += rowH;
        }
        HudStyle.resetGl();
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
