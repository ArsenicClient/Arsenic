package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.GuiStyle;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Components;
import arsenic.gui.design.M3Icons;
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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {
    protected final ModuleCategory self;
    protected float scroll, targetScroll, maxHeight;
    protected boolean isCC, isHovered;
    protected List<ModuleComponent> contentsL = new ArrayList<>();
    protected List<ModuleComponent> contentsR = new ArrayList<>();
    protected final List<ModuleComponent> contents;
    protected final AnimationTimer enabledTimer = new AnimationTimer(350, () -> isCC, TickMode.SINE);
    protected final AnimationTimer hoverTimer = new AnimationTimer(350, () -> isHovered, TickMode.SINE);

    public ModuleCategoryComponent(ModuleCategory category) {
        self = category;
        contents = self.getContents().stream().map(ModuleComponent::new).sorted(Comparator.comparing(ModuleComponent::getName)).collect(Collectors.toList());
refreshListing();
    }

    @Override
    public String getName() { return self.getName(); }

    private int listedSignature;

    private static int tierSignature() {
        return (GuiStyle.get().isShowBlatant() ? 1 : 0);
    }

    protected boolean followsMoreToggle() {
        return true;
    }

    private boolean isListed(ModuleComponent module) {
        return !followsMoreToggle()
                || GuiStyle.get().isTierShown(module.getTier());
    }

    public void refreshListing() {
        listedSignature = tierSignature();
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
        expandX = 0;
        M3Components.navItem(ri.getFr(), getName(), M3Icons.category(getName()), x1, y1, x2, y2,
                enabledTimer.getPercent(), hoverTimer.getPercent(), true);
        return height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        isHovered = isMouseInArea(mouseX, mouseY);
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
        if (followsMoreToggle() && listedSignature != tierSignature())
            refreshListing();
        maxHeight = 0;
        scroll += (targetScroll - scroll) * arsenic.gui.click.GuiStyle.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;
        drawSection(contentsL, pi, ri);
    }

    public void drawRight(PosInfo pi, RenderInfo ri) {
        drawSection(contentsR, pi, ri);
    }

    protected void drawSection(List<? extends Component> l, PosInfo pi, RenderInfo ri) {
        pi.moveY(scroll);
        float temp = pi.getY();
        float gap = 5f;
        l.forEach(moduleComponent -> pi.moveY(moduleComponent.updateComponent(pi, ri) + gap));
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
        M3Components.scrollbar(x, y, barHeight, maxHeight, scroll);
    }

    @Override
    public int getWidth(int i) {
        return 174;
    }

    @Override
    public int getHeight(int i) {
        return 36;
    }
}
