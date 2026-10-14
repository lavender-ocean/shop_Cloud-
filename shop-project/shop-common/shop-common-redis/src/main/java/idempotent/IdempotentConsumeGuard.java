package idempotent;

import constant.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MQ 消费去重（快路径）：SETNX 占位。
 *  消费失败必须 release，否则重试会被误判为"重复"而跳过。
 * 严格场景仍需 DB 唯一键兜底（如 payment_record.third_trade_no、inventory_log）。
 */
@Component
public class IdempotentConsumeGuard {

    private final StringRedisTemplate redisTemplate;

    public IdempotentConsumeGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //MQ消费者防幂等
    /** @return true = 首次处理；false = 已处理过 */
    public boolean tryAcquire(String bizKey, Duration ttl) {
        //使用 setIfAbsent 如果存在这个key！说明已经存在redis过了，这次是幂等
        //    如果不存在这个key！说明这是第一次，允许MQ去消费掉

        //关键的ttl！！！------用来小兜底
//        1. MQ 消息到来，`tryAcquire` 设置 key 占位，返回 true → 开始执行业务消费；
//        2. **如果业务抛出异常、消费失败，但没有调用`release(bizKey)`**，Redis 这个 key 会一直存在，直到 TTL 过期。
//        3. MQ broker 重试投递同一条消息；再次进入`tryAcquire`，`setIfAbsent`返回 false，直接判定为重复消息，直接跳过消费。
// **消息就丢了！业务不会再处理这条消息，直到 TTL 到期。**

        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeyConstants.IDEMPOTENT_CONSUME + bizKey, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

//    TTL 过期之后，Redis 里的占位 key 消失，这套 Redis 前置幂等就失效了。
//    如果 Broker 再次投递这条消息，会被当成新消息，执行业务，发生重复消费。**
//    但这不是无解，有两层兜底：Redis 只是**快路径**，底层还有**数据库唯一索引兜底**。
    //DB的大兜底
    public void release(String bizKey) {
        redisTemplate.delete(RedisKeyConstants.IDEMPOTENT_CONSUME + bizKey);
    }
}