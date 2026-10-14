package com.shop.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSelectedDTO {
    @NotNull private Long skuId;
    @NotNull private Boolean selected;
}