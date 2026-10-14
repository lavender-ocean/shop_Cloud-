package com.shop.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentRecord {
    private Long id;
    private String paymentNo;
    private String orderNo;
    private Long userId;
    private Integer channel;
    private Long amount;
    private Integer status;
    private String thirdTradeNo;
    private String callbackContent;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}