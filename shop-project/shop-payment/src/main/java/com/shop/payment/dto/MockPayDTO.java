package com.shop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MockPayDTO {
    @NotBlank(message = "支付单号不能为空")
    private String paymentNo;
}