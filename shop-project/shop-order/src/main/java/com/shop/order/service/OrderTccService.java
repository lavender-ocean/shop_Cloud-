package com.shop.order.service;

import com.shop.api.dto.StockTccReq;
import com.shop.api.feign.InventoryTccClient;
import com.shop.order.dto.CreateOrderDTO;
import com.shop.order.dto.OrderPreparation;
import context.UserContext;
import exception.BizAssert;
import exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import result.Result;
import result.ResultCode;

/**
 * 显式 TCC 下单编排：
 *   Try     → 预占库存
 *   本地事务 → 建单（失败则 Cancel 补偿）
 *   Confirm → 支付成功时（由 markPaid 路径调用 /inner/inventory/tcc/confirm）
 *   Cancel  → 取消/超时时调用
 */
@Slf4j
@Service
public class OrderTccService {

    private final OrderService orderService;
    private final InventoryTccClient inventoryTccClient;
    private final OrderPersistService orderPersistService;

    public OrderTccService(OrderService orderService, InventoryTccClient inventoryTccClient, OrderPersistService orderPersistService) {
        this.orderService = orderService;
        this.inventoryTccClient = inventoryTccClient;
        this.orderPersistService = orderPersistService;
    }

    public String createOrderByTcc(CreateOrderDTO dto) {
        //获取用户的userId 并且校验登陆状态
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);

        //价格计算、校验商品、生成orderNo、组装订单数据，没有写数据库
        //这个是准备阶段 还没有去写数据库
        OrderPreparation prep = orderService.prepare(userId, dto);

        // 1.Try：预占库存
        Result<Void> tryResult = inventoryTccClient.tryLock(
                new StockTccReq(prep.getOrderNo(), prep.getLockItems()));
        if (tryResult.getCode() != 0) {
            throw new BizException(tryResult.getCode(), tryResult.getMessage());
        }

        // 2. 本地事务：建单；失败 → 3. Cancel 补偿
        try {
            //本地事务落库
            orderPersistService.saveOrderAndItems(prep.getOrder(), prep.getItems());
        } catch (Exception e) {
            log.error("TCC 本地落库失败，执行 Cancel 补偿。orderNo={}", prep.getOrderNo(), e);
            try {
                //落库失败则cancel补偿
                inventoryTccClient.cancel(new StockTccReq(prep.getOrderNo(), prep.getLockItems()));
            } catch (Exception cancelError) {
                // 补偿失败 → 交给兜底任务（可扫描 fence 中"Try 成功但无订单"的分支重试）
                log.error("TCC Cancel 补偿失败，需兜底重试。orderNo={}", prep.getOrderNo(), cancelError);
            }
            throw e;
        }
        return prep.getOrderNo();
    }
}