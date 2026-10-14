package com.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentCallbackDTO {
    @NotBlank private String paymentNo;
    @NotBlank private String thirdTradeNo;
    @NotNull private Long amount;
    @NotBlank private String sign;
}