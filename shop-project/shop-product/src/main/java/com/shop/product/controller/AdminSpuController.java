package com.shop.product.controller;

import com.shop.api.dto.SkuInfoDTO;
import com.shop.product.dto.SpuDTO;
import com.shop.product.dto.SpuQuery;
import com.shop.product.entity.Spu;
import com.shop.product.mapper.SkuMapper;
import com.shop.product.service.SpuService;
import com.shop.product.vo.SpuDetailVO;
import exception.BizAssert;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import result.PageResult;
import result.Result;
import result.ResultCode;
import security.RequirePermission;

import java.util.List;

@RestController
@RequestMapping("/admin/product/spu")
public class AdminSpuController {

    private final SpuService spuService;
    private final SkuMapper skuMapper;

    public AdminSpuController(SpuService spuService, SkuMapper skuMapper) {
        this.spuService = spuService;
        this.skuMapper = skuMapper;
    }

    @GetMapping("/page")
    @RequirePermission("product:spu:list")
    public Result<PageResult<Spu>> page(SpuQuery query) {
        return Result.success(spuService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("product:spu:list")
    public Result<SpuDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(spuService.detail(id));
    }

    @PostMapping
    @RequirePermission("product:spu:add")
    public Result<Long> add(@Valid @RequestBody SpuDTO dto) {
        return Result.success(spuService.add(dto));
    }

    @PutMapping
    @RequirePermission("product:spu:edit")
    public Result<Void> update(@Valid @RequestBody SpuDTO dto) {
        spuService.update(dto);
        return Result.success();
    }

    @PutMapping("/{id}/status/{status}")
    @RequirePermission("product:spu:edit")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @PathVariable("status") Integer status) {
        spuService.updateStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("product:spu:delete")
    public Result<Void> delete(@PathVariable("id") Long id) {
        spuService.delete(id);
        return Result.success();
    }

    /**
     * 单 SKU 查询。
     * 复用带 spuName 的批量 SQL（join spu），避免与 /api/product/sku/{id} 出现两套不一致的结果。
     */
    @GetMapping("/sku/{id}")
    @RequirePermission("product:spu:list")     //
    public Result<SkuInfoDTO> getSku(@PathVariable("id") Long id) {
        List<SkuInfoDTO> list = skuMapper.selectInfoByIds(List.of(id));
        BizAssert.notEmpty(list, ResultCode.SKU_NOT_FOUND);
        return Result.success(list.get(0));
    }
}