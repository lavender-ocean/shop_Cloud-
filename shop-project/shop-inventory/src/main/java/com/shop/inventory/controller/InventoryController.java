package com.shop.inventory.controller;

import com.shop.inventory.entity.Inventory;
import com.shop.inventory.service.InventoryService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import result.Result;

@RestController
@RequestMapping("/api/inventory")
@Validated   // 新增：让 @PathVariable 上的 @Min 生效（走 AOP 机制）
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/sku/{skuId}")
    public Result<Inventory> getBySkuId(
            @PathVariable("skuId") @Min(value = 1, message = "skuId 必须大于 0") Long skuId) {
        return Result.success(inventoryService.getBySkuId(skuId));
    }
}