package com.shop.order.mapper;

import com.shop.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


//订单明细表
@Mapper
public interface OrderItemMapper {

//批量新增订单明细
//下单时，一个订单有多件商品，批量insert，减少数据库IO
    int insertBatch(@Param("list") List<OrderItem> list);

//根据订单号 查询该订单下全部明细商品

    List<OrderItem> selectByOrderNo(@Param("orderNo") String orderNo);
}