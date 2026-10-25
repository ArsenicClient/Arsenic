package arsenic.gui.click.impl;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import arsenic.gui.click.Component;
import arsenic.gui.click.UICategory;
import arsenic.gui.design.M3;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.renderer.entity.Render;

public class UICategoryComponent extends Component implements IContainer<ModuleCategoryComponent> {
    private final UICategory self;
    private final List<ModuleCategoryComponent> contents = new ArrayList<>();
    private boolean hovered;
    private final AnimationTimer hoverTimer = new AnimationTimer(350, () -> hovered, TickMode.SINE);

    public UICategoryComponent(UICategory self) {
        this.self = self;
        self.getContents().forEach(category -> contents.add(new ModuleCategoryComponent(category)));
        if (getName().equals("Misc")) {
            contents.add(new GuiComponent());
            contents.add(new ConfigsComponent());
        }
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        // no group heading ("COMBAT" above "Combat" said nothing new), and no gap: one continuous list
        PosInfo pi = new PosInfo(x1, y2);
        contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri)));
        expandY = pi.getY() - y2;

        return expandY + height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        hovered = arsenic.gui.design.Hover.test(this, x1, y1, x2, y2);
    }

    @Override
    public String getName() { return self.getName(); }

    @Override
    public Collection<ModuleCategoryComponent> getContents() { return contents; }

    @Override
    public int getHeight(int i) {
        return 0;
    }

    @Override
    public int getWidth(int i) {
        return 174;
    }
}
