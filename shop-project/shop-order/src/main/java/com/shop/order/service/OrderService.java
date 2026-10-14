package com.shop.order.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.api.dto.*;
import com.shop.api.feign.InventoryClient;
import com.shop.api.feign.InventoryTccClient;
import com.shop.api.feign.ProductClient;
import com.shop.api.feign.UserClient;
import com.shop.order.dto.CreateOrderDTO;
import com.shop.order.dto.OrderItemDTO;
import com.shop.order.dto.OrderPreparation;
import com.shop.order.entity.LocalMessage;
import com.shop.order.entity.OrderInfo;
import com.shop.order.entity.OrderItem;
import com.shop.order.entity.OrderStatusLog;
import com.shop.order.mapper.LocalMessageMapper;
import com.shop.order.mapper.OrderInfoMapper;
import com.shop.order.mapper.OrderItemMapper;
import com.shop.order.mapper.OrderStatusLogMapper;
import com.shop.order.vo.OrderDetailVO;
import constant.RedisKeyConstants;
import context.UserContext;
import exception.BizAssert;
import exception.BizException;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import result.PageResult;
import result.Result;
import result.ResultCode;
import util.JsonUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final LocalMessageMapper localMessageMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final UserClient userClient;
    private final StringRedisTemplate redisTemplate;

    private final InventoryTccClient inventoryTccClient;

    @Value("${shop.order.tx-mode:at}")
    private String txMode;



    /** 当前事务模式（供前端/联调查询） */
    public String currentTxMode() {
        return txMode;      // 就是现在那个 @Value 字段
    }

    /** 入口与开关不一致 → 直接拒绝，避免半路路由错乱 */
    public void assertTxMode(String expected) {
        BizAssert.isTrue(expected.equalsIgnoreCase(txMode),
                ResultCode.PARAM_ERROR,
                "事务模式开关为 " + txMode + "，该接口要求 " + expected);
    }

    // at | tcc
    private boolean isTcc() {
        return "tcc".equalsIgnoreCase(txMode);
    }

    private void lockStock(String orderNo, List<LockItemDTO> items) {
        Result<Void> r = isTcc()
                ? inventoryTccClient.tryLock(new StockTccReq(orderNo, items))
                : inventoryClient.lock(new StockLockReq(orderNo, items));
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMessage());
        }
    }

    private void confirmStock(String orderNo, List<LockItemDTO> items) {
        Result<Void> r = isTcc()
                ? inventoryTccClient.confirm(new StockTccReq(orderNo, items))
                : inventoryClient.confirm(new StockLockReq(orderNo, items));
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMessage());
        }
    }

    private void releaseStock(String orderNo, List<LockItemDTO> items) {
        Result<Void> r = isTcc()
                ? inventoryTccClient.cancel(new StockTccReq(orderNo, items))
                : inventoryClient.release(new StockLockReq(orderNo, items));
        if (r.getCode() != 0) {
            throw new BizException(r.getCode(), r.getMessage());
        }
    }

    private void releaseStock(String orderNo) {
        List<OrderItem> items = orderItemMapper.selectByOrderNo(orderNo);
        List<LockItemDTO> lockItems = items.stream()
                .map(i -> new LockItemDTO(i.getSkuId(), i.getQuantity()))
                .toList();
        releaseStock(orderNo, lockItems);
    }

    // ==================== 共用准备 ====================

    //生成前端防重复提交的幂等 token
    //把幂等token先放进redis中
    //网关层用的幂等令牌   返回给前端。
    //前端下单前先调用这个接口拿到 token，请求头带上这个idempotent‑token传给 gateway 网关
    //网关校验并消费 token，防止用户重复点击提交。
    //TTL5 分钟：防止 Redis 垃圾数据，超过时间令牌自动失效
    //注意：这里只是生成 token，真正消费删除 token 发生在 Gateway 全局过滤器，不在 OrderService 内部。
    public String createIdempotentToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                RedisKeyConstants.IDEMPOTENT + token, "1", Duration.ofMinutes(5));
        return token;
    }

    /** 取地址 + 取价 + 组装（两种事务模式共用；只读，无副作用） */
    //这个是只读
    public OrderPreparation prepare(Long userId, CreateOrderDTO dto) {
        //取地址
        Result<UserAddressDTO> addrResult = userClient.getAddress(dto.getAddressId());
        //校验地址是否存在
        BizAssert.isTrue(addrResult.getCode() == 0 && addrResult.getData() != null,
                ResultCode.ADDRESS_NOT_FOUND);
        UserAddressDTO address = addrResult.getData();
        //校验是否越权
        BizAssert.isTrue(userId.equals(address.getUserId()), ResultCode.FORBIDDEN);

        //雪花算法生成orderNo订单号 --- 只用一次不然会高并发出问题！！！！！！！
        long orderId = IdWorker.getId();
        String orderNo = "ORD" + orderId;

        List<OrderItem> items = new ArrayList<>();
        List<LockItemDTO> lockItems = new ArrayList<>();
        long total = 0L;
        for (OrderItemDTO it : dto.getItems()) {
            //去拿sku的信息
            Result<SkuInfoDTO> r = productClient.getSku(it.getSkuId());
            //判断这个sku是否存在
            BizAssert.isTrue(r.getCode() == 0 && r.getData() != null, ResultCode.SKU_NOT_FOUND);
            SkuInfoDTO sku = r.getData();
            //判断这个sku能不能卖
            BizAssert.isTrue(
                    sku.getStatus() != null && sku.getStatus() == 1, ResultCode.SKU_OFF_SHELF);

            //计算金额
            long amount = sku.getPrice() * it.getQuantity();
            total += amount;

            OrderItem oi = new OrderItem();
            oi.setId(IdWorker.getId());
            oi.setOrderId(orderId);
            oi.setOrderNo(orderNo);
            oi.setSpuId(sku.getSpuId());
            oi.setSkuId(sku.getId());
            oi.setSpuName(sku.getSpuName());
            oi.setSkuName(sku.getName());
            oi.setSkuImage(sku.getImage());
            oi.setSpecJson(sku.getSpecJson());
            oi.setPrice(sku.getPrice());
            oi.setQuantity(it.getQuantity());
            oi.setTotalAmount(amount);
            items.add(oi);

            lockItems.add(new LockItemDTO(it.getSkuId(), it.getQuantity()));
        }

        OrderInfo order = new OrderInfo();
        order.setId(orderId);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setDiscountAmount(0L);
        order.setFreightAmount(0L);
        order.setPayAmount(total);
        order.setStatus(0);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverDistrict(address.getDistrict());
        order.setReceiverDetail(address.getDetailAddress());
        order.setRemark(dto.getRemark());
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        OrderPreparation preparation = new OrderPreparation();
        preparation.setOrderNo(orderNo);
        preparation.setOrder(order);
        preparation.setItems(items);
        preparation.setLockItems(lockItems);
        return preparation;
    }






    private final OrderPersistService orderPersistService;
    // ==================== AT / TCC 模式下单 ====================

    @GlobalTransactional(name = "shop-create-order", rollbackFor = Exception.class)
    public String createOrder(CreateOrderDTO dto) {
        //从ThreadLocal中取出userId
        Long userId = UserContext.getUserId();
        //校验
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
        //取地址+取价+组装
        OrderPreparation prep = prepare(userId, dto);
        //库存预占用：路由AT/TCC
        lockStock(prep.getOrderNo(), prep.getLockItems());

        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //this调用使得事务失效！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！
        orderPersistService.saveOrderAndItems(prep.getOrder(), prep.getItems());
        return prep.getOrderNo();
    }

//    // ==================== 落库 ====================
//    //本地数据库事务，和订单业务同生共死！！！
//    @Transactional(rollbackFor = Exception.class)
//    public void saveOrderAndItems(OrderInfo order, List<OrderItem> items) {
//        //把创建的订单入DB
//        orderInfoMapper.insert(order);
//        //包括订单明细表
//        orderItemMapper.insertBatch(items);
//
//        OrderStatusLog statusLog = new OrderStatusLog();
//        statusLog.setId(IdWorker.getId());
//        statusLog.setOrderNo(order.getOrderNo());
//        statusLog.setFromStatus(null);
//        statusLog.setToStatus(0);
//        statusLog.setOperatorType(1);
//        statusLog.setOperatorId(order.getUserId());
//        statusLog.setRemark("创建订单");
//        //包括订单状态流水表
//        orderStatusLogMapper.insert(statusLog);
//
//        LocalMessage createdMessage = new LocalMessage();
//        createdMessage.setId(IdWorker.getId());
//        createdMessage.setMsgId(UUID.randomUUID().toString().replace("-", ""));
//        createdMessage.setBizType("ORDER_CREATED");
//        createdMessage.setBizNo(order.getOrderNo());
//        createdMessage.setPayload(JsonUtils.toJson(order));
//        createdMessage.setStatus(0);
//        createdMessage.setRetryCount(0);
//        createdMessage.setNextRetryTime(LocalDateTime.now());
//        //包括本地消息表的记录，这个是 订单创建
//        localMessageMapper.insert(createdMessage);
//
//        LocalMessage timeoutMessage = new LocalMessage();
//        timeoutMessage.setId(IdWorker.getId());
//        timeoutMessage.setMsgId(UUID.randomUUID().toString().replace("-", ""));
//        timeoutMessage.setBizType("ORDER_TIMEOUT");
//        timeoutMessage.setBizNo(order.getOrderNo());
//        //计算好超时时间
//        //后面发送 MQ 的代码读到 expireMillis，计算延时时间，发送延时消息到 MQ，不是马上发
//        timeoutMessage.setPayload(JsonUtils.toJson(Map.of(
//                "orderNo", order.getOrderNo(),
//                "expireMillis",
//                order.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())));
//        timeoutMessage.setStatus(0);
//        timeoutMessage.setRetryCount(0);
//        timeoutMessage.setNextRetryTime(LocalDateTime.now());
//        //这个是 订单超时
//        localMessageMapper.insert(timeoutMessage);
//    }

    // ==================== 查询 ====================
    //分页查询
    public PageResult<OrderInfo> page(Integer status, long pageNum, long pageSize) {
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
        Page<OrderInfo> page = new Page<>(pageNum, pageSize);
        List<OrderInfo> records = orderInfoMapper.selectPage(page, userId, status);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    //查询订单信息
    //普通用户只能查到自己的订单
    public OrderDetailVO detail(String orderNo) {
        Long userId = UserContext.getUserId();
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
        BizAssert.isTrue(
                userId != null && userId.equals(order.getUserId()), ResultCode.FORBIDDEN);
        return buildDetail(order);
    }

    //管理员可以去查到任何一个订单
    public OrderDetailVO adminDetail(String orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
        return buildDetail(order);
    }

    public PageResult<OrderInfo> adminPage(Integer status, long pageNum, long pageSize) {
        Page<OrderInfo> page = new Page<>(pageNum, pageSize);
        List<OrderInfo> records = orderInfoMapper.selectPage(page, null, status);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    //订单摘要
    public OrderSummaryDTO summary(String orderNo) {
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
        OrderSummaryDTO dto = new OrderSummaryDTO();
        BeanUtils.copyProperties(order, dto);
        return dto;
    }

    //orderInfo 转 OrderDetailVO
    private OrderDetailVO buildDetail(OrderInfo order) {
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(order);
        vo.setItems(orderItemMapper.selectByOrderNo(order.getOrderNo()));
        return vo;
    }

    // ==================== 状态流转（两种模式共用） ====================

    @GlobalTransactional(name = "shop-cancel-order", rollbackFor = Exception.class)
    //用户取消订单
    public void cancel(String orderNo) {
        //用户id
        Long userId = UserContext.getUserId();
        //要取消的订单号
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        //开始做校验
        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
        BizAssert.isTrue(
                userId != null && userId.equals(order.getUserId()), ResultCode.FORBIDDEN);
        BizAssert.isTrue(
                order.getStatus() != null && order.getStatus() == 0, ResultCode.ORDER_STATUS_ERROR);

        //在待支付的状态下才可以取消
        int rows = orderInfoMapper.cancelIfPending(orderNo);
        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ERROR);
        }
        //释放预占用的库存
        releaseStock(orderNo);
        //写入状态流水表中
        logStatus(orderNo, 0, 4, 1, "用户取消订单");
    }

    @GlobalTransactional(name = "shop-mark-paid", rollbackFor = Exception.class)
    //支付成功的回调接口
    public void markPaid(String orderNo) {

        //先找到这个订单
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        //可以加个校验 确认是为支付了的订单
        //不能是已经支付的订单，再支付一遍
        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
            return;
        }
        //在这里 写入DB这个订单已经是支付的状态 设置status = 1
        int rows = orderInfoMapper.markPaid(orderNo);
        if (rows == 0) {
            return;
        }
        //扣除库存！！！！！！！！！！ 不能支付完就不管了，库存也要扣减
        List<OrderItem> items = orderItemMapper.selectByOrderNo(orderNo);
        List<LockItemDTO> lockItems = items.stream()
                .map(i -> new LockItemDTO(i.getSkuId(), i.getQuantity()))
                .toList();

        confirmStock(orderNo, lockItems);

        //写入流水表中
        logStatus(orderNo, 0, 1, 2, "支付成功");
    }

    //超时关闭未支付的订单
    @GlobalTransactional(name = "shop-close-order", rollbackFor = Exception.class)
    public void closeOrder(String orderNo, String reason) {
        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
        //校验一定是 未支付 的 订单 不能 把支付了的也搞掉
        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
            return;
        }
        //关闭订单 ---  设置status 为  5
        int rows = orderInfoMapper.closeIfPending(orderNo, reason);
        if (rows == 0) {
            return;
        }
        //释放被这个订单所占用的库存
        releaseStock(orderNo);
        //写入流水表
        logStatus(orderNo, 0, 5, 2, reason);
    }

    //管理员去发货
    public void ship(String orderNo) {
        BizAssert.notNull(orderInfoMapper.selectByOrderNo(orderNo), ResultCode.ORDER_NOT_FOUND);
        //主要就是去改DB的状态 status = 2
        int rows = orderInfoMapper.markShipped(orderNo);
        //同时也要去校验 ---- 只有已经支付了的 订单 才可以去发货 ！！！！
        BizAssert.isTrue(rows == 1, ResultCode.ORDER_STATUS_ERROR);
        logStatus(orderNo, 1, 2, 3, "管理员发货");
    }

    //把状态写入流水表中
    public void logStatus(String orderNo, Integer fromStatus, Integer toStatus,
                          Integer operatorType, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setId(IdWorker.getId());
        statusLog.setOrderNo(orderNo);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setOperatorType(operatorType);
        statusLog.setOperatorId(0L);
        statusLog.setRemark(remark);
        orderStatusLogMapper.insert(statusLog);
    }
}







































//package com.shop.order.service;
//
//import com.baomidou.mybatisplus.core.toolkit.IdWorker;
//import com.shop.api.dto.LockItemDTO;
//import com.shop.api.dto.OrderSummaryDTO;
//import com.shop.api.dto.SkuInfoDTO;
//import com.shop.api.dto.StockLockReq;
//import com.shop.api.feign.InventoryClient;
//import com.shop.api.feign.ProductClient;
//import com.shop.order.dto.CreateOrderDTO;
//import com.shop.order.dto.OrderItemDTO;
//import com.shop.order.entity.LocalMessage;
//import com.shop.order.entity.OrderInfo;
//import com.shop.order.entity.OrderItem;
//import com.shop.order.entity.OrderStatusLog;
//import com.shop.order.mapper.LocalMessageMapper;
//import com.shop.order.mapper.OrderInfoMapper;
//import com.shop.order.mapper.OrderItemMapper;
//import com.shop.order.mapper.OrderStatusLogMapper;
//import constant.RedisKeyConstants;
//import context.UserContext;
//import exception.BizAssert;
//import exception.BizException;
//import io.seata.spring.annotation.GlobalTransactional;
//import org.springframework.beans.BeanUtils;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import result.Result;
//import result.ResultCode;
//import util.JsonUtils;
//
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.shop.order.vo.OrderDetailVO;
//import result.PageResult;
//
//@Service
//public class OrderService {
//
//    private final OrderInfoMapper orderInfoMapper;
//    private final OrderItemMapper orderItemMapper;
//    private final OrderStatusLogMapper orderStatusLogMapper;
//    private final LocalMessageMapper localMessageMapper;
//    private final ProductClient productClient;
//    private final InventoryClient inventoryClient;
//    private final StringRedisTemplate redisTemplate;
//
//    public OrderService(OrderInfoMapper orderInfoMapper,
//                        OrderItemMapper orderItemMapper,
//                        OrderStatusLogMapper orderStatusLogMapper,
//                        LocalMessageMapper localMessageMapper,
//                        ProductClient productClient,
//                        InventoryClient inventoryClient,
//                        StringRedisTemplate redisTemplate) {
//        this.orderInfoMapper = orderInfoMapper;
//        this.orderItemMapper = orderItemMapper;
//        this.orderStatusLogMapper = orderStatusLogMapper;
//        this.localMessageMapper = localMessageMapper;
//        this.productClient = productClient;
//        this.inventoryClient = inventoryClient;
//        this.redisTemplate = redisTemplate;
//    }
//
//    /** 下单前先拿幂等 token */
//    public String createIdempotentToken() {
//        String token = UUID.randomUUID().toString().replace("-", "");
//        redisTemplate.opsForValue().set(
//                RedisKeyConstants.IDEMPOTENT + token, "1", Duration.ofMinutes(5));
//        return token;
//    }
//
//    @GlobalTransactional(name = "shop-create-order", rollbackFor = Exception.class)
//    public String createOrder(CreateOrderDTO dto) {
//        Long userId = UserContext.getUserId();
//        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
//
////        // 1. 幂等：原子消费 token（Lua 语义的 DEL 返回是否删除成功）
////        Boolean consumed = redisTemplate.delete(RedisKeyConstants.IDEMPOTENT + dto.getIdempotentToken());
////        BizAssert.isTrue(Boolean.TRUE.equals(consumed), ResultCode.ORDER_DUPLICATE_SUBMIT);
//
//        String orderNo = "ORD" + IdWorker.getId();
//        long orderId = IdWorker.getId();
//
//        // 2. 逐个 SKU：取价 + 组装明细快照 + 收集锁定项
//        List<OrderItem> items = new ArrayList<>();
//        List<LockItemDTO> lockItems = new ArrayList<>();
//        long total = 0L;
//        for (OrderItemDTO it : dto.getItems()) {
//            Result<SkuInfoDTO> r = productClient.getSku(it.getSkuId());
//            BizAssert.isTrue(
//                    r.getCode() == 0 && r.getData() != null, ResultCode.SKU_NOT_FOUND);
//            SkuInfoDTO sku = r.getData();
//            BizAssert.isTrue(
//                    sku.getStatus() != null && sku.getStatus() == 1, ResultCode.SKU_OFF_SHELF);
//
//            long amount = sku.getPrice() * it.getQuantity();
//            total += amount;
//
//            OrderItem oi = new OrderItem();
//            oi.setId(IdWorker.getId());
//            oi.setOrderId(orderId);
//            oi.setOrderNo(orderNo);
//            oi.setSpuId(sku.getSpuId());
//            oi.setSkuId(sku.getId());
//            oi.setSpuName(sku.getSpuName());
//            oi.setSkuName(sku.getName());
//            oi.setSkuImage(sku.getImage());
//            oi.setSpecJson(sku.getSpecJson());
//            oi.setPrice(sku.getPrice());
//            oi.setQuantity(it.getQuantity());
//            oi.setTotalAmount(amount);
//            items.add(oi);
//
//            lockItems.add(new LockItemDTO(it.getSkuId(), it.getQuantity()));
//        }
//
//        // 3. 锁库存（Seata 分支；失败 → 全局回滚）
//        Result<Void> lockResult = inventoryClient.lock(new StockLockReq(orderNo, lockItems));
//        if (lockResult.getCode() != 0) {
//            throw new BizException(lockResult.getCode(), lockResult.getMessage());
//        }
//
//        // 4. 本地事务：建单 + 明细 + 状态流水 + 本地消息
//        OrderInfo order = new OrderInfo();
//        order.setId(orderId);
//        order.setOrderNo(orderNo);
//        order.setUserId(userId);
//        order.setTotalAmount(total);
//        order.setDiscountAmount(0L);
//        order.setFreightAmount(0L);
//        order.setPayAmount(total);
//        order.setStatus(0);
////        order.setReceiverName(dto.getReceiverName());
////        order.setReceiverPhone(dto.getReceiverPhone());
////        order.setReceiverProvince(dto.getReceiverProvince());
////        order.setReceiverCity(dto.getReceiverCity());
////        order.setReceiverDistrict(dto.getReceiverDistrict());
////        order.setReceiverDetail(dto.getReceiverDetail());
//        order.setRemark(dto.getRemark());
//        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
//
//        saveOrderAndItems(order, items);
//        return orderNo;
//    }
//
//    /** 本地事务：与订单同生共死 */
//    @Transactional(rollbackFor = Exception.class)
//    public void saveOrderAndItems(OrderInfo order, List<OrderItem> items) {
//        orderInfoMapper.insert(order);
//        orderItemMapper.insertBatch(items);
//
//        OrderStatusLog statusLog = new OrderStatusLog();
//        statusLog.setId(IdWorker.getId());
//        statusLog.setOrderNo(order.getOrderNo());
//        statusLog.setFromStatus(null);
//        statusLog.setToStatus(0);
//        statusLog.setOperatorType(1);
//        statusLog.setOperatorId(order.getUserId());
//        statusLog.setRemark("创建订单");
//        orderStatusLogMapper.insert(statusLog);
//
//        // 本地消息表：与业务同事务写入，18b 由定时任务投递到 MQ
//        LocalMessage message = new LocalMessage();
//        message.setId(IdWorker.getId());
//        message.setMsgId(UUID.randomUUID().toString().replace("-", ""));
//        message.setBizType("ORDER_CREATED");
//        message.setBizNo(order.getOrderNo());
//        message.setPayload(JsonUtils.toJson(order));
//        message.setStatus(0);
//        message.setRetryCount(0);
//        message.setNextRetryTime(LocalDateTime.now());
//        localMessageMapper.insert(message);
//
//
//        // 超时关单消息：payload 存 orderNo + 到期毫秒，由发送器计算 x-delay
//        LocalMessage timeoutMessage = new LocalMessage();
//        timeoutMessage.setId(IdWorker.getId());
//        timeoutMessage.setMsgId(UUID.randomUUID().toString().replace("-", ""));
//        timeoutMessage.setBizType("ORDER_TIMEOUT");
//        timeoutMessage.setBizNo(order.getOrderNo());
//        timeoutMessage.setPayload(JsonUtils.toJson(Map.of(
//                "orderNo", order.getOrderNo(),
//                "expireMillis", order.getExpireTime()
//                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())));
//        timeoutMessage.setStatus(0);
//        timeoutMessage.setRetryCount(0);
//        timeoutMessage.setNextRetryTime(LocalDateTime.now());
//        localMessageMapper.insert(timeoutMessage);
//    }
//
//
//
//
//
//
//    /** 关闭订单：关单 + 释放库存（Seata 全局事务；幂等） */
//    @GlobalTransactional(name = "shop-close-order", rollbackFor = Exception.class)
//    public void closeOrder(String orderNo, String reason) {
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
//            return;   // 不存在或已支付/已关闭 → 幂等忽略
//        }
//        int rows = orderInfoMapper.closeIfPending(orderNo, reason);
//        if (rows == 0) {
//            return;   // CAS 失败：并发下已被支付/取消 → 幂等忽略
//        }
//
//        // 释放库存（库存侧 release 自身幂等，靠 inventory_log 唯一键）
//        List<OrderItem> items = orderItemMapper.selectByOrderNo(orderNo);
//        List<LockItemDTO> lockItems = items.stream()
//                .map(i -> new LockItemDTO(i.getSkuId(), i.getQuantity()))
//                .toList();
//        Result<Void> releaseResult = inventoryClient.release(new StockLockReq(orderNo, lockItems));
//        if (releaseResult.getCode() != 0) {
//            throw new BizException(releaseResult.getCode(), releaseResult.getMessage());
//        }
//
//        logStatus(orderNo, 0, 5, 2, reason);
//    }
//
//    /** 状态流转留痕 */
//    public void logStatus(String orderNo, Integer fromStatus, Integer toStatus,
//                          Integer operatorType, String remark) {
//        OrderStatusLog statusLog = new OrderStatusLog();
//        statusLog.setId(IdWorker.getId());
//        statusLog.setOrderNo(orderNo);
//        statusLog.setFromStatus(fromStatus);
//        statusLog.setToStatus(toStatus);
//        statusLog.setOperatorType(operatorType);
//        statusLog.setOperatorId(0L);
//        statusLog.setRemark(remark);
//        orderStatusLogMapper.insert(statusLog);
//    }
//
//
//    public OrderSummaryDTO summary(String orderNo) {
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
//        OrderSummaryDTO dto = new OrderSummaryDTO();
//        BeanUtils.copyProperties(order, dto);
//        return dto;
//    }
//
//    /** 支付成功：订单 0→1 + 库存确认扣减（Seata；幂等） */
//    @GlobalTransactional(name = "shop-mark-paid", rollbackFor = Exception.class)
//    public void markPaid(String orderNo) {
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        if (order == null || order.getStatus() == null || order.getStatus() != 0) {
//            return;   // 幂等：已支付/已关闭
//        }
//        int rows = orderInfoMapper.markPaid(orderNo);
//        if (rows == 0) {
//            return;   // CAS 失败 → 并发下已处理
//        }
//        List<OrderItem> items = orderItemMapper.selectByOrderNo(orderNo);
//        List<LockItemDTO> lockItems = items.stream()
//                .map(i -> new LockItemDTO(i.getSkuId(), i.getQuantity()))
//                .toList();
//        Result<Void> confirmResult = inventoryClient.confirm(new StockLockReq(orderNo, lockItems));
//        if (confirmResult.getCode() != 0) {
//            throw new BizException(confirmResult.getCode(), confirmResult.getMessage());
//        }
//        logStatus(orderNo, 0, 1, 2, "支付成功");
//    }
//
//
//
//
//// ==================== 追加在 OrderService 类里 ====================
//
//    /**
//     * 我的订单（分页）。
//     * 按 user_id 过滤 —— 只能看自己的订单。
//     */
//    public PageResult<OrderInfo> page(Integer status, long pageNum, long pageSize) {
//        Long userId = UserContext.getUserId();
//        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
//
//        Page<OrderInfo> page = new Page<>(pageNum, pageSize);
//        List<OrderInfo> records = orderInfoMapper.selectPage(page, userId, status);
//        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
//    }
//
//    /**
//     * 我的订单详情。
//     * 越权校验：只能看自己的订单。
//     */
//    public OrderDetailVO detail(String orderNo) {
//        Long userId = UserContext.getUserId();
//        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
//
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
//        // 越权防护：不是自己的订单，报"不存在"（不暴露"存在但无权限"）
//        BizAssert.isTrue(userId.equals(order.getUserId()), ResultCode.ORDER_NOT_FOUND);
//
//        return buildDetail(order);
//    }
//
//    /**
//     * 管理端订单详情（不校验归属，运营可以看所有订单）。
//     */
//    public OrderDetailVO adminDetail(String orderNo) {
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
//        return buildDetail(order);
//    }
//
//    /**
//     * 管理端订单分页。
//     * userId 传 null —— 查所有用户的订单。
//     */
//    public PageResult<OrderInfo> adminPage(Integer status, long pageNum, long pageSize) {
//        Page<OrderInfo> page = new Page<>(pageNum, pageSize);
//        List<OrderInfo> records = orderInfoMapper.selectPage(page, null, status);
//        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
//    }
//
//    /**
//     * 用户取消订单。
//     * 事务：关单（0 → 4）+ 释放库存。Seata 全局事务保证原子性。
//     * 幂等：CAS 失败说明状态已变，直接报错；不重复释放库存。
//     */
//    @GlobalTransactional(name = "shop-cancel-order", rollbackFor = Exception.class)
//    public void cancel(String orderNo) {
//        Long userId = UserContext.getUserId();
//        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
//
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
//        // 越权校验
//        BizAssert.isTrue(userId.equals(order.getUserId()), ResultCode.ORDER_NOT_FOUND);
//        // 前置状态校验：只有待支付能取消
//        BizAssert.isTrue(order.getStatus() != null && order.getStatus() == 0,
//                ResultCode.ORDER_STATUS_ERROR);
//
//        // CAS 关单：0 → 4
//        int rows = orderInfoMapper.cancelIfPending(orderNo);
//        if (rows == 0) {
//            // 并发下已被支付/关闭 → 拒绝
//            throw new BizException(ResultCode.ORDER_STATUS_ERROR);
//        }
//
//        // 释放库存（inventory 侧 release 自身幂等，靠 inventory_log 唯一键）
//        releaseStock(orderNo);
//
//        // 状态流水
//        logStatus(orderNo, 0, 4, 1, "用户取消订单");
//    }
//
//    /**
//     * 管理员发货。
//     * 事务：状态 1 → 2（CAS）。不需要 Seata —— 只改一张表。
//     */
//    public void ship(String orderNo) {
//        OrderInfo order = orderInfoMapper.selectByOrderNo(orderNo);
//        BizAssert.notNull(order, ResultCode.ORDER_NOT_FOUND);
//
//        int rows = orderInfoMapper.markShipped(orderNo);
//        BizAssert.isTrue(rows == 1, ResultCode.ORDER_STATUS_ERROR);
//
//        logStatus(orderNo, 1, 2, 3, "管理员发货");
//    }
//
//// ==================== 私有工具方法 ====================
//
//    /**
//     * 组装订单详情。抽出来给 detail / adminDetail 复用。
//     */
//    private OrderDetailVO buildDetail(OrderInfo order) {
//        OrderDetailVO vo = new OrderDetailVO();
//        vo.setOrder(order);
//        vo.setItems(orderItemMapper.selectByOrderNo(order.getOrderNo()));
//        return vo;
//    }
//
//    /**
//     * 释放库存。抽出来给 cancel / closeOrder 复用。
//     * 库存侧 release 自身幂等（靠 inventory_log 的 uk_biz 唯一键）。
//     */
//    private void releaseStock(String orderNo) {
//        List<OrderItem> items = orderItemMapper.selectByOrderNo(orderNo);
//        List<LockItemDTO> lockItems = items.stream()
//                .map(i -> new LockItemDTO(i.getSkuId(), i.getQuantity()))
//                .toList();
//        Result<Void> releaseResult = inventoryClient.release(new StockLockReq(orderNo, lockItems));
//        if (releaseResult.getCode() != 0) {
//            throw new BizException(releaseResult.getCode(), releaseResult.getMessage());
//        }
//    }
//}
//
//
//
//
//
//
//
//
//
//
