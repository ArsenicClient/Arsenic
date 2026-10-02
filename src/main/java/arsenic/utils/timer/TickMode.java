package arsenic.utils.timer;

import java.util.function.UnaryOperator;

public enum TickMode {

    SINE(input -> (float) ((Math.sin(Math.PI * (input + 3/2f)) + 1)/2)),
    LINEAR(input -> input),
    ROOT(input -> (float) Math.sqrt(input)),
    SQR(input -> input * input),

    /** Ease-out cubic - decelerates into place. The default for hover/press feedback. */
    CUBIC(input -> {
        float inv = 1f - input;
        return 1f - inv * inv * inv;
    }),

    /** Ease-out exponential - very fast start, long settle. Good for panels sliding in. */
    EXPO(input -> input >= 1f ? 1f : (float) (1 - Math.pow(2, -10 * input))),

    /**
     * Ease-out back - overshoots past 1 then settles, which is what makes a toggle feel
     * mechanical rather than linear. Only use it for positions and scales; feeding an
     * overshooting value into a colour interpolation clips at the ends.
     */
    BACK(input -> {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float inv = input - 1f;
        return 1f + c3 * inv * inv * inv + c1 * inv * inv;
    });

    private final UnaryOperator<Float> i;

    TickMode(UnaryOperator<Float> i) {
        this.i = i;
    }

    public float toSmoothPercent(float f) {
        return i.apply(f);
    }

}
