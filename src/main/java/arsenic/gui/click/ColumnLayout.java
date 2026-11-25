package arsenic.gui.click;

import arsenic.gui.click.impl.GuiComponent;
import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.gui.design.Hover;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.gui.design.M3Icons;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Meteor-style layout for the click GUI: one floating, draggable panel per category.
 * <ul>
 *     <li>left-drag a header to move a panel (it snaps to the other panels' edges, hold Shift to move freely)</li>
 *     <li>right-click a header to fold / unfold the panel</li>
 *     <li>scroll over a panel to scroll it, type anywhere to search</li>
 * </ul>
 * A panel never grows over another panel placed below it. Positions are saved in {@link GuiStyle}.
 */
public final class ColumnLayout {

    private static final float HEADER_H = 34f;
    private static final float TOOLBAR_Y1 = 8f, TOOLBAR_H = 30f;
    private static final float PANELS_TOP = TOOLBAR_Y1 + TOOLBAR_H + 14f;
    private static final float ROW_GAP = 4f;
    private static final float PANEL_GAP = 10f;
    private static final float SNAP = 7f;
    private static final float APPEARANCE_W = 340f;
    /** Text is a little smaller than in the window view so it fits in narrow columns. */
    private static final float FONT_DIVISOR = 540f;

    private final List<Panel> panels = new ArrayList<>();   // draw order: last = on top
    private final GuiComponent guiComponent = new GuiComponent();
    private final List<Button> buttons = new ArrayList<>();
    private Panel dragging;
    private float dragDX, dragDY;
    private String query = "";
    private float screenW = 1040f, screenH = 567f;
    /** The whole window in canvas units (wider/taller than the canvas when the window has another shape). */
    private float viewX1 = 0f, viewY1 = 0f, viewX2 = 1040f, viewY2 = 567f;

    private float viewW() { return viewX2 - viewX1; }

    private void updateView(float width, float height) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (arsenic.utils.render.GuiCanvas.isActive()) {
     viewX1 = arsenic.utils.render.GuiCanvas.unitX(0f);
            viewX2 = arsenic.utils.render.GuiCanvas.unitX(mc.displayWidth);
            viewY1 = arsenic.utils.render.GuiCanvas.unitY(0f);
     viewY2 = arsenic.utils.render.GuiCanvas.unitY(mc.displayHeight);
        } else {
     viewX1 = 0f; viewY1 = 0f; viewX2 = width; viewY2 = height;
        }
    }

    public ColumnLayout(ClickGuiScreen screen) {
 rebuild();
    }

    // ================================================================== model

    private final class Panel {
        final String id, title, icon;
        final List<Module> modules = new ArrayList<>();
        final List<ModuleComponent> rows = new ArrayList<>();
        /** Rows drawn on the last frame (search / tier filters applied); only these take clicks. */
        List<ModuleComponent> visibleRows = new ArrayList<>();
        final boolean appearance;
        final Object key = new Object(), headerKey = new Object(), closeKey = new Object();
        final AnimationTimer openTimer;
        float x, y, scroll, targetScroll, contentH, bodyH;
        boolean collapsed, visible = true, searchHidden;

        Panel(String id, String title, String icon, boolean appearance) {
     this.id = id;
     this.title = title;
     this.icon = icon;
     this.appearance = appearance;
     this.openTimer = new AnimationTimer(240, () -> !collapsed, TickMode.CUBIC);
        }

        boolean shown() { return visible && !searchHidden; }
 float width() { return appearance ? APPEARANCE_W : GuiStyle.get().getPanelWidth(); }
        float bottom() { return y + HEADER_H + bodyH; }

 boolean inside(float mx, float my) {
     return mx >= x && mx <= x + width() && my >= y && my <= bottom();
        }

        boolean inHeader(float mx, float my) {
     return mx >= x && mx <= x + width() && my >= y && my <= y + HEADER_H;
        }

        boolean inBody(float mx, float my) {
     return mx >= x && mx <= x + width() && my > y + HEADER_H && my <= bottom();
        }

        boolean inClose(float mx, float my) {
     float cx = x + width() - 17f, cy = y + HEADER_H / 2f;
     return appearance && Math.abs(mx - cx) <= 11f && Math.abs(my - cy) <= 11f;
        }

 int enabledCount() {
     int n = 0;
     for (Module m : modules)
         if (m.isEnabled())
      n++;
     return n;
        }

        void save() { GuiStyle.get().setPanelState(id, x, y, collapsed); }
    }

    private static final class Button {
        final String icon;
        final java.util.function.Supplier<String> label;
        final java.util.function.Supplier<Boolean> active;
 final Runnable action;
        float x1, y1, x2, y2;

        Button(String icon, java.util.function.Supplier<String> label,
        java.util.function.Supplier<Boolean> active, Runnable action) {
     this.icon = icon;
     this.label = label;
            this.active = active;
            this.action = action;
        }
    }

    /** (Re)creates every panel, e.g. after addons were reloaded. */
    public void rebuild() {
        panels.clear();
        ModuleCategory[] cats = {ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.PLAYER,
  ModuleCategory.RENDER, ModuleCategory.CLIENT};
 for (ModuleCategory cat : cats) {
     Panel p = new Panel(cat.name(), cat.getName(), M3Icons.category(cat.name()), false);
     p.modules.addAll(cat.getContents());
            finish(p);
        }
        Panel addons = new Panel("ADDONS", "Addons", M3Icons.ADDONS, false);
 for (Module m : Arsenic.getArsenic().getModuleManager().getModules())
            if (m.isAddon())
         addons.modules.add(m);
        if (!addons.modules.isEmpty())
     finish(addons);

        Panel appearance = new Panel("APPEARANCE", "Appearance", M3Icons.SETTINGS, true);
 appearance.visible = false;
        panels.add(appearance);
 placeDefaults(false);

        buttons.clear();
        buttons.add(new Button(M3Icons.SETTINGS, () -> "Appearance", () -> find("APPEARANCE").visible, () -> {
     Panel a = find("APPEARANCE");
     a.visible = !a.visible;
     if (a.visible) {
  a.collapsed = false;
         toFront(a);
     }
        }));
        buttons.add(new Button(null, () -> allCollapsed() ? "Expand all" : "Collapse all", () -> false, () -> {
     boolean fold = !allCollapsed();
     for (Panel p : panels)
  if (!p.appearance) {
      p.collapsed = fold;
      p.save();
         }
     saveConfig();
        }));
        buttons.add(new Button(M3Icons.HUD, () -> "HUD editor", () -> false, () ->
         net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new arsenic.gui.hud.HudEditorScreen())));
 buttons.add(new Button(null, () -> "Window view", () -> false, () -> {
     GuiStyle.get().setLayout(GuiStyle.Layout.WINDOW);
     Hover.reset();
     saveConfig();
        }));
        buttons.add(new Button(null, () -> "Reset", () -> false, () -> {
     GuiStyle.get().clearPanelStates();
            placeDefaults(true);
     saveConfig();
 }));
    }

    private void finish(Panel p) {
        p.modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
 for (Module m : p.modules)
     p.rows.add(new ModuleComponent(m));
 panels.add(p);
    }

    private Panel find(String id) {
 for (Panel p : panels)
     if (p.id.equals(id))
         return p;
        return null;
    }

    private boolean allCollapsed() {
        for (Panel p : panels)
     if (!p.appearance && !p.collapsed)
         return false;
 return true;
    }

    /**
     * Left to right; panels that don't fit go into a second row, under the columns of the first
     * (the first-row panels then stop above them instead of overlapping).
     */
    private void placeDefaults(boolean force) {
 float w = GuiStyle.get().getPanelWidth();
        int perRow = Math.max(1, (int) ((viewW() - 24f + PANEL_GAP) / (w + PANEL_GAP)));
 float rowW = perRow * w + (perRow - 1) * PANEL_GAP;
        float startX = viewX1 + Math.max(12f, (viewW() - rowW) / 2f);
        int i = 0;
        for (Panel p : panels) {
     float[] saved = force ? null : GuiStyle.get().getPanelState(p.id);
     p.scroll = p.targetScroll = 0;
     if (p.appearance) {
  p.x = saved != null ? saved[0] : viewX2 - APPEARANCE_W - 12f;
         p.y = saved != null ? saved[1] : viewY1 + PANELS_TOP;
  p.collapsed = false;
         continue;
     }
     if (saved != null) {
         p.x = saved[0];
  p.y = saved[1];
  p.collapsed = saved[2] > 0.5f;
     } else {
  int col = i % perRow, row = i / perRow;
  p.x = startX + col * (w + PANEL_GAP);
  p.y = viewY1 + PANELS_TOP + row * (viewY2 - viewY1 - PANELS_TOP) * 0.55f;
         p.collapsed = false;
     }
     i++;
        }
    }

    private void toFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    private static void saveConfig() {
 Arsenic.getArsenic().getConfigManager().saveConfig();
    }

    // ================================================================== drawing

    public void draw(float width, float height, int mouseX, int mouseY, RenderInfo ri) {
        float oldVX = viewX1, oldVY = viewY1;
        updateView(width, height);
        boolean firstFrame = screenW != width || screenH != height || oldVX != viewX1 || oldVY != viewY1;
 screenW = width;
        screenH = height;
        if (firstFrame && GuiStyle.get().getPanelState("COMBAT") == null)
     placeDefaults(false);

 float fs = height / FONT_DIVISOR;
 M3.body().setScale(fs);
        M3.label().setScale(fs);
        M3.title().setScale(fs);
        M3.headline().setScale(fs);
        ri.getFr().setScale(fs);

 updateDrag(mouseX, mouseY);
        updateSearchVisibility();

        drawToolbar(width, ri);

        int oldRow = ModuleComponent.rowHeight;
        float oldColumn = ModuleComponent.columnWidth;
        float oldContent = ModuleCategoryComponent.contentWidth;
 ModuleComponent.rowHeight = GuiStyle.get().isCompactRows() ? 32 : 42;
        try {
     for (Panel p : new ArrayList<>(panels)) {
         if (!p.shown())
      continue;
         // keep panels reachable after a resize or a width change
  if (p != dragging) {
      p.x = MathUtils.clamp(p.x, viewX1, Math.max(viewX1, viewX2 - p.width()));
      p.y = MathUtils.clamp(p.y, viewY1, Math.max(viewY1, viewY2 - HEADER_H));
  }
  drawPanel(p, height, ri);
     }
 } finally {
     ModuleComponent.rowHeight = oldRow;
     ModuleComponent.columnWidth = oldColumn;
     ModuleCategoryComponent.contentWidth = oldContent;
 }

        if (!query.isEmpty() && noResults())
     drawCentredNote(width, height, "No module matches \"" + query + "\"", ri);
    }

    private void updateDrag(int mouseX, int mouseY) {
        if (dragging == null)
     return;
        if (!Mouse.isButtonDown(0)) {
            endDrag();
     return;
        }
        float w = dragging.width();
        float nx = mouseX - dragDX, ny = mouseY - dragDY;
 if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
     // snap to the edges of the other panels (side by side, aligned, or stacked)
            float bestX = SNAP + 1, bestY = SNAP + 1, sx = nx, sy = ny;
     // window edges
     for (float cx : new float[]{viewX1 + 8f, viewX2 - w - 8f})
  if (Math.abs(nx - cx) < bestX) { bestX = Math.abs(nx - cx); sx = cx; }
     for (float cy : new float[]{viewY1 + 8f, viewY2 - HEADER_H - 8f})
  if (Math.abs(ny - cy) < bestY) { bestY = Math.abs(ny - cy); sy = cy; }
     for (Panel q : panels) {
  if (q == dragging || !q.shown())
      continue;
         float[] xs = {q.x, q.x + q.width() + PANEL_GAP, q.x - w - PANEL_GAP, q.x + q.width() - w};
  for (float cx : xs)
      if (Math.abs(nx - cx) < bestX) { bestX = Math.abs(nx - cx); sx = cx; }
         float[] ys = {q.y, q.bottom() + PANEL_GAP};
         for (float cy : ys)
      if (Math.abs(ny - cy) < bestY) { bestY = Math.abs(ny - cy); sy = cy; }
     }
     nx = sx;
     ny = sy;
 }
        dragging.x = MathUtils.clamp(nx, viewX1, Math.max(viewX1, viewX2 - w));
        dragging.y = MathUtils.clamp(ny, viewY1, Math.max(viewY1, viewY2 - HEADER_H));
    }

    private void updateSearchVisibility() {
        for (Panel p : panels) {
     if (p.appearance || query.isEmpty()) {
  p.searchHidden = false;
         continue;
     }
     boolean any = false;
     for (Module m : p.modules)
         if (GuiStyle.get().isTierShown(m.getTier()) && ModuleComponent.matches(m, query)) {
      any = true;
      break;
         }
     p.searchHidden = !any;
        }
    }

    private boolean noResults() {
 for (Panel p : panels)
     if (!p.appearance && !p.searchHidden)
  return false;
        return true;
    }

    private void drawCentredNote(float width, float height, String text, RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
 float w = fr.getWidth(text) + 32f, cx = width / 2f, cy = height / 2f;
        M3Components.glass(cx - w / 2f, cy - 18f, cx + w / 2f, cy + 18f, 18f, M3.alpha(M3.surfaceContainer(), 225), 0.3f);
 fr.drawString(text, cx, cy, M3.onSurfaceVariant(), fr.CENTREX, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    private void drawToolbar(float width, RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
 float h = TOOLBAR_H, gap = 6f, searchW = 220f;
 float[] widths = new float[buttons.size()];
        float total = searchW;
 for (int i = 0; i < buttons.size(); i++) {
     Button b = buttons.get(i);
            widths[i] = fr.getWidth(b.label.get()) + 28f + (b.icon != null ? 18f : 0f);
     total += gap + widths[i];
        }
 float x = (viewX1 + viewX2 - total) / 2f, y1 = viewY1 + TOOLBAR_Y1, y2 = y1 + h, cy = (y1 + y2) / 2f;

        M3Components.glass(x - 6f, y1 - 5f, x + total + 6f, y2 + 5f, (h + 10f) / 2f,
  M3.alpha(M3.surfaceContainer(), 220), 0.35f);
        Hover.testRect(x - 6f, y1 - 5f, x + total + 6f, y2 + 5f);

        // search field: typing anywhere goes here
 boolean typing = !query.isEmpty();
        DrawUtils.drawRoundedRect(x, y1, x + searchW, y2, h / 2f, M3.alpha(M3.surfaceContainerHigh(), 245));
 DrawUtils.drawRoundedOutline(x, y1, x + searchW, y2, h / 2f, typing ? 1.5f : 1f,
  M3.alpha(typing ? M3.primary() : M3.outlineVariant(), typing ? 220 : 120));
        M3Icons.drawLeft(M3Icons.SEARCH, x + 10f, cy, typing ? M3.primary() : M3.onSurfaceVariant());
 float textX = x + 32f, room = searchW - 32f - 12f;
        String text;
        if (typing) {
     boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
     text = query;
     while (text.length() > 1 && fr.getWidth(text + "_") > room)
  text = text.substring(1);
            text += caret ? "_" : " ";
        } else {
     text = "Type to search...";
        }
        fr.drawString(text, textX, cy, typing ? M3.onSurface() : M3.alpha(M3.onSurfaceVariant(), 170), fr.CENTREY);
        RenderUtils.resetColorText();
 x += searchW;

        for (int i = 0; i < buttons.size(); i++) {
     Button b = buttons.get(i);
     x += gap;
     b.x1 = x; b.y1 = y1; b.x2 = x + widths[i]; b.y2 = y2;
     boolean hover = Hover.test(b, b.x1, b.y1, b.x2, b.y2);
     boolean active = b.active.get();
     int fill = active ? M3.mix(M3.primaryContainer(), M3.primary(), hover ? 0.25f : 0f)
      : M3.alpha(M3.surfaceContainerHighest(), hover ? 235 : 150);
     DrawUtils.drawRoundedRect(b.x1, b.y1, b.x2, b.y2, h / 2f, fill);
     if (!active)
         DrawUtils.drawRoundedOutline(b.x1, b.y1, b.x2, b.y2, h / 2f, 1f, M3.alpha(M3.outline(), hover ? 170 : 110));
     int fg = active ? M3.onPrimaryContainer() : M3.onSurface();
     float lx = b.x1 + 14f;
     if (b.icon != null) {
  M3Icons.drawLeft(b.icon, lx - 2f, cy, fg);
         lx += 18f;
     }
     fr.drawString(b.label.get(), lx, cy, fg, fr.CENTREY);
            RenderUtils.resetColorText();
     x += widths[i];
        }
    }

    /** How tall a panel's body may get: down to the screen edge or to the next panel stacked below. */
    private float maxBodyFor(Panel p, float height) {
        float bodyTop = p.y + HEADER_H;
 float limit = viewY2 - 8f;
        float w = p.width();
        for (Panel q : panels) {
     if (q == p || !q.shown() || q == dragging || p.appearance || q.appearance)
  continue;
     boolean overlapX = q.x < p.x + w - 4f && q.x + q.width() > p.x + 4f;
     if (overlapX && q.y > p.y + HEADER_H * 0.5f)
  limit = Math.min(limit, q.y - PANEL_GAP);
        }
        return Math.max(36f, limit - bodyTop);
    }

    private void drawPanel(Panel p, float height, RenderInfo ri) {
        GuiStyle style = GuiStyle.get();
        float w = p.width();
 float r = style.getPanelRadius();
 float open = p.openTimer.getPercent();
 float bodyTop = p.y + HEADER_H;
        float maxBody = maxBodyFor(p, height);

 List<ModuleComponent> shown = new ArrayList<>();
        if (!p.appearance)
            for (int i = 0; i < p.rows.size(); i++) {
  Module m = p.modules.get(i);
         if (style.isTierShown(m.getTier()) && ModuleComponent.matches(m, query))
      shown.add(p.rows.get(i));
            }
        p.visibleRows = shown;
        if (!p.appearance && p.contentH <= 0f)
            p.contentH = shown.size() * (ModuleComponent.rowHeight + ROW_GAP);   // first frame estimate
        float wanted = p.appearance ? Math.min(maxBody, 440f)
         : (shown.isEmpty() ? 0f : Math.min(maxBody, p.contentH + 8f));
        p.bodyH = wanted * open;

 // ---- shadow + background ----
 boolean lifted = p == dragging;
        if (lifted)
     DrawUtils.drawRoundedRect(p.x + 3f, p.y + 5f, p.x + w + 3f, p.bottom() + 6f, r + 2f, M3.alpha(0xFF000000, 70));
        int fill = M3.alpha(M3.surfaceContainer(), (int) (style.getPanelOpacity() * 2.55f));
 M3Components.glass(p.x, p.y, p.x + w, p.bottom(), r, fill, lifted ? 0.8f : 0.35f);
        Hover.test(p.key, p.x, p.y, p.x + w, p.bottom());
        DrawUtils.drawRoundedOutline(p.x, p.y, p.x + w, p.bottom(), r, 1f,
         M3.alpha(lifted ? M3.primary() : M3.outlineVariant(), lifted ? 210 : 90));

 drawHeader(p, w, r, open, ri);

 // ---- body ----
        if (p.bodyH > 1f) {
     float x1 = p.x + 2f, x2 = p.x + w - 2f, y1 = bodyTop + 1f, y2 = p.bottom() - 2f;
     ScissorUtils.subScissor((int) x1, (int) y1, (int) Math.ceil(x2), (int) Math.ceil(y2), 2);
     Hover.pushClip(x1, y1, x2, y2);
     try {
         if (p.appearance) {
      ModuleCategoryComponent.contentWidth = w - 4f;
      guiComponent.drawLeft(new PosInfo(p.x + 6f, bodyTop - 6f), ri);
      guiComponent.subtractFromMaxScrollHeight(wanted);
         } else {
      p.scroll += (p.targetScroll - p.scroll) * GuiStyle.scrollEase();
      if (Math.abs(p.targetScroll - p.scroll) < 0.5f)
          p.scroll = p.targetScroll;
      float maxScroll = Math.max(0f, p.contentH + 8f - maxBody);
      boolean bar = maxScroll > 0f;
      // leave a lane for the scrollbar so it never covers the switches
      ModuleComponent.columnWidth = w - 10f - (bar ? 5f : 0f);
      PosInfo pi = new PosInfo(p.x + 5f, bodyTop + 4f + p.scroll);
      float start = pi.getY();
      for (ModuleComponent row : shown)
          pi.moveY(row.updateComponent(pi, ri) + ROW_GAP);
      p.contentH = pi.getY() - start;
      maxScroll = Math.max(0f, p.contentH + 8f - maxBody);
      p.targetScroll = MathUtils.clamp(p.targetScroll, -maxScroll, 0f);
      if (maxScroll > 0f)
          M3Components.scrollbar(x2 - 2f, y1 + 2f, y2 - y1 - 4f, maxScroll, p.scroll);
  }
         RenderUtils.resetColorText();
            } finally {
  Hover.popClip();
         ScissorUtils.endSubScissor();
     }
        }
    }

    private void drawHeader(Panel p, float w, float r, float open, RenderInfo ri) {
        FontRendererExtension<?> fr = ri.getFr();
        GuiStyle.PanelHeader kind = GuiStyle.get().getPanelHeader();
        float x1 = p.x, y1 = p.y, x2 = p.x + w, y2 = p.y + HEADER_H, cy = (y1 + y2) / 2f;
        boolean hover = Hover.test(p.headerKey, x1, y1, x2, y2) || p == dragging;
        boolean squareBottom = p.bodyH > 1f;
        int text, sub;

 switch (kind) {
     case SOLID: {
         int c = M3.mix(M3.primaryContainer(), M3.primary(), hover ? 0.18f : 0f);
         DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, c);
         if (squareBottom && r > 0)
      DrawUtils.drawRect(x1, y2 - r, x2, y2, c);
  text = M3.onPrimaryContainer();
  sub = M3.alpha(text, 185);
         break;
     }
     case GRADIENT: {
         int left = M3.mix(M3.primary(), M3.onSurface(), hover ? 0.12f : 0f);
         int right = M3.mix(M3.primary(), M3.tertiary(), 0.75f);
  DrawUtils.drawGradientRoundedRect(x1, y1, x2, y2, r, left, left, right, right);
  if (squareBottom && r > 0)
      DrawUtils.drawGradientRoundedRect(x1, y2 - r, x2, y2, 0f, left, left, right, right);
         text = M3.onPrimary();
  sub = M3.alpha(text, 200);
         break;
     }
     default: {
         if (hover)
      DrawUtils.drawRoundedRect(x1, y1, x2, y2, r, M3.alpha(M3.onSurface(), 14));
  if (squareBottom)
      DrawUtils.drawRect(x1 + 8f, y2 - 1.5f, x2 - 8f, y2, M3.alpha(M3.primary(), 210));
  text = M3.onSurface();
         sub = M3.onSurfaceVariant();
  break;
     }
        }

        // right side: chevron (or close for the appearance panel), then the active-count pill
        float right = x2 - 10f;
 if (p.appearance) {
     boolean closeHover = Hover.test(p.closeKey, right - 14f, cy - 11f, right + 4f, cy + 11f);
     if (closeHover)
  DrawUtils.drawRoundedRect(right - 14f, cy - 10f, right + 3f, cy + 10f, 8f, M3.alpha(text, 40));
     fr.drawString("x", right - 5.5f, cy, text, fr.CENTREX, fr.CENTREY);
     RenderUtils.resetColorText();
     right -= 22f;
 } else {
     UITheme.chevron(right - 4f, cy, HEADER_H * 0.2f, 1.5f, M3.alpha(text, 230), open);
            right -= 16f;
     int on = p.enabledCount();
     String count = on + "/" + p.modules.size();
            float pillH = HEADER_H * 0.52f;
     float pillW = fr.getWidth(count) * 0.85f + pillH * 0.9f;
     int pillFill = on > 0 ? M3.alpha(text, 50) : M3.alpha(text, 22);
     DrawUtils.drawRoundedRect(right - pillW, cy - pillH / 2f, right, cy + pillH / 2f, pillH / 2f, pillFill);
     fr.drawString(count, right - pillW / 2f, cy, on > 0 ? text : sub,
      fr.getScaleModifier(0.85f), fr.CENTREX, fr.CENTREY);
     RenderUtils.resetColorText();
     right -= pillW + 6f;
        }

        // left side: icon + title, trimmed so it never runs under the pill
        M3Icons.drawLeft(p.icon, x1 + 10f, cy, text);
        float titleX = x1 + 32f;
 String title = p.title;
        float room = right - titleX;
        if (fr.getWidth(title) > room) {
            while (title.length() > 1 && fr.getWidth(title + "..") > room)
         title = title.substring(0, title.length() - 1);
     title += "..";
 }
        fr.drawString(title, titleX, cy, text, fr.CENTREY);
        RenderUtils.resetColorText();
    }

    // ================================================================== input

    /** Returns true when the click was used by the column layout. */
    public boolean mouseClick(int mx, int my, int button) {
        for (Button b : buttons)
     if (button == 0 && mx >= b.x1 && mx <= b.x2 && my >= b.y1 && my <= b.y2) {
         SoundUtils.chordOpen();
  b.action.run();
  return true;
     }
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
     if (!p.shown() || !p.inside(mx, my))
  continue;
     toFront(p);
            if (p.inHeader(mx, my)) {
  if (button == 0 && p.inClose(mx, my)) {
      p.visible = false;
      SoundUtils.chordEnum();
  } else if (button == 0) {
      dragging = p;
      dragDX = mx - p.x;
      dragDY = my - p.y;
         } else if (button == 1) {
      if (p.appearance)
          p.visible = false;
      else {
   p.collapsed = !p.collapsed;
          p.save();
          saveConfig();
      }
      SoundUtils.chordEnum();
         }
         return true;
     }
     if (p.inBody(mx, my)) {
  if (p.appearance)
      guiComponent.clickChildren(mx, my, button);
         else
      for (ModuleComponent row : new ArrayList<>(p.visibleRows))
   if (row.handleClick(mx, my, button))
       break;
     }
     return true;
        }
        return false;
    }

    public void mouseRelease(int mx, int my, int state) {
        if (dragging != null)
     endDrag();
 for (Panel p : panels)
     for (ModuleComponent row : p.rows)
         row.handleRelease(mx, my, state);
        guiComponent.handleRelease(mx, my, state);
    }

    private void endDrag() {
        dragging.save();
        dragging = null;
        saveConfig();
    }

    public void scroll(int mx, int my, int dir) {
 if (dir == 0)
     return;
        for (int i = panels.size() - 1; i >= 0; i--) {
     Panel p = panels.get(i);
     if (!p.shown() || !p.inside(mx, my))
  continue;
     if (p.appearance)
         guiComponent.scroll(dir * 30);
     else
         p.targetScroll += dir * 30;
     return;
 }
    }

    /** Typing filters every panel; returns true if the key was used. */
    public boolean keyTyped(char c, int keyCode) {
 if (keyCode == Keyboard.KEY_ESCAPE) {
     if (query.isEmpty())
         return false;
     query = "";
     resetScroll();
     return true;
        }
 if (keyCode == Keyboard.KEY_BACK) {
     if (!query.isEmpty())
         query = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_LMETA)
   ? "" : query.substring(0, query.length() - 1);
     resetScroll();
     return true;
        }
 if (c >= 32 && c != 127 && query.length() < 32) {
     query += c;
     resetScroll();
     return true;
        }
        return false;
    }

    private void resetScroll() {
        for (Panel p : panels)
            p.scroll = p.targetScroll = 0;
    }
}
