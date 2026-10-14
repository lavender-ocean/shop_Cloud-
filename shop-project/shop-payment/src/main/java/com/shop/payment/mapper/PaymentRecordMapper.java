package com.shop.payment.mapper;

import com.shop.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;

public interface PaymentRecordMapper {
    // 根据支付单号查询完整支付记录
    PaymentRecord selectByPaymentNo(@Param("paymentNo") String paymentNo);
    /** 该订单当前待支付的支付单 */
    PaymentRecord selectPendingByOrderNo(@Param("orderNo") String orderNo);
    // 新增一条支付流水记录
    int insert(PaymentRecord record);
    /** 支付成功 CAS：仅当仍为待支付 */
    int markSuccess(@Param("paymentNo") String paymentNo, @Param("thirdTradeNo") String thirdTradeNo);
}