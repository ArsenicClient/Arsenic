package arsenic.gui.click;

import arsenic.gui.themes.ThemeManager;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.render.PosInfo;
import arsenic.utils.render.RenderInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.AnimationTimer;
import arsenic.utils.timer.TickMode;
import arsenic.utils.io.Keys;

/**
 * Base of every ClickGUI element.
 * <p>
 * Beyond the layout bookkeeping it always did, this now owns the two interaction states every
 * component needs and each one used to re-implement: <b>hover</b> and <b>press</b>. Subclasses read
 * {@link #hoverPct()} and {@link #pressPct()} instead of declaring their own booleans and timers, so
 * feedback timing is identical everywhere in the GUI and a component gets it for free.
 * <p>
 * Sizes are resolved through {@link UITheme}'s scales rather than each subclass inventing its own
 * fraction of the screen, which is what keeps rows, cards and controls on a shared rhythm.
 */
public abstract class Component implements IContainable {

    protected float x1, y1, x2, y2, width, height, expandY, expandX, midPointY;

    /** True while the pointer is inside this component's own rect (not its children's). */
    protected boolean hovered;
    /** True between mouse-down on this component and the following mouse-up anywhere. */
    protected boolean pressed;

    private final AnimationTimer hoverAnim = new AnimationTimer(UITheme.DUR_HOVER, () -> hovered, TickMode.CUBIC);
    private final AnimationTimer pressAnim = new AnimationTimer(UITheme.DUR_PRESS, () -> pressed, TickMode.CUBIC);

    /** 0 = pointer away, 1 = fully hovered. Cheap to call; the timer is frame-driven. */
    protected final float hoverPct() { return hoverAnim.getPercent(); }

    /** 0 = released, 1 = fully pressed. Use it to sink an element slightly under the cursor. */
    protected final float pressPct() { return pressAnim.getPercent(); }

    // returns height
    public float updateComponent(PosInfo pi, RenderInfo ri) {
        width = getWidth(ri.getGuiScreen().width);
        height = getHeight(ri.getGuiScreen().height);
        x1 = pi.getX();
        x2 = x1 + width;
        y1 = pi.getY();
        y2 = y1 + height;
        midPointY = y1 + (height / 2f);

        // A press is only ever released by the physical button coming up. Tracking it here rather
        // than in each subclass means a component can never be left stuck "pressed" because the
        // mouse-up landed on a different element.
        if (pressed && !Keys.isMouseDown(0))
            pressed = false;

        mouseUpdate(ri.getMouseX(), ri.getMouseY());

        float r = drawComponent(ri);

        return r;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseX < x1 || mouseY < y1)
            return false;
        if (mouseX < x2 && mouseY < y2) {
            if (mouseButton == 0)
                pressed = true;
            clickComponent(mouseX, mouseY, mouseButton);
            playClickSound();
            return true;
        } else if (mouseX < (x2 + expandX) && mouseY < (y2 + expandY)) {
            if (this instanceof IContainer) {
                for (Component component : ((IContainer<Component>) this).getContents()) {
                    if (component.handleClick(mouseX, mouseY, mouseButton))
                        return true;
                }
            }
        }
        return false;
    }

    public final void handleRelease(int mouseX, int mouseY, int state) {
        pressed = false;
        mouseReleased(mouseX, mouseY, state);
        if (this instanceof IContainer) {
            ((IContainer) this).getContents()
                    .forEach(component -> ((Component) component).handleRelease(mouseX, mouseY, state));
        }
    }

    protected boolean isMouseInArea(float mouseX, float mouseY) {
        return mouseX > x1 && mouseY > y1 && mouseX < x2 && mouseY < y2;
    }

    protected int getEnabledColor() {
        return ThemeManager.getMainColor();
    }

    protected int getGradientColor() {
        return ThemeManager.getGradientColor();
    }

    protected int getDisabledColor() {
        return ThemeManager.getBlack();
    }

    protected int getWhite() {
        return ThemeManager.getWhite();
    }

    protected int getDarkerColor() {
        return ThemeManager.getDarkerColor();
    }

    protected abstract float drawComponent(RenderInfo ri);

    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {}

    // Plays a click sound for any component. SoundUtils debounces so nested
    // dispatch (a module row containing a toggle) only makes one sound.
    protected void playClickSound() {
        arsenic.utils.java.SoundUtils.cmajStep();
    }

    /**
     * Default hover tracking. Subclasses that override this must still update {@link #hovered} (or
     * call {@code super}) or their hover animation will never move.
     */
    public void mouseUpdate(int mouseX, int mouseY) {
        hovered = isMouseInArea(mouseX, mouseY);
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {}

    public int getHeight(int i) {
        return (int) UITheme.space(i, 5);
    }

    public int getWidth(int i) {
        return (int) UITheme.space(i, 5);
    }
}
