package constant;

public final class MqConstants {

    private MqConstants() {
    }

    // ===== 交换机 =====
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    // x-delayed-message
    public static final String DELAY_EXCHANGE = "delay.exchange";
    public static final String DLX_EXCHANGE = "dlx.exchange";

    // ===== 队列 =====
    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    public static final String PAYMENT_SUCCESS_QUEUE = "payment.success.queue";
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String ORDER_DLQ = "order.dlq";

    // ===== 路由键 =====
    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_PAYMENT_SUCCESS = "payment.success";
    public static final String RK_ORDER_DELAY = "order.delay";
    public static final String RK_ORDER_DLX = "order.dlx";



    // ===== 重试 & 死信 =====
    public static final String RETRY_EXCHANGE = "retry.exchange";
    public static final String RK_RETRY_ORDER_CREATED = "retry.order.created";
    public static final String RK_RETRY_ORDER_TIMEOUT = "retry.order.timeout";
    public static final String RK_RETRY_PAYMENT_SUCCESS = "retry.payment.success";

    public static final String RETRY_ORDER_CREATED_QUEUE = "order.created.retry.queue";
    public static final String RETRY_ORDER_TIMEOUT_QUEUE = "order.timeout.retry.queue";
    public static final String RETRY_PAYMENT_SUCCESS_QUEUE = "payment.success.retry.queue";

    public static final String ORDER_CREATED_DLQ = "order.created.dlq";
    public static final String ORDER_TIMEOUT_DLQ = "order.timeout.dlq";
    public static final String PAYMENT_SUCCESS_DLQ = "payment.success.dlq";

    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final String HEADER_ERROR = "x-error";
    public static final int MAX_RETRY = 3;
}