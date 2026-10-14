package com.shop.api.fallback;

import com.shop.api.dto.InventoryDTO;
import com.shop.api.dto.StockLockReq;
import com.shop.api.feign.InventoryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import result.Result;
import result.ResultCode;

/**
 * 库存服务降级。
 * 库存是"写"操作，必须 fail-closed（返回失败），让上游回滚/重试；
 *    绝不能降级成"成功"，否则会超卖。
 */
@Slf4j
@Component
// 首先使用实现FallbackFactory<接口>来去实现这个fallback  ！！！能拿到异常原因（最核心的差异）
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {


    //调用库存服务，如果失败了，然后触发降级，那么应该直接抛出异常code，不能用fallback吃掉，不然会出现数据问题
    //明明业务失败了，库存应该回滚数据，但是没抛出异常，就会导致以为正常扣减了，莫名其妙买的更多了！！，超卖！！！
    //设置code = 11002 告诉说失败了，去BizException抛出异常
    @Override
    public InventoryClient create(Throwable cause) {
        log.warn("调用 shop-inventory 失败，触发降级: {}", cause.getMessage());
        return new InventoryClient() {
            @Override
            public Result<Void> lock(StockLockReq req) {
                return unavailable();
            }

            @Override
            public Result<Void> confirm(StockLockReq req) {
                return unavailable();
            }

            @Override
            public Result<Void> release(StockLockReq req) {
                return unavailable();
            }

            @Override
            public Result<InventoryDTO> getStock(Long skuId) {
                return unavailable();
            }

            private <T> Result<T> unavailable() {
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE, "库存服务暂不可用，请稍后重试");
            }
        };
    }
}