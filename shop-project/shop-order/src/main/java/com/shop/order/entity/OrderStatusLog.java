package com.shop.order.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderStatusLog {
    private Long id;
    private String orderNo;
    private Integer fromStatus;
    private Integer toStatus;
    private Integer operatorType;   // 1用户 2系统 3管理员
    private Long operatorId;
    private String remark;
    private LocalDateTime createTime;
}