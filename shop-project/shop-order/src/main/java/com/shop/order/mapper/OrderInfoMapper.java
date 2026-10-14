package com.shop.order.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shop.order.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

//订单主表
@Mapper
public interface OrderInfoMapper {

    //0：待支付（新建订单，未付款）
    //1：已支付
    //2：已发货
    //3：完成
    //4：用户手动取消
    //5：系统超时关闭（过期未支付）

    //新增订单记录
    int insert(OrderInfo order);

    //根据订单号 查找订单详细
    OrderInfo selectByOrderNo(@Param("orderNo") String orderNo);

    //分页查找
    List<OrderInfo> selectPage(IPage<OrderInfo> page,
                               @Param("userId") Long userId,
                               @Param("status") Integer status);

    /** 关闭未支付订单（CAS：仅当仍为待支付） */ //就是 stats = 0 然后 设置 为 5
    int closeIfPending(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /** 扫描已过期未支付订单（兜底） */  //条件 ： expire_time < NOW()
    List<OrderInfo> selectExpired(@Param("shardIndex") int shardIndex,
                                  @Param("shardTotal") int shardTotal,
                                  @Param("limit") int limit);

    /** 支付成功 CAS：仅当仍为待支付 */ //status = 0才做
    int markPaid(@Param("orderNo") String orderNo);

    /** 用户取消 CAS：仅当仍为待支付 0 → 4 */ //改变 status 的 状态值
    int cancelIfPending(@Param("orderNo") String orderNo);

    /** 管理员发货 CAS：仅当已支付 1 → 2 */ //改变 status 的 状态值
    int markShipped(@Param("orderNo") String orderNo);
}