package com.shop.api.config;

import com.shop.api.fallback.InventoryClientFallbackFactory;
import com.shop.api.fallback.InventoryTccClientFallbackFactory;
import com.shop.api.fallback.ProductClientFallbackFactory;
import com.shop.api.fallback.UserClientFallbackFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.annotation.Bean;

/** Feign 降级工厂用自动装配注册（shop-api 不在各服务的组件扫描路径内） */

//来看看什么时候会调用降级：
//  首先业务失败是返回Resulu.fail -- 其实code还是200 ， 就是data是失败的
//  然后如果调用Client抛出异常----如果是被fallback配置的异常，会被fallback吃掉
//  如果是没有被fallback配置的异常，就是抛出给调用client的一方
//  如果是fallback也抛出异常呢？：这个异常会直接冒泡到调用方，不会被 Feign 再兜一次。
//  1. 网络层失败（最常见）
//  连接超时：Feign 发起 TCP 连接，下游没响应
//  读超时：下游收到请求了，但处理太久没返回
//  连接被拒：下游进程挂了 / 端口不通
//  下游返回 5xx：服务端内部错误
//  2. Sentinel 规则触发
//  Sentinel 拦截在 Feign 之前：
//  限流：QPS 超过阈值，Sentinel 直接拒绝，不会真的发请求
//  熔断：下游错误率超过阈值，Sentinel 打开熔断器，后续请求直接走 fallback，不再发请求
//  系统保护：Load 超过阈值，Sentinel 保护性拒绝
//  这正是 Sentinel + Feign 的价值：不仅仅是"调用失败后兜底"，还包括"失败还没发生就提前拒绝"。

@AutoConfiguration
@ConditionalOnClass(FallbackFactory.class)
public class FeignFallbackAutoConfiguration {

    //加入TCC
    @Bean
    @ConditionalOnMissingBean
    public InventoryTccClientFallbackFactory inventoryTccClientFallbackFactory() {
        return new InventoryTccClientFallbackFactory();
    }

    //product产品的失败降级工厂
    @Bean
    @ConditionalOnMissingBean
    public ProductClientFallbackFactory productClientFallbackFactory() {
        return new ProductClientFallbackFactory();
    }

    //库存的失败降级工厂
    @Bean
    @ConditionalOnMissingBean
    public InventoryClientFallbackFactory inventoryClientFallbackFactory() {
        return new InventoryClientFallbackFactory();
    }

    //user信息/地址簿的调用失败降级工厂
    @Bean
    @ConditionalOnMissingBean
    public UserClientFallbackFactory userClientFallbackFactory() {
        return new UserClientFallbackFactory();
    }
}