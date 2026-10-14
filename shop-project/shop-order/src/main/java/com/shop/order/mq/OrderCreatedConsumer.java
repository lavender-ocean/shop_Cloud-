package com.shop.order.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rabbitmq.client.Channel;
import constant.MqConstants;
import idempotent.IdempotentConsumeGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import util.JsonUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
//订单创建的MQ消费者
public class OrderCreatedConsumer {

    private final MqRetrySupport mqRetrySupport;


    /** 演示用开关（Nacos: shop.mq.simulate-fail=true 可制造消费失败） */
    @Value("${shop.mq.simulate-created-fail:false}")
    private boolean simulateFail;
    private final IdempotentConsumeGuard consumeGuard;

    public OrderCreatedConsumer(MqRetrySupport mqRetrySupport,IdempotentConsumeGuard consumeGuard) {
        this.consumeGuard = consumeGuard;
        this.mqRetrySupport = mqRetrySupport;
    }


    //监听order.created.queue队列
    @RabbitListener(queues = MqConstants.ORDER_CREATED_QUEUE)
    public void onCreated(Message message, Channel channel) throws IOException {
        //在这里去使用 mqRetry去处理：ack、重试、死信全部封装
        mqRetrySupport.handle(message, channel,
                // 重试队列路由key
                MqConstants.RK_RETRY_ORDER_CREATED,
                // 死信队列路由key
                MqConstants.ORDER_CREATED_DLQ,
                //用来模拟演示：如果消费失败
                payload -> {
                    if (simulateFail) {
                        throw new IllegalStateException("模拟订单创建事件消费失败");
                    }
                    String orderNo = String.valueOf(
                            JsonUtils.parse(payload,
                                    new TypeReference<Map<String, Object>>() {}).get("orderNo"));
                    // ========== 幂等核心逻辑 ==========
                    // 构造业务key，同一个订单事件唯一： order‑created:O2026

                    //重点：为什么要做 MQ 消费幂等？为什么 “多此一举”？
                    //
                    //
                    //RabbitMQ 给到你的保证是：至少投递一次（At‑least‑once）
                    //不保证只投递一次！会出现重复消息。

                    //比如消费完业务，还没 ack，服务崩溃
                    //流程：

                    //1. MQ 投递订单创建消息给消费者
                    //2. 消费者执行业务：给用户加积分成功（数据库已经提交）
                    //3. 正要执行channel.basicAck，应用直接宕机
                    //4. MQ 没有收到 ack，通道断开，MQ 把这条消息重新放回队列
                    //5. 服务重启之后，MQ**再次把同一条消息投递给消费者

                    //业务已经执行成功，消息又过来一遍。
                    //如果没有幂等防护：积分再加一遍，重复加积分，业务数据错乱



                    String bizKey = "order-created:" + orderNo;
                    //tryAcquire 获取“消费锁”
                    //过期时间1小时：这条事件1小时内不允许重复消费
                    //返回true：拿到锁，第一次消费，执行业务
                    //返回false：已经消费过，直接跳过业务逻辑
                    if (!consumeGuard.tryAcquire(bizKey, Duration.ofHours(1))) {
                        log.info("重复消费，跳过: {}", orderNo);
                        return;
                    }
                    try {
                        log.info("收到订单创建事件: {}", payload);
                        // 后续：加积分、通知...
                    } catch (Exception e) {
                        consumeGuard.release(bizKey);   // 失败释放，允许重试
                        throw e;
                    }
                });
    }


//
//    @RabbitListener(queues = MqConstants.ORDER_CREATED_QUEUE)
//    public void onCreated(Message message, Channel channel) throws IOException {
//        mqRetrySupport.handle(message, channel,
//                MqConstants.RK_RETRY_ORDER_CREATED,
//                MqConstants.ORDER_CREATED_DLQ,
//                payload -> {
//                    if (simulateFail) {
//                        throw new IllegalStateException("模拟订单创建事件消费失败");
//                    }
//                    log.info("收到订单创建事件: {}", payload);
//                    // 后续可在此加积分、通知等
//                });
//    }



}