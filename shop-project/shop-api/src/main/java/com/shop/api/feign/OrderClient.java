package com.shop.api.feign;

import com.shop.api.dto.OrderSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import result.Result;

@FeignClient(name = "shop-order")
public interface OrderClient {

    @GetMapping("/inner/order/{orderNo}")
    Result<OrderSummaryDTO> getOrder(@PathVariable("orderNo") String orderNo);
}