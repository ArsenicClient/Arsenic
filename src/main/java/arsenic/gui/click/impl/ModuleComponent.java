package arsenic.gui.click.impl;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

import arsenic.main.Arsenic;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.render.*;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import org.jetbrains.annotations.NotNull;

import arsenic.gui.click.Component;
import arsenic.module.Module;
import arsenic.utils.interfaces.IContainer;
import org.lwjgl.input.Keyboard;

public class ModuleComponent extends Component implements IContainer<PropertyComponent<?>>, IAlwaysKeyboardInput {
    private final Collection<PropertyComponent<?>> contents = new ArrayList<>();
    private boolean open, binding, hovered;
    private float bindX;
    private Module self;
    private PosInfo posInfo;
    private final AnimationTimer openAnimationTimer = new AnimationTimer(350, () -> open, TickMode.SINE);
    private final AnimationTimer enabledAnimationTimer = new AnimationTimer(350, () -> self.isEnabled(), TickMode.SINE);
    private final AnimationTimer hoverTimer = new AnimationTimer(200, () -> hovered, TickMode.SINE);
    private final ButtonComponent buttonComponent = new ButtonComponent(this) {
        @Override
        protected boolean isEnabled() {
            return self.isEnabled();
        }

        @Override
        protected void setEnabled(boolean enabled) {
            self.setEnabled(enabled);
        }

        @Override
        public int getWidth(int i) {
            return (int) (super.getWidth(i) * 0.95);
        }
    };
    private final String name;

    public ModuleComponent(@NotNull Module self) {
        self.getProperties().forEach(property -> contents.add(property.createComponent()));
        this.self = self;
        this.name = self.getName();
    }

    @Override
    public float updateComponent(PosInfo pi, RenderInfo ri) {
        posInfo = pi;
        return super.updateComponent(pi, ri);
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        float expand = width / 15f;
        float barWidth = width / 45f;
        int color = RenderUtils.interpolateColoursInt(getDisabledColor(), getEnabledColor(), enabledAnimationTimer.getPercent());

        DrawUtils.drawRoundedRect(x1, y1, x2, y2 + expandY, expand, new Color(5, 5, 5, 160).getRGB());

        if (hoverTimer.getPercent() > 0)
            DrawUtils.drawRoundedRect(x1, y1, x2, y2 + expandY, expand, new Color(255, 255, 255, (int) (15 * hoverTimer.getPercent())).getRGB());

        float accPct = enabledAnimationTimer.getPercent();
        if (accPct > 0) {
            int accentCol = RenderUtils.alpha(new Color(getEnabledColor()), (int) (accPct * 200));
            DrawUtils.drawRoundedRect(x1, y1, x1 + barWidth / 2f, y2 + expandY, barWidth, accentCol, new boolean[]{true, true, false, false});
        }

        buttonComponent.updateComponent(posInfo, ri);
        RenderUtils.resetColorText();

        ri.getFr().drawString(name, x1 + expand / 2f, midPointY, color, ri.getFr().CENTREY);
        RenderUtils.resetColorText();

        String bindName = binding ? "Press a key..." : "[" + Keyboard.getKeyName(self.getKeybind()) + "]";
        bindX = x2 - ((expand) * 3) - ri.getFr().getWidth(bindName);
        ri.getFr().drawString(bindName, bindX, midPointY, self.getKeybind() == 0 ? getDisabledColor() : getEnabledColor(), ri.getFr().CENTREY);

        PosInfo pi = new PosInfo(x1 + expand, y2);
        if (openAnimationTimer.getPercent() > 0) {
            ScissorUtils.subScissor((int) x1, (int) y2, (int) (x2 + expandX * 2), (int) (y2 + expandY), 2);
            contents.forEach(child -> pi.moveY((int) (child.updateComponent(pi, ri) * 1.1)));
            ScissorUtils.endSubScissor();
        }
        expandY = (pi.getY() - y2) * openAnimationTimer.getPercent();

        return expandY + height;
    }

    @Override
    public void mouseUpdate(int mouseX, int mouseY) {
        hovered = isMouseInArea(mouseX, mouseY);
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        if (mouseX < bindX && mouseButton == 1) {
            open = !open;
            return;
        } else if (mouseX > bindX && mouseX < x1 + (width * 0.85)) {
            binding = !binding;
            Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(binding ? this : null);
            return;
        }
        buttonComponent.handleClick(mouseX, mouseY, mouseButton);
    }

    @Override
    public final Collection<PropertyComponent<?>> getContents() { return contents; }

    public final String getName() { return name; }

    @Override
    public int getWidth(int i) {
        return 30 * (i / 100);
    }

    @Override
    public void setNotAlwaysRecieveInput() {
        binding = false;
    }

    @Override
    public boolean recieveInput(int key) {
        Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(null);
        if (key == 1) {
            self.setKeybind(0);
            return true;
        }
        self.setKeybind(key);
        return false;
    }
}
