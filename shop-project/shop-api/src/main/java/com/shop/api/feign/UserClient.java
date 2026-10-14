package com.shop.api.feign;

import com.shop.api.dto.UserAddressDTO;
import com.shop.api.dto.UserProfileDTO;
import com.shop.api.fallback.UserClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import result.Result;

@FeignClient(name = "shop-user",
        fallbackFactory = com.shop.api.fallback.UserClientFallbackFactory.class)
public interface UserClient {

    /**
     * 内部接口：按地址 id 查地址。
     * user 服务不做归属校验，由 order 自己校验 userId。
     */
    @GetMapping("/inner/user/address/{id}")
    Result<UserAddressDTO> getAddress(@PathVariable("id") Long id);

    @GetMapping("/inner/user/profile/{userId}")
    Result<UserProfileDTO> getProfile(@PathVariable("userId") Long userId);
}