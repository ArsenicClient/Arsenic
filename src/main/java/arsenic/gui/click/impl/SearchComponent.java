package arsenic.gui.click.impl;

import arsenic.gui.click.ClickGuiScreen;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.ScissorUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {
    final ClickGuiScreen gui = Arsenic.getArsenic().getClickGuiScreen();
    private final StringBuilder inp = new StringBuilder();
    private final AnimationTimer activateTimer = new AnimationTimer(200, () -> gui.getCmcc() == this, TickMode.SINE);

    private boolean selected;

    int x,y;
    public SearchComponent(ModuleCategory category) {
        super(category);
        gui.setAlwaysInputComponent(this);
    }
    public void setupGlowAndBlur(){
        DrawUtils.drawGradientRoundedRect(x, y - 10, x1, y + 10,8,getEnabledColor(),getEnabledColor(),getGradientColor(),getGradientColor());
    }
    @Override
    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        boolean isMouseOver = mouseX >= x && mouseX <= x1 && mouseY >= (y - 10) && mouseY <= (y + 10);
        if (isMouseOver && mouseButton == 0){
            toggleSearch();
        }
        return super.handleClick(mouseX,mouseY,mouseButton);
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        x = (int) (ri.getGuiScreen().width / (3 + (1 * activateTimer.getPercent())));
        y = ri.getGuiScreen().height / 10;
        x1 = ri.getGuiScreen().width - x;
        y1 = ri.getGuiScreen().height - y;

        String imlosingmymind = inp.length() == 0 ? gui.getCmcc() == this ? "Search" : "Press \"/\" to toggle search" : inp.toString();
        int centerX = (int) getCentre(imlosingmymind,x+x1, ri.getFr());

        //what is with using exact colours???
        //DrawUtils.drawRoundedRect(x, y - 10, x1, y + 10,8, 0xDD0C0C0C);
        //added this
        DrawUtils.drawBorderedRoundedRect(x, y - 10, x1, y + 10, 8, 2f, ColorUtils.setColor(getEnabledColor(), 0, (int) (Math.max(enabledTimer.getPercent(), hoverTimer.getPercent())* 225)), 0xDD0C0C0C);

        //this causes weird chars with normal mc font
        //Arsenic.getInstance().getFonts().Icon.drawString("B",x + 3,y - 3, 0xFFFFFFFF);

        ScissorUtils.subScissor(x, y - 10, (int) x1, y + 10);
        if (selected) {
            DrawUtils.drawRect(centerX, y - 4, centerX + ri.getFr().getWidth(inp.toString()) + 2, y + 6, 0x678686FF);
        }
        ScissorUtils.endSubScissor();
        ri.getFr().drawString(imlosingmymind, centerX, y, getEnabledColor(), ri.getFr().CENTREY);

        return height;
    }
    public float getCentre(String s, float width, FontRendererExtension<?> fr) {
        return width / 2f - (fr.getWidth(s) / 2f);
    }
    @Override
    public void setNotAlwaysRecieveInput() {

    }

    @Override
    public boolean recieveInput(int key) {
        if (key == Keyboard.KEY_SLASH){
            toggleSearch();
            return false;
        }

        if (gui.getCmcc() != this) return false;
        char keyName = Keyboard.getEventCharacter();
        //219,220 - mac & 29,157 - windows/linux
        boolean isCtrlDown =  (Minecraft.isRunningOnMac ? Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220) : Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157));
        switch (key){
            case Keyboard.KEY_BACK:
                if(inp.length() >= 1) {
                    if (!selected) {
                        inp.deleteCharAt(inp.length() - 1);
                    } else {
                        inp.delete(0,inp.length());
                        selected = false;
                    }
                }
                break;
            case Keyboard.KEY_A:
                if (isCtrlDown){
                    selected = true;
                }
                break;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(keyName)) {
            if (selected){
                inp.delete(0,inp.length());
                selected = false;
            }
            inp.append(keyName);
        }
        contentsL.clear();
        contentsR.clear();
        contents.stream().filter(m -> m.getName().toLowerCase().contains(inp.toString().toLowerCase())).collect(Collectors.toList()).forEach(module -> {
            if ((contentsL.size() + contentsR.size()) % 2 == 0) {
                contentsL.add(module);
            } else {
                contentsR.add(module);
            }
        });
        return false;
    }

    @Override
    public void setCurrentCategory(boolean currentCategory) {
        super.setCurrentCategory(currentCategory);
        inp.setLength(0);
    }

    private void toggleSearch(){
        if (gui.getCmcc() != this) {
            gui.setCmcc(this);
            gui.setAlwaysInputComponent(this);
        } else {
            gui.setCmcc(gui.getPrevCmcc());
            gui.setAlwaysInputComponent(this);
            inp.delete(0,inp.length());
        }
    }
}
