package arsenic.gui.click.impl;

import arsenic.addon.AddonManager;
import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.gui.click.GuiStyle;
import arsenic.gui.click.UITheme;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the ClickGUI shows while the Addon Manager is open, laid out like the module view. The category column becomes
 * a list of "All addons", every pack, "Loose addons" and (when something failed to load) "Errors", grouped and drawn
 * like the module categories, and scrollable when it does not fit. The main area shows the selected pack's header
 * (icon, description and how many of its addons are on) and then its addons in two columns, each drawn like a module: a switch
 * on the right, and a dropdown (chevron or right click) with the description and what it needs. Typing in the search
 * box searches every addon, whatever is selected.
 */
public class AddonPageComponent {

    private static final String ALL = "\u0000all", LOOSE = "\u0000loose", ERRORS = "\u0000errors";

    private final Supplier<String> query;
    private final Runnable clearQuery;
    private String selected = ALL;

    private List<AddonManager.PackInfo> packs = new ArrayList<>();
    private List<AddonManager.Info> loose = new ArrayList<>();
    private List<AddonManager.Info> addons = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    /** Group titles (key null) and entries, top to bottom. */
    private final List<SidebarEntry> sidebar = new ArrayList<>();
    private float sideScroll, sideTargetScroll, sideMaxScroll;
    private float sideX1, sideY1, sideX2, sideY2;

    /** Module rows for search results, kept like the addon rows so their state survives typing. */
    private final Map<Module, ModuleComponent> moduleRows = new HashMap<>();
    private List<AddonCardComponent> blocks = new ArrayList<>();
    private List<Component> left = new ArrayList<>(), right = new ArrayList<>();
    private String laidOutFor;
    private String lastQuery;
    private boolean resetScroll = true;
    private float scroll, targetScroll, maxHeight, lastMaxScroll;

    public AddonPageComponent(Supplier<String> query, Runnable clearQuery) {
        this.query = query;
        this.clearQuery = clearQuery;
    }

    /** Scroll back to the top the next time the list is laid out (used when the page is opened). */
    public void reset() {
        resetScroll = true;
    }

    /** Re-reads what is on disk (call when the page opens and after any change). */
    public void refresh() {
        AddonManager manager = Arsenic.getArsenic().getAddonManager();
        packs = manager.listPacks();
        loose = manager.listLoose();
        addons = new ArrayList<>(loose);
        for (AddonManager.PackInfo pack : packs)
            addons.addAll(pack.addons);
        addons.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        errors = new ArrayList<>(manager.getErrors());

        if (!selected.equals(ALL) && !selected.equals(LOOSE) && !selected.equals(ERRORS) && findPack(selected) == null)
            selected = ALL;
        if ((selected.equals(LOOSE) && loose.isEmpty()) || (selected.equals(ERRORS) && errors.isEmpty()))
            selected = ALL;

        Map<String, SidebarEntry> previous = new HashMap<>();
        for (SidebarEntry entry : sidebar)
            previous.put(entry.key == null ? "#" + entry.label : entry.key, entry);
        sidebar.clear();
        addEntry(previous, null, "Addons", null, false);
        addEntry(previous, ALL, "All addons", "presets", false);
        addEntry(previous, null, "Packs", null, false);
        for (AddonManager.PackInfo pack : packs)
            addEntry(previous, pack.meta.id, pack.meta.name, pack.meta.icon, true);
        if (!loose.isEmpty() || !errors.isEmpty())
            addEntry(previous, null, "Other", null, false);
        if (!loose.isEmpty())
            addEntry(previous, LOOSE, "Loose addons", "local", false);
        if (!errors.isEmpty())
            addEntry(previous, ERRORS, "Errors (" + errors.size() + ")", "other", false);
        laidOutFor = null;
    }

    /** Reuses the entry from before a reload so its highlight does not fade in again. */
    private void addEntry(Map<String, SidebarEntry> previous, String key, String label, String icon, boolean packIcon) {
        SidebarEntry entry = previous.get(key == null ? "#" + label : key);
        if (entry == null)
            entry = new SidebarEntry(key);
        entry.label = label;
        entry.icon = icon;
        entry.packIcon = packIcon;
        sidebar.add(entry);
    }

    private AddonManager.PackInfo findPack(String id) {
        for (AddonManager.PackInfo pack : packs)
            if (pack.meta.id.equals(id))
                return pack;
        return null;
    }

    private void select(String key) {
        if (key.equals(selected) && query.get().trim().isEmpty())
            return;
        clearQuery.run();
        selected = key;
        resetScroll = true;
        laidOutFor = null;
    }

    /** An addon as a module row: the same row a module gets, kept across layouts and reloads. */
    private ModuleComponent row(AddonManager.Info info) {
        return AddonRow.of(info).component();
    }

    private ModuleComponent moduleRow(Module module) {
        return moduleRows.computeIfAbsent(module, ModuleComponent::new);
    }

    private void layout(String q) {
        List<AddonCardComponent> top = new ArrayList<>();
        List<Component> list = new ArrayList<>();
        if (!q.isEmpty()) {
            // modules first (addons that are modules are found through their addon row below)
            int modules = 0;
            for (Module module : Arsenic.getArsenic().getModuleManager().getModules()) {
                if (!module.isAddon() && ModuleComponent.matches(module, q)) {
                    list.add(moduleRow(module));
                    modules++;
                }
            }
            int addonHits = 0;
            for (AddonManager.Info info : addons) {
                if (AddonSource.matches(info, q)) {
                    list.add(row(info));
                    addonHits++;
                }
            }
            String found = list.isEmpty() ? "No addon or module matches. Search looks at names, descriptions and pack names."
                    : addonHits + (addonHits == 1 ? " addon" : " addons") + " and " + modules
                    + (modules == 1 ? " module" : " modules") + " found.";
            top.add(AddonCardComponent.section("Search: " + q, found));
        } else if (selected.equals(ALL)) {
            top.add(AddonCardComponent.section("All addons", "Every addon in every pack. Each one works like a module: "
                    + "the switch turns it on, and opening it (arrow or right click) shows what it does and its settings."));
            for (AddonManager.Info info : addons)
                list.add(row(info));
        } else if (selected.equals(LOOSE)) {
            top.add(AddonCardComponent.section("Loose addons", "Single .java files in the Arsenic/addons folder that are "
                    + "not part of a pack."));
            for (AddonManager.Info info : loose)
                list.add(row(info));
        } else if (selected.equals(ERRORS)) {
            top.add(AddonCardComponent.section("Errors", "Problems from the last time addons were loaded. An addon that "
                    + "fails to compile is not loaded; the others still are."));
            for (String error : errors)
                top.add(AddonCardComponent.error(error));
        } else {
            AddonManager.PackInfo pack = findPack(selected);
            if (pack != null) {
                top.add(AddonCardComponent.pack(pack));
                for (AddonManager.Info info : pack.addons)
                    list.add(row(info));
            }
        }
        blocks = top;
        left = new ArrayList<>();
        right = new ArrayList<>();
        for (Component r : list)
            (left.size() <= right.size() ? left : right).add(r);
        laidOutFor = q;
        // only a different list sends you back to the top; reloading after a switch keeps your place
        if (resetScroll || !q.equals(lastQuery)) {
            scroll = 0;
            targetScroll = 0;
        }
        resetScroll = false;
        lastQuery = q;
    }

    private void relayoutIfNeeded() {
        String q = query.get().trim().toLowerCase(Locale.ROOT);
        if (!q.equals(laidOutFor))
            layout(q);
    }

    /**
     * The left column, in place of the module categories and drawn the same way. Scrolls with the mouse wheel when
     * the pointer is over it; clipped to the column panel.
     */
    public void drawSidebar(float x, float top, float panelX1, float panelX2, float bottom, RenderInfo ri) {
        sideX1 = panelX1;
        sideX2 = panelX2;
        sideY1 = top;
        sideY2 = bottom;
        String q = query.get().trim();
        sideScroll += (sideTargetScroll - sideScroll) * GuiStyle.scrollEase();
        if (Math.abs(sideTargetScroll - sideScroll) < 0.5f)
            sideScroll = sideTargetScroll;

        ScissorUtils.subScissor((int) panelX1, (int) top, (int) panelX2, (int) bottom, 2);
        PosInfo pi = new PosInfo(x, top + sideScroll);
        float start = pi.getY();
        for (SidebarEntry entry : sidebar) {
            entry.active = entry.key != null && q.isEmpty() && entry.key.equals(selected);
            pi.moveY(entry.updateComponent(pi, ri) * 1.1f);
        }
        ScissorUtils.endSubScissor();
        sideMaxScroll = Math.max(0, (pi.getY() - start) - (bottom - top));
        sideTargetScroll = Math.max(sideTargetScroll, -sideMaxScroll);
    }

    /** True when the pointer is over the left column (the mouse wheel scrolls it instead of the list). */
    public boolean isOverSidebar(int mouseX, int mouseY) {
        return mouseX >= sideX1 && mouseX <= sideX2 && mouseY >= sideY1 && mouseY <= sideY2;
    }

    public void scrollSidebar(int amount) {
        sideTargetScroll = Math.max(Math.min(0, sideTargetScroll + amount), -sideMaxScroll);
    }

    /**
     * The main area: the full-width header blocks, then the addon rows in two columns like the module view
     * ({@code leftX} and {@code rightX} are where the module columns start).
     */
    public void drawContent(float leftX, float rightX, float top, float fullWidth, RenderInfo ri) {
        relayoutIfNeeded();
        // content may have got shorter (an addon disappeared): keep the scroll inside the new range
        targetScroll = Math.max(targetScroll, -lastMaxScroll);
        scroll = Math.max(scroll, -lastMaxScroll);
        scroll += (targetScroll - scroll) * GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;

        float gap = ri.getGuiScreen().width / 100f;
        PosInfo pi = new PosInfo(leftX, top + scroll + gap);
        for (AddonCardComponent block : blocks) {
            block.setPixelWidth(fullWidth);
            pi.moveY(block.updateComponent(pi, ri) + gap);
        }
        float columnsTop = pi.getY();
        PosInfo l = new PosInfo(leftX, columnsTop);
        for (Component r : left)
            l.moveY(r.updateComponent(l, ri) + gap);
        PosInfo rr = new PosInfo(rightX, columnsTop);
        for (Component r : right)
            rr.moveY(r.updateComponent(rr, ri) + gap);
        maxHeight = Math.max(l.getY(), rr.getY()) - (top + scroll);
    }

    public void clickSidebar(int mouseX, int mouseY, int mouseButton) {
        if (!isOverSidebar(mouseX, mouseY))
            return;
        for (SidebarEntry entry : new ArrayList<>(sidebar))
            if (entry.key != null)
                entry.handleClick(mouseX, mouseY, mouseButton);
    }

    public void clickContent(int mouseX, int mouseY, int mouseButton) {
        for (AddonCardComponent card : new ArrayList<>(blocks))
            card.handleClick(mouseX, mouseY, mouseButton);
        for (Component card : new ArrayList<>(left))
            card.handleClick(mouseX, mouseY, mouseButton);
        for (Component card : new ArrayList<>(right))
            card.handleClick(mouseX, mouseY, mouseButton);
    }

    public void scroll(int amount) {
        targetScroll += amount;
        targetScroll = Math.max(Math.min(0, targetScroll), -maxHeight);
    }

    public void subtractFromMaxScrollHeight(float f) {
        maxHeight = Math.max(0, maxHeight - f);
        lastMaxScroll = maxHeight;
    }

    public void drawScrollbar(float x, float y, float barHeight, RenderInfo ri) {
        if (maxHeight <= 0)
            return;
        float thumbH = barHeight * (barHeight / (barHeight + maxHeight));
        float thumbY = y + (scroll / -maxHeight) * (barHeight - thumbH);
        DrawUtils.drawRoundedRect(x - 3, y, x - 1, y + barHeight, 1f, ThemeManager.getScrollbarTrack());
        DrawUtils.drawRoundedRect(x - 3, thumbY, x - 1, thumbY + thumbH, 1f, ThemeManager.getScrollbarThumb());
    }

    /**
     * One line in the left column. With a key it is drawn like ModuleCategoryComponent (icon, name, a pill when
     * selected or hovered); without one it is a group title drawn like UICategoryComponent.
     */
    private final class SidebarEntry extends Component {
        private final String key;
        private String label, icon;
        private boolean packIcon, active, isHovered;
        private final AnimationTimer activeTimer = new AnimationTimer(350, () -> active, TickMode.SINE);
        private final AnimationTimer hoverTimer = new AnimationTimer(350, () -> isHovered, TickMode.SINE);

        SidebarEntry(String key) {
            this.key = key;
        }

        @Override
        protected float drawComponent(RenderInfo ri) {
            if (key == null) {
                ri.getFr().drawString(label, x1, midPointY, getWhite(), ri.getFr().CENTREY);
                RenderUtils.resetColorText();
                return height;
            }
            float anim = Math.max(activeTimer.getPercent(), hoverTimer.getPercent());
            expandX = anim * (width / 40f);
            int mainC = ColorUtils.setColor(getEnabledColor(), 0, (int) (anim * 255));
            int gradientC = ColorUtils.setColor(getGradientColor(), 0, (int) (anim * 255));
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            if (anim > 0.01f)
                DrawUtils.drawShadow(x1 + expandX, y1, x2 + expandX, y2, height / 4f,
                        GuiStyle.shadowSpread(height * 0.18f), GuiStyle.shadowAlpha((int) (130 * anim)), 4);
            DrawUtils.drawGradientRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height / 4f, mainC, mainC, gradientC, gradientC);

            int onPanel = UITheme.readableOn(ThemeManager.getModuleBackground());
            int onAccent = UITheme.readableOn(getEnabledColor());
            int foreground = UITheme.mix(onPanel, onAccent, anim);
            if (key.equals(ERRORS))
                foreground = UITheme.mix(0xFFFF5555, onAccent, anim);

            float iconSize = ri.getFr().getHeight("|") * (ri.getGuiScreen().height / 300f);
            float iconX = x1 + (width / 7f) + expandX - iconSize;
            float iconY = midPointY - iconSize / 2f;
            if (packIcon) {
                AddonCardComponent.drawIcon(icon, iconX, iconY, iconSize);
            } else if (icon != null) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation("arsenic", "icons/" + icon + ".png"));
                RenderUtils.color2(foreground, 1f);
                Gui.drawModalRectWithCustomSizedTexture((int) iconX, (int) iconY, 0, 0, (int) iconSize, (int) iconSize, (int) iconSize, (int) iconSize);
                GlStateManager.color(1f, 1f, 1f, 1f);
            }
            float textX = iconX + iconSize + 2;
            ri.getFr().drawString(AddonCardComponent.fit(ri.getFr(), label, x2 + expandX - textX - width * 0.04f),
                    textX, midPointY, foreground, ri.getFr().CENTREY);
            RenderUtils.resetColorText();
            return height;
        }

        @Override
        public void mouseUpdate(int mouseX, int mouseY) {
            isHovered = key != null && isMouseInArea(mouseX, mouseY) && isOverSidebar(mouseX, mouseY);
        }

        @Override
        protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
            select(key);
        }

        @Override
        protected void playClickSound() {
            SoundUtils.chordCategory();
        }

        @Override
        public int getWidth(int i) {
            return key == null ? 9 * (i / 100) : 10 * (i / 100);
        }

        @Override
        public int getHeight(int i) {
            return 5 * (i / 100);
        }
    }
}
