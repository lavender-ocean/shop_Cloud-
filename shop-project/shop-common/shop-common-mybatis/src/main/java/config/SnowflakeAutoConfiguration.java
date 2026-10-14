//// 方案一：直接用 MP 的 IdentifierGenerator 扩展点（推荐，兼容所有 MP API）
//package config;
//
//import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class SnowflakeAutoConfiguration {
//
//    /** 每个实例必须唯一：容器用 StatefulSet 序号 / K8s Downward API 注入 */
//    @Bean
//    public IdentifierGenerator identifierGenerator(
//            @Value("${shop.snowflake.worker-id}") long workerId,
//            @Value("${shop.snowflake.datacenter-id}") long datacenterId) {
//        return new ShopSnowflakeIdGenerator(workerId, datacenterId);
//    }
//}