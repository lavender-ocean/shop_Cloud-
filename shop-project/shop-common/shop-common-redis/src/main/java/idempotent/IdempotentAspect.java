package idempotent;

import exception.BizAssert;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import result.ResultCode;

import java.util.List;

@Aspect
public class IdempotentAspect {

    /** TOKEN：存在则删除（原子消费），返回 1 表示消费成功 */
    //lua脚本
    private static final DefaultRedisScript<Long> TOKEN_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1])\n" +
                    "if v == false then return 0 end\n" +
                    "redis.call('DEL', KEYS[1])\n" +
                    "return 1", Long.class);

    /** PARAM：SETNX + 过期，返回 1 表示占位成功 */
    //使用SETNX   返回1表示站位成功
    private static final DefaultRedisScript<Long> PARAM_SCRIPT = new DefaultRedisScript<>(
            "local ok = redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1])\n" +
                    "if ok then return 1 else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final IdempotentKeyResolver resolver;

    public IdempotentAspect(StringRedisTemplate redisTemplate, IdempotentKeyResolver resolver) {
        this.redisTemplate = redisTemplate;
        this.resolver = resolver;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {

        //通过请求/SpEl 不同的方式解析出，输出不同的 Redis 完整 key
        String key = resolver.resolve(pjp, idempotent);

        //在这里去执行的lua脚本
        Long acquired = idempotent.type() == IdempotentType.TOKEN
                //第二个是key ---> 参数
                //在 Lua 脚本内部，通过 `KEYS[1]` 访问这个 key。
                //下标从**1 开始**，不是 0！`KEYS[1]` 对应 List 第 0 个元素。
                // redis的key必须放在这里
                // 没有使用ARGV[]
                ? redisTemplate.execute(TOKEN_SCRIPT, List.of(key))
                //ARGV[]放预期的时间
                : redisTemplate.execute(PARAM_SCRIPT, List.of(key), String.valueOf(idempotent.timeout()));

        BizAssert.isTrue(acquired != null && acquired == 1L,
                ResultCode.DUPLICATE_SUBMIT, idempotent.message());

        //`release`：是否**业务执行完毕之后，要把 Redis 里面的幂等 key 手动删除释放**。
        //就是1.类型必须是SpEl的 2.设置的必须是请求头token消费之后要释放的
        boolean release = idempotent.type() == IdempotentType.PARAM && idempotent.deleteAfter();
        try {
            //防幂等不能把业务防出去
            return pjp.proceed();
        } finally {
            //release在这里执行
            // PARAM 模式：执行完释放，只防"并发重复提交"；TOKEN 模式：已消费，不释放
            if (release) {
                redisTemplate.delete(key);
            }
        }
    }
}