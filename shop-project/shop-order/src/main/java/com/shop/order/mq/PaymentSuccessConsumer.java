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
//MQ的支付成功的 消费者
public class PaymentSuccessConsumer {

    private final OrderService orderService;
    private final MqRetrySupport mqRetrySupport;

    public PaymentSuccessConsumer(OrderService orderService, MqRetrySupport mqRetrySupport) {
        this.orderService = orderService;
        this.mqRetrySupport = mqRetrySupport;
    }

    @RabbitListener(queues = MqConstants.PAYMENT_SUCCESS_QUEUE)
    public void onPaid(Message message, Channel channel) throws IOException {
        mqRetrySupport.handle(message, channel,
                //重试队列路由key
                MqConstants.RK_RETRY_PAYMENT_SUCCESS,
                //死信队列路由key
                MqConstants.PAYMENT_SUCCESS_DLQ,
                payload -> {
                    Map<String, Object> map = JsonUtils.parse(payload,
                            new TypeReference<Map<String, Object>>() {});
                    String orderNo = String.valueOf(map.get("orderNo"));
                    log.info("收到支付成功事件: {}", orderNo);
                    //处理业务
                    orderService.markPaid(orderNo);
                });
    }
}