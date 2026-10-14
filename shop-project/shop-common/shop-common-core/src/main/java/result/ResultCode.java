package result;

import lombok.Data;
import lombok.Getter;

/**
 *
 * 错误码 = 模块 2 位 + 序号 3 位
 */
@Getter
public enum ResultCode implements IErrorCode {

    SUCCESS(0, "成功"),


    // ===== 通用 10xxx =====
    PARAM_ERROR(10001, "参数校验失败"),
    UNAUTHORIZED(10002, "未登录或登录已过期"),
    FORBIDDEN(10003, "无权限访问"),
    NOT_FOUND(10004, "资源不存在"),
    METHOD_NOT_ALLOWED(10005, "请求方法不支持"),
    DUPLICATE_SUBMIT(10006, "请勿重复提交"),
    SYSTEM_ERROR(10999, "系统繁忙，请稍后重试"),

    // ===== 网关 11xxx =====
    RATE_LIMIT(11001, "请求过于频繁，请稍后再试"),
    SERVICE_UNAVAILABLE(11002, "服务暂不可用"),

    // ===== 认证 12xxx =====
    LOGIN_FAILED(12001, "用户名或密码错误"),
    ACCOUNT_DISABLED(12002, "账号已被禁用"),
    TOKEN_INVALID(12003, "登录凭证无效"),
    TOKEN_EXPIRED(12004, "登录凭证已过期"),

    // ===== 用户 13xxx =====
    USER_NOT_FOUND(13001, "用户不存在"),
    ADDRESS_NOT_FOUND(13002, "收货地址不存在"),

    // ===== 商品 14xxx =====
    SPU_NOT_FOUND(14001, "商品不存在"),
    SKU_NOT_FOUND(14002, "商品规格不存在"),
    SKU_OFF_SHELF(14003, "商品已下架"),

    // ===== 库存 15xxx =====
    STOCK_NOT_ENOUGH(15001, "库存不足"),
    STOCK_RESTORE_DUPLICATE(15002, "库存重复归还"),

    // ===== 订单 16xxx =====
    ORDER_NOT_FOUND(16001, "订单不存在"),
    ORDER_STATUS_ERROR(16002, "当前订单状态不允许该操作"),
    ORDER_DUPLICATE_SUBMIT(16003, "请勿重复提交订单"),

    // ===== 支付 17xxx =====
    PAY_FAILED(17001, "支付失败"),
    PAY_RECORD_NOT_FOUND(17002, "支付流水不存在"),

    // ===== 促销 18xxx =====
    COUPON_NOT_FOUND(18001, "优惠券不存在"),
    COUPON_UNAVAILABLE(18002, "优惠券不可用"),

    // ===== MQ / 系统 19xxx =====
    MQ_SEND_FAILED(19001, "消息发送失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}