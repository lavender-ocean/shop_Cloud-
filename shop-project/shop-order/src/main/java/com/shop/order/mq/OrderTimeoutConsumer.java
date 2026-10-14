package com.shop.order.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rabbitmq.client.Channel;
import com.shop.order.service.OrderService;
import constant.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import util.JsonUtils;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
//订单超时未支付 的 MQ消费者
public class OrderTimeoutConsumer {

    private static final String CLOSE_REASON = "超时未支付，系统自动关闭";

    private final OrderService orderService;
    private final MqRetrySupport mqRetrySupport;

    public OrderTimeoutConsumer(OrderService orderService, MqRetrySupport mqRetrySupport) {
        this.orderService = orderService;
        this.mqRetrySupport = mqRetrySupport;
    }

    //监听order.timeout.queue这个队列
    @RabbitListener(queues = MqConstants.ORDER_TIMEOUT_QUEUE)
    public void onTimeout(Message message, Channel channel) throws IOException {
        //还是一律去调用mqRetry封装的逻辑去处理
        //当然，这次消息的类型就是超时类型了，和正常创建订单类型不一样
        mqRetrySupport.handle(message, channel,
                // 重试队列路由key
                MqConstants.RK_RETRY_ORDER_TIMEOUT,
                //死信队列路由key
                MqConstants.ORDER_TIMEOUT_DLQ,
                payload -> {
                    Map<String, Object> map = JsonUtils.parse(payload, new TypeReference<Map<String, Object>>() {});
                    String orderNo = String.valueOf(map.get("orderNo"));
                    log.info("收到超时关单消息: {}", orderNo);
                    //业务处理逻辑
                    orderService.closeOrder(orderNo, CLOSE_REASON);
                });
    }
}