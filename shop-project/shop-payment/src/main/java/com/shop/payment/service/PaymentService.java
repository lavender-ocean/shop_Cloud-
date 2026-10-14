package com.shop.payment.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shop.api.dto.OrderSummaryDTO;
import com.shop.api.feign.OrderClient;
import com.shop.payment.dto.CreatePaymentDTO;
import com.shop.payment.dto.MockPayDTO;
import com.shop.payment.dto.PaymentCallbackDTO;
import com.shop.payment.entity.PaymentRecord;
import com.shop.payment.mapper.PaymentRecordMapper;
import com.shop.payment.vo.PaymentVO;
import constant.MqConstants;
import context.UserContext;
import exception.BizAssert;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import result.Result;
import result.ResultCode;
import util.JsonUtils;

import java.util.Map;

@Service
public class PaymentService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SUCCESS = 1;

    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderClient orderClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${shop.pay.mock-secret:shop-mock-secret}")
    private String mockSecret;

    public PaymentService(PaymentRecordMapper paymentRecordMapper,
                          OrderClient orderClient,
                          RabbitTemplate rabbitTemplate) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.orderClient = orderClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** 创建支付单（金额取自订单，绝不信任前端） */
    public PaymentVO create(CreatePaymentDTO dto) {
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);

        Result<OrderSummaryDTO> result = orderClient.getOrder(dto.getOrderNo());
        BizAssert.isTrue(result.getCode() == 0 && result.getData() != null
                , ResultCode.ORDER_NOT_FOUND);
        OrderSummaryDTO order = result.getData();

        BizAssert.isTrue(userId.equals(order.getUserId()), ResultCode.FORBIDDEN);
        BizAssert.isTrue(order.getStatus() != null && order.getStatus() == STATUS_PENDING,
                ResultCode.ORDER_STATUS_ERROR);

        PaymentRecord pending = paymentRecordMapper.selectPendingByOrderNo(order.getOrderNo());
        if (pending == null) {
            pending = new PaymentRecord();
            pending.setId(IdWorker.getId());
            pending.setPaymentNo("PAY" + IdWorker.getId());
            pending.setOrderNo(order.getOrderNo());
            pending.setUserId(userId);
            pending.setChannel(1);
            pending.setAmount(order.getPayAmount());
            pending.setStatus(STATUS_PENDING);
            paymentRecordMapper.insert(pending);
        }
        return new PaymentVO(pending.getPaymentNo(), pending.getOrderNo(), pending.getAmount());
    }

    /**
     * Mock 支付：模拟第三方"支付成功"并回调。
     * ★ 加 @Transactional：mockPay 内部是 this.handleCallback(...) 自调用，
     *   只给 handleCallback 加注解不会生效，外层这里必须也加，否则整个方法无事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void mockPay(MockPayDTO dto) {
        PaymentRecord record = paymentRecordMapper.selectByPaymentNo(dto.getPaymentNo());
        BizAssert.notNull(record, ResultCode.PAY_RECORD_NOT_FOUND);
        // 越权校验：只能支付自己的支付单
        Long userId = UserContext.getUserId();
        BizAssert.isTrue(userId != null && userId.equals(record.getUserId()),
                ResultCode.FORBIDDEN);
        BizAssert.isTrue(record.getStatus() != null && record.getStatus() == STATUS_PENDING,
                ResultCode.PAY_FAILED, "该支付单已处理");

        PaymentCallbackDTO callback = new PaymentCallbackDTO();
        callback.setPaymentNo(record.getPaymentNo());
        callback.setThirdTradeNo("MOCK" + System.currentTimeMillis());
        callback.setAmount(record.getAmount());
        callback.setSign(sign(record.getPaymentNo(), record.getAmount()));
        handleCallback(callback);
    }

    /**
     * 第三方回调：验签 → 幂等更新 → 投递支付成功事件。
     *
     * ★ 加 @Transactional 的意义：把"改支付记录"与"发 MQ"绑成原子操作。
     *   MQ 抖动导致 convertAndSend 抛异常时，整个方法回滚 →
     *   payment_record 回到"待支付"→ 第三方重推 / mockPay 重试都还能走通。
     *   不这么做的话：记录已置成功、事件却没发出去，订单永远停在待支付且零补偿。
     *
     * 这是"简单 mock"方案。要做到真正 at-least-once，仍需本地消息表
     * （和 order 模块同一套：落库 + XXL-JOB 补偿投递）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(PaymentCallbackDTO dto) {
        // 1. 验签
        BizAssert.isTrue(sign(dto.getPaymentNo(), dto.getAmount()).equalsIgnoreCase(dto.getSign()),
                ResultCode.PAY_FAILED, "签名校验失败");

        PaymentRecord record = paymentRecordMapper.selectByPaymentNo(dto.getPaymentNo());
        BizAssert.notNull(record, ResultCode.PAY_RECORD_NOT_FOUND);

        // 2. 金额一致性校验（纵深防御，防签名拼接歧义）
        BizAssert.isTrue(dto.getAmount() != null && dto.getAmount().equals(record.getAmount()),
                ResultCode.PAY_FAILED, "回调金额与支付单不一致");

        // 3. 幂等：CAS 更新（重复回调时影响行数为 0）
        if (record.getStatus() == null || record.getStatus() != STATUS_SUCCESS) {
            paymentRecordMapper.markSuccess(dto.getPaymentNo(), dto.getThirdTradeNo());
        }

        // 4. 至少一次投递（消费端 CAS 幂等，重复投递无害）
        rabbitTemplate.convertAndSend(MqConstants.PAYMENT_EXCHANGE, MqConstants.RK_PAYMENT_SUCCESS,
                JsonUtils.toJson(Map.of("orderNo", record.getOrderNo()
                        , "paymentNo", dto.getPaymentNo())));
    }

    /**
     * 签名：保留 mock 的简单算法。
     * 已知弱点（面试可讲）：MD5 已不适合签名；且拼接无分隔符，
     * "PAY1"+23 与 "PAY12"+3 会产生同一待签串。生产应换 HMAC-SHA256 + 字段分隔。
     */
    private String sign(String paymentNo, Long amount) {
        return SecureUtil.md5(paymentNo + "|" + amount + "|" + mockSecret);
    }
}