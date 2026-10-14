package com.shop.order.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuName;
    private String skuImage;
    private String specJson;
    private Long price;
    private Integer quantity;
    private Long totalAmount;
    private LocalDateTime createTime;
}