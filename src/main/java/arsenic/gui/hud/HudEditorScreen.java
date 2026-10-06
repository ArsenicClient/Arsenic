package arsenic.gui.hud;

import arsenic.utils.java.MathUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

public class HudEditorScreen extends GuiScreen {

    private static final int SNAP_DISTANCE = 5;
    private static final int GRID = 0;

    private final List<Element> elements = new ArrayList<>();
    private Element dragging;
    private int dragOffsetX, dragOffsetY;
    private ScaledResolution sr;

    private final List<Float> activeGuidesX = new ArrayList<>();
    private final List<Float> activeGuidesY = new ArrayList<>();

    private float saveX1, saveY1, saveX2, saveY2;
    private float resetX1, resetY1, resetX2, resetY2;

    private static final class Element {
        final HudElement h;
        final String label;

        Element(Module owner, HudElement h) {
            this.h = h;
            this.label = h.label;
        }

        int width() { return h.width; }

        int height() { return h.height; }

        float x1(ScaledResolution sr) {
            return h.rightAnchored ? sr.getScaledWidth() + h.x - h.width : h.x;
        }

        float y1() { return h.y; }

        void moveTo(ScaledResolution sr, float x, float y) {
            h.x = Math.round(h.rightAnchored ? x + h.width - sr.getScaledWidth() : x);
            h.y = Math.round(y);
        }
    }

    @Override
    public void initGui() {
        sr = new ScaledResolution(mc);
        elements.clear();
        Arsenic.getArsenic().getModuleManager().getModules().stream()
                .sorted(Comparator.comparing(Module::getName))
                .forEach(m -> m.getHudElements().forEach(h -> elements.add(new Element(m, h))));

        snapOnScreenElements();
    }


    private void snapOnScreenElements() {
        for (Element e : elements) {
            float x1 = e.x1(sr), y1 = e.y1();
            float clampedX = Math.max(-e.width() + 12, Math.min(width - 12, x1));
            float clampedY = MathUtils.clamp(y1, 0, height - 12);
            if (clampedX != x1 || clampedY != y1)
                e.moveTo(sr, clampedX, clampedY);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        drawRect(0, 0, width, height, UITheme.alpha(0x000000, 130));

        DrawUtils.drawRect(0, height / 2f, width, height / 2f + 0.75f, UITheme.alpha(ThemeManager.getWhite(), 26));
        DrawUtils.drawRect(width / 2f, 0, width / 2f + 0.75f, height, UITheme.alpha(ThemeManager.getWhite(), 26));

        if (fr != null) {
            drawHeader(fr);
            for (Element e : elements)
                drawElement(fr, e, mouseX, mouseY);
            drawGuides();
            drawButtons(fr, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
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
        float x1 = e.x1(sr), y1 = e.y1();
        float x2 = x1 + e.width(), y2 = y1 + e.height();
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
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0)
            return;

        if (inside(mouseX, mouseY, saveX1, saveY1, saveX2, saveY2)) {
            Arsenic.getArsenic().getConfigManager().saveConfig();
            mc.displayGuiScreen(null);
            return;
        }
        if (inside(mouseX, mouseY, resetX1, resetY1, resetX2, resetY2)) {
            elements.forEach(e -> e.h.reset());
            return;
        }

        for (int i = elements.size() - 1; i >= 0; i--) {
            Element e = elements.get(i);
            float x1 = e.x1(sr), y1 = e.y1();
            if (inside(mouseX, mouseY, x1, y1, x1 + e.width(), y1 + e.height())) {
                dragging = e;
                dragOffsetX = (int) (mouseX - x1);
                dragOffsetY = (int) (mouseY - y1);
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragging == null || clickedMouseButton != 0)
            return;

        float targetX = mouseX - dragOffsetX;
        float targetY = mouseY - dragOffsetY;

        activeGuidesX.clear();
        activeGuidesY.clear();

        targetX = snapAxis(targetX, dragging.width(), candidateXs(dragging), activeGuidesX);
        targetY = snapAxis(targetY, dragging.height(), candidateYs(dragging), activeGuidesY);

        if (GRID > 1) {
            targetX = Math.round(targetX / GRID) * (float) GRID;
            targetY = Math.round(targetY / GRID) * (float) GRID;
        }

        targetX = Math.max(-dragging.width() + 12, Math.min(width - 12, targetX));
        targetY = MathUtils.clamp(targetY, 0, height - 12);

        dragging.moveTo(sr, targetX, targetY);
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
            float x1 = e.x1(sr);
            list.add(x1);
            list.add(x1 + e.width() / 2f);
            list.add(x1 + e.width());
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
            list.add(y1 + e.height() / 2f);
            list.add(y1 + e.height());
        }
        return list;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragging = null;
        activeGuidesX.clear();
        activeGuidesY.clear();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            Arsenic.getArsenic().getConfigManager().saveConfig();
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
