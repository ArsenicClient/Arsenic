package arsenic.gui.click;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.module.ModuleManager;
import arsenic.utils.functionalinterfaces.IInt;
import arsenic.utils.render.DimensionInfo;
import com.google.gson.JsonObject;

import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

public class ClickGuiScreen extends GuiScreen implements ISerializable {

    public final Color topColor = new Color(44, 62, 80), bgColor = new Color(52, 73, 94);
    private final ClickGui module;
    private final List<UICategoryComponent> components = new ArrayList<>();


    public ClickGuiScreen() {
        this.module = (ClickGui) ModuleManager.Modules.CLICKGUI.getModule();
        for (UICategory value : UICategory.values()) {
            components.add(new UICategoryComponent(value));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer());

        RenderUtils.drawRect(0, 0, width, height, 0x35000000);

        int x = width/8;
        int y = height/6;
        int x1 = width - x;
        int y1 = height - y;

        Gui.drawRect(x, y, x1, y1, 0x900000FF);

        DimensionInfo di = new DimensionInfo( x + 5, y + 25, x + 40, y + 40);
        components.forEach(component -> {
            di.moveY(component.updateComponent(di, ri));
        });

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {

        //panels.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public final IFontRenderer getFontRenderer() {
        return module.customFont.getValue() ? Arsenic.getInstance().getFonts().MEDIUM_FR : (IFontRenderer) mc.fontRendererObj;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void loadFromJson(JsonObject obj) {

    }
    
    @Override
	public JsonObject saveInfoToJson(JsonObject obj) {
		return obj;
	}

    @Override
    public JsonObject addToJson(JsonObject obj) {
        return obj;
    }

    @Override
    public String getJsonKey() {
        return "clickgui";
    }

}
