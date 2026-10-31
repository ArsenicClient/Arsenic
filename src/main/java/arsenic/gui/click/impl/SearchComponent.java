package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import arsenic.addon.AddonManager;
import arsenic.gui.click.Component;
import arsenic.utils.java.MathUtils;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.gui.click.GuiStyle;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.util.StringUtil;
import com.mojang.blaze3d.platform.InputConstants;
import arsenic.utils.io.Keys;

/**
 * The search box. With nothing typed it lists the modules; once something is typed it also lists the addons, in the
 * same two columns, so an addon found here can be switched, opened and bound without going to the Addon Manager.
 */
public class SearchComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {
    @Override
    protected boolean followsMoreToggle() {
        return false;
    }

    final ClickGuiScreen gui = Arsenic.getArsenic().getClickGuiScreen();
    private final StringBuilder inp = new StringBuilder();
    private final AnimationTimer activateTimer = new AnimationTimer(200, () -> gui.isSearchActive(), TickMode.SINE);

    private boolean selected;

    /** Results for the query in {@link #builtFor}; rebuilt whenever the query changes. */
    private final List<Component> resultsL = new ArrayList<>(), resultsR = new ArrayList<>();
    private final Map<Module, ModuleComponent> moduleRows = new HashMap<>();
    private String builtFor;

    int x,y;
    public SearchComponent(ModuleCategory category) {
        super(category);
    }
    public void setupGlowAndBlur(int alpha){
        DrawUtils.drawGradientRoundedRect(x1, y1, x2, y2, (y2 - y1) / 2f,
                M3.alpha(M3.primary(), alpha), M3.alpha(M3.primary(), alpha),
                M3.alpha(M3.primaryContainer(), alpha), M3.alpha(M3.primaryContainer(), alpha));
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && MathUtils.inside(mouseX, mouseY, x1, y1, x2, y2)) {
            if (!gui.isAddonMode() && gui.getCmcc() != this)
                gui.setCmcc(this);
            return true;
        }
        return false;
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        boolean active = gui.isSearchActive();
        float hover = hoverPct();
        float radius = (y2 - y1) / 2f;

        int fill = active ? M3.surfaceContainerHigh() : M3.alpha(M3.surfaceContainerHighest(), 210);
        DrawUtils.drawRoundedRect(x1, y1, x2, y2, radius, fill);
        DrawUtils.drawRoundedOutline(x1, y1, x2, y2, radius, 1f,
                active ? M3.alpha(M3.primary(), 210)
                        : M3.alpha(M3.outlineVariant(), (int) (130 + 50 * hover)));

        float pad = (y2 - y1) * 0.34f;
        float iconSize = (y2 - y1) * 0.38f;
        M3Icons.drawLeft(M3Icons.SEARCH, x1 + pad * 0.85f, (y1 + y2) / 2f,
                active ? M3.primary() : M3.onSurfaceVariant());

        String text = inp.length() == 0
                ? (active ? "Search modules and addons" : "Press / to search")
                : inp.toString();
        int textColor = inp.length() == 0 ? M3.alpha(M3.onSurfaceVariant(), 190) : M3.onSurface();
        float textX = x1 + pad * 0.85f + M3Icons.font().getWidth(M3Icons.SEARCH) + pad * 0.45f;
        float textY = (y1 + y2) / 2f;

        ScissorUtils.subScissor((int) x1, (int) y1, (int) x2, (int) y2);
        ri.getFr().drawString(text, textX, textY, textColor, ri.getFr().CENTREY);
        if (active && (System.currentTimeMillis() / 530L) % 2L == 0L) {
            float caretX = textX + ri.getFr().getWidth(inp.toString()) + 1.5f;
            DrawUtils.drawRect(caretX, textY - 6f, caretX + 1.2f, textY + 7f, M3.primary());
        }
        ScissorUtils.endSubScissor();
        return height;
    }

    @Override
    public int getWidth(int i) {
        return 258;
    }

    @Override
    public int getHeight(int i) {
        return 38;
    }

    public boolean recieveInput(int key) {
        this.scroll = 0;
        this.targetScroll = 0;
        if (key == InputConstants.KEY_SLASH){
            toggleSearch();
            return false;
        }

        if (!gui.isSearchActive()) return false;
        boolean isCtrlDown = Keys.isKeyDown(InputConstants.KEY_LCONTROL) || Keys.isKeyDown(InputConstants.KEY_RCONTROL)
                || Keys.isKeyDown(227) || Keys.isKeyDown(231); // left/right GUI (cmd) on mac
        switch (key){
            case InputConstants.KEY_BACKSPACE:
                if(inp.length() >= 1) {
                    if (!selected) {
                        inp.deleteCharAt(inp.length() - 1);
                    } else {
                        inp.delete(0,inp.length());
                        selected = false;
                    }
                }
                break;
            case InputConstants.KEY_A:
                if (isCtrlDown){
                    selected = true;
                }
                break;
        }
        refilter();
        return false;
    }

    @Override
    public boolean recieveChar(char c) {
        // "/" toggles the search bar; it arrives as a key press too, so it is not typed here
        if (!gui.isSearchActive() || c == '/' || !StringUtil.isAllowedChatCharacter(c))
            return false;
        if (selected){
            inp.delete(0,inp.length());
            selected = false;
        }
        inp.append(c);
        refilter();
        return true;
    }

    private void refilter() {
        builtFor = null;
    }

    @Override
    public void setNotAlwaysRecieveInput() {
    }

    private void refreshResults() {
        String q = inp.toString().trim().toLowerCase(Locale.ROOT);
        if (q.equals(builtFor))
            return;
        builtFor = q;
        resultsL.clear();
        resultsR.clear();

        List<Component> found = new ArrayList<>();
        List<Module> modules = new ArrayList<>(Arsenic.getArsenic().getModuleManager().getModules());
        modules.sort(Comparator.comparing(Module::getName));
        for (Module module : modules)
            if (!module.isAddon() && ModuleComponent.matches(module, q))
                found.add(moduleRow(module));
        if (!q.isEmpty()) {
            for (AddonManager.Info info : Arsenic.getArsenic().getAddonManager().listAll())
                if (AddonSource.matches(info, q))
                    found.add(AddonRow.of(info).component());
        }
        for (Component component : found)
            (resultsL.size() <= resultsR.size() ? resultsL : resultsR).add(component);
    }

    private ModuleComponent moduleRow(Module module) {
        return moduleRows.computeIfAbsent(module, ModuleComponent::new);
    }

    @Override
    public void drawLeft(PosInfo pi, RenderInfo ri) {
        refreshResults();
        maxHeight = 0;
        scroll += (targetScroll - scroll) * GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;
        drawSection(resultsL, pi, ri);
    }

    @Override
    public void drawRight(PosInfo pi, RenderInfo ri) {
        drawSection(resultsR, pi, ri);
    }

    @Override
    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        for (Component component : new ArrayList<>(resultsL))
            component.handleClick(mouseX, mouseY, mouseButton);
        for (Component component : new ArrayList<>(resultsR))
            component.handleClick(mouseX, mouseY, mouseButton);
    }
    /** What is typed in the box right now (the addon manager filters by it). */
    public String getQuery() {
        return inp.toString();
    }

    public void setQuery(String query) {
        inp.setLength(0);
        inp.append(query);
        selected = false;
        builtFor = null;
    }

    private void toggleSearch(){
        if (gui.isAddonMode()) {
            gui.toggleAddonSearch();
            return;
        }
        if (gui.getCmcc() != this) {
            gui.setCmcc(this);
        } else {
            gui.setCmcc(gui.getPrevCmcc());
            inp.delete(0,inp.length());
        }
    }
}
