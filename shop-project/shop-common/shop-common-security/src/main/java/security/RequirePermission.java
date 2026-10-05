package security;

import java.lang.annotation.*;

/** 需要的权限码，满足任意一个即通过 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    String[] value();
}