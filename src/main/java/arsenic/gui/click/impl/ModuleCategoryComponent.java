package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.GuiStyle;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleTier;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {
    protected final ModuleCategory self;
    protected final Identifier icon;
    protected float scroll, targetScroll, maxHeight;
    protected boolean isCC, isHovered;
    protected List<ModuleComponent> contentsL = new ArrayList<>();
    protected List<ModuleComponent> contentsR = new ArrayList<>();
    protected final List<ModuleComponent> contents;
    protected final AnimationTimer enabledTimer = new AnimationTimer(350, () -> isCC, TickMode.SINE);
    protected final AnimationTimer hoverTimer = new AnimationTimer(350, () -> isHovered, TickMode.SINE);

    public ModuleCategoryComponent(ModuleCategory category) {
        self = category;
        icon = Identifier.fromNamespaceAndPath("arsenic", "icons/" + self.getName().toLowerCase() + ".png");
        contents = self.getContents().stream().map(ModuleComponent::new).sorted(Comparator.comparing(ModuleComponent::getName)).collect(Collectors.toList());
refreshListing();
    }

    @Override
    public String getName() { return self.getName(); }

    private boolean listedWithMore;

    protected boolean followsMoreToggle() {
        return true;
    }

    private boolean isListed(ModuleComponent module) {
        return !followsMoreToggle() || GuiStyle.get().isShowMoreModules()
                || module.getModule().getTier() == ModuleTier.CORE;
    }

    public void refreshListing() {
        listedWithMore = GuiStyle.get().isShowMoreModules();
        contentsL.clear();
        contentsR.clear();
        for (ModuleComponent module : contents) {
            if (!isListed(module))
                continue;
            if ((contentsL.size() + contentsR.size()) % 2 == 0)
                contentsL.add(module);
            else
                contentsR.add(module);
        }
        scroll = 0;
        targetScroll = 0;
    }

    @Override
    public Collection<ModuleComponent> getContents() { return contents; }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float anim = Math.max(enabledTimer.getPercent(), hoverTimer.getPercent());
        expandX = anim * (width / 40f);
        int mainC = ColorUtils.setColor(getEnabledColor(), 0, (int) (anim * 255));
        int gradientC = ColorUtils.setColor(getGradientColor(), 0, (int) (anim * 255));


        if (anim > 0.01f)
            DrawUtils.drawShadow(x1 + expandX, y1, x2 + expandX, y2, height / 4f,
                    arsenic.gui.click.GuiStyle.shadowSpread(height * 0.18f),
                    arsenic.gui.click.GuiStyle.shadowAlpha((int) (130 * anim)), 4);

        DrawUtils.drawGradientRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height / 4f, mainC, mainC, gradientC, gradientC);

        int onPanel = arsenic.gui.click.UITheme.readableOn(ThemeManager.getModuleBackground());
        int onAccent = arsenic.gui.click.UITheme.readableOn(getEnabledColor());
        int foreground = arsenic.gui.click.UITheme.mix(onPanel, onAccent, anim);

        float iconSize = ri.getFr().getHeight("|") * (ri.getGuiScreen().height / 300f);
        float iconX = x1 + (width / 7f) + expandX - iconSize;
        float iconY = midPointY - iconSize / 2f;
        DrawUtils.drawTexture(icon, iconX, iconY, iconSize, iconSize, 0xFF000000 | foreground);

        ri.getFr().drawString(getName(), iconX + iconSize + 2, midPointY, foreground, ri.getFr().CENTREY);
        return height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        isHovered = isMouseInArea(mouseX, mouseY);
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
if (followsMoreToggle() && listedWithMore != GuiStyle.get().isShowMoreModules())            refreshListing();
        maxHeight = 0;
        scroll += (targetScroll - scroll) * arsenic.gui.click.GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;
        drawSection(contentsL, pi, ri);
    }

    public void drawRight(PosInfo pi, RenderInfo ri) {
        drawSection(contentsR, pi, ri);
    }

    private void drawSection(List<ModuleComponent> l, PosInfo pi, RenderInfo ri) {
        pi.moveY(scroll);
        float temp = pi.getY();
        float expand = width/10f;
        pi.moveY(expand);
        l.forEach(moduleComponent -> pi.moveY(moduleComponent.updateComponent(pi, ri) + expand));
        temp = pi.getY() - temp;
        if(temp > maxHeight)
            maxHeight = temp;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        Arsenic.getArsenic().getClickGuiScreen().setCmcc(this);
    }

    @Override
    protected void playClickSound() {
        arsenic.utils.java.SoundUtils.chordCategory();
    }

    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        for (ModuleComponent component : new ArrayList<>(contentsL))
            component.handleClick(mouseX, mouseY, mouseButton);
        for (ModuleComponent component : new ArrayList<>(contentsR))
            component.handleClick(mouseX, mouseY, mouseButton);
    }

    public void setCurrentCategory(boolean currentCategory) {
        this.isCC = currentCategory;
        if (currentCategory) {
            scroll = 0;
            targetScroll = 0;
        }
    }

    public void scroll(int scroll) {
        this.targetScroll += scroll;
        this.targetScroll = Math.max(Math.min(0, this.targetScroll), -maxHeight);
    }

    public void subtractFromMaxScrollHeight(float f) {
        maxHeight = maxHeight - f;
        maxHeight = Math.max(0, maxHeight);
    }

    public void drawScrollbar(float x, float y, float barHeight, RenderInfo ri) {
        if (maxHeight <= 0) return;
        float scrollbarHeight = barHeight * (barHeight / (barHeight + maxHeight));
        float scrollbarY = y + (this.scroll / -maxHeight) * (barHeight - scrollbarHeight);
        DrawUtils.drawRoundedRect(x - 3, y, x - 1, y + barHeight, 1f, ThemeManager.getScrollbarTrack());
        DrawUtils.drawRoundedRect(x - 3, scrollbarY, x - 1, scrollbarY + scrollbarHeight, 1f, ThemeManager.getScrollbarThumb());
    }

    @Override
    public int getWidth(int i) {
        return 10 * (i / 100);
    }

    @Override
    public int getHeight(int i) {
        return 5 * (i / 100);
    }
}
