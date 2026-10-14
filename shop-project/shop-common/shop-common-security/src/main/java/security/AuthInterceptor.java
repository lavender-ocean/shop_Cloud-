//package security;
//
//
//import constant.SecurityConstants;
//import context.LoginUser;
//import context.UserContext;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.util.StringUtils;
//import org.springframework.web.servlet.HandlerInterceptor;
//
///**
// * 服务端上下文拦截器：读取网关透传的用户头，写入 UserContext。
// *  内部服务信任这些头的前提是"外部流量只能经网关"，网关会强制覆盖这些头。
// */
//
////这个是入站
////身份拦截器
//public class AuthInterceptor implements HandlerInterceptor {
//
//    //跨服务调用，别的服务拿不到用户信息
//
//    //> ThreadLocal 只留在本机。需要把 userId 放到**请求头**，
//    // Feign 拦截器把 UserContext 里面的 userId 取出，塞进 HTTP Header；
//    //> 下游服务再从请求头解析出来，重新放到下游自己服务的 `UserContext`（下游自己的 ThreadLocal）。
//
//    //在Controller之前去执行
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
//        //网关把traceId和userid放进request中啦！！！
//        // 放进在request的Header之中了！！！
//        String userId = request.getHeader(SecurityConstants.HEADER_USER_ID);
//
//        if (StringUtils.hasText(userId)) {
//            LoginUser user = new LoginUser();
//            user.setUserId(Long.valueOf(userId));
//            //都是从request中取出对应的信息
//            user.setUsername(request.getHeader(SecurityConstants.HEADER_USER_NAME));
//            String userType = request.getHeader(SecurityConstants.HEADER_USER_TYPE);
//            if (StringUtils.hasText(userType)) {
//                //如果也传入了userType，那么一并把userType也存入LoginUser中
//                user.setUserType(Integer.valueOf(userType));
//            }
//            //最后使用的是ThreadLocal来做线程绑定资源！！！
//            UserContext.set(user);
//        }
//        return true;
//    }
//
//
//    //必须！！！清除掉Threadlocal的资源
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
//                                Object handler, Exception ex) {
//        UserContext.clear();
//    }
//}


package security;

import constant.SecurityConstants;
import context.LoginUser;
import context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 【入站】读取网关透传的用户头（身份 + 权限），写入本服务的 UserContext。
 * 信任前提：外部流量只能经网关，且网关会强制剥离并覆盖这些头。
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(SecurityConstants.HEADER_USER_ID);
        if (!StringUtils.hasText(userId)) {
            return true;   // 未登录请求（如白名单接口）
        }
        LoginUser user = new LoginUser();
        user.setUserId(Long.valueOf(userId));
        user.setUsername(request.getHeader(SecurityConstants.HEADER_USER_NAME));
        String userType = request.getHeader(SecurityConstants.HEADER_USER_TYPE);
        if (StringUtils.hasText(userType)) {
            user.setUserType(Integer.valueOf(userType));
        }
        // ★ 权限来自 token（网关注入），逗号分隔；头存在就以此为准（可能为空串 = 无权限）
        String perms = request.getHeader(SecurityConstants.HEADER_USER_PERMS);
        if (perms != null) {
            List<String> list = perms.isBlank()
                    ? List.of()
                    : Arrays.stream(perms.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            user.setPermissions(list);
        }
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}