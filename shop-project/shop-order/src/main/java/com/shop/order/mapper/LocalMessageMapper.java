package com.shop.order.mapper;

import com.shop.order.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

//本地消息表，负责可靠消息的最终一致性
@Mapper
public interface LocalMessageMapper {
// 状态
//   0：待投递
//   1：投递成功
//   2：投递中（被实例抢占，正在发 MQ）
//   3：投递失败，等待重试

//分片取消息 `MOD(id,shardTotal)=shardIndex`**，多实例分片，避免所有实例抢同一批数据，降低数据库锁冲突



    //插入本地消息记录
    //这时候是状态0 ---- 待投递
    int insert(LocalMessage message);



    /** 抢占一批：FOR UPDATE SKIP LOCKED，按分片过滤 */
    List<LocalMessage> selectClaimable(@Param("shardIndex") int shardIndex,
                                       @Param("shardTotal") int shardTotal,
                                       @Param("limit") int limit);

    /** 批量标记为"投递中" */
    //status = 2
    int markClaimed(@Param("ids") List<Long> ids);

    //这个是已经MQ投递成功 status = 1
    int markSent(@Param("id") Long id);

    /** 投递失败：置为 3，并延后重试 */
    //发送MQ异常时调用，不会立刻重试，延后规避短时故障风暴
    //投递失败 status = 3
    int markRetry(@Param("id") Long id, @Param("retryCount") Integer retryCount);

    /** 回收僵死："投递中"超过 N 秒的行（实例崩溃导致），重置为待重试 */
    //status 由 2 变为 3 就是回收僵死消息，处理服务实例崩溃，消息卡在 status=2 投递中

    /**
     * 僵死消息回收
     * 场景：实例抢到消息(status=2投递中)，还没发送完成服务就崩溃；消息永远卡在投递中
     * 判断条件：status=投递中，并且抢占时间已经超过超时秒数
     * 重置状态为待重试，立刻可以被其他实例重新抢占处理
     */
    int releaseStaleClaims(@Param("timeoutSeconds") int timeoutSeconds);
}