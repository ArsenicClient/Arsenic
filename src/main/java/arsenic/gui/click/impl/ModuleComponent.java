package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.module.property.Property;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class ModuleComponent extends Component implements IContainer<PropertyComponent> {

    private final Module self;
    private final Collection<PropertyComponent> contents = new ArrayList<>();
    private final String name;
    public ModuleComponent(@NotNull Module self) {
        this.self = self;
        self.getProperties().forEach(property -> contents.add(property.createComponent())); //streaming and mapping didn't keep order so i had to do this
        this.name = self.getName();
    }

    @Override
    protected int drawComponent(RenderInfo ri) {
        RenderUtils.drawRect(x1, y1, x2,  y2, 0xFF00FF00);
        ri.getFr().drawString(getName(), x1, y1 + (height)/2, 0xFF00FFFF);

        PosInfo pi = new PosInfo(x1 + 3, y2 + 1);
        contents.forEach(child -> pi.moveY((int) (child.updateComponent(pi, ri) * 1.1)));

        expandY = pi.getY() - y1;

        return expandY;
    }

    public final Collection<PropertyComponent> getContents() {
        return contents;
    }

    public final String getName() {
        return name;
    }

}
