package arsenic.module.property;

import java.util.List;
import java.util.function.Supplier;

/** A setting other settings can depend on with {@link ShowWhen}. */
public interface IReliable {

    /** True while this setting has the value and is itself shown. Kept for older code; {@link ShowWhen} uses the methods below. */
    Supplier<Boolean> valueCheck(String value);

    /** The value as {@link ShowWhen} compares it: "true"/"false", or the enum constant's name. */
    String currentValue();

    /** Every value {@link #currentValue()} can return, so {@link ShowWhen} values can be checked when the module is registered. */
    List<String> possibleValues();
}
