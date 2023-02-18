package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.DimensionInfo;
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
    protected int drawComponent(DimensionInfo di, RenderInfo ri) {
        RenderUtils.drawRect(di.getX(), di.getY(), di.getX1(),  di.getY() + HEIGHT, 0xFFFFFF00);
        ri.getFr().drawString(getName(), di.getX(), di.getY() + (HEIGHT)/2, 0xFFFF0000);
        return HEIGHT;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {

    }
}
