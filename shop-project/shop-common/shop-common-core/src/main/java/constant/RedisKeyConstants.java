package constant;

public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }
    // cart:{userId}
    public static final String CART = "cart:";
    // stock:sku:{skuId}
    public static final String STOCK = "stock:sku:";
    // seckill:stock:{actId}:{skuId}
    public static final String SECKILL_STOCK = "seckill:stock:";
    // cache:product:{skuId}
    public static final String CACHE_PRODUCT = "cache:product:";
    // idempotent:token:{token}
    public static final String IDEMPOTENT = "idempotent:token:";
    // 登出黑名单
    public static final String TOKEN_BLACKLIST = "auth:blacklist:";
    // refresh token
    public static final String REFRESH_TOKEN = "auth:refresh:";
    // 限流维度 key
    public static final String LIMIT = "limit:";
    // 分布式锁前缀
    public static final String LOCK = "lock:";

    // auth:perms:{userId}    权限缓存 key
    public static final String USER_PERMISSIONS = "auth:perms:";

    // MQ 消费去重
    public static final String IDEMPOTENT_CONSUME = "idempotent:consume:";
}