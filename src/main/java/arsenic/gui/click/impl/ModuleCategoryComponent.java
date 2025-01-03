package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {
    protected final ModuleCategory self;
    protected float scroll, maxHeight;
    protected boolean isCC, isHovered;
    protected List<ModuleComponent> contentsL = new ArrayList<>();
    protected List<ModuleComponent> contentsR = new ArrayList<>();
    protected final List<ModuleComponent> contents;
    protected final AnimationTimer enabledTimer = new AnimationTimer(350, () -> isCC, TickMode.SQR);
    protected final AnimationTimer hoverTimer = new AnimationTimer(350, () -> isHovered, TickMode.SQR);

    public ModuleCategoryComponent(ModuleCategory category) {
        self = category;
        contents = self.getContents().stream().map(ModuleComponent::new).sorted(Comparator.comparing(ModuleComponent::getName)).collect(Collectors.toList());
        contents.forEach(module -> {
            if ((contentsL.size() + contentsR.size()) % 2 == 0) {
                contentsL.add(module);
            } else {
                contentsR.add(module);
            }
        });
    }

    @Override
    public String getName() { return self.getName(); }

    @Override
    public Collection<ModuleComponent> getContents() { return contents; }

    @Override
    protected float drawComponent(RenderInfo ri) {
        expandX = hoverTimer.getPercent() * (width/14f);

        int mainC = ColorUtils.setColor(getEnabledColor(), 0, (int) (Math.max(enabledTimer.getPercent(), hoverTimer.getPercent())* 225));
        int gradientC = ColorUtils.setColor(getGradientColor(), 0, (int) (Math.max(enabledTimer.getPercent(), hoverTimer.getPercent())* 225));

        DrawUtils.drawGradientRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height/4f, mainC,mainC,gradientC,gradientC);
        ri.getFr().drawString(getName(), x1 + expandX + (width/7f), midPointY, getWhite(), ri.getFr().CENTREY);
        return height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        isHovered = isMouseInArea(mouseX, mouseY);
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
        maxHeight = 0;
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

    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        this.contents.forEach(component -> component.handleClick(mouseX, mouseY, mouseButton));
    }

    public void setCurrentCategory(boolean currentCategory) {
        this.isCC = currentCategory;
    }

    public void scroll(int scroll) {
        this.scroll += scroll;
        this.scroll = Math.max(Math.min(0, this.scroll), -maxHeight);
    }

    public void subtractFromMaxScrollHeight(float f) {
        maxHeight = maxHeight - f;
        maxHeight = Math.max(0, maxHeight);
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
