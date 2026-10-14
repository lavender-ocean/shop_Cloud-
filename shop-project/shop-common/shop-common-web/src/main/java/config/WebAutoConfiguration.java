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

    //SpringBoot 使用 Jackson 做 JSON 序列化 / 反序列化。这里统一全局规则：
    //1. `Long.class → ToStringSerializer`
    //> **解决雪花 ID 精度丢失问题**：后端 Long（雪花 id）返回给前端 JS，JS Number 只能安全存 53 位；超过就精度丢失。把 Long 序列化为字符串传给前端。
    //> 注意：只处理包装类 `Long`；基础类型 `long` 不受影响。
    //2. LocalDateTime / LocalDate 时间序列化、反序列化

    //> Java8 时间类，默认 Jackson 会输出一堆数组`[2026,10,10,18,30]`，很不友好。这里指定格式 `yyyy‑MM‑dd HH:mm:ss`。
    //> 同时配置序列化（后端输出 JSON）、反序列化（前端传 JSON 时间字符串到后端对象）双向。

    //3. `serializationInclusion(JsonInclude.Include.NON_NULL)`
    //> 对象字段值为 null 时，JSON 输出直接忽略这个字段，不输出 `"field":null`。

    //4. `timeZone(Asia/Shanghai)` 设置东八区时区，防止时间偏移 8 小时 bug。
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