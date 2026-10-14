package com.shop.order.job;

import com.shop.order.entity.OrderInfo;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.service.OrderService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

//订单超时关闭的兜底扫描任务
@Slf4j
@Component
public class OrderTimeoutJobHandler {

    private static final String CLOSE_REASON = "超时未支付，系统自动关闭(兜底扫描)";
    //也是一次去扫描100个
    private static final int BATCH = 100;

    private final OrderInfoMapper orderInfoMapper;
    private final OrderService orderService;

    public OrderTimeoutJobHandler(OrderInfoMapper orderInfoMapper, OrderService orderService) {
        this.orderInfoMapper = orderInfoMapper;
        this.orderService = orderService;
    }

    @XxlJob("orderTimeoutScanHandler")
    public void scanTimeoutOrders() {
        // 获取分片参数，支持多实例分片广播部署
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        //从db中拿到超时的order
        List<OrderInfo> expired = orderInfoMapper.selectExpired(shardIndex, shardTotal, BATCH);
        int closed = 0;
        for (OrderInfo order : expired) {
            try {


//                 调用关闭订单；内部是数据库CAS幂等更新：status=0(待支付) → status=5(已关闭)
//                 多实例分片场景下，极端情况多个实例查到同一个订单；
//                 CAS保证：就算多个线程进入方法，只会真正关闭一次，不会重复处理

                orderService.closeOrder(order.getOrderNo(), CLOSE_REASON);
                //这个用来打印日志
                closed++;
            } catch (Exception e) {
                log.warn("兜底关单失败 orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
            }
        }
        XxlJobHelper.log("分片 [{}/{}] 超时兜底：扫描 {} 单，关闭 {} 单",
                shardIndex, shardTotal, expired.size(), closed);
    }
}