package com.shop.order.job;

import com.shop.order.entity.LocalMessage;
import com.shop.order.mq.LocalMessageClaimer;
import com.shop.order.mq.LocalMessageSender;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// 为什么用 XXL‑Job 分片？
//
//        如果单机：shardTotal=1，shardIndex=0，单机处理全部消息。
//        如果集群部署 3 台服务实例：shardTotal=3；三台分别 shardIndex=0、1、2。
//每台机器只处理一部分消息，mod(id,3)，压力分散，提升补偿任务吞吐量。
//
//
//        配置 XXL‑Job 后台任务：
//        路由策略选择 分片广播；Cron 表达式例如：0/10 * * * * ? 每 10 秒执行一次补偿任务。
@Slf4j
@Component
public class LocalMessageJobHandler {

    //一次扫描100个
    private static final int BATCH = 100;

    private final LocalMessageClaimer localMessageClaimer;
    private final LocalMessageSender localMessageSender;

    public LocalMessageJobHandler(LocalMessageClaimer localMessageClaimer,
                                  LocalMessageSender localMessageSender) {
        this.localMessageClaimer = localMessageClaimer;
        this.localMessageSender = localMessageSender;
    }

    //定时任务的设置
    @XxlJob("localMessageCompensateHandler")
    public void compensateLocalMessage() {
        //分片
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        // 1. 回收僵死
//        什么是僵死消息？
//        多实例部署场景：
//        某个服务实例抢占了这条消息（claim，把消息标记为处理中），还没发送完成，实例直接宕机。
//        这条消息就永远停留在 `processing` 处理中状态，不会再被别的实例拾取，消息卡死。
        int released = localMessageSender.releaseStaleClaims();
        // 2. 原子抢占    防止多个实例抢同一条消息，避免重复投递；
        List<LocalMessage> claimed = localMessageClaimer.claim(shardIndex, shardTotal, BATCH);
        // 3. 事务外投递
        int sent = localMessageSender.dispatchClaimed(claimed);

        XxlJobHelper.log("分片 [{}/{}] 消息补偿：回收僵死 {} 条，抢占 {} 条，投递 {} 条",
                shardIndex, shardTotal, released, claimed.size(), sent);
    }
}