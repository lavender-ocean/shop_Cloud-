package config;

import constant.SecurityConstants;
import context.LoginUser;
import context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 【出站】Feign 调用前：把 traceId 与当前登录用户透传给下游。
 *  - traceId  ← MDC          （日志上下文，由 TraceIdFilter 写入）
 *  - 用户身份 ← UserContext   （业务上下文，由 AuthInterceptor 写入）
 */
public class FeignTraceInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 1) traceId
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            template.header(SecurityConstants.HEADER_TRACE_ID, traceId);
        }

        // 2) 用户身份（从 UserContext 取）
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            return;
        }
        template.header(SecurityConstants.HEADER_USER_ID, String.valueOf(user.getUserId()));
        if (StringUtils.hasText(user.getUsername())) {
            template.header(SecurityConstants.HEADER_USER_NAME, user.getUsername());
        }
        if (user.getUserType() != null) {
            template.header(SecurityConstants.HEADER_USER_TYPE, String.valueOf(user.getUserType()));
        }

        // 3) 权限码：必须与网关保持一致的【逗号分隔】格式
        //    原来写的是 user.getPermissions().toString()：
        //      a) permissions 为 null 时直接 NPE（网关只在 perms 非空时才注入头）
        //      b) List.toString() 产出 "[a, b]"（带方括号和空格），
        //         下游 AuthInterceptor 按 "," split 后拿到 "[a" / "b]" → 权限码全部对不上
        List<String> permissions = user.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            template.header(SecurityConstants.HEADER_USER_PERMS, String.join(",", permissions));
        }
    }
}