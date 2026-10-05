package security;

import context.LoginUser;
import context.UserContext;
import exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import result.ResultCode;

import java.util.Arrays;
import java.util.Set;

/** 校验处理器上的 @RequirePermission；抛出的 BizException 由全局异常处理 */
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionLoader permissionLoader;

    public PermissionInterceptor(PermissionLoader permissionLoader) {
        this.permissionLoader = permissionLoader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation == null) {
            return true;   // 无需鉴权
        }
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Set<String> permissions = permissionLoader.load(user.getUserId());
        boolean allowed = Arrays.stream(annotation.value()).anyMatch(permissions::contains);
        if (!allowed) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return true;
    }
}