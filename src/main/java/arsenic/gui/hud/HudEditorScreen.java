package arsenic.gui.hud;

import arsenic.utils.java.MathUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.impl.visual.HUD;
import arsenic.module.impl.visual.Radar;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.screens.Screen;
import arsenic.utils.io.Keys;
import arsenic.utils.render.RenderContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {

    private static final int SNAP_DISTANCE = 5;
    private static final int GRID = 0;

    private final List<Element> elements = new ArrayList<>();
    private Element dragging;
    private int dragOffsetX, dragOffsetY;

    private final List<Float> activeGuidesX = new ArrayList<>();
    private final List<Float> activeGuidesY = new ArrayList<>();

    private float saveX1, saveY1, saveX2, saveY2;
    private float resetX1, resetY1, resetX2, resetY2;

    private static final class Element {
        final String label;
        final IntSupplier getX, getY;
        final IntConsumer setX, setY;
        int width, height;
        final IntSupplier sizeW, sizeH;
        final boolean rightAnchored;

        Element(String label, IntSupplier getX, IntConsumer setX, IntSupplier getY, IntConsumer setY,
                int width, int height, boolean rightAnchored) {
            this(label, getX, setX, getY, setY, width, height, rightAnchored, null, null);
        }

        Element(String label, IntSupplier getX, IntConsumer setX, IntSupplier getY, IntConsumer setY,
                int width, int height, boolean rightAnchored, IntSupplier sizeW, IntSupplier sizeH) {
            this.label = label;
            this.getX = getX;
            this.setX = setX;
            this.getY = getY;
            this.setY = setY;
            this.width = width;
            this.height = height;
            this.rightAnchored = rightAnchored;
            this.sizeW = sizeW;
            this.sizeH = sizeH;
        }

        float x1(int screenWidth) {
            return rightAnchored ? screenWidth + getX.getAsInt() - width : getX.getAsInt();
        }

        float y1() { return getY.getAsInt(); }

        void refresh() {
            if (sizeW != null)
                width = sizeW.getAsInt();
            if (sizeH != null)
                height = sizeH.getAsInt();
        }

        void moveTo(int screenWidth, float x, float y) {
            setX.accept(Math.round(rightAnchored ? x + width - screenWidth : x));
            setY.accept(Math.round(y));
        }
    }

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        elements.clear();
        elements.add(new Element("Module List",
                () -> HUD.arrayListX, v -> HUD.arrayListX = v,
                () -> HUD.arrayListY, v -> HUD.arrayListY = v, 92, 70, true));
        elements.add(new Element("Watermark",
                () -> HUD.watermarkX, v -> HUD.watermarkX = v,
                () -> HUD.watermarkY, v -> HUD.watermarkY = v, 78, 16, false,
                () -> HUD.watermarkW, () -> HUD.watermarkH));
        elements.add(new Element("TargetHUD",
                () -> HUD.targetHUDX, v -> HUD.targetHUDX = v,
                () -> HUD.targetHUDY, v -> HUD.targetHUDY = v, 152, 52, false));
        elements.add(new Element("Coordinates",
                () -> HUD.coordsX, v -> HUD.coordsX = v,
                () -> HUD.coordsY, v -> HUD.coordsY = v, 96, 16, false,
                () -> HUD.coordsW, () -> HUD.coordsH));
        elements.add(new Element("Keybinds",
                () -> HUD.keybindsX, v -> HUD.keybindsX = v,
                () -> HUD.keybindsY, v -> HUD.keybindsY = v, 104, 46, false,
                () -> HUD.keybindsW, () -> HUD.keybindsH));
        elements.add(new Element("Radar",
                () -> Radar.radarX, v -> Radar.radarX = v,
                () -> Radar.radarY, v -> Radar.radarY = v, 124, 124, false));

        elements.forEach(Element::refresh);
        snapOnScreenElements();
    }

    private void snapOnScreenElements() {
        for (Element e : elements) {
            float x1 = e.x1(width), y1 = e.y1();
            float clampedX = Math.max(-e.width + 12, Math.min(width - 12, x1));
            float clampedY = MathUtils.clamp(y1, 0, height - 12);
            if (clampedX != x1 || clampedY != y1)
                e.moveTo(width, clampedX, clampedY);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        try (RenderContext ignored = RenderContext.begin(graphics)) {
            draw(mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
    }

    private void draw(int mouseX, int mouseY) {
        elements.forEach(Element::refresh);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        DrawUtils.drawRect(0, 0, width, height, UITheme.alpha(0x000000, 130));

        DrawUtils.drawRect(0, height / 2f, width, height / 2f + 0.75f, UITheme.alpha(ThemeManager.getWhite(), 26));
        DrawUtils.drawRect(width / 2f, 0, width / 2f + 0.75f, height, UITheme.alpha(ThemeManager.getWhite(), 26));

        if (fr != null) {
            drawHeader(fr);
            for (Element e : elements)
                drawElement(fr, e, mouseX, mouseY);
            drawGuides();
            drawButtons(fr, mouseX, mouseY);
        }
    }

    private void drawHeader(FontRendererExtension<?> fr) {
        String title = "HUD Editor";
        String hint = "Drag to move  ·  Esc to finish";
        float pad = 8f;
        float w = Math.max(fr.getWidth(title), fr.getWidth(hint)) + pad * 2f;
        float h = fr.getHeight(title) + fr.getHeight(hint) + pad * 1.6f;
        float x = width / 2f - w / 2f;

        UITheme.surface(x, 10, x + w, 10 + h, 6f, UITheme.alpha(0x000000, 170), UITheme.Elevation.FLOATING);
        fr.drawString(title, width / 2f, 10 + pad * 0.6f, ThemeManager.getWhite(), fr.CENTREX);
        fr.drawString(hint, width / 2f, 10 + pad * 0.6f + fr.getHeight(title) + 2,
                ThemeManager.getTextMuted(), fr.CENTREX);
    }

    private void drawElement(FontRendererExtension<?> fr, Element e, int mouseX, int mouseY) {
        float x1 = e.x1(width), y1 = e.y1();
        float x2 = x1 + e.width, y2 = y1 + e.height;
        boolean active = dragging == e;
        boolean hovered = MathUtils.inside(mouseX, mouseY, x1, y1, x2, y2);

        int fill = UITheme.alpha(active ? UITheme.accent() : 0x000000, active ? 60 : (hovered ? 120 : 80));
        UITheme.surface(x1, y1, x2, y2, 5f, fill,
                active ? UITheme.Elevation.FLOATING : UITheme.Elevation.RAISED, active ? 1f : 0.5f);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, 5f, active ? 1.6f : 1f,
                UITheme.alpha(UITheme.accent(), active ? 255 : (hovered ? 200 : 110)));

        fr.drawString(e.label, (x1 + x2) / 2f, (y1 + y2) / 2f,
                active || hovered ? ThemeManager.getWhite() : UITheme.alpha(ThemeManager.getWhite(), 200),
                fr.CENTREX, fr.CENTREY);

        if (active)
            fr.drawString(Math.round(x1) + ", " + Math.round(y1), (x1 + x2) / 2f, y2 + 7,
                    UITheme.alpha(UITheme.accent(), 230), fr.CENTREX);
    }

    private void drawGuides() {
        for (float gx : activeGuidesX)
            DrawUtils.drawRect(gx, 0, gx + 0.75f, height, UITheme.alpha(UITheme.accent(), 190));
        for (float gy : activeGuidesY)
            DrawUtils.drawRect(0, gy, width, gy + 0.75f, UITheme.alpha(UITheme.accent(), 190));
    }

    private void drawButtons(FontRendererExtension<?> fr, int mouseX, int mouseY) {
        float bw = 96, bh = 22, gap = 8;
        float y = height - bh - 14;
        saveX1 = width / 2f - bw - gap / 2f;
        saveX2 = saveX1 + bw;
        saveY1 = y;
        saveY2 = y + bh;
        resetX1 = width / 2f + gap / 2f;
        resetX2 = resetX1 + bw;
        resetY1 = y;
        resetY2 = y + bh;

        boolean saveHover = inside(mouseX, mouseY, saveX1, saveY1, saveX2, saveY2);
        boolean resetHover = inside(mouseX, mouseY, resetX1, resetY1, resetX2, resetY2);

        DrawUtils.drawGradientRoundedRect(saveX1, saveY1, saveX2, saveY2, bh / 2f,
                UITheme.accent(), UITheme.accent(), UITheme.accentAlt(), UITheme.accentAlt());
        DrawUtils.drawRoundedOutline(saveX1, saveY1, saveX2, saveY2, bh / 2f, 1f,
                UITheme.alpha(ThemeManager.getWhite(), saveHover ? 140 : 60));
        fr.drawString("Save & Close", (saveX1 + saveX2) / 2f, (saveY1 + saveY2) / 2f,
                ThemeManager.getWhite(), fr.CENTREX, fr.CENTREY);

        UITheme.surface(resetX1, resetY1, resetX2, resetY2, bh / 2f,
                UITheme.alpha(0x000000, resetHover ? 190 : 150), UITheme.Elevation.RAISED, 0.6f);
        DrawUtils.drawRoundedOutline(resetX1, resetY1, resetX2, resetY2, bh / 2f, 1f,
                UITheme.alpha(ThemeManager.getWhite(), resetHover ? 150 : 70));
        fr.drawString("Reset", (resetX1 + resetX2) / 2f, (resetY1 + resetY2) / 2f,
                resetHover ? ThemeManager.getWhite() : UITheme.alpha(ThemeManager.getWhite(), 200),
                fr.CENTREX, fr.CENTREY);
    }

    private static boolean inside(float mx, float my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        elements.forEach(Element::refresh);
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (Keys.fromSdlButton(event.button()) != 0)
            return false;

        if (inside(mouseX, mouseY, saveX1, saveY1, saveX2, saveY2)) {
            Arsenic.getArsenic().getConfigManager().saveConfig();
            minecraft.gui.setScreen(null);
            return true;
        }
        if (inside(mouseX, mouseY, resetX1, resetY1, resetX2, resetY2)) {
            HUD.resetPositions();
            return true;
        }

        for (int i = elements.size() - 1; i >= 0; i--) {
            Element e = elements.get(i);
            float x1 = e.x1(width), y1 = e.y1();
            if (inside(mouseX, mouseY, x1, y1, x1 + e.width, y1 + e.height)) {
                dragging = e;
                dragOffsetX = (int) (mouseX - x1);
                dragOffsetY = (int) (mouseY - y1);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        elements.forEach(Element::refresh);
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (dragging == null || Keys.fromSdlButton(event.button()) != 0)
            return false;

        float targetX = mouseX - dragOffsetX;
        float targetY = mouseY - dragOffsetY;

        activeGuidesX.clear();
        activeGuidesY.clear();

        targetX = snapAxis(targetX, dragging.width, candidateXs(dragging), activeGuidesX);
        targetY = snapAxis(targetY, dragging.height, candidateYs(dragging), activeGuidesY);

        if (GRID > 1) {
            targetX = Math.round(targetX / GRID) * (float) GRID;
            targetY = Math.round(targetY / GRID) * (float) GRID;
        }

        targetX = Math.max(-dragging.width + 12, Math.min(width - 12, targetX));
        targetY = MathUtils.clamp(targetY, 0, height - 12);

        dragging.moveTo(width, targetX, targetY);
        return true;
    }

    private float snapAxis(float start, float size, List<Float> candidates, List<Float> guidesOut) {
        float best = start;
        float bestDelta = SNAP_DISTANCE + 1;
        float guide = Float.NaN;

        for (float candidate : candidates) {
            float[] anchors = {start, start + size / 2f, start + size};
            float[] offsets = {0, size / 2f, size};
            for (int i = 0; i < anchors.length; i++) {
                float delta = Math.abs(candidate - anchors[i]);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = candidate - offsets[i];
                    guide = candidate;
                }
            }
        }

        if (!Float.isNaN(guide) && bestDelta <= SNAP_DISTANCE)
            guidesOut.add(guide);
        return bestDelta <= SNAP_DISTANCE ? best : start;
    }

    private List<Float> candidateXs(Element moving) {
        List<Float> list = new ArrayList<>();
        list.add(0f);
        list.add(width / 2f);
        list.add((float) width);
        for (Element e : elements) {
            if (e == moving)
                continue;
            float x1 = e.x1(width);
            list.add(x1);
            list.add(x1 + e.width / 2f);
            list.add(x1 + e.width);
        }
        return list;
    }

    private List<Float> candidateYs(Element moving) {
        List<Float> list = new ArrayList<>();
        list.add(0f);
        list.add(height / 2f);
        list.add((float) height);
        for (Element e : elements) {
            if (e == moving)
                continue;
            float y1 = e.y1();
            list.add(y1);
            list.add(y1 + e.height / 2f);
            list.add(y1 + e.height);
        }
        return list;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        activeGuidesX.clear();
        activeGuidesY.clear();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            Arsenic.getArsenic().getConfigManager().saveConfig();
            minecraft.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
