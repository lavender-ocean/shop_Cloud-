package com.shop.order.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 远程调用专用线程池（隔离于 Tomcat 业务线程）。
 * TtlExecutors 包装 → 提交任务时捕获 UserContext，执行前回放，执行后恢复。
 */
@Configuration
public class AsyncExecutorConfig {

//    1. 线程池隔离：不使用 Tomcat Web 线程做耗时聚合、远程 RPC 调用；防止 Tomcat 工作线程被打满，整个服务接口全部卡死。
//    2. TTL 包装线程池：解决ThreadLocal在线程池复用场景上下文丢失，把登录用户信息UserContext传递到异步子线程。
//    3. 优雅销毁：destroyMethod = "shutdown"，Spring 容器关闭时自动关闭线程池。

    @Bean(name = "orderAggregateExecutor", destroyMethod = "shutdown")
    public ExecutorService orderAggregateExecutor() {
        AtomicInteger seq = new AtomicInteger(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                8 //核心线程
                , 32 //最大线程数
                , 60L //空闲线程的存活时间
                , TimeUnit.SECONDS //时间单位
                , new LinkedBlockingQueue<>(200), //设置阻塞队列的容量
                r -> { //线程工厂
                    Thread t = new Thread(r, "order-agg-" + seq.getAndIncrement());
                    t.setDaemon(true); //设置为守护线程
                    return t;
                },
                // 拒绝策略：回退到调用线程执行，避免直接失败

//              队列满、最大线程耗尽，任务不会丢弃、不会抛 RejectedException；任务退回给提交任务的调用线程同步执行
//
//              优点：不会直接报错，服务不会丢任务；
//              缺点：调用者（Tomcat 线程）会被阻塞，如果持续高并发，压力会反向打回 Tomcat，要监控队列长度。

//              对比其他拒绝策略：

//              AbortPolicy：抛异常（默认），业务直接报错
//              DiscardPolicy：直接丢弃任务，无声丢失数据
//              DiscardOldestPolicy：丢弃队列最老任务


                //TtlExecutors 包装线程池，底层做了什么
                //
                //重写线程池的submit/execute，提交任务时，自动把 Runnable 包装为 TtlRunnable。
                //任务执行前后做上下文快照捕获、恢复。


                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutorService(pool);
    }
}