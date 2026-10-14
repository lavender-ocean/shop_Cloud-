package com.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.inventory.entity.InventoryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

//库存变动流水日志表
@Mapper
public interface InventoryLogMapper {

//     同一个订单号 + 同一个 sku + 同一个操作类型，统计数据库已经存在多少条流水。
//     返回 >0：这条业务已经执行过，**不能重复扣 / 释放库存**，直接走幂等拦截
    int countByBiz(@Param("orderNo") String orderNo
            , @Param("type") Integer type
            , @Param("skuId") Long skuId);
    //插入一条库存流水
    int insert(InventoryLog log);
    //分页查找
    List<InventoryLog> selectPage(IPage<InventoryLog> page, @Param("skuId") Long skuId);
}

