package cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import util.JsonUtils;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 多级缓存（Caffeine L1 + Redis L2）。
 *
 * 多实例一致性 = Push 加速 + Pull 精确兜底 + 日志截断水位保守兜底 + TTL 终极兜底 + Epoch 防 Redis 重启。
 *
 *  evict(key)  → 删本地 + 删 Redis
 *              + 全局序号 INCR（作为失效日志的游标）
 *              + ZADD cache:inval:{app} score=序号 member=fullKey（失效日志）
 *              + 裁剪日志只留最近 MAX_LOG 条
 *              + PUBLISH（推，加速）
 *
 *  轮询        → 序号落后时，从失效日志里精确取出 (seen, remote] 的 key 逐个失效；
 *              若日志最老一条的序号已经 > seen（被裁剪掉了，无法重放）→ 回退 invalidateAll 保守清空。
 *
 * 本地缓存"只删不写"，因此天然免疫广播乱序/重复。
 */
@Slf4j
public class MultiLevelCache {

    private static final String KEY_PREFIX = "cache:";
    private static final String VERSION_KEY = "cache:ver:";
    private static final String EPOCH_KEY = "cache:epoch:";
    /** 失效日志（ZSET：score=全局序号, member=fullKey） */
    private static final String INVAL_LOG_KEY = "cache:inval:";
    /** 失效日志最多保留多少条，超出则裁剪（配合下面的水位判断保证不丢失效） */
    private static final long MAX_LOG = 1000L;
    private static final Duration LOG_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final String appName;

    private final Cache<String, Object> local = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.SECONDS)   // TTL 终极兜底
            .build();

    /** 本实例已确认重放到的全局序号；-1 = 还没对齐过基线 */
    private final AtomicLong seenVersion = new AtomicLong(-1);
    private volatile String seenEpoch;

    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mlc-version-poller");
                t.setDaemon(true);
                return t;
            });

    public MultiLevelCache(StringRedisTemplate redisTemplate, String appName, long pollIntervalMs) {
        this.redisTemplate = redisTemplate;
        this.appName = appName;
        this.poller.scheduleWithFixedDelay(this::pollSafely, pollIntervalMs,
                pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    public String appName() {
        return appName;
    }

    public static String channel(String appName) {
        return "cache:evict:" + appName;
    }

    private String fullKey(String key) {
        return KEY_PREFIX + appName + ":" + key;
    }

    private String versionKey() {
        return VERSION_KEY + appName;
    }

    private String invalLogKey() {
        return INVAL_LOG_KEY + appName;
    }

    /* ==================== 读 ==================== */

    public <T> T get(String key, Class<T> type, Duration redisTtl, Supplier<T> loader) {
        String fullKey = fullKey(key);

        Object cached = local.getIfPresent(fullKey);
        if (cached != null) {
            log.debug("[多级缓存] L1 命中 key={}", fullKey);
            return type.cast(cached);
        }

        String json = redisTemplate.opsForValue().get(fullKey);
        if (json != null) {
            log.debug("[多级缓存] L2 命中 key={}", fullKey);
            T value = JsonUtils.parse(json, type);
            local.put(fullKey, value);
            return value;
        }

        log.debug("[多级缓存] 回源 DB key={}", fullKey);
        T value = loader.get();
        if (value != null) {
            redisTemplate.opsForValue().set(fullKey, JsonUtils.toJson(value), redisTtl);
            local.put(fullKey, value);
        }
        return value;
    }

    /* ==================== 写后失效 ==================== */

    public void evict(String key) {
        String fullKey = fullKey(key);
        local.invalidate(fullKey);

        // 删 L2：失败则抛出，让调用方的本地事务回滚 —— 宁可写失败，也不要留下脏缓存
        redisTemplate.delete(fullKey);

        Long seq = redisTemplate.opsForValue().increment(versionKey());
        if (seq == null) {
            log.warn("[多级缓存] 序号自增失败，跳过失效日志 key={}", fullKey);
            return;
        }

        // 失效日志：score = 序号，member = fullKey
        String logKey = invalLogKey();
        redisTemplate.opsForZSet().add(logKey, fullKey, seq);
        // 只保留最近 MAX_LOG 条：裁掉 score <= seq-MAX_LOG 的老记录
        redisTemplate.opsForZSet().removeRangeByScore(logKey, 0, seq - MAX_LOG);
        redisTemplate.expire(logKey, LOG_TTL);

        // 广播只是加速，丢了有轮询兜底
        try {
            redisTemplate.convertAndSend(channel(appName), fullKey + "|" + seq);
        } catch (Exception e) {
            log.warn("[多级缓存] 广播失败（轮询会兜底）key={}", fullKey, e);
        }
        log.info("[多级缓存] 已失效 key={}, seq={}", fullKey, seq);
    }

    /** 收到广播：只删本地目标 key（不推进 seenVersion，等轮询统一推进） */
    public void invalidateLocal(String message) {
        String fullKey = message.contains("|") ? message.substring(0, message.indexOf('|')) : message;
        boolean removed = local.getIfPresent(fullKey) != null;
        local.invalidate(fullKey);
        if (removed) {
            log.info("[多级缓存] 收到广播，清除本地 key={}", fullKey);
        }
    }

    /* ==================== 兜底轮询 ==================== */

    private void pollSafely() {
        try {
            pollOnce();
        } catch (Exception e) {
            // Redis 不可用时不要清空本地缓存（Redis 挂了 L2 也没了，清空会打爆 DB）
            log.warn("[多级缓存] 版本轮询失败（忽略）: {}", e.getMessage());
        }
    }

    private void pollOnce() {
        // 1. 纪元检测：Redis 重启/换实例 → 版本号丢失，只能全清
        String epoch = redisTemplate.opsForValue().get(EPOCH_KEY + appName);
        if (epoch == null) {
            redisTemplate.opsForValue().setIfAbsent(EPOCH_KEY + appName, UUID.randomUUID().toString());
            epoch = redisTemplate.opsForValue().get(EPOCH_KEY + appName);
        }
        if (epoch != null && !epoch.equals(seenEpoch)) {
            if (seenEpoch != null) {
                log.warn("[多级缓存] 检测到 Redis 纪元变化({} -> {})，清空本地缓存", seenEpoch, epoch);
                local.invalidateAll();
            }
            seenEpoch = epoch;
            seenVersion.set(-1);
        }

        // 2. 序号检测
        String remoteStr = redisTemplate.opsForValue().get(versionKey());
        if (remoteStr == null) {
            return;
        }
        long remote = Long.parseLong(remoteStr);
        long seen = seenVersion.get();
        if (seen < 0) {
            // 服务刚启动/纪元换代：只对齐基线，避免无意义全清（本地本来就是空的）
            seenVersion.set(remote);
            return;
        }
        if (remote <= seen) {
            return;
        }

        // 3. 从失效日志里精确重放 (seen, remote] 区间
        String logKey = invalLogKey();
        Set<ZSetOperations.TypedTuple<String>> oldest =
                redisTemplate.opsForZSet().rangeWithScores(logKey, 0, 0);
        Double floor = (oldest == null || oldest.isEmpty())
                ? null : oldest.iterator().next().getScore();

        if (floor == null || seen < floor) {
            // 日志被裁剪/过期，区间内可能有取不到的失效 → 保守全清（保证不脏读）
            log.warn("[多级缓存] 失效日志已截断(floor={}, seen={})，保守清空本地缓存", floor, seen);
            local.invalidateAll();
        } else {
            Set<String> keys = redisTemplate.opsForZSet()
                    .rangeByScore(logKey, seen, remote);   // 含 seen，重复失效无害
            if (keys != null && !keys.isEmpty()) {
                keys.forEach(local::invalidate);
                log.info("[多级缓存] 精确补齐 {} 个漏掉的失效 key（seen={} → remote={}）",
                        keys.size(), seen, remote);
            }
        }
        seenVersion.set(remote);
    }

    @PreDestroy
    public void shutdown() {
        poller.shutdownNow();
    }
}