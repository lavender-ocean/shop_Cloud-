package com.shop.order.controller;

import com.shop.api.dto.OrderSummaryDTO;
import com.shop.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.Result;

@RestController
@RequestMapping("/inner/order")
public class InnerOrderController {

    private final OrderService orderService;

    public InnerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderNo}")
    public Result<OrderSummaryDTO> getOrder(@PathVariable String orderNo) {
        return Result.success(orderService.summary(orderNo));
    }
}