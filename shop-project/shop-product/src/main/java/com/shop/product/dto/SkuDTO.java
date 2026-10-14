package com.shop.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SkuDTO {
    @NotBlank(message = "SKU 名称不能为空")
    private String name;
    private String specJson;
    private String image;
    @NotNull(message = "SKU 价格不能为空")
    private Long price;              // 分
    private Integer status;
}