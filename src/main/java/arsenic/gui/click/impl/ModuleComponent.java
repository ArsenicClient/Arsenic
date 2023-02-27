package arsenic.gui.click.impl;

import java.util.ArrayList;
import java.util.Collection;

import arsenic.utils.functionalinterfaces.IInt;
import arsenic.utils.render.DrawUtils;
import org.jetbrains.annotations.NotNull;

import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

public class ModuleComponent extends Component implements IContainer<PropertyComponent> {
    private final Module self;
    private final Collection<PropertyComponent> contents = new ArrayList<>();
    private final String name;

    public ModuleComponent(@NotNull Module self) {
        this.self = self;
        self.getProperties().forEach(property -> contents.add(property.createComponent())); // streaming and mapping
                                                                                            // didn't keep order so i
                                                                                            // had to do this
        this.name = self.getName();
    }

    @Override
    protected int drawComponent(RenderInfo ri) {
        DrawUtils.drawRect(x1, y1, x2, y2, 0xFF00FF00);
        ri.getFr().drawString(getName(), x1, y1 + (height) / 2, 0xFF00FFFF);

        PosInfo pi = new PosInfo(x1 + 3, y2 + 1);
        contents.forEach(child -> pi.moveY((int) (child.updateComponent(pi, ri) * 1.1)));

        expandY = pi.getY() - y1;

        return expandY;
    }

    public final Collection<PropertyComponent> getContents() { return contents; }

    public final String getName() { return name; }

    @Override
    protected int getWidth(int i) {
        return 35 * (i / 100);
    }


}
