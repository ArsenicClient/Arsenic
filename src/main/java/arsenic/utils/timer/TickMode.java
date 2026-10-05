package arsenic.utils.timer;

import java.util.function.UnaryOperator;

public enum TickMode {

    SINE(input -> (float) ((Math.sin(Math.PI * (input + 3/2f)) + 1)/2)),
    LINEAR(input -> input),
    ROOT(input -> (float) Math.sqrt(input)),
    SQR(input -> input * input),

    CUBIC(input -> {
        float inv = 1f - input;
        return 1f - inv * inv * inv;
    }),

    EXPO(input -> input >= 1f ? 1f : (float) (1 - Math.pow(2, -10 * input))),

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

    /** Eases the input after clamping it to 0..1. */
    public float clamped(float f) {
        return i.apply(f < 0f ? 0f : f > 1f ? 1f : f);
    }

}
