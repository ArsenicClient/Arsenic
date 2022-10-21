package arsenic.gui.click;

import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiScreen;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ClickGuiScreen extends GuiScreen implements ISerializable {

    public final Color topColor = new Color(44, 62, 80), bgColor = new Color(52, 73, 94);

    private final Collection<Panel> panels = new ArrayList<>();
    private final ClickGui module;

    public ClickGuiScreen() {
        List<IContainer> containers = new ArrayList<>(Arrays.asList(ModuleCategory.values()));
        containers.add(Arsenic.getInstance());

        int i = 0;
        for(IContainer c : containers) {
            Panel panel = new Panel(c.getName(), 20 + (128*i), 25, this);

            for(IContainable ic : c.getContents()) {
                panel.addAsComponent(ic);
            }

            panels.add(panel);
            i++;
        }

        this.module = Arsenic.getInstance().getModuleManager()
                .getModule(ClickGui.class);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderInfo selfRI = new RenderInfo(mouseX, mouseY, getFontRenderer());

        RenderUtils.drawRect(0, 0, width, height, 0x35000000);

        panels.forEach(panel -> panel.handleRender(selfRI));

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {

        panels.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public final IFontRenderer getFontRenderer() {
        return module.customFont.getValue()? Arsenic.getInstance().getFonts().MEDIUM_FR : (IFontRenderer) mc.fontRendererObj;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void loadFromJson(JsonObject obj) {

    }

    @Override
    public JsonObject saveToJson() {
        return new JsonObject();
    }

    @Override
    public String getJsonKey() {
        return "clickgui";
    }

}
