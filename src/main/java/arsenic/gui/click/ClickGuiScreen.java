package arsenic.gui.click;

import com.google.gson.JsonObject;
import arsenic.utils.interfaces.ISerializable;
import net.minecraft.client.gui.GuiScreen;

public class ClickGuiScreen extends GuiScreen implements ISerializable {

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
