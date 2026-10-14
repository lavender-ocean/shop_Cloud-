package idempotent;

public enum IdempotentType {
    /** token 模式：从请求头 Idempotent-Token 取，一次性消费（GET+DEL） */
    TOKEN,
    /** 参数模式：key 由 SpEL 从方法参数生成，SETNX 占位；可配置完成后释放 */
    PARAM
}