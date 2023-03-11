package arsenic.utils.timer;

import arsenic.utils.functionalinterfaces.IFunction;

public enum TickMode {

    SINE(input -> (float) ((Math.sin(Math.PI * (input + 3/2f)) + 1)/2)),
    LINEAR(input -> input),
    ROOT(input -> (float) Math.sqrt(input));

    private IFunction<Float> i;

    TickMode(IFunction<Float> i) {
        this.i = i;
    }

    public float toSmoothPercent(float f) {
        return i.getValue(f);
    }

}
