package com.shop.order.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.shop.order.dto.CreateOrderDTO;
import com.shop.order.entity.OrderInfo;
import com.shop.order.service.OrderAggregateService;
import com.shop.order.service.OrderService;
import com.shop.order.service.OrderTccService;
import com.shop.order.vo.OrderAggregateVO;
import com.shop.order.vo.OrderDetailVO;
import idempotent.Idempotent;
import idempotent.IdempotentType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import result.PageResult;
import result.Result;
import result.ResultCode;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    //异步编排
    private final OrderAggregateService orderAggregateService;

    //显示TCC
    private final OrderTccService orderTccService;

    /** 显式 TCC 下单（与 AT 模式同一业务，用于两种事务模式对比） */
    @PostMapping("/tcc")
    @Idempotent(type = IdempotentType.TOKEN, message = "请勿重复提交订单")
    @SentinelResource(value = "order:createTcc", blockHandler = "createBlocked")
    public Result<String> createByTcc(@Valid @RequestBody CreateOrderDTO dto) {
        orderService.assertTxMode("tcc");
        return Result.success(orderTccService.createOrderByTcc(dto));
    }

    @GetMapping("/token")
    public Result<String> token() {
        return Result.success(orderService.createIdempotentToken());
    }

//    @PostMapping
//    @SentinelResource(value = "order:create", blockHandler = "createBlocked")
//    public Result<String> create(@Valid @RequestBody CreateOrderDTO dto) {
//        return Result.success(orderService.createOrder(dto));
//    }


    /** 订单聚合详情：?mode=serial|parallel 用于对比串行/并行耗时 */
    @GetMapping("/{orderNo}/aggregate")
    public Result<OrderAggregateVO> aggregate(@PathVariable String orderNo,
                                              //默认为并行模式
                                              @RequestParam(defaultValue = "parallel") String mode) {
        return Result.success(orderAggregateService.aggregate(orderNo, mode));
    }

    @PostMapping
    @Idempotent(type = IdempotentType.TOKEN, message = "请勿重复提交订单")
    @SentinelResource(value = "order:create", blockHandler = "createBlocked")
    public Result<String> create(@Valid @RequestBody CreateOrderDTO dto) {
        orderService.assertTxMode("at");         //

        return Result.success(orderService.createOrder(dto));
    }

    public Result<String> createBlocked(CreateOrderDTO dto, BlockException ex) {
        return Result.fail(ResultCode.RATE_LIMIT, "当前下单人数过多，请稍后再试");
    }

    /** 当前事务模式，前端据此选择下单入口 */
    @GetMapping("/tx-mode")
    public Result<String> txMode() {

        return Result.success(orderService.currentTxMode());
    }

    @GetMapping("/page")
    public Result<PageResult<OrderInfo>> page(@RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(orderService.page(status, pageNum, pageSize));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> detail(@PathVariable("orderNo") String orderNo) {

        return Result.success(orderService.detail(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable("orderNo") String orderNo) {
        orderService.cancel(orderNo);
        return Result.success();
    }
}