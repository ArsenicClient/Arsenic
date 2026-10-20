package arsenic.module.property;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts a property under one of the icons at the bottom right of an open module: {@code @PropertyGroup(Group.VISUAL)}.
 * Properties without it are main settings. A folder's group applies to everything inside it.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyGroup {
    Group value();
}
