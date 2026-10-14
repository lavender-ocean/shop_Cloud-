package com.shop.api.fallback;

import com.shop.api.dto.StockTccReq;
import com.shop.api.feign.InventoryTccClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import result.Result;
import result.ResultCode;

import java.util.List;

/** 降级必须 fail-closed：返回失败，让编排走补偿/回滚，绝不能返回"成功" */
@Slf4j
@Component
public class InventoryTccClientFallbackFactory implements FallbackFactory<InventoryTccClient> {


//    1. TCC 调用失败（网络/超时/5xx/限流）
//            │
//            ▼
//            2. Feign 捕获异常，调 FallbackFactory.create(cause)
//        │
//                ▼
//                3. 降级返回一个 InventoryTccClient 的匿名实现
//        │
//                ▼
//                4. 调用 tryLock() → 返回 Result.fail(SERVICE_UNAVAILABLE, "库存服务暂不可用")
//        │   ← 到这里为止，没有任何异常被抛
//        ▼
//                5. OrderTccService 拿到这个 Result，判断 code != 0
//            │
//            ▼
//            6. 主动 throw new BizException(code, msg)   ←  异常是这里抛的

    @Override
    public InventoryTccClient create(Throwable cause) {
        log.warn("调用 shop-inventory TCC 接口失败，触发降级: {}", cause.getMessage());
        return new InventoryTccClient() {
            @Override
            public Result<Void> tryLock(StockTccReq req) {
                return unavailable();
            }

            @Override
            public Result<Void> confirm(StockTccReq req) {
                return unavailable();
            }

            @Override
            public Result<Void> cancel(StockTccReq req) {
                return unavailable();
            }

            @Override
            public Result<List<StockTccReq>> staleBranches(int limit) {
                return unavailable();
            }

            private <T> Result<T> unavailable() {
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE, "库存服务暂不可用");
            }
        };
    }
}