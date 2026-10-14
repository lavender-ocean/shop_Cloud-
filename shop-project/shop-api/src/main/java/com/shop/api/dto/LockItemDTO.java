package com.shop.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class LockItemDTO {
    private Long skuId;
    private Integer quantity;
}