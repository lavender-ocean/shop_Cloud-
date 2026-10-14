package com.shop.api.dto;

import lombok.Data;

@Data
public class InventoryDTO {
    private Long skuId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockedStock;
}