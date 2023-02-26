package arsenic.module.property.impl.doubleProperty;

public class DoubleValue {
    private final double minBound, maxBound, inc;
    private double value;

    public DoubleValue(double minBound, double maxBound, double value, double inc) {
        this.minBound = minBound;
        this.maxBound = maxBound;
        this.value = value;
        this.inc = inc;
    }

    private double getCorrectedValue(double value) {
        value = Math.min(Math.max(value, minBound), maxBound);
        return Math.round(value * inc) / inc;
    }

    public double getInput() { return value; }

    public void setInput(double value) { this.value = getCorrectedValue(value); }

    public double getMaxBound() { return maxBound; }

    public double getMinBound() { return minBound; }
}
