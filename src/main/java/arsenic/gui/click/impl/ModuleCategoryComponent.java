package arsenic.gui.click.impl;

import arsenic.gui.click.Component;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleCategoryComponent extends Component implements IContainer<ModuleComponent> {
    protected final ModuleCategory self;
    protected final ResourceLocation icon;
    protected float scroll, targetScroll, maxHeight;
    protected boolean isCC, isHovered;
    protected List<ModuleComponent> contentsL = new ArrayList<>();
    protected List<ModuleComponent> contentsR = new ArrayList<>();
    protected final List<ModuleComponent> contents;
    protected final AnimationTimer enabledTimer = new AnimationTimer(350, () -> isCC, TickMode.SINE);
    protected final AnimationTimer hoverTimer = new AnimationTimer(350, () -> isHovered, TickMode.SINE);

    public ModuleCategoryComponent(ModuleCategory category) {
        self = category;
        icon = new ResourceLocation("arsenic", "icons/" + self.getName().toLowerCase() + ".png");
        contents = self.getContents().stream().map(ModuleComponent::new).sorted(Comparator.comparing(ModuleComponent::getName)).collect(Collectors.toList());
        contents.forEach(module -> {
            if ((contentsL.size() + contentsR.size()) % 2 == 0) {
                contentsL.add(module);
            } else {
                contentsR.add(module);
            }
        });
    }

    @Override
    public String getName() { return self.getName(); }

    @Override
    public Collection<ModuleComponent> getContents() { return contents; }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float anim = Math.max(enabledTimer.getPercent(), hoverTimer.getPercent());
        expandX = anim * (width / 40f);
        int mainC = ColorUtils.setColor(getEnabledColor(), 0, (int) (anim * 255));
        int gradientC = ColorUtils.setColor(getGradientColor(), 0, (int) (anim * 255));

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // lift the active/hovered pill toward the viewer
        if (anim > 0.01f)
            DrawUtils.drawShadow(x1 + expandX, y1, x2 + expandX, y2, height / 4f,
                    arsenic.module.impl.visual.ClickGui.shadowSpread(height * 0.18f),
                    arsenic.module.impl.visual.ClickGui.shadowAlpha((int) (130 * anim)), 4);

        DrawUtils.drawGradientRoundedRect(x1 + expandX, y1, x2 + expandX, y2, height / 4f, mainC, mainC, gradientC, gradientC);

        float iconSize = ri.getFr().getHeight("|") * (ri.getGuiScreen().height / 300f);
        float iconX = x1 + (width / 7f) + expandX - iconSize;
        float iconY = midPointY - iconSize / 2f;
        Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
        Gui.drawModalRectWithCustomSizedTexture((int) iconX, (int) iconY, 0, 0, (int) iconSize, (int) iconSize, (int) iconSize, (int) iconSize);

        GlStateManager.color(1f, 1f, 1f, 1f); // reset after texture, not before gradient

        ri.getFr().drawString(getName(), iconX + iconSize + 2, midPointY, getWhite(), ri.getFr().CENTREY);
        return height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        isHovered = isMouseInArea(mouseX, mouseY);
    }

    public void drawLeft(PosInfo pi, RenderInfo ri) {
        maxHeight = 0;
        // ease the actual scroll toward the target each frame for smooth wheel scrolling
        scroll += (targetScroll - scroll) * arsenic.module.impl.visual.ClickGui.scrollEase();
        if (Math.abs(targetScroll - scroll) < 0.5f)
            scroll = targetScroll;
        drawSection(contentsL, pi, ri);
    }

    public void drawRight(PosInfo pi, RenderInfo ri) {
        drawSection(contentsR, pi, ri);
    }

    private void drawSection(List<ModuleComponent> l, PosInfo pi, RenderInfo ri) {
        pi.moveY(scroll);
        float temp = pi.getY();
        float expand = width/10f;
        pi.moveY(expand);
        l.forEach(moduleComponent -> pi.moveY(moduleComponent.updateComponent(pi, ri) + expand));
        temp = pi.getY() - temp;
        if(temp > maxHeight)
            maxHeight = temp;
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        Arsenic.getArsenic().getClickGuiScreen().setCmcc(this);
    }

    @Override
    protected void playClickSound() {
        // switching category gets its own chord (G major)
        arsenic.utils.java.SoundUtils.chordCategory();
    }

    public void clickChildren(int mouseX, int mouseY, int mouseButton) {
        this.contents.forEach(component -> component.handleClick(mouseX, mouseY, mouseButton));
    }

    public void setCurrentCategory(boolean currentCategory) {
        this.isCC = currentCategory;
        if (currentCategory) {
            scroll = 0;
            targetScroll = 0;
        }
    }

    public void scroll(int scroll) {
        // move the target; drawLeft eases the visible scroll toward it
        this.targetScroll += scroll;
        this.targetScroll = Math.max(Math.min(0, this.targetScroll), -maxHeight);
    }

    public void subtractFromMaxScrollHeight(float f) {
        maxHeight = maxHeight - f;
        maxHeight = Math.max(0, maxHeight);
    }

    public void drawScrollbar(float x, float y, float barHeight, RenderInfo ri) {
        if (maxHeight <= 0) return;
        float scrollbarHeight = barHeight * (barHeight / (barHeight + maxHeight));
        float scrollbarY = y + (this.scroll / -maxHeight) * (barHeight - scrollbarHeight);
        DrawUtils.drawRoundedRect(x - 3, y, x - 1, y + barHeight, 1f, ThemeManager.getScrollbarTrack());
        DrawUtils.drawRoundedRect(x - 3, scrollbarY, x - 1, scrollbarY + scrollbarHeight, 1f, ThemeManager.getScrollbarThumb());
    }

    @Override
    public int getWidth(int i) {
        return 10 * (i / 100);
    }

    @Override
    public int getHeight(int i) {
        return 5 * (i / 100);
    }
}
