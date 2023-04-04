package arsenic.gui.click;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.render.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// allow escape to bind to none
// make font scale
//make things not click when they arent in view

public class ClickGuiScreen extends CustomGuiScreen {
    private ClickGui module;
    private List<UICategoryComponent> components;
    private List<IVoidFunction> renderLastList = new ArrayList<>();
    private ModuleCategoryComponent cmcc;
    private IAlwaysClickable alwaysClickedComponent;
    private IAlwaysKeyboardInput alwaysKeyboardInput;
    private ResourceLocation logoPath;

    public void init() {
        logoPath = RenderUtils.getResourcePath("/assets/arsenic/arseniclogo.png");
        module = (ClickGui) ModuleManager.Modules.CLICKGUI.getModule();
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
    }

    @Override
    public void drawScr(int mouseX, int mouseY, float partialTicks) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);

        // makes whole screen slightly darker
        // to be replaced with a blur
        DrawUtils.drawRect(0, 0, width, height, 0x35000000);

        int x = width / 8;
        int y = height / 6;
        int x1 = width - x;
        int y1 = height - y;

        // main container
        RenderUtils.resetColor();
        DrawUtils.drawBorderedRoundedRect(x, y, x1, y1, 1f, 1f, 0xFF2ECC71, 0xDD0C0C0C);

        int vLineX = 2 * x;
        int hLineY = (int) (1.5 * y);

        // vertical line
        DrawUtils.drawRect(vLineX, y, vLineX + 1.0f, y1, 0xFF2ECC71);
        // horizontal line
        DrawUtils.drawRect(x, hLineY, x1, hLineY + 1.0f, 0xFF2ECC71);

        //logo
        mc.getTextureManager().bindTexture(logoPath);
        int tempExpand = (int) (x * 0.1f);
        Gui.drawModalRectWithCustomSizedTexture(x + tempExpand, y + tempExpand, 0, 0, vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2), vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2) );

        // draws each module category component
        PosInfo pi = new PosInfo(x + 5, hLineY + 5);
        components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));

        // makes the currently selected category component draw its modules
        ScissorUtils.subScissor(vLineX, hLineY, x1, y1, 2);
        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        cmcc.drawRight(piR, ri);
        cmcc.subtractFromMaxScrollHeight(y1 - hLineY);

        renderLastList.forEach(IVoidFunction::voidFunction);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        ScissorUtils.resetScissor();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if(alwaysClickedComponent != null) {
            if(alwaysClickedComponent.clickAlwaysClickable(mouseX, mouseY, mouseButton)) return;
        }
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        cmcc.clickChildren(mouseX, mouseY, mouseButton);
    }

    public void setCmcc(ModuleCategoryComponent mcc) {
        cmcc.setCurrentCategory(false);
        mcc.setCurrentCategory(true);
        cmcc = mcc;
    }

    public <T extends Component & IAlwaysClickable> void setAlwaysClickedComponent(T component) {
        if(alwaysClickedComponent != null)
            alwaysClickedComponent.setNotAlwaysClickable();
        this.alwaysClickedComponent = component;
    }

    public <T extends Component & IAlwaysKeyboardInput> void setAlwaysInputComponent(T component) {
        if(alwaysKeyboardInput != null)
            alwaysKeyboardInput.setNotAlwaysRecieveInput();
        this.alwaysKeyboardInput = component;
    }

    public final IFontRenderer getFontRenderer() {
        return module.customFont.getValue() ? Arsenic.getInstance().getFonts().MEDIUM_FR
                : (IFontRenderer) mc.fontRendererObj;
    }

    public void addToRenderLastList(IVoidFunction v) {
        renderLastList.add(v);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();
        i = Integer.compare(i, 0);
        cmcc.scroll(i * 5);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        if(alwaysKeyboardInput != null)
            alwaysKeyboardInput.recieveInput(keyCode);
    }

    @Override
    public void mouseRelease(int mouseX, int mouseY, int state) {
        components.forEach(component -> component.handleRelease(mouseX, mouseY, state));
        super.mouseRelease(mouseX, mouseY, state);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

}
