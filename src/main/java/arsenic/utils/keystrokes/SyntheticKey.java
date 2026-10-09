package arsenic.utils.keystrokes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose every call is one press Arsenic makes for the player. The class transformer adds a
 * {@link SyntheticKeys#press} call at the start of the method, so the Keystrokes HUD sees it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SyntheticKey {
    SyntheticKeys.Key value();
}
