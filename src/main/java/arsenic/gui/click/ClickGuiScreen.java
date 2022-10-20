package arsenic.gui.click;

import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClickGuiScreen extends GuiScreen implements ISerializable {

    private final List<IContainer> containers = new ArrayList<>();

    {
        containers.addAll(Arrays.asList(ModuleCategory.values()));
        containers.add(Arsenic.getInstance());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {

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
