package com.shop.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class StockLockDTO {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
    @Valid
    @NotEmpty(message = "库存项不能为空")
    private List<LockItem> items;
}