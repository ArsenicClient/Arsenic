package arsenic.module.property.impl;

/**
 * How a slider's position maps to its value.
 * <p>
 * {@link #LINEAR} spreads the range evenly. {@link #LOG} stretches the low end: the slider's
 * halfway point sits at {@link #LOG_MIDPOINT} of the range (about 50 on 1-360), so small values
 * are easy to pick precisely while the rest of the range is still the other half of a drag.
 * The curve is {@code lo + c * ((1 + span / c)^pct - 1)}, with {@code c} chosen to put the
 * midpoint there; it works for ranges that start at 0 too.
 */
public enum SliderScale {
    LINEAR, LOG;

    /** Where the middle of a LOG slider lands, as a fraction of the range. */
    private static final double LOG_MIDPOINT = 0.14;

    /** Slider position (0-1) of {@code v} within {@code lo}..{@code hi}. */
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

    /** Value at slider position {@code pct} (0-1) within {@code lo}..{@code hi}. */
    public double fromPercent(double pct, double lo, double hi) {
        double span = hi - lo;
        if (this == LOG) {
            double c = curve(span);
            return lo + c * (Math.pow(1 + span / c, pct) - 1);
        }
        return lo + pct * span;
    }

    /** The {@code c} that puts the halfway point at LOG_MIDPOINT of {@code span}: m^2 / (span - 2m). */
    private static double curve(double span) {
        double m = span * LOG_MIDPOINT;
        return m * m / (span - 2 * m);
    }
}
