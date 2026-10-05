package enums;

import lombok.Getter;
//订单枚举
@Getter
public enum OrderStatus {
    PENDING_PAYMENT(0, "待支付"),
    PAID(1, "待发货"),
    SHIPPED(2, "配送中"),
    FINISHED(3, "已完成"),
    CANCELLED(4, "已取消"),
    CLOSED(5, "已关闭"),
    REFUNDING(6, "退款中"),
    REFUNDED(7, "已退款");
    private final int code;
    private final String desc;
    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
