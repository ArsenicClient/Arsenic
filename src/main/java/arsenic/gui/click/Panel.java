package arsenic.gui.click;

import java.util.ArrayList;
import java.util.Collection;

import arsenic.gui.click.impl.ContainerComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.IExpandable;
import arsenic.utils.io.MouseButton;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;

public class Panel implements IExpandable<Component> {

    protected final int WIDTH = 118, HEIGHT = 14;
    protected final Minecraft mc = Minecraft.getMinecraft();

    private final String name;
    private final Collection<Component> components = new ArrayList<>();

    private final ClickGui module;
    private final ClickGuiScreen screen;

    private boolean expanded;
    private int x, y;

    public Panel(String name, int x, int y, ClickGuiScreen screen) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.module = Arsenic.getInstance().getModuleManager()
                .getModule(ClickGui.class);
        this.screen = screen;
    }

    public final String getName() {
        return name;
    }

    public final Collection<Component> getComponents() {
        return components;
    }

    public final void addComponent(Component component) {
        components.add(component);
    }

    public final void addAsComponent(IContainable ic) {
        if(ic instanceof IContainer) {
            if(ic instanceof Module) {
                components.add(new ModuleComponent(((Module) ic), new ArrayList<>()));
            } else {
                components.add(new ContainerComponent(((IContainer) ic), new ArrayList<>()));
            }
        } else {
            // todo this shit when i eventually do setting components
        }
    }

    public final void handleClick(int mouseX, int mouseY, int mouseButton) {
        double expand = module.expandTop.getValue().getInput();
        MouseButton button = MouseButton.getButton(mouseButton);

        if(mouseX > (x-expand) && mouseX < (x+WIDTH+expand) && mouseY > y-1 && mouseY < y+HEIGHT) {
            switch (button) {
                case RIGHT:
                    setExpanded(!expanded);
                    break;
            }
        }
    }

    public final void handleRender(RenderInfo ri) {
        double expand = module.expandTop.getValue().getInput();

        RenderUtils.drawRect(x-expand, y-1, x+WIDTH+expand, y+HEIGHT, screen.topColor);
        ri.getFr().drawStringWithShadow("E: " + expanded, x, y, -1);

    }

    public final int getX() {
        return x;
    }

    public final void setX(int x) {
        this.x = x;
    }

    public final int getY() {
        return y;
    }

    public final void setY(int y) {
        this.y = y;
    }

    @Override
    public final boolean isExpanded() {
        return expanded;
    }

    @Override
    public final void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public final Collection<Component> getContents() {
        return components;
    }

    public final ClickGui getModule() {
        return module;
    }

    public final ClickGuiScreen getScreen() {
        return screen;
    }

}
