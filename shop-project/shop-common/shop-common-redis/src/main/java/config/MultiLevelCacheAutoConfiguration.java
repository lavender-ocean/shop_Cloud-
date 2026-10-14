package config;

import cache.MultiLevelCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@AutoConfiguration
@ConditionalOnClass({Caffeine.class, StringRedisTemplate.class})
public class MultiLevelCacheAutoConfiguration {

//    @Bean
//    @ConditionalOnMissingBean
//    public MultiLevelCache multiLevelCache(StringRedisTemplate redisTemplate, Environment environment) {
//        return new MultiLevelCache(redisTemplate, environment.getProperty("spring.application.name"));
//    }


    @Bean
    @ConditionalOnMissingBean
    public MultiLevelCache multiLevelCache(StringRedisTemplate redisTemplate,
                                           Environment environment) {
        //传入单线程的执行定时轮询时间
        long pollInterval = Long.parseLong(
                environment.getProperty("shop.cache.poll-interval-ms", "1000"));
        return new MultiLevelCache(redisTemplate,
                environment.getProperty("spring.application.name"), pollInterval);
    }

    /** 订阅本服务的失效频道 */
    @Bean
    public RedisMessageListenerContainer cacheEvictListenerContainer(
            RedisConnectionFactory connectionFactory,
            MultiLevelCache multiLevelCache) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> multiLevelCache.invalidateLocal(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(MultiLevelCache.channel(multiLevelCache.appName())));
        return container;
    }
}