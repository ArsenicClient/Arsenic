package arsenic.utils.timer;

import arsenic.utils.functionalinterfaces.IFunction;
import arsenic.utils.functionalinterfaces.IInt;

public enum TickMode {

    SINE(i -> i),
    LINEAR(i -> i),
    ROOT(i -> i);

    private IFunction<Float> i;
    TickMode(IFunction<Float> i) {
        this.i = i;
    }

    public float toSmoothPercent(float f) {
        return i.getValue(f);
    }

}
