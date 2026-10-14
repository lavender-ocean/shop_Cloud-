package idempotent;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    IdempotentType type() default IdempotentType.TOKEN;

    /** PARAM 模式必填：SpEL，如 "#dto.orderNo" */
    String key() default "";

    /** 幂等窗口（PARAM 模式的过期时间 / TOKEN 的兜底过期） */
    long timeout() default 10;

    /** PARAM 模式：方法执行完是否释放 key（true=只防并发重复；false=窗口内都拒绝） */
    boolean deleteAfter() default true;

    /** 重复提交时的提示 */
    String message() default "请勿重复提交";
}