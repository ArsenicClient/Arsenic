package arsenic.gui.hud;

import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private float backX1, backY1, backX2, backY2;

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
        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();

        DrawUtils.drawRect(0, 0, width, height, M3.alpha(M3.scrim(), 115));

        float onePx = 1f / Math.max(1f, HudStyle.scaleFactor());
        int centerGridCol = M3.alpha(M3.outlineVariant(), 55);
        DrawUtils.drawRect(0, HudStyle.snap(height / 2f - onePx * 0.5f), width, HudStyle.snap(height / 2f + onePx * 0.5f), centerGridCol);
        DrawUtils.drawRect(HudStyle.snap(width / 2f - onePx * 0.5f), 0, HudStyle.snap(width / 2f + onePx * 0.5f), height, centerGridCol);

        if (bodyFr != null && labelFr != null) {
            for (Element e : elements)
                drawElement(bodyFr, labelFr, e, mouseX, mouseY);
            drawGuides();
            drawHeader(bodyFr, labelFr);
            drawButtons(labelFr, mouseX, mouseY);
        }

        HudStyle.resetGl();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(FontRendererExtension<?> bodyFr, FontRendererExtension<?> labelFr) {
        String title = "HUD Editor";
        String hint = "Drag elements to align  ·  Esc to save";
        float padX = 12f;
        float w = HudStyle.snap(Math.max(labelFr.getWidth(title) + 20f, bodyFr.getWidth(hint)) + padX * 2f);
        float h = 34f;
        float x = HudStyle.snap(width / 2f - w / 2f);
        float y = 10f;

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_CARD, 240);
        float row1Cy = y + 11.5f;
        float row2Cy = y + 22.5f;

        float titleW = labelFr.getWidth(title);
        float iconW = M3Icons.font().getWidth(M3Icons.HUD) * 0.85f;
        float groupW = iconW + 5f + titleW;
        float startX = x + (w - groupW) / 2f;
        M3Icons.drawCentered(M3Icons.HUD, startX + iconW / 2f, row1Cy, M3.primary(), 0.85f);
        labelFr.drawString(title, startX + iconW + 5f, row1Cy, M3.onSurface(), labelFr.CENTREY);

        bodyFr.drawString(hint, x + w / 2f, row2Cy, M3.onSurfaceVariant(), bodyFr.CENTREX, bodyFr.CENTREY);
        HudStyle.resetGl();
    }

    private void drawElement(FontRendererExtension<?> bodyFr, FontRendererExtension<?> labelFr,
                             Element e, int mouseX, int mouseY) {
        float x1 = HudStyle.snap(e.x1(sr));
        float y1 = HudStyle.snap(e.y1());
        float x2 = HudStyle.snap(x1 + e.width());
        float y2 = HudStyle.snap(y1 + e.height());
        boolean active = dragging == e;
        boolean hovered = MathUtils.inside(mouseX, mouseY, x1, y1, x2, y2);

        float r = HudStyle.RADIUS_COMPACT;
        if (active || hovered) {
            int tint = active
                    ? M3.alpha(M3.primaryContainer(), 75)
                    : M3.alpha(M3.surfaceContainerHighest(), 55);
            DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, tint);
        }

        int outlineCol = active
                ? M3.primary()
                : (hovered ? M3.alpha(M3.primary(), 185) : M3.alpha(M3.outlineVariant(), 130));
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, active ? 1.5f : 1f, outlineCol);

        float cx = (x1 + x2) / 2f;
        float cy = (y1 + y2) / 2f;
        if (active || hovered) {
            float pillW = HudStyle.snap(labelFr.getWidth(e.label) + 10f);
            float pillH = 13f;
            int pillBg = active ? M3.primaryContainer() : M3.alpha(M3.surfaceContainerHigh(), 235);
            int pillFg = active ? M3.onPrimaryContainer() : M3.onSurface();
            DrawUtils.drawRoundedRect(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f, pillH / 2f, pillBg);
            DrawUtils.drawRoundedOutline(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f, pillH / 2f, 1f,
                    M3.alpha(M3.primary(), active ? 180 : 90));
            labelFr.drawString(e.label, cx, cy, pillFg, labelFr.CENTREX, labelFr.CENTREY);
        }

        if (active) {
            String coords = Math.round(x1) + ", " + Math.round(y1);
            float cw = HudStyle.snap(bodyFr.getWidth(coords) + 8f);
            float ch = 12f;
            float chipY = y2 + 4f + ch / 2f;
            DrawUtils.drawRoundedRect(cx - cw / 2f, chipY - ch / 2f, cx + cw / 2f, chipY + ch / 2f, ch / 2f,
                    M3.alpha(M3.surfaceContainerHigh(), 235));
            bodyFr.drawString(coords, cx, chipY, M3.primary(), bodyFr.CENTREX, bodyFr.CENTREY);
        }
        HudStyle.resetGl();
    }

    private void drawGuides() {
        float onePx = 1f / Math.max(1f, HudStyle.scaleFactor());
        int guideCol = M3.alpha(M3.primary(), 210);
        for (float gx : activeGuidesX)
            DrawUtils.drawRect(HudStyle.snap(gx - onePx * 0.5f), 0, HudStyle.snap(gx + onePx * 0.5f), height, guideCol);
        for (float gy : activeGuidesY)
            DrawUtils.drawRect(0, HudStyle.snap(gy - onePx * 0.5f), width, HudStyle.snap(gy + onePx * 0.5f), guideCol);
    }

    private void drawButtons(FontRendererExtension<?> labelFr, int mouseX, int mouseY) {
        float bw = 96f, bh = 22f, gap = 8f;
        float totalW = bw * 3f + gap * 2f;
        float startX = HudStyle.snap(width / 2f - totalW / 2f);
        float y = HudStyle.snap(height - bh - 12f);

        backX1 = startX;
        backX2 = backX1 + bw;
        backY1 = y;
        backY2 = y + bh;

        saveX1 = backX2 + gap;
        saveX2 = saveX1 + bw;
        saveY1 = y;
        saveY2 = y + bh;

        resetX1 = saveX2 + gap;
        resetX2 = resetX1 + bw;
        resetY1 = y;
        resetY2 = y + bh;

        boolean backHover = inside(mouseX, mouseY, backX1, backY1, backX2, backY2);
        boolean saveHover = inside(mouseX, mouseY, saveX1, saveY1, saveX2, saveY2);
        boolean resetHover = inside(mouseX, mouseY, resetX1, resetY1, resetX2, resetY2);

        drawPillButton(labelFr, M3Icons.ARROW_BACK, "ClickGUI", backX1, backY1, backX2, backY2, backHover, false);
        drawPillButton(labelFr, M3Icons.CHECK, "Save & Close", saveX1, saveY1, saveX2, saveY2, saveHover, true);
        drawPillButton(labelFr, M3Icons.RELOAD, "Reset All", resetX1, resetY1, resetX2, resetY2, resetHover, false);
    }

    private void drawPillButton(FontRendererExtension<?> labelFr, String glyph, String text,
                                float x1, float y1, float x2, float y2, boolean hovered, boolean primary) {
        float r = (y2 - y1) / 2f;
        int fill, fg, outline;
        if (primary) {
            fill = hovered ? M3.mix(M3.primary(), 0xFFFFFFFF, 0.12f) : M3.primary();
            fg = M3.onPrimary();
            outline = M3.alpha(M3.primary(), 220);
        } else {
            fill = hovered ? M3.surfaceContainerHighest() : M3.alpha(M3.surfaceContainerHigh(), 235);
            fg = hovered ? M3.onSurface() : M3.onSurfaceVariant();
            outline = M3.alpha(M3.outlineVariant(), hovered ? 180 : 120);
        }
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, r, 1f, outline);

        float cy = (y1 + y2) / 2f;
        float iconW = M3Icons.font().getWidth(glyph) * 0.85f;
        float textW = labelFr.getWidth(text);
        float groupW = iconW + 5f + textW;
        float start = x1 + (x2 - x1 - groupW) / 2f;
        M3Icons.drawCentered(glyph, start + iconW / 2f, cy, fg, 0.85f);
        labelFr.drawString(text, start + iconW + 5f, cy, fg, labelFr.CENTREY);
        HudStyle.resetGl();
    }

    private static boolean inside(float mx, float my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0)
            return;

        if (inside(mouseX, mouseY, backX1, backY1, backX2, backY2)) {
            SoundUtils.chordOpen();
            Arsenic.getArsenic().getConfigManager().saveConfig();
            mc.displayGuiScreen(Arsenic.getArsenic().getClickGuiScreen());
            return;
        }
        if (inside(mouseX, mouseY, saveX1, saveY1, saveX2, saveY2)) {
            SoundUtils.chordOpen();
            Arsenic.getArsenic().getConfigManager().saveConfig();
            mc.displayGuiScreen(null);
            return;
        }
        if (inside(mouseX, mouseY, resetX1, resetY1, resetX2, resetY2)) {
            SoundUtils.chordEnum();
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
