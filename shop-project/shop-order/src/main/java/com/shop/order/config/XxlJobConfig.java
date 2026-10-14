package com.shop.order.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL‑JOB 执行器配置类
 * 作用：配置当前微服务作为XXL‑JOB的执行器，注册连接到xxl‑job‑admin调度中心，接收调度中心下发的定时任务执行
 */
@Configuration
public class XxlJobConfig {

    /** xxl‑job admin调度中心服务地址，多个地址逗号分隔 */
    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    /**
     * 执行器与调度中心通信令牌，做鉴权
     * 配置文件未配置时，使用默认值 default_token
     */
    @Value("${xxl.job.accessToken:default_token}")
    private String accessToken;

    /**
     * 执行器AppName
     * 执行器标识，调度中心根据appname发现、分组管理当前执行器
     */
    @Value("${xxl.job.executor.appname}")
    private String appName;

    /**
     * 执行器内嵌服务端口
     * 执行器会开启此端口，供admin调度中心发起HTTP回调触发任务
     * 默认9999
     */
    @Value("${xxl.job.executor.port:9999}")
    private int port;

    /** 任务执行日志本地存储路径，默认 ./logs/xxl-job */
    @Value("${xxl.job.executor.logpath:./logs/xxl-job}")
    private String logPath;

    /** 执行器本地日志文件保留天数，超过自动清理，默认7天 */
    @Value("${xxl.job.executor.logretentiondays:7}")
    private int logRetentionDays;

    /**
     * 注册XXL‑JOB Spring执行器Bean
     * Spring容器启动时实例化执行器，向admin调度中心注册，完成执行器初始化
     * @return XxlJobSpringExecutor xxl‑job执行器实例
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        // 设置调度中心地址
        executor.setAdminAddresses(adminAddresses);
        // 设置通信鉴权token
        executor.setAccessToken(accessToken);
        // 设置执行器应用标识
        executor.setAppname(appName);
        // 设置执行器回调端口
        executor.setPort(port);
        // 设置本地任务日志路径
        executor.setLogPath(logPath);
        // 设置本地日志保留天数
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
