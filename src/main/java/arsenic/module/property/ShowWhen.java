package arsenic.module.property;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows a setting only while another setting of the same module has certain values.
 * <pre>
 * &#64;ShowWhen(property = "Rotation Mode", is = { "Heuristics", "Snap" })   // any of these
 * &#64;ShowWhen(property = "Auto Block", not = "None")                         // anything but these
 * &#64;ShowWhen(property = "Pitch Band", is = "true")                          // a BooleanProperty
 * </pre>
 * Several on one field must all hold. {@code property} is the other setting's display name (or its field name), and
 * names and values are matched ignoring case. Only {@code BooleanProperty} and {@code EnumProperty} can be depended on,
 * and a setting whose target is hidden is hidden too.
 * <p>
 * Everything is checked when the module is registered: an unknown target or value is reported (in the log, or in the
 * addon errors) and the setting stays visible, so it can never become unreachable.
 */
@Repeatable(ShowWhen.All.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShowWhen {

    String property();

    /** Shown while the target has any of these values. */
    String[] is() default {};

    /** Shown while the target has none of these values. */
    String[] not() default {};

    /** Holds repeated {@link ShowWhen}s; Java creates it, it is not written by hand. */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface All {
        ShowWhen[] value();
    }
}
