package config;

import idempotent.IdempotentAspect;
import idempotent.IdempotentConsumeGuard;
import idempotent.IdempotentKeyResolver;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass({Aspect.class, StringRedisTemplate.class})
public class IdempotentAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public IdempotentConsumeGuard idempotentConsumeGuard(StringRedisTemplate redisTemplate) {
        return new IdempotentConsumeGuard(redisTemplate);
    }


    @Bean
    @ConditionalOnMissingBean
    public IdempotentKeyResolver idempotentKeyResolver() {
        return new IdempotentKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(StringRedisTemplate redisTemplate,
                                             IdempotentKeyResolver idempotentKeyResolver) {
        return new IdempotentAspect(redisTemplate, idempotentKeyResolver);
    }
}