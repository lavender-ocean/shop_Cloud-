package com.shop.inventory.controller;

import com.shop.api.dto.InventoryDTO;
import com.shop.inventory.dto.StockLockDTO;
import com.shop.inventory.entity.Inventory;
import com.shop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import result.Result;

@RestController
@RequestMapping("/inner/inventory")
public class InnerInventoryController {

    private final InventoryService inventoryService;

    public InnerInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }




    @PostMapping("/lock")
    public Result<Void> lock(@Valid @RequestBody StockLockDTO dto) {
        inventoryService.lock(dto);
        return Result.success();
    }

    @PostMapping("/confirm")
    public Result<Void> confirm(@Valid @RequestBody StockLockDTO dto) {
        inventoryService.confirm(dto);
        return Result.success();
    }

    @PostMapping("/release")
    public Result<Void> release(@Valid @RequestBody StockLockDTO dto) {
        inventoryService.release(dto);
        return Result.success();
    }



    @GetMapping("/stock/{skuId}")
    public Result<InventoryDTO> stock(@PathVariable("skuId") Long skuId) {
        Inventory inventory = inventoryService.getBySkuId(skuId);

        if (inventory == null) {
            return Result.success(null);
        }

        InventoryDTO dto = new InventoryDTO();
        BeanUtils.copyProperties(inventory, dto);

        return Result.success(dto);

    }

}