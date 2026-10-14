package com.shop.order.vo;

import com.shop.order.entity.OrderInfo;
import com.shop.order.entity.OrderItem;
import lombok.Data;

import java.util.List;

/**
 * 订单详情：订单主表 + 明细列表。
 * 为什么单独定义而不是直接在 Controller 组装？
 *   - 订单详情是"聚合数据"，属于 Service 的职责
 *   - 前端只需要一次请求拿到全部
 *   - 未来如果加"物流信息""支付信息"，只扩展这个 VO 即可
 */
@Data
public class OrderDetailVO {
    private OrderInfo order;
    private List<OrderItem> items;
}