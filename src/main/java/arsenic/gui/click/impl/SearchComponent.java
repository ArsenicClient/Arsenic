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
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

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
        DrawUtils.drawGradientRoundedRect(x, y - 10, x1, y + 10, 8,
                ColorUtils.setColor(getEnabledColor(), 0, alpha),
                ColorUtils.setColor(getEnabledColor(), 0, alpha),
                ColorUtils.setColor(getGradientColor(), 0, alpha),
                ColorUtils.setColor(getGradientColor(), 0, alpha));
    }
    @Override
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        boolean isMouseOver = MathUtils.inside(mouseX, mouseY, x, y - 10, x1, y + 10);
        if (isMouseOver && mouseButton == 0){
            toggleSearch();
        }
        return super.handleClick(mouseX,mouseY,mouseButton);
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        x = (int) (ri.getGuiScreen().width / (3 + (1 * activateTimer.getPercent())));
        x1 = ri.getGuiScreen().width - x;
        y = ri.getGuiScreen().height / 10;
        y1 = ri.getGuiScreen().height - y;

        String imlosingmymind = inp.length() == 0 ? gui.isSearchActive() ? "Search" : "Press \"/\" to toggle search" : inp.toString();
        int centerX = (int) getCentre(imlosingmymind,x+x1, ri.getFr());

        DrawUtils.drawRoundedRect(x, y - 10, x1, y + 10,8, GuiStyle.glassify(ThemeManager.getClickGuiBackground()));
        if (GuiStyle.glassEnabled())
            DrawUtils.drawGlassRect(x, y - 10, x1, y + 10, 8,
                    ColorUtils.setColor(ThemeManager.getMainColor(), 0, 14), ThemeManager.getWhite(), GuiStyle.glassStrength());

        if (GuiStyle.fontEnabled()) {
            Arsenic.getInstance().getFonts().Icon.drawString("B", x + 3, y - 3, ThemeManager.getWhite());
        }
        ScissorUtils.subScissor(x, y - 10, (int) x1, y + 10);
        if (selected) {
            DrawUtils.drawRect(centerX, y - 4, centerX + ri.getFr().getWidth(inp.toString()) + 2, y + 6, ThemeManager.getGradientColor());
        }
        ScissorUtils.endSubScissor();
        ri.getFr().drawString(imlosingmymind, centerX, y, getEnabledColor(), ri.getFr().CENTREY);

        return height;
    }
    public float getCentre(String s, float width, FontRendererExtension<?> fr) {
        return width / 2f - (fr.getWidth(s) / 2f);
    }
    @Override
    public void setNotAlwaysRecieveInput() {

    }

    public boolean recieveInput(int key) {
        this.scroll = 0;
        this.targetScroll = 0;
        if (key == Keyboard.KEY_SLASH){
            toggleSearch();
            return false;
        }

        if (!gui.isSearchActive()) return false;
        char keyName = Keyboard.getEventCharacter();
        boolean isCtrlDown =  (Minecraft.isRunningOnMac ? Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220) : Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157));
        switch (key){
            case Keyboard.KEY_BACK:
                if(inp.length() >= 1) {
                    if (!selected) {
                        inp.deleteCharAt(inp.length() - 1);
                    } else {
                        inp.delete(0,inp.length());
                        selected = false;
                    }
                }
                break;
            case Keyboard.KEY_A:
                if (isCtrlDown){
                    selected = true;
                }
                break;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(keyName)) {
            if (selected){
                inp.delete(0,inp.length());
                selected = false;
            }
            inp.append(keyName);
        }
        builtFor = null;
        return false;
    }

    @Override
    public void setCurrentCategory(boolean currentCategory) {
        super.setCurrentCategory(currentCategory);
        inp.setLength(0);
    }

    /** The modules and addons for the query, dealt into the two columns. Rebuilt on the next draw after a change. */
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
