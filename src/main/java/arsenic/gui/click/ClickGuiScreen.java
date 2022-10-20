package arsenic.gui.click;

import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.render.RenderInfo;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ClickGuiScreen extends GuiScreen implements ISerializable {

    private final Collection<Panel> panels = new ArrayList<>();

    public ClickGuiScreen() {
        List<IContainer> containers = new ArrayList<>(Arrays.asList(ModuleCategory.values()));
        containers.add(Arsenic.getInstance());

        for(IContainer c : containers) {
            Panel panel = new Panel(c.getName());

            for(IContainable ic : c.getContents()) {
                panel.addAsComponent(ic);
            }

            panels.add(panel);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderInfo selfRI = new RenderInfo(mouseX, mouseY);

        Gui.drawRect(0, 0, width, height, 0x35000000);

        super.drawScreen(mouseX, mouseY, partialTicks);
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
