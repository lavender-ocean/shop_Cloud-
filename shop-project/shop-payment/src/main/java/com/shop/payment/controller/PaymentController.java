package com.shop.payment.controller;

import com.shop.payment.dto.CreatePaymentDTO;
import com.shop.payment.dto.MockPayDTO;
import com.shop.payment.dto.PaymentCallbackDTO;
import com.shop.payment.service.PaymentService;
import com.shop.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.Result;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 创建支付单（需登录） */
    @PostMapping("/create")
    public Result<PaymentVO> create(@Valid @RequestBody CreatePaymentDTO dto) {
        return Result.success(paymentService.create(dto));
    }

    /** 第三方回调（网关白名单，免 token） */
    @PostMapping("/callback")
    public Result<Void> callback(@Valid @RequestBody PaymentCallbackDTO dto) {
        paymentService.handleCallback(dto);
        return Result.success();
    }

    /** 便捷测试：模拟"去支付"（需登录） */
    @PostMapping("/mock-pay")
    public Result<Void> mockPay(@Valid @RequestBody MockPayDTO dto) {
        paymentService.mockPay(dto);
        return Result.success();
    }
}