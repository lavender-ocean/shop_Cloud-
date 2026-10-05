package filter;


import constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jboss.logging.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路 ID 过滤器：优先复用上游传入的 traceId（网关/Feign 透传），否则新建。
 * 写入 MDC 供日志使用，并回写到响应头。
 */
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String MDC_TRACE_ID = "traceId";
    @Override
    protected void doFilterInternal(
            HttpServletRequest request
            , HttpServletResponse response
            , FilterChain filterChain) throws ServletException, IOException {
        //使用安全常量
        //从请求头把traceId---链路Id拿出来
        String traceId = request.getHeader(SecurityConstants.HEADER_TRACE_ID);
        if(!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");

        }
        MDC.put(MDC_TRACE_ID, traceId);
        //再放回请求头中去
        response.setHeader(SecurityConstants.HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理，防止线程复用导致 traceId 串号
            MDC.remove(MDC_TRACE_ID);
        }


    }
}
