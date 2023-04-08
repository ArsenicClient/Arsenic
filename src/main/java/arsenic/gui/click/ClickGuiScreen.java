package arsenic.gui.click;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.font.ScalableFontRenderer;
import arsenic.utils.functionalinterfaces.IVoidFunction;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.render.*;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.Timer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// allow escape to bind to none

public class ClickGuiScreen extends CustomGuiScreen {
    private ClickGui module;
    private List<UICategoryComponent> components;
    private List<IVoidFunction> renderLastList = new ArrayList<>();
    private ModuleCategoryComponent cmcc;
    private IAlwaysClickable alwaysClickedComponent;
    private IAlwaysKeyboardInput alwaysKeyboardInput;
    private ResourceLocation logoPath;
    private AnimationTimer blurTimer = new AnimationTimer(500, () -> true);
    private int vLineX, hLineY, x1, y1;

    public void init() {
        logoPath = RenderUtils.getResourcePath("/assets/arsenic/arseniclogo.png");
        module = (ClickGui) ModuleManager.Modules.CLICKGUI.getModule();
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
        blurTimer = new AnimationTimer(500, () -> true);
    }

    @Override
    public void drawScr(int mouseX, int mouseY, float partialTicks) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);
        getFontRenderer().setScale(height/480f);

        // makes whole screen slightly darker
        // to be replaced with a blur
        BlurUtils.blur(2 * blurTimer.getPercent(),2 * blurTimer.getPercent());
        DrawUtils.drawRect(0, 0, width, height, 0x35000000);

        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;

        // main container
        RenderUtils.resetColor();
        DrawUtils.drawBorderedRoundedRect(x, y, x1, y1, 1f, 1f, 0xFF2ECC71, 0xDD0C0C0C);

        vLineX = 2 * x;
        hLineY = (int) (1.5 * y);

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
        ScissorUtils.subScissor(vLineX + 1, hLineY, x1, y1, 2);
        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        cmcc.drawRight(piR, ri);
        cmcc.subtractFromMaxScrollHeight(y1 - hLineY);

        renderLastList.forEach(IVoidFunction::voidFunction);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        ScissorUtils.resetScissor();
        getFontRenderer().resetScale();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if(alwaysClickedComponent != null) {
            if(alwaysClickedComponent.clickAlwaysClickable(mouseX, mouseY, mouseButton)) return;
        }
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        if(mouseX > vLineX && mouseX < x1 && mouseY > hLineY && mouseY < y1)
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

    public final ScalableFontRenderer<?> getFontRenderer() {
        return module.customFont.getValue() ? Arsenic.getInstance().getFonts().MEDIUM_FR.getScalableFontRenderer() : ((IFontRenderer) mc.fontRendererObj).getScalableFontRenderer();
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
        if(alwaysKeyboardInput != null)
            if(alwaysKeyboardInput.recieveInput(keyCode))
                return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseRelease(int mouseX, int mouseY, int state) {
        components.forEach(component -> component.handleRelease(mouseX, mouseY, state));
        super.mouseRelease(mouseX, mouseY, state);
    }

    @Override
    public void onResize(Minecraft mcIn, int p_175273_2_, int p_175273_3_) {
        super.onResize(mcIn, p_175273_2_, p_175273_3_);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Arsenic.getArsenic().getConfigManager().saveConfig();
    }
}
