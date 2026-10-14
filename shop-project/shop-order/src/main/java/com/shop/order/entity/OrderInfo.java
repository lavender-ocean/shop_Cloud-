package com.shop.order.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderInfo {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long totalAmount;
    private Long discountAmount;
    private Long freightAmount;
    private Long payAmount;
    private Integer status;
    private Integer payType;
    private LocalDateTime payTime;
    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetail;
    private String remark;
    private LocalDateTime expireTime;
    private Integer cancelType;
    private String cancelReason;
    private LocalDateTime finishTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}