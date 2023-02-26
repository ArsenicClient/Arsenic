package arsenic.gui.click;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.interfaces.ISerializable;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

public class ClickGuiScreen extends GuiScreen implements ISerializable {
    private final ClickGui module;
    private final List<UICategoryComponent> components;
    private ModuleCategoryComponent cmcc;

    public ClickGuiScreen() {
        this.module = (ClickGui) ModuleManager.Modules.CLICKGUI.getModule();
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);

        // makes whole screen slightly darker
        RenderUtils.drawRect(0, 0, width, height, 0x35000000);

        int x = width / 8;
        int y = height / 6;
        int x1 = width - x;
        int y1 = height - y;

        // main container
        Gui.drawRect(x, y, x1, y1, 0x900000FF);

        int vLineX = 2 * x;
        int hLineY = (int) (1.5 * y);

        // vertical line
        Gui.drawRect(vLineX, y, vLineX + 1, y1, 0xFFFF0000);
        // horizontal line
        Gui.drawRect(x, hLineY, x1, hLineY + 1, 0xFFFF0000);

        // draws each module category component
        PosInfo pi = new PosInfo(x + 5, hLineY + 5);
        components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));

        // makes the currently selected category component draw its modules
        PosInfo piL = new PosInfo(vLineX + 5, hLineY + 5);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2, hLineY + 5);
        cmcc.drawRight(piR, ri);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        cmcc.clickChildren(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void setCmcc(ModuleCategoryComponent mcc) {
        cmcc.setCurrentCategory(false);
        mcc.setCurrentCategory(true);
        cmcc = mcc;
    }

    public final IFontRenderer getFontRenderer() {
        return module.customFont.getValue() ? Arsenic.getInstance().getFonts().MEDIUM_FR
                : (IFontRenderer) mc.fontRendererObj;
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
    public String getJsonKey() { return "clickgui"; }

}
