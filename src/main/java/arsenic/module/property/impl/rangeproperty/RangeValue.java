package arsenic.module.property.impl.rangeproperty;

import java.util.Random;

public class RangeValue {

    private Random random = new Random();
    private final double minBound, maxBound, inc;
    private double min, max;

    public RangeValue(double minBound, double maxBound, double min, double max, double inc) {
        this.minBound = minBound;
        this.maxBound = maxBound;
        this.min = min;
        this.max = max;
        this.inc = inc;
    }

    private double getCorrectedValue(double value) {
        value = Math.min(Math.max(value, minBound), maxBound);
        return Math.round(value * inc) / inc;
    }

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = getCorrectedValue(min);
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = getCorrectedValue(max);
    }

    public double getMaxBound() {
        return maxBound;
    }

    public double getMinBound() {
        return minBound;
    }

    public double getRandomInRange() {
        return getMin() + (random.nextDouble() * (getMax() - getMin()));
    }
}