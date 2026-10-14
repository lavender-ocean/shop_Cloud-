package com.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePaymentDTO {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}