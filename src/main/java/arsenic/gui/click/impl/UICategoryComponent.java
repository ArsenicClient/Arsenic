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
        float hoverPct = hoverTimer.getPercent();
        ri.getFr().drawString(getName().toUpperCase(java.util.Locale.ROOT), x1 + 12f, midPointY,
                M3.mix(M3.alpha(M3.onSurfaceVariant(), 185), M3.onSurfaceVariant(), hoverPct * 0.4f),
                ri.getFr().CENTREY);
        RenderUtils.resetColorText();

        PosInfo pi = new PosInfo(x1, y2);
        contents.forEach(child -> pi.moveY(child.updateComponent(pi, ri)));
        expandY = pi.getY() - y2;

        return expandY + height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        hovered = isMouseInArea(mouseX, mouseY);
    }

    @Override
    public String getName() { return self.getName(); }

    @Override
    public Collection<ModuleCategoryComponent> getContents() { return contents; }

    @Override
    public int getHeight(int i) {
        return 26;
    }

    @Override
    public int getWidth(int i) {
        return 174;
    }
}
