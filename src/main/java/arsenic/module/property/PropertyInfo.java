package arsenic.module.property;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The old form of {@link ShowWhen}: {@code @PropertyInfo(reliesOn = "X", value = "Y")} is
 * {@code @ShowWhen(property = "X", is = "Y")}, and is checked the same way.
 * @deprecated use {@link ShowWhen}, which can also match several values or exclude values
 */
@Deprecated
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyInfo {
    String reliesOn();

    String value();
}
