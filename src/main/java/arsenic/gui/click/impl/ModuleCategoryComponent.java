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
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {

    private int colour = 0xFFFFFF00;
    // private final int HEIGHT =
    // Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 3;
    private final ModuleCategory self;
    private final List<ModuleComponent> contentsL = new ArrayList<>(), contentsR = new ArrayList<>(), contents;

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
    protected int drawComponent(RenderInfo ri) {
        DrawUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
        ri.getFr().drawString(getName(), x1, y1 + (height) / 2, colour);
        return height;
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
        contentsL.forEach(moduleComponent -> moduleComponent.updateComponent(pi, ri));
    }

    public void drawRight(PosInfo pi, RenderInfo ri) {
        contentsR.forEach(moduleComponent -> moduleComponent.updateComponent(pi, ri));
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        ClickGui module = (ClickGui) (ModuleManager.Modules.CLICKGUI.getModule());
        module.getScreen().setCmcc(this);
    }

    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        this.contents.forEach(component -> component.handleClick(mouseX, mouseY, mouseButton));
    }

    public void setCurrentCategory(boolean currentCategory) { colour = currentCategory ? 0xFFFF00FF : 0xFFFFFF00; }
}
