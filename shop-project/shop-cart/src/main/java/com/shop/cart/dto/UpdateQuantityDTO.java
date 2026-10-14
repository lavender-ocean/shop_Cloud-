package com.shop.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateQuantityDTO {
    @NotNull private Long skuId;
    @NotNull @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;
}