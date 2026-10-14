package com.shop.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LockItem {
    @NotNull private Long skuId;
    @NotNull @Min(1) private Integer quantity;
}