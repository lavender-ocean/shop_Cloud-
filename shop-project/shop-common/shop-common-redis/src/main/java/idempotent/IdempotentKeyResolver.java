package idempotent;

import constant.RedisKeyConstants;
import constant.SecurityConstants;
import exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import result.ResultCode;

import java.lang.reflect.Method;

@Component
public class IdempotentKeyResolver {

    //SpEL表达式解析器
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    //discovery发现参数名称
    private static final ParameterNameDiscoverer DISCOVERER = new DefaultParameterNameDiscoverer();

    public String resolve(ProceedingJoinPoint pjp, Idempotent idempotent) {
        return switch (idempotent.type()) {
            //    /** token 模式：从请求头 Idempotent-Token 取，一次性消费（GET+DEL） */
            case TOKEN -> RedisKeyConstants.IDEMPOTENT + resolveToken();
            //    /** 参数模式：key 由 SpEL 从方法参数生成，SETNX 占位；可配置完成后释放 */
            // 使用eval
            case PARAM -> RedisKeyConstants.IDEMPOTENT + "param:" + evalKey(pjp, idempotent.key());
        };
    }

    //进行请求头中解析Token
    private String resolveToken() {
        //从上下文拿到当前请求属性
        //- `RequestContextHolder`：Spring Web 提供的工具，**基于 ThreadLocal 保存当前 Http 请求对象**。
        //- 作用：在非 Controller 层（拦截器、AOP 切面、工具类）拿到本次 http 的 request。
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        //从请求头中拿到当前的幂等Token
        String token = attrs == null ? null : attrs.getRequest()
                .getHeader(SecurityConstants.HEADER_IDEMPOTENT_TOKEN);

        //使用hasText 除去空格
        if (!StringUtils.hasText(token)) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "缺少幂等 token");
        }
        return token;
    }

    //正常解析SpEl回填context上下文
    private String evalKey(ProceedingJoinPoint pjp, String expression) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalStateException("@Idempotent PARAM 模式必须配置 key");
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = DISCOVERER.getParameterNames(method);
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        return PARSER.parseExpression(expression).getValue(context, String.class);
    }
}