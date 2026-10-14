package com.shop.api.feign;

import com.shop.api.dto.StockTccReq;
import com.shop.api.fallback.InventoryTccClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import result.Result;

import java.util.List;

@FeignClient(name = "shop-inventory"
        , contextId = "inventoryTccClient"
        , fallbackFactory = InventoryTccClientFallbackFactory.class)
public interface InventoryTccClient {



    @PostMapping("/inner/inventory/tcc/try")
    Result<Void> tryLock(@RequestBody StockTccReq req);

    @PostMapping("/inner/inventory/tcc/confirm")
    Result<Void> confirm(@RequestBody StockTccReq req);

    @PostMapping("/inner/inventory/tcc/cancel")
    Result<Void> cancel(@RequestBody StockTccReq req);

    /** 悬挂兜底：拉取长时间停在 Try 阶段的分支（含 items，便于真正释放库存） */
    @GetMapping("/inner/inventory/tcc/stale")
    Result<List<StockTccReq>> staleBranches(@RequestParam("limit") int limit);
}