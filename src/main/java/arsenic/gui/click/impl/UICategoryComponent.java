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
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5, 0.5, 0.5);
        ri.getFr().drawString(getName(), x1 * 2f, y1 * 2f, 0xFFFFFFFE);
        GlStateManager.popMatrix();

        PosInfo pi = new PosInfo(x1 + 3, y2 + 1);
        contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri) + 2));

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
