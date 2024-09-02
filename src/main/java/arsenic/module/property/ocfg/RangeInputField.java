
package arsenic.module.property.ocfg;

import arsenic.module.property.impl.rangeproperty.RangeValue;
import cc.polyfrost.oneconfig.gui.animations.ColorAnimation;
import cc.polyfrost.oneconfig.gui.elements.BasicElement;
import cc.polyfrost.oneconfig.gui.elements.text.TextInputField;
import cc.polyfrost.oneconfig.internal.assets.Colors;
import cc.polyfrost.oneconfig.internal.assets.SVGs;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;

public class RangeInputField extends TextInputField {
    private final BasicElement upArrow = new BasicElement(12, 14, false);
    private final BasicElement downArrow = new BasicElement(12, 14, false);
    private final ColorAnimation colorTop = new ColorAnimation(ColorPalette.SECONDARY);
    private final ColorAnimation colorBottom = new ColorAnimation(ColorPalette.SECONDARY);
    private RangeValue value;

    public RangeInputField(int width, int height, RangeValue value) {
        super(width - 16, height, true, "");
        super.onlyNums = true;
        this.value = value;
        this.input = value.getMin() + " - " + value.getMax();
    }

    public void draw(long vg, float x, float y, InputHandler inputHandler, ConfigRange.Helping helping) {
        NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
        super.errored = false;
        nanoVGHelper.drawRoundedRect(vg, x + width + 4, y, 12, 28, Colors.GRAY_500, 6f);
        upArrow.disable(disabled);
        downArrow.disable(disabled);
        upArrow.update(x + width + 4, y, inputHandler);
        downArrow.update(x + width + 4, y + 14, inputHandler);


        if (value.getMin() < value.getMinBound() || value.getMax() > value.getMaxBound()) {
            super.errored = true;
        } else {
            upArrow.disable(false);
            downArrow.disable(false);
        }

        if (upArrow.isClicked()) {
            if(helping == ConfigRange.Helping.MAX)
                value.setMax(value.getMax() + 1);
            else
                value.setMin(value.getMin() + 1);
            setCurrentValue();
        }
        if (downArrow.isClicked()) {
            if(helping == ConfigRange.Helping.MAX)
                value.setMax(value.getMax() - 1);
            else
                value.setMin(value.getMin() - 1);
            setCurrentValue();
        }

        nanoVGHelper.setAlpha(vg, 1f);
        if (value.getMax() >= value.getMaxBound()) {
            nanoVGHelper.setAlpha(vg, 0.3f);
            upArrow.disable(true);
        }

        nanoVGHelper.drawRoundedRectVaried(vg, x + width + 4, y, 12, 14, colorTop.getColor(upArrow.isHovered(), upArrow.isPressed()), 6f, 6f, 0f, 0f);
        nanoVGHelper.drawSvg(vg, SVGs.CHEVRON_UP, x + width + 5, y + 2, 10, 10);

        nanoVGHelper.setAlpha(vg, 1f);
        if (value.getMin() <= value.getMinBound()) {
            nanoVGHelper.setAlpha(vg, 0.3f);
            upArrow.disable(true);
        }

        nanoVGHelper.drawRoundedRectVaried(vg, x + width + 4, y + 14, 12, 14, colorBottom.getColor(downArrow.isHovered(), downArrow.isPressed()), 0f, 0f, 6f, 6f);
        nanoVGHelper.drawSvg(vg, SVGs.CHEVRON_DOWN, x + width + 5, y + 15, 10, 10);
        nanoVGHelper.setAlpha(vg, 1f);

        try {
            super.draw(vg, x, y - 2, inputHandler);
        } catch (Exception e) {
            super.caretPos = 0;
            super.prevCaret = 0;
        }
    }

    public void setCurrentValue() {
        input = value.getMin() + " - " + value.getMax();
    }

    public void setCurrentValue(String str) {
        input = str;
    }

    public boolean arrowsClicked() {
        return upArrow.isClicked() || downArrow.isClicked();
    }
}