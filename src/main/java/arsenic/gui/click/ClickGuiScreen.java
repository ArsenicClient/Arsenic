package arsenic.gui.click;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.ModuleComponent;
import arsenic.gui.click.impl.SearchComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.module.impl.visual.ClickGui;
import arsenic.module.impl.visual.ClickGui.LogoMode;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysClickable;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// allow escape to bind to none

public class ClickGuiScreen extends CustomGuiScreen {
    private ClickGui module;
    private List<UICategoryComponent> components;

    private ModuleCategoryComponent searchComponent;
    private final List<Runnable> renderLastList = new ArrayList<>();
    private ModuleCategoryComponent cmcc,prevCmcc;
    private IAlwaysClickable alwaysClickedComponent;
    private IAlwaysKeyboardInput alwaysKeyboardInput;
    private int vLineX, hLineY, x1, y1;

    private long openTime;
    private static final int OPEN_ANIMATION_DURATION = 400;

    //called once
    public void init(ClickGui clickGui) {
        components = Arrays.stream(UICategory.values()).map(UICategoryComponent::new).distinct()
                .collect(Collectors.toList());
        cmcc = (ModuleCategoryComponent) components.get(0).getContents().toArray()[0];
        cmcc.setCurrentCategory(true);
        this.module = clickGui;
        searchComponent = new SearchComponent(ModuleCategory.SEARCH);
    }

    //called every time the ui is created
    @Override
    public void doInit() {
        super.doInit();
        openTime = System.currentTimeMillis();
    }

    public void drawBloom() {
        if (getFontRenderer() == null)
            return;
        rescale(this.scale);
        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;

        // Glow fades in after the scale animation is mostly done
        float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
        float glowFactor = Math.max(0, Math.min(1, (openProgress - 0.5f) / 0.5f));
        int glowAlpha = (int) (glowFactor * 255);

        RenderUtils.resetColor();
        int mainC = ColorUtils.setColor(ThemeManager.getMainColor(), 0, glowAlpha);
        int gradientC = ColorUtils.setColor(ThemeManager.getGradientColor(), 0, glowAlpha);
        ((SearchComponent) searchComponent).setupGlowAndBlur(glowAlpha);
        DrawUtils.drawGradientRoundedRect(x, y, x1, y1, 30f, mainC,mainC,gradientC, gradientC);
        rescaleMC();
    }

    @Override
    public void drawScr(int mouseX, int mouseY, float partialTicks) {
        RenderInfo ri = new RenderInfo(mouseX, mouseY, getFontRenderer(), this);
        getFontRenderer().setScale(height/450f);
        int x = width / 8;
        int y = height / 6;
        x1 = width - x;
        y1 = height - y;
        ClickGui clickGui = Arsenic.getArsenic().getModuleManager().getModuleByClass(ClickGui.class);
        ResourceLocation logoPath = clickGui.logoMode.getValue() == ClickGui.LogoMode.MODERN
                ? Arsenic.getArsenic().getThemeManager().getCurrentTheme().getAltLogoPath()
                : Arsenic.getArsenic().getThemeManager().getCurrentTheme().getLogoPath();

        float openProgress = Math.min(1f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIMATION_DURATION);
        float eased = 1f - (float) Math.pow(1f - openProgress, 3);

        GlStateManager.pushMatrix();
        if (eased < 1f) {
            float scale = 0.85f + eased * 0.15f;
            float centerX = width / 2f;
            float centerY = height / 2f;
            GlStateManager.translate(centerX, centerY, 0);
            GlStateManager.scale(scale, scale, 1f);
            GlStateManager.translate(-centerX, -centerY, 0);
        }

        // main container
        RenderUtils.resetColor();
        DrawUtils.drawRoundedRect(x, y, x1, y1, 30f, ThemeManager.getClickGuiBackground());

        vLineX = 2 * x;
        hLineY = (int) (1.5 * y);

        // vertical line
        DrawUtils.drawRect(vLineX, y, vLineX + 1.0f, y1, ThemeManager.getClickGuiSeparator());
        // horizontal line
        DrawUtils.drawRect(x, hLineY, x1, hLineY + 1.0f, ThemeManager.getClickGuiSeparator());

        //logo
        mc.getTextureManager().bindTexture(logoPath);
        int tempExpand = (int) (x * 0.1f);
        int logoCol = ThemeManager.getMainColor();
        GlStateManager.color(((logoCol >> 16) & 0xFF) / 255f, ((logoCol >> 8) & 0xFF) / 255f, (logoCol & 0xFF) / 255f, 1f);
        Gui.drawModalRectWithCustomSizedTexture(x + tempExpand, y + tempExpand, 0, 0, vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2), vLineX - x - (tempExpand * 2), hLineY - y - (tempExpand * 2) );
        GlStateManager.color(1f, 1f, 1f, 1f);

        // draws each module category component
        PosInfo pi = new PosInfo(x + 5, hLineY + 5);
        components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));

        //search
        searchComponent.updateComponent(new PosInfo((vLineX + 5), (float) ((y + hLineY) / 2.05)), ri);

        // makes the currently selected category component draw its modules
        ScissorUtils.subScissor(vLineX + 1, hLineY, x1, y1, 2);

        PosInfo piL = new PosInfo(vLineX + 5, hLineY);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2f, hLineY);
        cmcc.drawRight(piR, ri);
        cmcc.subtractFromMaxScrollHeight(y1 - hLineY);

        renderLastList.forEach(Runnable::run);
        renderLastList.clear();

        ScissorUtils.endSubScissor();
        cmcc.drawScrollbar(x1, hLineY, y1 - hLineY, ri);
        ScissorUtils.resetScissor();

        GlStateManager.popMatrix();

        getFontRenderer().resetScale();
    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        if(alwaysClickedComponent != null) {
            if(alwaysClickedComponent.clickAlwaysClickable(mouseX, mouseY, mouseButton)) return;
        }
        searchComponent.handleClick(mouseX, mouseY, mouseButton);
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        if(mouseX > vLineX && mouseX < x1 && mouseY > hLineY && mouseY < y1)
            cmcc.clickChildren(mouseX, mouseY, mouseButton);
    }

    public void setCmcc(ModuleCategoryComponent mcc) {
        if (cmcc != mcc) {
            prevCmcc = cmcc;
            cmcc.setCurrentCategory(false);
            mcc.setCurrentCategory(true);
            cmcc = mcc;
        }
    }
    public ModuleCategoryComponent getCmcc() {
        return cmcc;
    }
    public ModuleCategoryComponent getPrevCmcc() {
        return prevCmcc;
    }
    public <T extends Component & IAlwaysClickable> void setAlwaysClickedComponent(T component) {
        if(alwaysClickedComponent != null)
            alwaysClickedComponent.setNotAlwaysClickable();
        this.alwaysClickedComponent = component;
    }

    public <T extends Component & IAlwaysKeyboardInput> void setAlwaysInputComponent(T component) {
        if(alwaysKeyboardInput != null && alwaysKeyboardInput != component)
            alwaysKeyboardInput.setNotAlwaysRecieveInput();
        this.alwaysKeyboardInput = component;
    }

    public final FontRendererExtension<?> getFontRenderer() {
        try {
            return module.customFont.getValue() ?
                    Arsenic.getInstance().getFonts().Comfortaa.getFontRendererExtension() :
                    ((IFontRenderer) mc.fontRendererObj).getFontRendererExtension();
        } catch (NullPointerException e) {
            return null;
        }
    }

    public void addToRenderLastList(Runnable v) {
        renderLastList.add(v);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();
        i = Integer.compare(i, 0);
        cmcc.scroll(i * 15);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if(alwaysKeyboardInput != null) {
            alwaysKeyboardInput.recieveInput(keyCode);
            if (alwaysKeyboardInput != null) return;
        }
        ((SearchComponent) searchComponent).recieveInput(keyCode);
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

}
