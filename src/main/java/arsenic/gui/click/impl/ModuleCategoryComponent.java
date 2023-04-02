package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import arsenic.gui.click.Component;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {
    private final ModuleCategory self;
    private boolean isCC, isHovered;
    private final List<ModuleComponent> contentsL = new ArrayList<>(), contentsR = new ArrayList<>(), contents;
    private final AnimationTimer enabledTimer = new AnimationTimer(350, () -> isCC, TickMode.SQR);
    private final AnimationTimer hoverTimer = new AnimationTimer(350, () -> isHovered, TickMode.SQR);

    public ModuleCategoryComponent(ModuleCategory category) {
        self = category;
        contents = self.getContents().stream().map(ModuleComponent::new).distinct().collect(Collectors.toList());
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

        int color = ColorUtils.setColor(enabledColor.getRGB(), 0, (int) (Math.max(enabledTimer.getPercent(), hoverTimer.getPercent())* 225));
        DrawUtils.drawRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height/4f, color);
        ri.getFr().drawYCenteredString(getName(), x1 + expandX + (width/7f), midPointY, 0xFFFFFFFE);
        return height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        isHovered = isMouseInArea(mouseX, mouseY);
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
        contentsL.forEach(moduleComponent -> pi.moveY(moduleComponent.updateComponent(pi, ri)));
    }

    public void drawRight(PosInfo pi, RenderInfo ri) {
        contentsR.forEach(moduleComponent -> pi.moveY(moduleComponent.updateComponent(pi, ri)));
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        ClickGui module = (ClickGui) (ModuleManager.Modules.CLICKGUI.getModule());
        module.getScreen().setCmcc(this);
    }

    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        this.contents.forEach(component -> component.handleClick(mouseX, mouseY, mouseButton));
    }

    public void setCurrentCategory(boolean currentCategory) {
        this.isCC = currentCategory;
    }

    @Override
    protected int getWidth(int i) {
        return 10 * (i / 100);
    }

    @Override
    protected int getHeight(int i) {
        return 5 * (i / 100);
    }
}
