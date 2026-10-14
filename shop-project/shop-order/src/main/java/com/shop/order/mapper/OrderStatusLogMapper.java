package com.shop.order.mapper;

import com.shop.order.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


//订单状态流水表
//只做 追加写入，不做更新、不做删除
// 记录订单每一次状态流转轨迹，相当于订单的操作流水，用于对账、排查问题、后台查看订单变更历史。
@Mapper
public interface OrderStatusLogMapper {
    //写入流水
    int insert(OrderStatusLog log);
    //根据订单号来查找相关流水
    List<OrderStatusLog> selectByOrderNo(@Param("orderNo") String orderNo);
}