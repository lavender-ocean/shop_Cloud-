package com.shop.inventory.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InventoryLog {
    private Long id;
    private Long skuId;
    private String orderNo;
    private Integer type;
    private Integer quantity;
    private Integer beforeStock;
    private Integer afterStock;
    private String bizNo;
    private String remark;
    private LocalDateTime createTime;
}