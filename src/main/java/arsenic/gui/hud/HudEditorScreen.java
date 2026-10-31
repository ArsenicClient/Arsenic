package arsenic.gui.hud;

import arsenic.utils.java.MathUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.main.Arsenic;
import arsenic.module.Module;
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

    private float backX1, backY1, backX2, backY2;
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

        float x1(int screenWidth) {
            return h.rightAnchored ? screenWidth + h.x - h.width : h.x;
        }

        float y1() { return h.y; }

        void moveTo(int screenWidth, float x, float y) {
            h.x = Math.round(h.rightAnchored ? x + h.width - screenWidth : x);
            h.y = Math.round(y);
        }
    }

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        elements.clear();
        // every HUD spot of the enabled modules, addons included
        Arsenic.getArsenic().getModuleManager().getModules().stream()
                .sorted(Comparator.comparing(Module::getName))
                .forEach(m -> m.getHudElements().stream()
                        .filter(h -> m.isEnabled() && h.active.getAsBoolean())
                        .forEach(h -> elements.add(new Element(m, h))));

        snapOnScreenElements();
    }

    private void snapOnScreenElements() {
        for (Element e : elements) {
            float x1 = e.x1(width), y1 = e.y1();
            float clampedX = Math.max(-e.width() + 12, Math.min(width - 12, x1));
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
        FontRendererExtension<?> bodyFr = HudStyle.body();
        FontRendererExtension<?> labelFr = HudStyle.label();

        DrawUtils.drawRect(0, 0, width, height, M3.alpha(M3.scrim(), 115));

        DrawUtils.drawRect(0, height / 2f, width, height / 2f + 0.75f, M3.alpha(M3.outlineVariant(), 90));
        DrawUtils.drawRect(width / 2f, 0, width / 2f + 0.75f, height, M3.alpha(M3.outlineVariant(), 90));

        if (bodyFr != null) {
            for (Element e : elements)
                drawElement(bodyFr, labelFr, e, mouseX, mouseY);
            drawGuides();
            drawHeader(bodyFr, labelFr);
            drawButtons(labelFr, mouseX, mouseY);
        }
    }

    private void drawHeader(FontRendererExtension<?> bodyFr, FontRendererExtension<?> labelFr) {
        String title = "HUD Editor";
        String hint = "Drag elements to align  ·  Esc to save";
        float padX = 12f;
        float w = Math.max(labelFr.getWidth(title) + 20f, bodyFr.getWidth(hint)) + padX * 2f;
        float h = 34f;
        float x = width / 2f - w / 2f;
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
    }

    private void drawElement(FontRendererExtension<?> bodyFr, FontRendererExtension<?> labelFr,
                             Element e, int mouseX, int mouseY) {
        float x1 = e.x1(width), y1 = e.y1();
        float x2 = x1 + e.width(), y2 = y1 + e.height();
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
            float pillW = labelFr.getWidth(e.label) + 10f;
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
            float cw = bodyFr.getWidth(coords) + 8f;
            float ch = 12f;
            float chipY = y2 + 4f + ch / 2f;
            DrawUtils.drawRoundedRect(cx - cw / 2f, chipY - ch / 2f, cx + cw / 2f, chipY + ch / 2f, ch / 2f,
                    M3.alpha(M3.surfaceContainerHigh(), 235));
            bodyFr.drawString(coords, cx, chipY, M3.primary(), bodyFr.CENTREX, bodyFr.CENTREY);
        }
    }

    private void drawGuides() {
        int guideCol = M3.alpha(M3.primary(), 210);
        for (float gx : activeGuidesX)
            DrawUtils.drawRect(gx - 0.4f, 0, gx + 0.4f, height, guideCol);
        for (float gy : activeGuidesY)
            DrawUtils.drawRect(0, gy - 0.4f, width, gy + 0.4f, guideCol);
    }

    private void drawButtons(FontRendererExtension<?> labelFr, int mouseX, int mouseY) {
        float bw = 96f, bh = 22f, gap = 8f;
        float totalW = bw * 3f + gap * 2f;
        float startX = width / 2f - totalW / 2f;
        float y = height - bh - 12f;

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
    }

    private static boolean inside(float mx, float my, float x1, float y1, float x2, float y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (Keys.fromSdlButton(event.button()) != 0)
            return false;

        if (inside(mouseX, mouseY, backX1, backY1, backX2, backY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            Arsenic.getArsenic().getConfigManager().saveConfig();
            minecraft.gui.setScreen(Arsenic.getArsenic().getClickGuiScreen());
            return true;
        }
        if (inside(mouseX, mouseY, saveX1, saveY1, saveX2, saveY2)) {
            arsenic.utils.java.SoundUtils.chordOpen();
            Arsenic.getArsenic().getConfigManager().saveConfig();
            minecraft.gui.setScreen(null);
            return true;
        }
        if (inside(mouseX, mouseY, resetX1, resetY1, resetX2, resetY2)) {
            arsenic.utils.java.SoundUtils.chordEnum();
            elements.forEach(e -> e.h.reset());
            return true;
        }

        for (int i = elements.size() - 1; i >= 0; i--) {
            Element e = elements.get(i);
            float x1 = e.x1(width), y1 = e.y1();
            if (inside(mouseX, mouseY, x1, y1, x1 + e.width(), y1 + e.height())) {
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
        int mouseX = (int) event.x(), mouseY = (int) event.y();
        if (dragging == null || Keys.fromSdlButton(event.button()) != 0)
            return false;

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
