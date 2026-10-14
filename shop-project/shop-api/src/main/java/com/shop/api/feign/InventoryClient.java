package com.shop.api.feign;

import com.shop.api.dto.InventoryDTO;
import com.shop.api.dto.StockLockReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import result.Result;

//@FeignClient(name = "shop-inventory")
@FeignClient(name = "shop-inventory"
        , contextId = "inventoryClient"        //  新增，唯一标识
        , fallbackFactory = com.shop.api.fallback.InventoryClientFallbackFactory.class)
public interface InventoryClient {

    @PostMapping("/inner/inventory/lock")
    Result<Void> lock(@RequestBody StockLockReq req);

    @PostMapping("/inner/inventory/confirm")
    Result<Void> confirm(@RequestBody StockLockReq req);

    @PostMapping("/inner/inventory/release")
    Result<Void> release(@RequestBody StockLockReq req);

    @GetMapping("/inner/inventory/stock/{skuId}")
    Result<InventoryDTO> getStock(@PathVariable("skuId") Long skuId);
}