package com.shop.order.controller;

import com.shop.order.entity.OrderInfo;
import com.shop.order.service.OrderService;
import com.shop.order.vo.OrderDetailVO;
import org.springframework.web.bind.annotation.*;
import result.PageResult;
import result.Result;
import security.RequirePermission;

@RestController
@RequestMapping("/admin/order")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/page")
    @RequirePermission("order:list")
    public Result<PageResult<OrderInfo>> page(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(orderService.adminPage(status, pageNum, pageSize));
    }

    @GetMapping("/{orderNo}")
    @RequirePermission("order:list")
    public Result<OrderDetailVO> detail(@PathVariable("orderNo") String orderNo) {
        return Result.success(orderService.adminDetail(orderNo));
    }

    @PutMapping("/{orderNo}/ship")
    @RequirePermission("order:ship")
    public Result<Void> ship(@PathVariable("orderNo") String orderNo) {
        orderService.ship(orderNo);
        return Result.success();
    }
}