package com.shop.api.dto;

import lombok.Data;

@Data
public class OrderSummaryDTO {
    private String orderNo;
    private Long userId;
    private Long payAmount;
    private Integer status;
}