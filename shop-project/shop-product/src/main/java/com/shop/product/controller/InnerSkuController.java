package com.shop.product.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.shop.api.dto.SkuInfoDTO;
import com.shop.product.mapper.SkuMapper;
import exception.BizAssert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.Result;
import result.ResultCode;

import java.util.List;

/**
 * 内部 SKU 接口：仅供其他微服务通过 Feign 调用。
 * /inner/** 不配置网关路由，集群外部不可达。
 */
@RestController
@RequestMapping("/inner/product/sku")
public class InnerSkuController {

    private final SkuMapper skuMapper;

    public InnerSkuController(SkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @GetMapping("/{id}")
    @SentinelResource(value = "product:getSku", blockHandler = "getSkuBlocked")
    public Result<SkuInfoDTO> getSku(@PathVariable("id") Long id) {
        List<SkuInfoDTO> list = skuMapper.selectInfoByIds(List.of(id));
        BizAssert.notEmpty(list, ResultCode.SKU_NOT_FOUND);
        return Result.success(list.get(0));
    }

    /** 批量查询：购物车/订单一次拿全 */
    @PostMapping("/batch")
    @SentinelResource(value = "product:batchGetSku", blockHandler = "batchBlocked")
    public Result<List<SkuInfoDTO>> batch(@RequestBody List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Result.success(List.of());
        }
        return Result.success(skuMapper.selectInfoByIds(skuIds));
    }

    /** 限流/熔断兜底：签名 = 原方法参数 + BlockException */
    public Result<SkuInfoDTO> getSkuBlocked(Long id, BlockException ex) {
        return Result.fail(ResultCode.RATE_LIMIT, "商品查询过于频繁，请稍后再试");
    }

    public Result<List<SkuInfoDTO>> batchBlocked(List<Long> skuIds, BlockException ex) {
        return Result.fail(ResultCode.RATE_LIMIT, "商品查询过于频繁，请稍后再试");
    }
}