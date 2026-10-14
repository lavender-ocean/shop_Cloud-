package com.shop.inventory.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

//电商经典**三段式库存模型**：
// 总库存total_stock、
// 可用库存available_stock、
// 锁定库存locked_stock，
// 配合乐观锁version版本号。

public interface InventoryMapper {
    //通过skuId来查找sku
    Inventory selectBySkuId(@Param("skuId") Long skuId);

    //分页查找
    List<Inventory> selectPage(IPage<Inventory> page, @Param("skuId") Long skuId);

    //插入一个物品库存 , 商品上架 都要改 这个应该还没人去锁定
    int insert(Inventory inventory);

    //补货，增加可用库存  ---可用库存 和 总库存 都要改 锁定的库存 不用改 ！！！！！！！！！！！！！！！！！
    int increase(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    //用户下单，锁定库存
    int lock(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    //用户支付，那就出库 qty----用户把用一件商品买了几个
    int confirm(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    //取消订单释放库存  超时未支付取消/用户不买了取消
    int release(@Param("skuId") Long skuId, @Param("qty") Integer qty);
}