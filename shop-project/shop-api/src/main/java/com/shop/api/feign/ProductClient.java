package com.shop.api.feign;

import com.shop.api.dto.SkuInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import result.Result;

import java.util.List;

@FeignClient(name = "shop-product",
        fallbackFactory = com.shop.api.fallback.ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/inner/product/sku/{id}")
    Result<SkuInfoDTO> getSku(@PathVariable("id") Long id);

    /** 批量查询：购物车/订单一次拿全，避免 N+1。POST + body 防 URL 超长 */
    @PostMapping("/inner/product/sku/batch")
    Result<List<SkuInfoDTO>> batchGetSku(@RequestBody List<Long> skuIds);
}