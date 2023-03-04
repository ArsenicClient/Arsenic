package arsenic.gui.click;

import arsenic.gui.click.impl.ModuleCategoryComponent;
import arsenic.gui.click.impl.UICategoryComponent;
import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.interfaces.IFontRenderer;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClickGuiScreen extends CustomGuiScreen {
    private final ClickGui module;
    private final List<UICategoryComponent> components;
    private ModuleCategoryComponent cmcc;

    public ClickGuiScreen() {
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
        //DrawUtils.drawRect(0, 0, width, height, 0x35000000);

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
        DrawUtils.drawRect(vLineX, y, vLineX + 1.0, y1, 0xFF2ECC71);
        // horizontal line
        DrawUtils.drawRect(x, hLineY, x1, hLineY + 1.0, 0xFF2ECC71);

        // draws each module category component
        PosInfo pi = new PosInfo(x + 5, hLineY + 5);
        components.forEach(component -> pi.moveY(component.updateComponent(pi, ri)));

        // draws the box around the current category
        //DrawUtils.drawRoundedRect(cmcc.x1, cmcc.y1, cmcc.x2, cmcc.y2, 2 * sf, 0x60FFFFFF);

        // makes the currently selected category component draw its modules
        PosInfo piL = new PosInfo(vLineX + 5, hLineY + 5);
        cmcc.drawLeft(piL, ri);
        PosInfo piR = new PosInfo(vLineX + (x1 - vLineX) / 2, hLineY + 5);
        cmcc.drawRight(piR, ri);

    }

    @Override
    public void mouseClick(int mouseX, int mouseY, int mouseButton) {
        components.forEach(panel -> panel.handleClick(mouseX, mouseY, mouseButton));
        cmcc.clickChildren(mouseX, mouseY, mouseButton);
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

}
