package com.shop.order.job;

import com.shop.api.dto.StockTccReq;
import com.shop.api.feign.InventoryTccClient;
import com.shop.order.entity.OrderInfo;
import com.shop.order.mapper.OrderInfoMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import result.Result;

import java.util.List;

/**
 * TCC 悬挂兜底：Try 成功但订单侧没有对应订单 → 重试 Cancel 释放预占库存。
 *
 * 显式 TCC 没有 TC 帮忙自动重试，Cancel 失败只能靠这里兜。
 * 判据：tcc_fence 中长时间停在 Try 阶段 且 order_info 里查不到该 orderNo。
 */
@Slf4j
@Component
public class TccSuspendedJobHandler {

    private static final int BATCH = 100;

    private final InventoryTccClient inventoryTccClient;
    private final OrderInfoMapper orderInfoMapper;

    public TccSuspendedJobHandler(InventoryTccClient inventoryTccClient,
                                  OrderInfoMapper orderInfoMapper) {
        this.inventoryTccClient = inventoryTccClient;
        this.orderInfoMapper = orderInfoMapper;
    }

    @XxlJob("tccSuspendedBranchHandler")
    public void scanSuspendedBranches() {
        Result<List<StockTccReq>> result = inventoryTccClient.staleBranches(BATCH);
        if (result.getCode() != 0 || result.getData() == null) {
            XxlJobHelper.log("拉取悬挂分支失败：{}", result.getMessage());
            return;
        }

        int compensated = 0;
        for (StockTccReq req : result.getData()) {
            OrderInfo order = orderInfoMapper.selectByOrderNo(req.getOrderNo());
            if (order != null) {
                continue;   // 订单在 → 不是悬挂，正常流程会走 Cancel/Confirm
            }
            try {
                // 订单不存在 → Try 的预占必须撤销。
                // req 带上了 Try 时的 items，release 才能真正把库存还回去；
                // Cancel 内部有 fence 幂等 + 空回滚保护，重复调用安全。
                inventoryTccClient.cancel(req);
                compensated++;
                log.warn("[TCC兜底] 检测到悬挂分支并已补偿 Cancel。orderNo={}, items={}",
                        req.getOrderNo(), req.getItems());
            } catch (Exception e) {
                log.error("[TCC兜底] Cancel 补偿失败，下轮重试。orderNo={}", req.getOrderNo(), e);
            }
        }
        XxlJobHelper.log("TCC 悬挂兜底：扫描 {} 个分支，补偿 {} 个", result.getData().size(), compensated);
    }
}