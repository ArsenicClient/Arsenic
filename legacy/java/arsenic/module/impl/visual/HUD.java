package arsenic.module.impl.visual;

import arsenic.utils.timer.FrameClock;
import arsenic.utils.java.MathUtils;
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
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.event.impl.EventTick;
import arsenic.gui.click.UITheme;
import arsenic.gui.hud.HudEditorScreen;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;

import java.util.function.BinaryOperator;

@ModuleInfo(name = "HUD", category = ModuleCategory.RENDER, hidden = true)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.RAINBOW);
    public final EnumProperty<WatermarkMode> watermarkMode = new EnumProperty<>("Watermark: ", WatermarkMode.TEXT);
    public final EnumProperty<ArrayListSort> arraylistSort = new EnumProperty<>("Sort: ", ArrayListSort.LENGTH);
    public final EnumProperty<ArrayListBackground> arraylistBackground = new EnumProperty<>("Background: ", ArrayListBackground.PANEL);
    public final BooleanProperty showInfo = new BooleanProperty("Module Info", true);
    public final BooleanProperty showCoords = new BooleanProperty("Show Coords", false);
    public final BooleanProperty showKeybinds = new BooleanProperty("Show Keybinds", false);
    public final DoubleProperty backgroundOpacity = new DoubleProperty("Opacity", new DoubleValue(0, 100, 62, 1));
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);

    public static int arrayListX = 0;
    public static int arrayListY = 0;
    public static int watermarkX = 4;
    public static int watermarkY = 4;
    public static int targetHUDX = 100;
    public static int targetHUDY = 100;
    public static int coordsX = 4;
    public static int coordsY = 60;
    public static int keybindsX = 4;
    public static int keybindsY = 80;

    public static int watermarkW = 78, watermarkH = 16;
    public static int coordsW = 96, coordsH = 16;
    public static int keybindsW = 104, keybindsH = 46;

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
        if (editPosition.getValue() && !(mc.currentScreen instanceof HudEditorScreen)) {
            mc.displayGuiScreen(new HudEditorScreen());
            editPosition.setValue(false);
        }
    };

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
        if (showCoords.getValue() && mc.thePlayer != null)
            renderCoords(fr, accent);
        if (showKeybinds.getValue())
            renderKeybinds(fr, accent);

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
        return mc.currentScreen == null
                || mc.currentScreen instanceof GuiChat
                || mc.currentScreen instanceof HudEditorScreen;
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
        float right = sr.getScaledWidth() + arrayListX;
        float top = arrayListY;
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
                suffix = Minecraft.getDebugFPS() + " fps";
                break;
            case COORDS:
                if (mc.thePlayer != null)
                    suffix = String.format("%.0f, %.0f, %.0f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                break;
            case IP:
                suffix = mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "singleplayer";
                break;
            case TEXT:
            default:
                break;
        }

        String title = "Arsenic";
        float pad = 4f;
        float h = fr.getHeight(title) + pad * 1.5f;
        float w = fr.getWidth(title) + (suffix == null ? 0 : fr.getWidth("  " + suffix)) + pad * 2f;
        float mid = watermarkY + h / 2f;
        watermarkW = (int) Math.ceil(w);
        watermarkH = (int) Math.ceil(h);

        chipBackground(watermarkX, watermarkY, watermarkX + w, watermarkY + h);
        DrawUtils.drawRoundedRect(watermarkX, watermarkY + pad * 0.6f,
                watermarkX + 1.6f, watermarkY + h - pad * 0.6f, 0.8f, color);

        fr.drawStringWithShadow(title, watermarkX + pad, mid, color, fr.CENTREY);
        if (suffix != null)
            fr.drawStringWithShadow(suffix, watermarkX + pad + fr.getWidth(title + "  "),
                    mid, ThemeManager.getTextMuted(), fr.CENTREY);
    }

    private void renderCoords(FontRendererExtension<?> fr, int color) {
        String text = String.format("%.0f  %.0f  %.0f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        float pad = 4f;
        float h = fr.getHeight(text) + pad * 1.5f;
        float w = fr.getWidth("XYZ  " + text) + pad * 2f;
        coordsW = (int) Math.ceil(w);
        coordsH = (int) Math.ceil(h);
        chipBackground(coordsX, coordsY, coordsX + w, coordsY + h);
        float coordsMid = coordsY + h / 2f;
        fr.drawStringWithShadow("XYZ", coordsX + pad, coordsMid, ThemeManager.getTextMuted(), fr.CENTREY);
        fr.drawStringWithShadow(text, coordsX + pad + fr.getWidth("XYZ  "), coordsMid, color, fr.CENTREY);
    }

    private void renderKeybinds(FontRendererExtension<?> fr, int color) {
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
            w = Math.max(w, fr.getWidth(m.getName() + "   " + GameSettings.getKeyDisplayString(m.getKeybind())));
        w += pad * 2f;
        float h = binds.size() * rowH + pad * 1.5f;
        keybindsW = (int) Math.ceil(w);
        keybindsH = (int) Math.ceil(h);

        chipBackground(keybindsX, keybindsY, keybindsX + w, keybindsY + h);

        float y = keybindsY + pad * 0.75f + rowH / 2f;
        for (Module m : binds) {
            String key = GameSettings.getKeyDisplayString(m.getKeybind());
            fr.drawStringWithShadow(m.getName(), keybindsX + pad, y, m.isEnabled() ? color : ThemeManager.getTextMuted(), fr.CENTREY);
            fr.drawStringWithShadow(key, keybindsX + w - pad - fr.getWidth(key), y, ThemeManager.getTextMuted(), fr.CENTREY);
            y += rowH;
        }
    }

    private void chipBackground(float x1, float y1, float x2, float y2) {
        int a = (int) (255 * (backgroundOpacity.getValue().getInput() / 100.0));
        DrawUtils.drawShadow(x1, y1, x2, y2, RADIUS, 3f, (int) (70 * (a / 255f)), 3);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, RADIUS, UITheme.alpha(0x000000, a));
    }


    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        JsonObject pos = new JsonObject();
        pos.addProperty("watermarkX", watermarkX);
        pos.addProperty("watermarkY", watermarkY);
        pos.addProperty("arrayListX", arrayListX);
        pos.addProperty("arrayListY", arrayListY);
        pos.addProperty("targetHUDX", targetHUDX);
        pos.addProperty("targetHUDY", targetHUDY);
        pos.addProperty("coordsX", coordsX);
        pos.addProperty("coordsY", coordsY);
        pos.addProperty("keybindsX", keybindsX);
        pos.addProperty("keybindsY", keybindsY);
        pos.addProperty("radarX", Radar.radarX);
        pos.addProperty("radarY", Radar.radarY);
        obj.add("positions", pos);

        obj.addProperty("bind", getKeybind());
        obj.addProperty("enabled", isEnabled());
        serializableProperties.forEach(property -> property.addToJson(obj));
        return obj;
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        try {
            JsonObject pos = obj.getAsJsonObject("positions");
            if (pos != null) {
                watermarkX = pos.get("watermarkX").getAsInt();
                watermarkY = pos.get("watermarkY").getAsInt();
                arrayListX = pos.get("arrayListX").getAsInt();
                arrayListY = pos.get("arrayListY").getAsInt();
                targetHUDX = pos.get("targetHUDX").getAsInt();
                targetHUDY = pos.get("targetHUDY").getAsInt();
                coordsX = pos.get("coordsX").getAsInt();
                coordsY = pos.get("coordsY").getAsInt();
                keybindsX = pos.get("keybindsX").getAsInt();
                keybindsY = pos.get("keybindsY").getAsInt();
                Radar.radarX = pos.get("radarX").getAsInt();
                Radar.radarY = pos.get("radarY").getAsInt();
            }
        } catch (NullPointerException | IllegalArgumentException e) {
            Arsenic.getArsenic().getLogger().info("Error loading HUD positions (first launch or update)");
        }

        try {
            setKeybind(obj.get("bind").getAsInt());
            setEnabledSilently(obj.get("enabled").getAsBoolean());
            serializableProperties.forEach(property -> property.loadFromJson(obj.getAsJsonObject(property.getJsonKey())));
        } catch (NullPointerException | IllegalArgumentException e) {
            Arsenic.getArsenic().getLogger().info("Error loading HUD config (first launch or update)");
        }
        postApplyConfig();
    }

    public static void resetPositions() {
        arrayListX = 0;
        arrayListY = 0;
        watermarkX = 4;
        watermarkY = 4;
        targetHUDX = 100;
        targetHUDY = 100;
        coordsX = 4;
        coordsY = 60;
        keybindsX = 4;
        keybindsY = 80;
        Radar.radarX = 4;
        Radar.radarY = 4;
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
