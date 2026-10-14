//package com.shop.order.job;
//
//import com.shop.order.entity.OrderInfo;
//import com.shop.order.mapper.OrderInfoMapper;
//import com.shop.order.service.OrderService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
///**
// * 超时关单兜底：防止延时消息丢失导致订单永远挂着。
// * 后续可迁移到 XXL-JOB。
// */
//@Slf4j
//@Component
//public class OrderTimeoutJob {
//
//    private static final String CLOSE_REASON = "超时未支付，系统自动关闭(兜底扫描)";
//
//    private final OrderInfoMapper orderInfoMapper;
//    private final OrderService orderService;
//
//    public OrderTimeoutJob(OrderInfoMapper orderInfoMapper, OrderService orderService) {
//        this.orderInfoMapper = orderInfoMapper;
//        this.orderService = orderService;
//    }
//
//    @Scheduled(fixedDelay = 60000)
//    public void scan() {
//        List<OrderInfo> expired = orderInfoMapper.selectExpired(100);
//        for (OrderInfo order : expired) {
//            try {
//                orderService.closeOrder(order.getOrderNo(), CLOSE_REASON);
//            } catch (Exception e) {
//                log.warn("兜底关单失败 orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
//            }
//        }
//    }
//}