package arsenic.gui.click.impl;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.click.Component;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;

import java.util.Collection;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {

    private int colour = 0xFFFFFF00;
    private boolean currentCategory;
    private final int HEIGHT = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 3;
    private final ModuleCategory self;

    public ModuleCategoryComponent(ModuleCategory category) {
        self = category;

    }

    @Override
    public String getName() {
        return self.getName();
    }

    @Override
    public Collection<ModuleComponent> getContents() {
        return null;
    }

    @Override
    protected int drawComponent(RenderInfo ri) {
        RenderUtils.drawRect(x1, y1, x2,  y2, 0xFF00FF00);
        ri.getFr().drawString(getName(), x1, y1 + (height)/2, colour);

        //PosInfo pi = new PosInfo(x1 + 3, y2 + 1);
        //contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri) + 2));

        return height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        ClickGui module = (ClickGui) (ModuleManager.Modules.CLICKGUI.getModule());
        module.getScreen().setCmcc(this);
    }

    public void setCurrentCategory(boolean currentCategory) {
        this.currentCategory = currentCategory;
        colour = currentCategory ? 0xFFFF00FF : 0xFFFFFF00;
    }
}
