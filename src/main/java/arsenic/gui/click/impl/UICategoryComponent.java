package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.click.UICategory;
import arsenic.utils.functionalinterfaces.IInt;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UICategoryComponent extends Component implements IContainer<ModuleCategoryComponent> {

    private final IInt heightP = (i -> Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 5);
    private final UICategory self;
    private final List<ModuleCategoryComponent> contents = new ArrayList<>();
    public UICategoryComponent(UICategory self) {
        this.self = self;
        self.getContents().forEach(category -> contents.add(new ModuleCategoryComponent(category)));
    }

    @Override
    protected int drawComponent(RenderInfo ri) {
        RenderUtils.drawRect(x1, y1, x2,  y2, 0xFF00FF00);
        ri.getFr().drawString(getName(), x1, y1 + (height)/2, 0xFFFF0000);

        PosInfo pi = new PosInfo(x1 + 3, y2 + 1);
        contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri) + 2));

        wholeHeight = pi.getY() - y1;
        wholeY2 = pi.getY();

        return wholeHeight;
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
