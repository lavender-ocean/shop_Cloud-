package com.shop.product.controller;

import com.shop.product.dto.SpuQuery;
import com.shop.product.entity.Spu;
import com.shop.product.mapper.SpuMapper;
import com.shop.product.service.SpuService;
import com.shop.product.vo.SpuDetailVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.PageResult;
import result.Result;

import com.shop.api.dto.SkuInfoDTO;
import com.shop.product.mapper.SkuMapper;
import com.shop.product.entity.Sku;
import org.springframework.beans.BeanUtils;
import exception.BizAssert;
import result.ResultCode;

@RestController
@RequestMapping("/api/product/spu")
public class SpuController {

    private final SpuService spuService;

    private final SkuMapper skuMapper;

    public SpuController(SpuService spuService, SkuMapper skuMapper)
    {
        this.spuService = spuService;
        this.skuMapper = skuMapper;
    }

    @GetMapping("/page")
    public Result<PageResult<Spu>> page(SpuQuery query) {
        query.setStatus(1);
        return Result.success(spuService.page(query));
    }

    // ✅ 补 ("id")
    @GetMapping("/{id}")
    public Result<SpuDetailVO> detail(@PathVariable("id") Long id) {

        return Result.success(spuService.detail(id));
    }

    @GetMapping("/sku/{id}")
    public Result<SkuInfoDTO> getSku(@PathVariable("id") Long id) {
        Sku sku = skuMapper.selectById(id);      // 需在 SkuMapper 加 selectById
        BizAssert.notNull(sku, ResultCode.SKU_NOT_FOUND);
        SkuInfoDTO dto = new SkuInfoDTO();
        BeanUtils.copyProperties(sku, dto);
        return Result.success(dto);
    }




}