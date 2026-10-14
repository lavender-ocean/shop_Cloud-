package com.shop.order.vo;

import com.shop.api.dto.UserProfileDTO;
import com.shop.order.entity.OrderInfo;
import lombok.Data;

import java.util.List;

@Data
public class OrderAggregateVO {
    private OrderInfo order;
    private List<OrderItemVO> items;
    private UserProfileDTO user;
    /** 本次聚合耗时（毫秒），用于串行/并行对比 */
    private long costMs;
    /** serial | parallel */
    private String mode;
}