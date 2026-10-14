//package security;
//
//import context.LoginUser;
//import context.UserContext;
//import exception.BizException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.web.method.HandlerMethod;
//import org.springframework.web.servlet.HandlerInterceptor;
//import result.ResultCode;
//
//import java.util.Arrays;
//import java.util.Set;
//
//
////权限拦截器
///** 校验处理器上的 @RequirePermission；抛出的 BizException 由全局异常处理 */
//public class PermissionInterceptor implements HandlerInterceptor {
//
//    private final PermissionLoader permissionLoader;
//
//    public PermissionInterceptor(PermissionLoader permissionLoader) {
//
//
//        this.permissionLoader = permissionLoader;
//    }
//
//    @Override
////    还是拦截器啊
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
//        //静态资源放行
//        if (!(handler instanceof HandlerMethod handlerMethod)) {
//            return true;
//        }
//        //查这个方法（Contorller）上面有没有RequirePermission的注解
//        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
//        if (annotation == null) {
//            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
//        }
//        if (annotation == null) {
//            return true;   // 无需鉴权
//        }
//        //如果有这个注解
//        LoginUser user = UserContext.get();
//        //如果当前的userId是null --- 那注解绝对要挂 抛出异常
//        if (user == null || user.getUserId() == null) {
//            throw new BizException(ResultCode.UNAUTHORIZED);
//        }
//        //把允许的权限都读出来
//        Set<String> permissions = permissionLoader.load(user.getUserId());
//        //校验注解的是否包含要求的字符串----就是有没有这个权限！！！
//        boolean allowed = Arrays.stream(annotation.value()).anyMatch(permissions::contains);
//        if (!allowed) {
//            //没有权限也不允许去正常访问Controller
//            throw new BizException(ResultCode.FORBIDDEN);
//        }
//        return true;
//    }
//}



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
import java.util.Collections;
import java.util.Set;

/**
 * 细粒度鉴权。权限来源优先级：
 *   1. UserContext.permissions（来自 token，网关透传）—— 主通道，零远程依赖
 *   2. PermissionLoader（Redis）—— 仅当 token 未携带权限时兜底（如内部直连/旧 token）
 */
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
            return true;
        }
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        Set<String> permissions = resolvePermissions(user);
        boolean allowed = Arrays.stream(annotation.value()).anyMatch(permissions::contains);
        if (!allowed) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return true;
    }

    private Set<String> resolvePermissions(LoginUser user) {
        // 1. 主通道：token 内嵌权限
        if (user.getPermissions() != null) {
            return Set.copyOf(user.getPermissions());
        }
        // 2. 兜底：Redis（token 未带权限时）
        Set<String> fromRedis = permissionLoader == null
                ? Collections.emptySet()
                : permissionLoader.load(user.getUserId());
        return fromRedis;
    }
}