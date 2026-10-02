package arsenic.module.impl.visual;

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
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Options;

import java.util.function.BinaryOperator;

/**
 * The in-game overlay.
 * <p>
 * Each arraylist row draws its own backdrop, sized to that row's text, with an accent edge on the
 * outer side. Every row carries its module's live state next to the name - "LagRange 240ms",
 * "DoubleHit witholding" - sourced from {@link Module#getHudInfo()}.
 * <p>
 * Rows animate. Entries are kept in {@link #entries} across frames so a module that is toggled off
 * can slide out instead of vanishing, and rows ease toward their target Y so the list reflows
 * smoothly when something in the middle disappears. That state is the reason the render is split
 * into "measure and advance" ({@link #buildEntries}) and "draw" ({@link #drawArrayList}): the bloom
 * and blur passes re-draw the exact same geometry and must not advance the animation a second time.
 */
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

    /** Row geometry, in HUD pixels. Everything else is derived from these two. */
    private static final float ROW_HEIGHT = 13f;
    private static final float PANEL_PAD_X = 6f;
    private static final float ACCENT_WIDTH = 1.6f;
    private static final float RADIUS = 3.5f;

    /** Width changes smaller than this never reorder the list at all. */
    private static final float SORT_TOLERANCE = 2f;
    /** How long a new width must hold before it is allowed to reorder the list. */
    private static final long SORT_SETTLE_MS = 500L;

    /** Live row state, keyed by module so a toggle can animate rather than pop. */
    private final Map<Module, Entry> entries = new LinkedHashMap<>();
    /** The ordered, laid-out snapshot the draw passes consume. */
    private List<Entry> visible = new ArrayList<>();
    private long lastFrameMs = System.currentTimeMillis();

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (editPosition.getValue() && !(mc.gui.screen() instanceof HudEditorScreen)) {
            mc.gui.setScreen(new HudEditorScreen());
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
        if (showCoords.getValue() && mc.player != null)
            renderCoords(fr, accent);
        if (showKeybinds.getValue())
            renderKeybinds(fr, accent);

        buildEntries(fr);
        drawArrayList(fr, sr, Pass.NORMAL);
    };

    /**
     * Bloom pass. It re-draws the arraylist at full opacity into the bloom buffer so the panel and
     * accents glow; it deliberately reuses the already-measured {@link #visible} snapshot instead of
     * rebuilding, because rebuilding here would double-step every animation.
     */
    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (!shouldRender())
            return;
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null || visible.isEmpty())
            return;
        drawArrayList(fr, new ScaledResolution(mc), Pass.BLOOM);
    };

    /** Blur pass: solid silhouettes only, which is all the blur mask needs. */
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

    // ---------------------------------------------------------------
    //  Arraylist
    // ---------------------------------------------------------------

    /**
     * Reconciles the live module list against the animated {@link #entries} map and lays the rows
     * out. Called exactly once per frame, from the normal render pass.
     */
    private void buildEntries(FontRendererExtension<?> fr) {
        long now = System.currentTimeMillis();
        // Delta-time driven so the animation runs at the same speed regardless of framerate.
        float dt = Math.min(120f, now - lastFrameMs) / 1000f;
        lastFrameMs = now;

        Set<Module> enabled = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                .stream().filter(m -> !m.isHidden()).collect(Collectors.toSet());

        for (Module m : enabled)
            entries.computeIfAbsent(m, Entry::new);

        // Measure first: sorting by width needs the width the row will actually be drawn at.
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

        // Advance each row toward its target opacity and slot.
        float y = 0;
        visible = new ArrayList<>(ordered.size());
        for (Entry entry : ordered) {
            float target = entry.alive ? 1f : 0f;
            entry.opacity += Math.signum(target - entry.opacity) * dt * 5.5f;
            entry.opacity = Math.max(0f, Math.min(1f, entry.opacity));

            if (entry.y < 0)
                entry.y = y; // first appearance: start in place rather than sliding from the top
            entry.y += (y - entry.y) * Math.min(1f, dt * 14f);

            if (entry.opacity > 0.004f) {
                visible.add(entry);
                y += ROW_HEIGHT * entry.opacity;
            }
        }

        // A row is only forgotten once it is both disabled and fully faded, so re-enabling a module
        // mid-fade picks the same row back up instead of restarting it from nothing.
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

            // Rows slide in from the right as they fade, which reads as the list making room.
            float slide = (1f - entry.opacity) * 10f;
            float rowRight = right + slide;
            // Each row's backdrop is exactly as wide as that row's own text. A shared panel sized
            // to the longest name leaves a dead slab of background behind every shorter one.
            float rowLeft = rowRight - (entry.width + PANEL_PAD_X * 2f);
            float alpha = pass == Pass.BLOOM ? 1f : entry.opacity;

            if (pass == Pass.BLUR) {
                // The blur mask only needs coverage, so a plain solid row is both correct and the
                // cheapest thing to submit.
                DrawUtils.drawRoundedRect(rowLeft, rowY, rowRight, rowY + rowH, RADIUS, 0xFFFFFFFF);
                continue;
            }

            // The bloom pass deliberately skips the backdrop: it is near-black, so drawing it into
            // the bloom buffer would only mask the glow coming off the text and accent on top.
            if (pass == Pass.NORMAL
                    && (bg == ArrayListBackground.PANEL || bg == ArrayListBackground.RECTANGLE)) {
                DrawUtils.drawRoundedRect(rowLeft, rowY, rowRight, rowY + rowH, RADIUS,
                        UITheme.alpha(0x000000, (int) (panelAlpha * entry.opacity)));
            }

            // Accent edge on the outer side of the row.
            if (bg != ArrayListBackground.NONE)
                DrawUtils.drawRoundedRect(rowRight - ACCENT_WIDTH, rowY, rowRight, rowY + rowH,
                        ACCENT_WIDTH / 2f, UITheme.alpha(color, alpha));

            // Info first, right-aligned, then the name to its left - so the name column stays put
            // while a changing info string grows and shrinks.
            float cursor = rowRight - PANEL_PAD_X;
            if (entry.info != null) {
                float infoW = fr.getWidth(entry.info);
                fr.drawStringWithShadow(entry.info, cursor - infoW, rowMid - fr.getHeight(entry.info) / 2f,
                        UITheme.alpha(pass == Pass.BLOOM ? color : ThemeManager.getTextMuted(), alpha));
                cursor -= infoW + fr.getWidth(" ");
            }
            fr.drawStringWithShadow(entry.name, cursor - entry.nameWidth,
                    rowMid - fr.getHeight(entry.name) / 2f, UITheme.alpha(color, alpha));
        }
    }

    // ---------------------------------------------------------------
    //  Other elements
    // ---------------------------------------------------------------

    private void renderWatermark(FontRendererExtension<?> fr, int color) {
        String suffix = null;
        switch (watermarkMode.getValue()) {
            case FPS:
                suffix = Minecraft.getDebugFPS() + " fps";
                break;
            case COORDS:
                if (mc.player != null)
                    suffix = String.format("%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
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

        chipBackground(watermarkX, watermarkY, watermarkX + w, watermarkY + h);
        DrawUtils.drawRoundedRect(watermarkX, watermarkY + pad * 0.6f,
                watermarkX + 1.6f, watermarkY + h - pad * 0.6f, 0.8f, color);

        fr.drawStringWithShadow(title, watermarkX + pad, mid - fr.getHeight(title) / 2f, color);
        if (suffix != null)
            fr.drawStringWithShadow(suffix, watermarkX + pad + fr.getWidth(title + "  "),
                    mid - fr.getHeight(suffix) / 2f, ThemeManager.getTextMuted());
    }

    private void renderCoords(FontRendererExtension<?> fr, int color) {
        String text = String.format("%.0f  %.0f  %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float pad = 4f;
        float h = fr.getHeight(text) + pad * 1.5f;
        float w = fr.getWidth("XYZ  " + text) + pad * 2f;
        chipBackground(coordsX, coordsY, coordsX + w, coordsY + h);
        fr.drawStringWithShadow("XYZ", coordsX + pad, coordsY + pad * 0.75f, ThemeManager.getTextMuted());
        fr.drawStringWithShadow(text, coordsX + pad + fr.getWidth("XYZ  "), coordsY + pad * 0.75f, color);
    }

    private void renderKeybinds(FontRendererExtension<?> fr, int color) {
        List<Module> binds = Arsenic.getArsenic().getModuleManager().getModules().stream()
                .filter(m -> m.getKeybind() != 0)
                .sorted(Comparator.comparing(Module::getName))
                .collect(Collectors.toList());
        if (binds.isEmpty())
            return;

        float pad = 4f;
        float rowH = 11f;
        float w = 0;
        for (Module m : binds)
            w = Math.max(w, fr.getWidth(m.getName() + "   " + Options.getKeyDisplayString(m.getKeybind())));
        w += pad * 2f;
        float h = binds.size() * rowH + pad * 1.5f;

        chipBackground(keybindsX, keybindsY, keybindsX + w, keybindsY + h);

        float y = keybindsY + pad * 0.75f;
        for (Module m : binds) {
            String key = Options.getKeyDisplayString(m.getKeybind());
            fr.drawStringWithShadow(m.getName(), keybindsX + pad, y, m.isEnabled() ? color : ThemeManager.getTextMuted());
            fr.drawStringWithShadow(key, keybindsX + w - pad - fr.getWidth(key), y, ThemeManager.getTextMuted());
            y += rowH;
        }
    }

    /** Shared translucent backing used by every non-arraylist element, so they read as one family. */
    private void chipBackground(float x1, float y1, float x2, float y2) {
        int a = (int) (255 * (backgroundOpacity.getValue().getInput() / 100.0));
        DrawUtils.drawShadow(x1, y1, x2, y2, RADIUS, 3f, (int) (70 * (a / 255f)), 3);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, RADIUS, UITheme.alpha(0x000000, a));
    }

    // ---------------------------------------------------------------
    //  Persistence
    // ---------------------------------------------------------------

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

    /** Restores every draggable element to its default corner. Used by the editor's Reset. */
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

    /** One animated row of the arraylist. */
    private static class Entry {
        final String name;
        String info;
        /** Width of the name alone. */
        float nameWidth;
        /** Full drawn width including the suffix - the size of this row's backdrop. */
        float width;
        /**
         * The width the list is actually ordered by: {@link #width}, but only after it has held
         * still. See {@link #settleSortWidth(long)}.
         */
        float sortWidth = -1f;
        /** The width we are currently waiting on, and when we first saw it. */
        private float candidateWidth;
        private long candidateSince;
        /** 0..1 fade, also drives the row's contribution to the panel height. */
        float opacity;
        /** Eased Y offset from the top of the panel; negative means "not placed yet". */
        float y = -1f;
        boolean alive;

        Entry(Module module) {
            this.name = module.getName();
        }

        /**
         * Decides when a width change is allowed to reorder the list.
         * <p>
         * Sorting straight off {@link #width} means every tick of a live suffix - LagRange counting
         * "240ms" down to "180ms", Clicker's cps, Blink's tick counter - reshuffles the list. Sorting
         * off the name alone is stable but then a row with a long suffix visibly breaks the length
         * ordering. So the list sorts by the full width, but a new width only becomes the sort key
         * once it has stayed put for {@link #SORT_SETTLE_MS}, and changes smaller than
         * {@link #SORT_TOLERANCE} never count at all. A suffix that flickers between two lengths
         * therefore never reorders anything, while a real, lasting change does - just half a second
         * late, which nobody notices.
         */
        void settleSortWidth(long now) {
            if (sortWidth < 0f) {            // first frame: adopt immediately
                sortWidth = width;
                candidateWidth = width;
                candidateSince = now;
                return;
            }

            if (Math.abs(width - sortWidth) <= SORT_TOLERANCE) {
                candidateWidth = sortWidth;  // close enough to what we already use - nothing to do
                candidateSince = now;
                return;
            }

            if (Math.abs(width - candidateWidth) > SORT_TOLERANCE) {
                candidateWidth = width;      // a different width again: restart the clock
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

    /** RECTANGLE is kept so configs written by older builds still resolve; it renders as PANEL. */
    public enum ArrayListBackground {
        PANEL, RECTANGLE, BAR, NONE
    }
}
