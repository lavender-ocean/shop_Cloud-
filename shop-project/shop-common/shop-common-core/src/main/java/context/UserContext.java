package context;


//clear很重要！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
/**
 * 当前登录用户上下文。
 * 必须在请求结束时 clear()，否则线程池复用会串用户数据。
 */
public final class UserContext {
    //使用ThreadLocal来做
    // 等做异步编排时换成 TransmittableThreadLocal（阿里的 TTL）
    // 实现跨线程池上下文透传——那是后期亮点，现在别加依赖。
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

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
