package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Collection;

import arsenic.utils.render.DrawUtils;
import org.jetbrains.annotations.NotNull;

import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;

public class ModuleComponent extends Component implements IContainer<PropertyComponent<?>> {
    private final Collection<PropertyComponent<?>> contents = new ArrayList<>();
    private final String name;

    public ModuleComponent(@NotNull Module self) {
        self.getProperties().forEach(property -> contents.add(property.createComponent()));
        this.name = self.getName();
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        ri.getFr().drawString(getName() + ":", x1, y1 + (height) / 2f, 0xFF2ECC71);

        float expand = width/10f;
        PosInfo pi = new PosInfo(x1 + expand, y2 + expand/2f);
        contents.forEach(child -> pi.moveY((int) (child.updateComponent(pi, ri) * 1.1)));

        expandY = pi.getY() - y2;

        return expandY + height;
    }

    public final Collection<PropertyComponent<?>> getContents() { return contents; }

    public final String getName() { return name; }

    @Override
    protected int getWidth(int i) {
        return 30 * (i / 100);
    }

}
