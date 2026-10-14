package context;


//clear很重要！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
/**
 * 当前登录用户上下文。
 * 必须在请求结束时 clear()，否则线程池复用会串用户数据。
 */

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 当前登录用户上下文。
 * 用 TransmittableThreadLocal（阿里 TTL）→ 跨线程池自动透传。
 * 必须在请求结束时 clear()，否则线程复用会串用户数据。
 * TTL 只负责 ThreadLocal 类；MDC（traceId）需在任务里单独回放。
 */
public final class UserContext {
    //使用ThreadLocal来做
    // 等做异步编排时换成 TransmittableThreadLocal（阿里的 TTL）
    // 实现跨线程池上下文透传——那是后期，现在别加依赖。
//    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    //现在开始TTL
    private static final ThreadLocal<LoginUser> HOLDER = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }
    public static LoginUser get() {
        return HOLDER.get();
    }
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }
    public static void clear() {
        HOLDER.remove();
    }
}
