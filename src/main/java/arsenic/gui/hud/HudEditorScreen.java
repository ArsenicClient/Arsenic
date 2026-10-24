package arsenic.gui.hud;

import arsenic.utils.java.MathUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import arsenic.gui.click.UITheme;
import arsenic.gui.design.Backdrop;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
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
    private Element resizing;
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

        int width() { return h.scaledWidth(); }

        int height() { return h.scaledHeight(); }

        float x1(ScaledResolution sr) {
            return h.rightAnchored ? sr.getScaledWidth() + h.x - width() : h.x;
        }

        float y1() { return h.y; }

        void moveTo(ScaledResolution sr, float x, float y) {
            h.x = Math.round(h.rightAnchored ? x + width() - sr.getScaledWidth() : x);
            h.y = Math.round(y);
        }
    }

    @Override
    public void initGui() {
        sr = new ScaledResolution(mc);
        elements.clear();
        Arsenic.getArsenic().getModuleManager().getModules().stream()
                .sorted(Comparator.comparing(Module::getName))
                .forEach(m -> m.getHudElements().stream()
                        .filter(h -> m.isEnabled() && h.active.getAsBoolean())
                        .forEach(h -> elements.add(new Element(m, h))));

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

        Backdrop.draw(width, height);

        DrawUtils.drawRect(0, height / 2f, width, height / 2f + 0.75f, M3.alpha(M3.outlineVariant(), 90));
        DrawUtils.drawRect(width / 2f, 0, width / 2f + 0.75f, height, M3.alpha(M3.outlineVariant(), 90));

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
        String hint = "Drag to move  ·  Corner to resize  ·  Esc to finish";
        float pad = 8f;
        float w = Math.max(fr.getWidth(title), fr.getWidth(hint)) + pad * 2f;
        float h = fr.getHeight(title) + fr.getHeight(hint) + pad * 1.6f;
        float x = width / 2f - w / 2f;

        M3Components.card(x, 10, x + w, 10 + h, 16f, M3.alpha(M3.surfaceContainerHigh(), 245));
        fr.drawString(title, width / 2f, 10 + pad * 0.6f, M3.onSurface(), fr.CENTREX);
        fr.drawString(hint, width / 2f, 10 + pad * 0.6f + fr.getHeight(title) + 2,
                M3.onSurfaceVariant(), fr.CENTREX);
    }

    private void drawElement(FontRendererExtension<?> fr, Element e, int mouseX, int mouseY) {
        float x1 = e.x1(sr), y1 = e.y1();
        float x2 = x1 + e.width(), y2 = y1 + e.height();
        boolean active = dragging == e || resizing == e;
        boolean hovered = MathUtils.inside(mouseX, mouseY, x1, y1, x2, y2);

        int fill = active ? M3.alpha(M3.primaryContainer(), 220)
                : M3.alpha(M3.surfaceContainerHigh(), hovered ? 210 : 165);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, 8f, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, 8f, active ? 1.4f : 1f,
                M3.alpha(active ? M3.primary() : M3.outlineVariant(), active ? 240 : (hovered ? 170 : 100)));

        fr.drawString(e.label, (x1 + x2) / 2f, (y1 + y2) / 2f,
                active ? M3.onPrimaryContainer() : M3.onSurface(),
                fr.CENTREX, fr.CENTREY);

        if (hovered || active) {
            float hx = x2 - 5f, hy = y2 - 5f;
            DrawUtils.drawCircle(hx, hy, 5f, M3.alpha(M3.primary(), 220));
            DrawUtils.drawCircle(hx, hy, 2f, M3.onPrimary());
        }

        if (active)
            fr.drawString(Math.round(x1) + ", " + Math.round(y1) + "  " + Math.round(e.h.scale * 100) + "%",
                    (x1 + x2) / 2f, y2 + 7, M3.primary(), fr.CENTREX);
    }

    private void drawGuides() {
        for (float gx : activeGuidesX)
            DrawUtils.drawRect(gx, 0, gx + 0.75f, height, M3.alpha(M3.primary(), 160));
        for (float gy : activeGuidesY)
            DrawUtils.drawRect(0, gy, width, gy + 0.75f, M3.alpha(M3.primary(), 160));
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

        M3Components.button(fr, "Save & Close", saveX1, saveY1, saveX2, saveY2, saveHover ? 1f : 0f, true);
        M3Components.button(fr, "Reset", resetX1, resetY1, resetX2, resetY2, resetHover ? 1f : 0f, false);
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
            float x2 = x1 + e.width(), y2 = y1 + e.height();
            if (inside(mouseX, mouseY, x2 - 11f, y2 - 11f, x2 + 2f, y2 + 2f)) {
                resizing = e;
                return;
            }
            if (inside(mouseX, mouseY, x1, y1, x2, y2)) {
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
        if (clickedMouseButton != 0)
            return;
        if (resizing != null) {
            Element e = resizing;
            float baseW = Math.max(1f, e.h.width);
            float baseH = Math.max(1f, e.h.height);
            float left = e.h.rightAnchored
                    ? sr.getScaledWidth() + e.h.x - e.h.scaledWidth() : e.h.x;
            float targetW = mouseX - left;
            float targetH = mouseY - e.h.y;
            float wanted = Math.max(targetW / baseW, targetH / baseH);
            e.h.scale = MathUtils.clamp(wanted, 0.5f, 2.5f);
            if (e.h.rightAnchored)
                e.h.x = Math.round(left + e.h.scaledWidth() - sr.getScaledWidth());
            return;
        }
        if (dragging == null)
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
        resizing = null;
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
