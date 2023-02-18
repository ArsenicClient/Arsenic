package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;

import java.util.Collection;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {

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
        ri.getFr().drawString(getName(), x1, y1 + (height)/2, 0xFFFFFF00);

        //PosInfo pi = new PosInfo(x1 + 3, y2 + 1);
        //contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri) + 2));

        return height;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {

    }
}
