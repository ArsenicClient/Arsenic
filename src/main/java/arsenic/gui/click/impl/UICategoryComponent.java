package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.UICategory;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.DimensionInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UICategoryComponent extends Component implements IContainer<ModuleCategoryComponent> {

    private final int HEIGHT = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 5;
    private final UICategory self;
    private final List<ModuleCategoryComponent> contents = new ArrayList<>();
    public UICategoryComponent(UICategory self) {
        this.self = self;
        self.getContents().forEach(category -> contents.add(new ModuleCategoryComponent(category)));
    }

    @Override
    protected int drawComponent(DimensionInfo di, RenderInfo ri) {
        int height = HEIGHT;
        int originalY = di.getY();

        RenderUtils.drawRect(di.getX(), di.getY(), di.getX1(),  di.getY() + HEIGHT, 0xFF00FF00);
        ri.getFr().drawString(getName(), di.getX(), di.getY() + (HEIGHT)/2, 0xFFFF0000);

        di.moveX(3);
        di.moveY(HEIGHT);
        contents.forEach(child -> di.moveY(child.updateComponent(di, ri)));


        return HEIGHT;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {

    }

    @Override
    public String getName() {
        return self.getName();
    }

    @Override
    public Collection<ModuleCategoryComponent> getContents() {
        return contents;
    }
}
