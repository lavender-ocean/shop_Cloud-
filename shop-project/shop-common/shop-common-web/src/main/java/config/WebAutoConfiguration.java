package config;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import filter.TraceIdFilter;
import handler.GlobalExceptionHandler;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@AutoConfiguration
public class WebAutoConfiguration {

    private static final String DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE = "yyyy-MM-dd";

    /** traceId 过滤器，最高优先级，保证最先执行 */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("traceIdFilter");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Jackson 统一配置：
     * - Long -> String（防前端雪花 ID 精度丢失；只转包装类型 Long，保留基本类型 long 为数字）
     * - LocalDateTime/LocalDate 统一格式
     * - 忽略 null 字段
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer shopJacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);

            DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern(DATE_TIME);
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(DATE);
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFmt));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFmt));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(dateFmt));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(dateFmt));

            builder.serializationInclusion(JsonInclude.Include.NON_NULL);
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI shopOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("shop-cloud API")
                .description("线上超市分布式电商平台接口文档")
                .version("1.0.0"));
    }
}