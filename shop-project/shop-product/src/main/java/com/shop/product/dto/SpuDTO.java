package com.shop.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SpuDTO {
    private Long id;                 // 编辑时传
    @NotBlank(message = "商品名称不能为空")
    private String name;
    @NotNull(message = "类目不能为空")
    private Long categoryId;
    private Long brandId;
    private String mainImage;
    private List<String> images;
    private String detail;
    private String unit;             // 超市：500g / 盒
    private Integer isColdChain;     // 超市：冷链
    private Integer status;
    @Valid
    @NotEmpty(message = "至少一个 SKU")
    private List<SkuDTO> skus;
}