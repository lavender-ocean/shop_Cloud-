package com.shop.order.mq;

import com.rabbitmq.client.Channel;
import constant.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 手动 ack + 有限重试 + 死信 的统一处理。
 * 策略：成功 ack；失败且未超次数 → 显式重投到重试队列并 ack 原消息；
 *      失败且超次数 → 投递到死信队列并 ack 原消息。
 *  失败也必须 ack，否则会无限 requeue。
 */

//这个是给 mq的消费者去调用的
    //消费端收到订单消息，执行业务报错，执行 republish，发送到**重试交换机 / 死信交换机
//生产者侧【下单时写入本地消息表】；消费端 MqRetrySupport 的【republish 重投消息，不会写入本地消息表】
@Slf4j
@Component
public class MqRetrySupport {

    private final RabbitTemplate rabbitTemplate;

    public MqRetrySupport(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    //神了 直接使用 消费者Consumer接口 来搞
    public void handle(Message message, Channel channel,
                       String retryRoutingKey, String dlqRoutingKey,
                       Consumer<String> processor) throws IOException {
        //正常的去拿 deliveryTag
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 执行业务消费逻辑；body转字符串传给业务
            processor.accept(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
            //手动ack 成功
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            //有异常说明是 投递失败 如果 重投次数之内 就重投
            int retry = readRetryCount(message);
            //在允许的重投次数之内
//            if (retry < MqConstants.MAX_RETRY) {
//                log.warn("消费失败，第 {} 次重试，原因: {}", retry + 1, e.getMessage());
//                //重投，并且设置重投次数 +1
//                republish(message, MqConstants.RETRY_EXCHANGE, retryRoutingKey, retry + 1, e);
//            } else {
//                //重投次数耗尽 去死信队列
//                log.error("重试耗尽({}次)，进入死信队列 {}", MqConstants.MAX_RETRY, dlqRoutingKey, e);
//                republish(message, MqConstants.DLX_EXCHANGE, dlqRoutingKey, retry, e);
//            }

            // 永久性错误（如 JSON 反序列化失败）：重试多少次都一样，直接进死信
            if (isPermanent(e) || retry >= MqConstants.MAX_RETRY) {
                log.error("不可重试或重试耗尽({}次)，进入死信队列 {}，原因: {}",
                        retry, dlqRoutingKey, e.getMessage());
                republish(message, MqConstants.DLX_EXCHANGE, dlqRoutingKey, retry, e);
            } else {
                log.warn("消费失败，第 {} 次重试，原因: {}", retry + 1, e.getMessage());
                republish(message, MqConstants.RETRY_EXCHANGE, retryRoutingKey, retry + 1, e);
            }


            // 关键点：原始消息一定要ACK！！！
            // 把原来这条消息确认消费完成，原始队列删除这条消息
            // 新的重试/死信是我们自己republish出来的新消息
            //我靠不写这个会出问题md
            channel.basicAck(deliveryTag, false);
        }
    }


    /** 永久性错误：重试没有意义。沿 cause 链找 Jackson 的解析异常。 */
    private boolean isPermanent(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                return true;
            }
        }
        return false;
    }


    /**
     * 消息重新发布：构造新消息，保留原始body、header，追加重试计数、异常信息
     * @param message 原始消息
     * @param exchange 投递目标交换机（重试交换机 / 死信交换机）
     * @param routingKey 路由key
     * @param retryCount 当前重试次数
     * @param e 异常对象，记录错误原因到header
     */
    /** 保留原 body/contentType，追加重试计数与错误信息后重投 */
    //但是也会带来deliveryTag的改变
    private void republish(Message message, String exchange, String routingKey
            , int retryCount, Exception e) {
        // 复制原有消息属性：contentType、编码、原有全部header
        MessageProperties props = new MessageProperties();

        props.setContentType(message.getMessageProperties().getContentType());

        props.setContentEncoding(message.getMessageProperties().getContentEncoding());
        // 追加自定义header：重试次数、错误信息
        props.getHeaders().putAll(message.getMessageProperties().getHeaders());

        props.setHeader(MqConstants.HEADER_RETRY_COUNT, retryCount);

        props.setHeader(MqConstants.HEADER_ERROR, e.getMessage());
// 设置消息持久化，防止MQ重启丢失消息
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        Message out = new Message(message.getBody(), props);
        rabbitTemplate.send(exchange, routingKey, out);
    }

    /**
     * 从消息header读取已经重试的次数
     * @param message mq消息
     * @return 重试次数，没有header返回0
     */
    private int readRetryCount(Message message) {
        //从msg 的 Header中取出
        Object value = message.getMessageProperties().getHeaders().get(MqConstants.HEADER_RETRY_COUNT);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}