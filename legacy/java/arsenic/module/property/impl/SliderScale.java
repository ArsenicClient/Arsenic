package arsenic.module.property.impl;

public enum SliderScale {
    LINEAR, LOG;

    private static final double LOG_MIDPOINT = 0.14;

    public float toPercent(double v, double lo, double hi) {
        double span = hi - lo;
        double pct;
        if (this == LOG) {
            double c = curve(span);
            pct = Math.log(1 + (v - lo) / c) / Math.log(1 + span / c);
        } else {
            pct = (v - lo) / span;
        }
        return (float) Math.max(0, Math.min(1, pct));
    }

    public double fromPercent(double pct, double lo, double hi) {
        double span = hi - lo;
        if (this == LOG) {
            double c = curve(span);
            return lo + c * (Math.pow(1 + span / c, pct) - 1);
        }
        return lo + pct * span;
    }

    private static double curve(double span) {
        double m = span * LOG_MIDPOINT;
        return m * m / (span - 2 * m);
    }
}
