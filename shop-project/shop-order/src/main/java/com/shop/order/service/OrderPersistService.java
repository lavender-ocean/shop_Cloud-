package com.shop.order.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shop.order.entity.LocalMessage;
import com.shop.order.entity.OrderInfo;
import com.shop.order.entity.OrderItem;
import com.shop.order.entity.OrderStatusLog;
import com.shop.order.mapper.LocalMessageMapper;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderStatusLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import util.JsonUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class OrderPersistService {
    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final LocalMessageMapper localMessageMapper;

    //构造注入mapper
    public OrderPersistService(OrderInfoMapper orderInfoMapper,
                               OrderItemMapper orderItemMapper,
                               OrderStatusLogMapper orderStatusLogMapper,
                               LocalMessageMapper localMessageMapper) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
        this.localMessageMapper = localMessageMapper;
    }

    // ==================== 落库 ====================
    //本地数据库事务，和订单业务同生共死！！！
    @Transactional(rollbackFor = Exception.class)
    public void saveOrderAndItems(OrderInfo order, List<OrderItem> items) {
        //把创建的订单入DB
        orderInfoMapper.insert(order);
        //包括订单明细表
        orderItemMapper.insertBatch(items);

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setId(IdWorker.getId());
        statusLog.setOrderNo(order.getOrderNo());
        statusLog.setFromStatus(null);
        statusLog.setToStatus(0);
        statusLog.setOperatorType(1);
        statusLog.setOperatorId(order.getUserId());
        statusLog.setRemark("创建订单");
        //包括订单状态流水表
        orderStatusLogMapper.insert(statusLog);

        LocalMessage createdMessage = new LocalMessage();
        createdMessage.setId(IdWorker.getId());
        createdMessage.setMsgId(UUID.randomUUID().toString().replace("-", ""));
        createdMessage.setBizType("ORDER_CREATED");
        createdMessage.setBizNo(order.getOrderNo());
        createdMessage.setPayload(JsonUtils.toJson(order));
        createdMessage.setStatus(0);
        createdMessage.setRetryCount(0);
        createdMessage.setNextRetryTime(LocalDateTime.now());
        //包括本地消息表的记录，这个是 订单创建
        localMessageMapper.insert(createdMessage);

        LocalMessage timeoutMessage = new LocalMessage();
        timeoutMessage.setId(IdWorker.getId());
        timeoutMessage.setMsgId(UUID.randomUUID().toString().replace("-", ""));
        timeoutMessage.setBizType("ORDER_TIMEOUT");
        timeoutMessage.setBizNo(order.getOrderNo());
        //计算好超时时间
        //后面发送 MQ 的代码读到 expireMillis，计算延时时间，发送延时消息到 MQ，不是马上发
        timeoutMessage.setPayload(JsonUtils.toJson(Map.of(
                "orderNo", order.getOrderNo(),
                "expireMillis",
                order.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())));
        timeoutMessage.setStatus(0);
        timeoutMessage.setRetryCount(0);
        timeoutMessage.setNextRetryTime(LocalDateTime.now());
        //这个是 订单超时
        localMessageMapper.insert(timeoutMessage);
    }

}
