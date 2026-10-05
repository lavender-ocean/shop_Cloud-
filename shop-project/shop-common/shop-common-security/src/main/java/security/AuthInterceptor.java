package security;


import constant.SecurityConstants;
import context.LoginUser;
import context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 服务端上下文拦截器：读取网关透传的用户头，写入 UserContext。
 *  内部服务信任这些头的前提是"外部流量只能经网关"，网关会强制覆盖这些头。
 */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(SecurityConstants.HEADER_USER_ID);
        if (StringUtils.hasText(userId)) {
            LoginUser user = new LoginUser();
            user.setUserId(Long.valueOf(userId));
            user.setUsername(request.getHeader(SecurityConstants.HEADER_USER_NAME));
            String userType = request.getHeader(SecurityConstants.HEADER_USER_TYPE);
            if (StringUtils.hasText(userType)) {
                user.setUserType(Integer.valueOf(userType));
            }
            UserContext.set(user);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}