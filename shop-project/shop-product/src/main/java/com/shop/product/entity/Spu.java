package com.shop.product.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Spu {
    private Long id;
    private String spuCode;
    private String name;
    private Long categoryId;
    private Long brandId;
    private String mainImage;
    private String images;          // JSON 数组字符串
    private String detail;
    private Long priceMin;
    private Long priceMax;
    private String unit;
    private Integer isColdChain;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}