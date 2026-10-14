package config;

import constant.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@Slf4j
public class ShopRabbitAutoConfiguration {

    private static final int RETRY_TTL_MS = 5000;


    /** publisher-returns 的回执：消息不可路由时告警，避免"静默丢消息" */
    @Bean
    public RabbitTemplateCustomizer shopRabbitReturnsCustomizer() {
        return template -> template.setReturnsCallback(returned ->
                log.error("[MQ] 消息不可路由！exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                        returned.getExchange(), returned.getRoutingKey(),
                        returned.getReplyCode(), returned.getReplyText(),
                        new String(returned.getMessage().getBody(), StandardCharsets.UTF_8)));
    }


    // ==================== 交换机 ====================
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(MqConstants.ORDER_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(MqConstants.PAYMENT_EXCHANGE, true, false);
    }

    //延时队列插件在这里
    // 延迟计时发生在交换机层面，不是队列 TTL
    @Bean
    public CustomExchange delayExchange() {
        Map<String, Object> args = new HashMap<>();
        //发送消息的时候在消息 header 设置`x-delay`，单条消息独立计时
        args.put("x-delayed-type", "direct");
        return new CustomExchange(
                MqConstants.DELAY_EXCHANGE
                , "x-delayed-message"
                , true, false
                , args);
    }

    /** 重试交换机（direct） */
    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(MqConstants.RETRY_EXCHANGE, true, false);
    }

    /** 死信交换机（direct） */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
    }

    // ==================== 业务队列 ====================
    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(MqConstants.ORDER_CREATED_QUEUE, true);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(MqConstants.ORDER_TIMEOUT_QUEUE, true);
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return new Queue(MqConstants.PAYMENT_SUCCESS_QUEUE, true);
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(orderExchange()).with(MqConstants.RK_ORDER_CREATED);
    }

    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue()).to(paymentExchange()).with(MqConstants.RK_PAYMENT_SUCCESS);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(delayExchange()).with(MqConstants.RK_ORDER_DELAY).noargs();
    }

    // ==================== 重试队列（TTL + 死信回原队列）====================
    // x-dead-letter-exchange="" + routing-key=原队列名 → 走默认交换机直接回原队列


//    **TTL = Time To Live，消息存活时间**：消息进入队列后，最多存活多久；到期没被消费，消息变成**死信**。

//            1. **TTL（原生 RabbitMQ 能力，用于重试队列）**：
//            `retryQueue` 使用队列 TTL + DLX，**不需要任何插件**
//            2. **x-delayed-message 延迟交换机插件（用于订单超时关单）**：
//            **不是 TTL**，是插件单独实现的延迟消息，和 TTL 两套东西

    @Bean
    public Queue orderCreatedRetryQueue() {
        return retryQueue(MqConstants.RETRY_ORDER_CREATED_QUEUE, MqConstants.ORDER_CREATED_QUEUE);
    }

    @Bean
    public Queue orderTimeoutRetryQueue() {
        return retryQueue(MqConstants.RETRY_ORDER_TIMEOUT_QUEUE, MqConstants.ORDER_TIMEOUT_QUEUE);
    }

    @Bean
    public Queue paymentSuccessRetryQueue() {
        return retryQueue(MqConstants.RETRY_PAYMENT_SUCCESS_QUEUE, MqConstants.PAYMENT_SUCCESS_QUEUE);
    }

    @Bean
    public Binding orderCreatedRetryBinding() {
        return BindingBuilder.bind(orderCreatedRetryQueue()).to(retryExchange()).with(MqConstants.RK_RETRY_ORDER_CREATED);
    }

    @Bean
    public Binding orderTimeoutRetryBinding() {
        return BindingBuilder.bind(orderTimeoutRetryQueue()).to(retryExchange()).with(MqConstants.RK_RETRY_ORDER_TIMEOUT);
    }

    @Bean
    public Binding paymentSuccessRetryBinding() {
        return BindingBuilder.bind(paymentSuccessRetryQueue()).to(retryExchange()).with(MqConstants.RK_RETRY_PAYMENT_SUCCESS);
    }

    //这个队列里**所有消息，存活时间都是 5s**。消息进队列，5s 内没有消费者消费，就自动变成死信，按照配置的死信路由转发。
    //只用在【重试队列】：orderCreatedRetryQueue、orderTimeoutRetryQueue、paymentSuccessRetryQueue
    private Queue retryQueue(String retryQueueName, String backToQueueName) {
        return QueueBuilder.durable(retryQueueName)
                .ttl(RETRY_TTL_MS) //.ttl(RETRY_TTL_MS) 队列级别TTL，单位ms，5000ms=5秒
                .deadLetterExchange("")
                .deadLetterRoutingKey(backToQueueName)
                .build();
    }

    // ==================== 死信队列（不被消费，供人工/兜底处理）====================
    @Bean
    public Queue orderCreatedDlq() {
        return new Queue(MqConstants.ORDER_CREATED_DLQ, true);
    }

    @Bean
    public Queue orderTimeoutDlq() {
        return new Queue(MqConstants.ORDER_TIMEOUT_DLQ, true);
    }

    @Bean
    public Queue paymentSuccessDlq() {
        return new Queue(MqConstants.PAYMENT_SUCCESS_DLQ, true);
    }

    @Bean
    public Binding orderCreatedDlqBinding() {
        return BindingBuilder.bind(orderCreatedDlq()).to(dlxExchange()).with(MqConstants.ORDER_CREATED_DLQ);
    }

    @Bean
    public Binding orderTimeoutDlqBinding() {
        return BindingBuilder.bind(orderTimeoutDlq()).to(dlxExchange()).with(MqConstants.ORDER_TIMEOUT_DLQ);
    }

    @Bean
    public Binding paymentSuccessDlqBinding() {
        return BindingBuilder.bind(paymentSuccessDlq()).to(dlxExchange()).with(MqConstants.PAYMENT_SUCCESS_DLQ);
    }
}