package arsenic.module.property.ocfg;

import arsenic.module.property.impl.rangeproperty.RangeValue;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.animations.*;
import cc.polyfrost.oneconfig.gui.elements.IFocusable;
import cc.polyfrost.oneconfig.internal.assets.Colors;
import cc.polyfrost.oneconfig.internal.config.Preferences;
import cc.polyfrost.oneconfig.platform.Platform;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.MathUtils;

import java.lang.reflect.Field;

public class ConfigRange extends BasicOption implements IFocusable {
    private static final int STEP_POPUP_DURATION = 400;
    private static final int INDICATOR_POPUP_DURATION = 200;

    private static final float STEP_HEIGHT_TOTAL = 16;
    private static final float STEP_HEIGHT_HOVER = 10;
    private static final float STEP_HEIGHT_DRAG = 16;
    private static final float TOUCH_TARGET_TOTAL = 16;
    private static final float TOUCH_TARGET_HOVER = 16;
    private static final float TOUCH_TARGET_DRAG = 10;

    private final RangeInputField inputField;
    private boolean dragging = false;
    private boolean mouseWasDown = false;
    private Animation targetAnimationMin;
    private Animation targetAnimationMax;
    private Animation stepSlideAnimationMin;
    private Animation stepSlideAnimationMax;
    private float animationStartMax;
    private float animationStartMin;
    private float lastSliderTargetMin = 1;
    private float lastSliderTargetMax = 1;
    private boolean animResetMin;
    private boolean animResetMax;
    private float lastX = -1;
    private float lastX2 = -1;
    private RangeValue rangeValue;
    private Helping helping = Helping.MAX;
    private Helping hover = Helping.MAX;

    public ConfigRange(Field field, Object parent, String name, String description, String category, String subcategory, RangeValue value) {
        super(field, parent, name, description, category, subcategory, 2);
        this.rangeValue = value;
        this.inputField = new RangeInputField(84, 32, value);
        this.targetAnimationMin = new DummyAnimation(0);
        this.targetAnimationMax = new DummyAnimation(0);
        this.stepSlideAnimationMin = new DummyAnimation(1);
        this.stepSlideAnimationMax = new DummyAnimation(1);
    }

    @Override
    public void draw(long vg, int x, int y, InputHandler inputHandler) {
        final NanoVGHelper nanoVGHelper = NanoVGHelper.INSTANCE;
        int xCoordinate = 0;
        int xCoordinate2 = 0;
        float value = 0;
        float value2 = 0;
        boolean hovered = inputHandler.isAreaHovered(x + 352, y, 512, 32) && isEnabled();

        inputField.disable(!isEnabled());
        if (!isEnabled()) nanoVGHelper.setAlpha(vg, 0.5f);

        boolean isMouseDown = Platform.getMousePlatform().isButtonDown(0);

        float i = (float) (rangeValue.getMaxBound() - rangeValue.getMinBound());
        float minMapped = ((float) rangeValue.getMin()) / i;
        float maxMapped = ((float) rangeValue.getMax()) / i;
        float mouseMapped = (inputHandler.mouseX() - x - 352) / 512f;
        float minDiff = Math.abs(minMapped - mouseMapped);
        float maxDiff = Math.abs(maxMapped - mouseMapped);
        hover = minDiff < maxDiff ? Helping.MIN : Helping.MAX;
        if(minDiff == maxDiff)
            hover = (minMapped - mouseMapped) > 0 ? Helping.MIN : Helping.MAX;

        if (hovered && isMouseDown && !mouseWasDown) {
            helping = hover;
            dragging = true;
        }
        boolean startedDragging = !mouseWasDown && isMouseDown;
        mouseWasDown = isMouseDown;


        if (dragging) {
            if (helping == Helping.MAX) {
                xCoordinate2 = (int) MathUtils.clamp(inputHandler.mouseX(), x + 352, x + 864);
                value2 = MathUtils.map(xCoordinate2, x + 352, x + 864, (float) rangeValue.getMinBound(), (float) rangeValue.getMaxBound());
                rangeValue.setMax(value2);
                value = (float) rangeValue.getMin();
                xCoordinate = (int) MathUtils.clamp(MathUtils.map(value, (float) rangeValue.getMinBound(), (float) rangeValue.getMaxBound(), x + 352, x + 864), x + 352, x + 864);
            } else {
                xCoordinate = (int) MathUtils.clamp(inputHandler.mouseX(), x + 352, x + 864);
                value = MathUtils.map(xCoordinate, x + 352, x + 864, (float) rangeValue.getMinBound(), (float) rangeValue.getMaxBound());
                rangeValue.setMin(value);
                value2 = (float) rangeValue.getMax();
                xCoordinate2 = (int) MathUtils.clamp(MathUtils.map( value2, (float) rangeValue.getMinBound(), (float) rangeValue.getMaxBound(), x + 352, x + 864), x + 352, x + 864);
            }
        }

        if (dragging && inputHandler.isClicked() || inputField.isToggled() || inputField.arrowsClicked()) {
            dragging = false;
        }


        float targetPercentMin = targetAnimationMin.get();
        float targetPercentMax = targetAnimationMax.get();

        if (isEnabled()) {

            if (dragging && startedDragging && hover == Helping.MIN) {
                targetAnimationMin = new EaseOutExpo(INDICATOR_POPUP_DURATION, targetPercentMin, TOUCH_TARGET_DRAG / TOUCH_TARGET_TOTAL, false);
                animResetMin = true;
            } else if (!dragging && hovered && hover == Helping.MIN) {
                if (targetAnimationMin.getEnd() != 1) {
                    targetAnimationMin = new EaseInOutQuart(INDICATOR_POPUP_DURATION, targetPercentMin, 1, false);
                    animResetMin = true;
                }
            } else if (!dragging && animResetMin) {
                targetAnimationMin = new EaseOutExpo(INDICATOR_POPUP_DURATION, targetPercentMin, 0, false);
                animResetMin = false;
            }

            if (dragging && startedDragging && hovered && hover == Helping.MAX) {
                targetAnimationMax = new EaseOutExpo(INDICATOR_POPUP_DURATION, targetPercentMax, TOUCH_TARGET_DRAG / TOUCH_TARGET_TOTAL, false);
                animResetMax = true;
            } else if (!dragging && hovered && hover == Helping.MAX) {
                if (targetAnimationMax.getEnd() != 1) {
                    targetAnimationMax = new EaseInOutQuart(INDICATOR_POPUP_DURATION, targetPercentMax, 1, false);
                    animResetMax = true;
                }
            } else if (!dragging && animResetMax) {
                targetAnimationMax = new EaseOutExpo(INDICATOR_POPUP_DURATION, targetPercentMax, 0, false);
                animResetMax = false;
            }

        }

        if (!dragging && !inputField.isToggled()) {
            value = (float) rangeValue.getMin();
            value2 = (float) rangeValue.getMax();
            xCoordinate  = (int) MathUtils.clamp(MathUtils.map(value, (float) rangeValue.getMinBound(), (float) rangeValue.getMaxBound(), x + 352, x + 864), x + 352, x + 864);
            xCoordinate2 = (int) MathUtils.clamp(MathUtils.map(value2, (float) rangeValue.getMinBound(), (float) rangeValue.getMaxBound(), x + 352, x + 864), x + 352, x + 864);
        }

        if (!inputField.isToggled()) {
            inputField.setCurrentValue(rangeValue.getCorrectedValue(value) + " - " + rangeValue.getCorrectedValue(value2));
        }

        //Animate sliding
        if (stepSlideAnimationMin.isFinished() && lastSliderTargetMin != -1 && lastSliderTargetMin != xCoordinate && (!dragging || (startedDragging && helping == Helping.MIN)) && lastX == x) {
            animationStartMin = lastSliderTargetMin;
            stepSlideAnimationMin = new EaseInOutCubic((int) Preferences.trackerResponseDuration, 0f, 1f, false);
        }

        float progress = stepSlideAnimationMin.get();
        lastSliderTargetMin = xCoordinate;
        lastX = x;
        xCoordinate = (int) (xCoordinate * progress + animationStartMin * (1 - progress));

        if (stepSlideAnimationMax.isFinished() && lastSliderTargetMax != -1 && lastSliderTargetMax != xCoordinate2 && (!dragging || (startedDragging && helping == Helping.MAX)) && lastX2 == x) {
            animationStartMax = lastSliderTargetMax;
            stepSlideAnimationMax = new EaseInOutCubic((int) Preferences.trackerResponseDuration, 0f, 1f, false);
        }

        float progress2 = stepSlideAnimationMax.get();
        lastSliderTargetMax = xCoordinate2;
        lastX2 = x;
        xCoordinate2 = (int) (xCoordinate2 * progress2 + animationStartMax * (1 - progress2));


        // Ease-out the radius when the steps are in view
        float radius = 4;

        nanoVGHelper.drawText(vg, name, x, y + 17, nameColor, 14f, Fonts.MEDIUM);
        nanoVGHelper.drawRoundedRect(vg, x + 352, y + 13, 512, 4, Colors.GRAY_300, radius);

        //draws line between two circles
        nanoVGHelper.drawRoundedRect(vg, xCoordinate, y + 13 - 1, xCoordinate2 - xCoordinate, 6, Colors.PRIMARY_500, 4f);

        nanoVGHelper.drawRoundedRect(vg, xCoordinate - 12, y + 4, 24, 24, Colors.WHITE, 12f);
        nanoVGHelper.drawRoundedRect(vg, xCoordinate2 - 12, y + 4, 24, 24, Colors.WHITE, 12f);

        if (targetPercentMin > 0.02f) {
            nanoVGHelper.drawRoundedRect(vg, xCoordinate - (TOUCH_TARGET_HOVER / 2 * targetPercentMin), y + 16 - (TOUCH_TARGET_HOVER / 2 * targetPercentMin), TOUCH_TARGET_HOVER * targetPercentMin, TOUCH_TARGET_HOVER * targetPercentMin, Colors.PRIMARY_500, 12f);
        }
        if (targetPercentMax > 0.02f) {
            nanoVGHelper.drawRoundedRect(vg, xCoordinate2 - (TOUCH_TARGET_HOVER / 2 * targetPercentMax), y + 16 - (TOUCH_TARGET_HOVER / 2 * targetPercentMax), TOUCH_TARGET_HOVER * targetPercentMax, TOUCH_TARGET_HOVER * targetPercentMax, Colors.PRIMARY_500, 12f);
        }

        inputField.draw(vg, x + 892, y, inputHandler, helping);
        nanoVGHelper.setAlpha(vg, 1f);
    }

    public enum Helping {
        MIN,
        MAX;
    }

    @Override
    public void keyTyped(char key, int keyCode) {
        inputField.keyTyped(key, keyCode);
    }

    @Override
    public int getHeight() {
        return 32;
    }

    @Override
    public boolean hasFocus() {
        return inputField.isToggled();
    }
}