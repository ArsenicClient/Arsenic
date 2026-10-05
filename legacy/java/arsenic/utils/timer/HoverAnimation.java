package arsenic.utils.timer;

/** An {@link AnimationTimer} driven by a boolean you pass in each frame instead of a supplier. */
public class HoverAnimation {

    public static final int DEFAULT_MS = 160;

    private boolean active;
    private final AnimationTimer timer;

    public HoverAnimation() {
        this(DEFAULT_MS, TickMode.CUBIC);
    }

    public HoverAnimation(int ms, TickMode mode) {
        timer = new AnimationTimer(ms, () -> active, mode);
        timer.getPercent(); // start the clock now so the first frame does not snap
    }

    /** Sets whether the target is hovered/active this frame and returns the eased 0..1 amount. */
    public float update(boolean active) {
        this.active = active;
        return timer.getPercent();
    }
}
