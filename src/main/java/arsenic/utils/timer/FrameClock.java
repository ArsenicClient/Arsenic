package arsenic.utils.timer;

/**
 * Per-frame delta time for smoothing that chases a moving target (parallax, scrolling, fading values).
 * For a plain eased 0..1 transition prefer {@link AnimationTimer} or {@link HoverAnimation}.
 */
public class FrameClock {

    private static final float MAX_DT = 0.1f;

    private final MSTimer timer = new MSTimer();

    /** Seconds since the previous call, capped so a stalled frame does not make values jump. */
    public float tick() {
        float dt = Math.max(0f, Math.min(MAX_DT, timer.getTime() / 1000f));
        timer.reset();
        return dt;
    }

    /** Frame-rate independent {@code current += (target - current) * rate * dt}. */
    public static float approach(float current, float target, float rate, float dt) {
        return current + (target - current) * Math.min(1f, dt * rate);
    }
}
