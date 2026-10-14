package constant;

public final class SecurityConstants {

    private SecurityConstants() {
    }

    /** 请求头：认证 */
    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    //内网请求头，很重要！！！！！！！！！！！！！！！！！！！！！！！！！！！！
    /** 网关透传给下游服务的内网请求头（防止外部伪造，网关会强制覆盖） */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_TYPE = "X-User-Type";
    /** 链路 ID */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    //JWT的东西
    public static final String JWT_CLAIM_USER_ID = "userId";
    public static final String JWT_CLAIM_USERNAME = "username";
    public static final String JWT_CLAIM_USER_TYPE = "userType";

    /** 幂等 token 请求头 */
    public static final String HEADER_IDEMPOTENT_TOKEN = "Idempotent-Token";

    /** 网关透传的权限码（逗号分隔） */
    public static final String HEADER_USER_PERMS = "X-User-Perms";

}