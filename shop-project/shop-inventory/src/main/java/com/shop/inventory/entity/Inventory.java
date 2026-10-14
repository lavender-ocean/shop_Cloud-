package com.shop.inventory.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Inventory {
    private Long id;
    private Long skuId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer warnStock;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}