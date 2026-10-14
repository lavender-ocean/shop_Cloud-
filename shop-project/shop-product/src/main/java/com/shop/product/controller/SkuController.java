package com.shop.product.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.shop.api.dto.SkuInfoDTO;
import com.shop.product.entity.Sku;
import com.shop.product.entity.Spu;
import com.shop.product.mapper.SkuMapper;
import com.shop.product.mapper.SpuMapper;
import exception.BizAssert;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.Result;
import result.ResultCode;

@RestController
@RequestMapping("/api/product/sku")
public class SkuController {

    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;

    public SkuController(SkuMapper skuMapper, SpuMapper spuMapper) {
        this.skuMapper = skuMapper;
        this.spuMapper = spuMapper;
    }

    @GetMapping("/{id}")
    @SentinelResource(value = "product:getSku", blockHandler = "getSkuBlocked")
    public Result<SkuInfoDTO> getSku(@PathVariable Long id) {
        Sku sku = skuMapper.selectById(id);
        BizAssert.notNull(sku, ResultCode.SKU_NOT_FOUND);

        SkuInfoDTO dto = new SkuInfoDTO();
        BeanUtils.copyProperties(sku, dto);
        Spu spu = spuMapper.selectById(sku.getSpuId());
        if (spu != null) {
            dto.setSpuName(spu.getName());
        }
        return Result.success(dto);
    }

    /** 限流/熔断后的兜底：签名 = 原方法参数 + BlockException */
    public Result<SkuInfoDTO> getSkuBlocked(Long id, BlockException ex) {
        return Result.fail(ResultCode.RATE_LIMIT, "商品查询过于频繁，请稍后再试");
    }
}