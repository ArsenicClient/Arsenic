package arsenic.gui.click;

import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

public class ClickGuiScreen extends GuiScreen implements ISerializable {


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
