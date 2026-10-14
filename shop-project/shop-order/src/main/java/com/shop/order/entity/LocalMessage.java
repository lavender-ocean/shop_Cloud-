package com.shop.order.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LocalMessage {
    private Long id;
    private String msgId;
    private String bizType;
    private String bizNo;
    private String payload;
    private Integer status;          // 0待投递 1已投递 2失败
    private Integer retryCount;      //重试次数
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
}