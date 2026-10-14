package com.shop.api.fallback;

import com.shop.api.dto.SkuInfoDTO;
import com.shop.api.feign.ProductClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import result.Result;
import result.ResultCode;

import java.util.List;

/** 商品服务降级：查询类接口 fail-safe */
@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        log.warn("调用 shop-product 失败，触发降级: {}", cause.getMessage());
        return new ProductClient() {
            @Override
            public Result<SkuInfoDTO> getSku(Long id) {
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
            }

            @Override
            public Result<List<SkuInfoDTO>> batchGetSku(List<Long> skuIds) {
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
            }
        };
    }
}