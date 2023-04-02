package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import arsenic.gui.click.Component;
import arsenic.gui.click.UICategory;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import net.minecraft.client.renderer.GlStateManager;

public class UICategoryComponent extends Component implements IContainer<ModuleCategoryComponent> {
    private final UICategory self;
    private final List<ModuleCategoryComponent> contents = new ArrayList<>();

    public UICategoryComponent(UICategory self) {
        this.self = self;
        self.getContents().forEach(category -> contents.add(new ModuleCategoryComponent(category)));
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        ri.getFr().drawYCenteredString(getName(), x1, midPointY, enabledColor.getRGB());

        PosInfo pi = new PosInfo(x1, y2);
        contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri) * 1.1f));
        expandY = pi.getY() - y1;

        return expandY + height;
    }

    @Override
    public String getName() { return self.getName(); }

    @Override
    public Collection<ModuleCategoryComponent> getContents() { return contents; }

    @Override
    protected int getHeight(int i) {
        return 5 * (i / 100);
    }

    @Override
    protected int getWidth(int i) {
        return 9 * (i / 100);
    }
}
