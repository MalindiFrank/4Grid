package za.co.fourgrid;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Local Timeout annotation to avoid accidental shadowing issues in test runs.
 * Provides a minimal shape compatible with usages that expect value() and unit().
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Timeout {
    long value() default 0L;
    TimeUnit unit() default TimeUnit.SECONDS;
}
