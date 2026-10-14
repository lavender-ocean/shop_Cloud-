package com.shop.api.fallback;

import com.shop.api.dto.UserAddressDTO;
import com.shop.api.dto.UserProfileDTO;
import com.shop.api.feign.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import result.Result;
import result.ResultCode;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {


    //code 返回 11002
    @Override
    public UserClient create(Throwable cause) {
        log.warn("调用 shop-user 失败，触发降级: {}", cause.getMessage());

        return new UserClient() {

            @Override
            public Result<UserAddressDTO> getAddress(Long id) {
                return unavailable();
            }

            @Override
            public Result<UserProfileDTO> getProfile(Long userId) {
                return unavailable();
            }


            private <T> Result<T> unavailable() {
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE,"用户服务暂不可用，请稍后重试");
            }
        };


    }
}