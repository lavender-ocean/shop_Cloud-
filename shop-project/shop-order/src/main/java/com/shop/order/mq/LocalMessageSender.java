package com.shop.order.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.shop.order.entity.LocalMessage;
import com.shop.order.mapper.LocalMessageMapper;
import constant.MqConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import util.JsonUtils;

import java.util.List;
import java.util.Map;

/**
 * 本地消息投递（两阶段）：
 *   1.由 LocalMessageClaimer 在短事务里原子抢占（SKIP LOCKED）
 *   2.事务外真正发送 MQ，成功 markSent、失败 markRetry
 * 这样"持锁时间"极短，且多实例不会重复投递
 */
@Slf4j
@Component
public class LocalMessageSender {

    //创建订单
    private static final String BIZ_ORDER_CREATED = "ORDER_CREATED";
    //订单超时
    private static final String BIZ_ORDER_TIMEOUT = "ORDER_TIMEOUT";
    private static final int STALE_CLAIM_SECONDS = 60;

    private final LocalMessageMapper localMessageMapper;
    private final RabbitTemplate rabbitTemplate;

    //僵死消息超时阈值：抢占投递中超过60秒，判定实例崩溃
    //releaseStaleClaims 会把status=2投递中且抢占超过60s的消息重置，允许重新抢占重试
    public LocalMessageSender(LocalMessageMapper localMessageMapper, RabbitTemplate rabbitTemplate) {
        this.localMessageMapper = localMessageMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 回收僵死：上次抢占后实例崩了，消息永远停在"投递中" → 重置为待重试 */
    public int releaseStaleClaims() {
        //僵死消息回收
        return localMessageMapper.releaseStaleClaims(STALE_CLAIM_SECONDS);
    }

    /** 投递已抢占的消息，返回成功条数 */
    public int dispatchClaimed(List<LocalMessage> claimed) {
        int sent = 0;
        for (LocalMessage message : claimed) {
            try {
                //
                dispatch(message);
                //mq发送成功，设置对应的状态为 1
                localMessageMapper.markSent(message.getId());
                sent++;
            } catch (Exception e) {
                log.warn("本地消息投递失败 msgId={}, reason={}", message.getMsgId(), e.getMessage());
                //mq投递失败 设置对应的状态为 3
                localMessageMapper.markRetry(message.getId(), message.getRetryCount() + 1);
            }
        }
        return sent;
    }

    //根据消息bizType区分，分发到不同RabbitMQ交换机
    //两种消息：普通订单消息 / 订单超时延时消息
    private void dispatch(LocalMessage message) {
        if (BIZ_ORDER_CREATED.equals(message.getBizType())) {
            //这个是去mq 投递 要创建订单的 消息
            rabbitTemplate.convertAndSend(MqConstants.ORDER_EXCHANGE,
                    MqConstants.RK_ORDER_CREATED, message.getPayload());
        } else if (BIZ_ORDER_TIMEOUT.equals(message.getBizType())) {
            //这个是 mq 去投递 要投递到 延时交换机，并且计算剩余延时，设置x‑delay消息头
            long delay = computeDelay(message.getPayload());
            rabbitTemplate.convertAndSend(MqConstants.DELAY_EXCHANGE,
                    MqConstants.RK_ORDER_DELAY, message.getPayload(), msg -> {
                        msg.getMessageProperties().setHeader("x-delay", Math.max(delay, 0L));
                        return msg;
                    });
        }
    }

    //计算延迟时间
    private long computeDelay(String payload) {
        //传入payload
        Map<String, Object> map = JsonUtils.parse(payload, new TypeReference<Map<String, Object>>() {});
        Object value = map.get("expireMillis");
        long expireMillis = value instanceof Number ? ((Number) value).longValue() : 0L;
        return expireMillis - System.currentTimeMillis();
    }
}