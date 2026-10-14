package com.shop.order.mq;

import com.shop.order.entity.LocalMessage;
import com.shop.order.mapper.LocalMessageMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 原子抢占：短事务内 SELECT ... FOR UPDATE SKIP LOCKED + 标记"投递中"，提交后释放行锁。
 * 单独成 Bean：若写在 LocalMessageSender 内部自调用，@Transactional 不会生效（代理失效）。
 */

//若写在 LocalMessageSender 内部自调用，@Transactional 不会生效（代理失效）
//
//Spring 事务原理：@Transactional 生效需要走 Spring 代理对象。
//
//如果把claim()方法写在LocalMessageSender类里面，
// sender 内部普通方法直接调用this.claim()，是本对象直接调用，不走代理，事务注解完全失效。
//解决方案：把带@Transactional的抢占方法抽取到独立 Bean LocalMessageClaimer。
// 上层通过注入的 Bean 调用，会经过 Spring 代理，事务正常生效。
@Component
public class LocalMessageClaimer {

    private final LocalMessageMapper localMessageMapper;

    public LocalMessageClaimer(LocalMessageMapper localMessageMapper) {


        this.localMessageMapper = localMessageMapper;
    }

    /**
     * 抢占一批待发送消息
     * 整个方法运行在数据库事务中
     * 1. selectClaimable：FOR UPDATE SKIP LOCKED 查询本分片可投递消息，加行排他锁，跳过已经被其他实例锁住的行
     * 2. markClaimed：批量把抢到的消息设置 status=2【投递中】，同时记录claim_time抢占时间
     * 3. 方法结束事务提交，行锁释放；返回抢到的消息集合给上层发送MQ
     *
     * @param shardIndex 当前实例分片下标
     * @param shardTotal 总分片数量
     * @param limit 单次最多抓取条数
     * @return 抢占到的消息列表；没有可发送消息返回空集合
     */
    //在一个数据库事务内完成 查询抢占消息 + 批量标记投递 ，事务提交释放行锁
    @Transactional(rollbackFor = Exception.class)
    //抢占一批消息 ----- 上for update 行级锁 + skip locked
    public List<LocalMessage> claim(int shardIndex, int shardTotal, int limit) {

        List<LocalMessage> list = localMessageMapper.selectClaimable(shardIndex, shardTotal, limit);
        if (!list.isEmpty()) {
            //批量标记为投递中 status = 2
            localMessageMapper.markClaimed(list.stream().map(LocalMessage::getId).toList());
        }
        return list;
    }
}