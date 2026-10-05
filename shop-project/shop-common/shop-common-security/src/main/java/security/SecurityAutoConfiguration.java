package security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils(JwtProperties props) {
        return new JwtUtils(props);
    }

    /** PermissionLoader 仅在存在 Redis 时装配 */
    @AutoConfiguration
    @ConditionalOnClass(StringRedisTemplate.class)
    static class PermissionAutoConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public PermissionLoader permissionLoader(StringRedisTemplate redisTemplate) {
            return new PermissionLoader(redisTemplate);
        }
    }

    /** 仅 Servlet 应用：注册身份拦截器 + 权限拦截器 */
    @AutoConfiguration
    @ConditionalOnClass({WebMvcConfigurer.class, HandlerInterceptor.class})
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class SecurityWebAutoConfiguration {
        @Bean
        public WebMvcConfigurer securityWebMvcConfigurer(ObjectProvider<PermissionLoader> permissionLoaderProvider) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new AuthInterceptor()).addPathPatterns("/**").order(0);
                    PermissionLoader loader = permissionLoaderProvider.getIfAvailable();
                    if (loader != null) {
                        registry.addInterceptor(new PermissionInterceptor(loader)).addPathPatterns("/**").order(1);
                    }
                }
            };
        }
    }
}