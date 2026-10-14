package com.shop.product.vo;

import com.shop.product.entity.Sku;
import lombok.Data;

import java.util.List;

@Data
public class SpuDetailVO {
    private Long id;
    private String spuCode;
    private String name;
    private Long categoryId;
    private Long brandId;
    private String mainImage;
    private List<String> images;
    private String detail;
    private Long priceMin;
    private Long priceMax;
    private String unit;
    private Integer isColdChain;
    private Integer status;
    private List<Sku> skus;
}