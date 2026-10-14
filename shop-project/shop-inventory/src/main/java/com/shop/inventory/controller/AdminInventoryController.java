package com.shop.inventory.controller;

import com.shop.inventory.dto.StockChangeDTO;
import com.shop.inventory.entity.Inventory;
import com.shop.inventory.entity.InventoryLog;
import com.shop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import result.PageResult;
import result.Result;
import security.RequirePermission;

@RestController
@RequestMapping("/admin/inventory")
@Validated
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/page")
    @RequirePermission("product:manage")
    public Result<PageResult<Inventory>> page(
            @RequestParam(value = "pageNum", defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于 0") long pageNum,

            @RequestParam(value = "pageSize", defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于 0") long pageSize,

            @RequestParam(value = "skuId", required = false) Long skuId) {
        return Result.success(inventoryService.page(pageNum, pageSize, skuId));
    }

    @GetMapping("/log/page")
    @RequirePermission("product:manage")
    public Result<PageResult<InventoryLog>> logPage(
            @RequestParam(value = "pageNum", defaultValue = "1")
            @Min(value = 1, message = "pageNum 必须大于 0") long pageNum,

            @RequestParam(value = "pageSize", defaultValue = "10")
            @Min(value = 1, message = "pageSize 必须大于 0") long pageSize,

            @RequestParam(value = "skuId", required = false) Long skuId) {
        return Result.success(inventoryService.logPage(pageNum, pageSize, skuId));
    }

    @PostMapping("/init")
    @RequirePermission("product:manage")
    public Result<Void> init(@Valid @RequestBody StockChangeDTO dto) {
        inventoryService.init(dto);
        return Result.success();
    }

    @PostMapping("/increase")
    @RequirePermission("product:manage")
    public Result<Void> increase(@Valid @RequestBody StockChangeDTO dto) {
        inventoryService.increase(dto);
        return Result.success();
    }
}