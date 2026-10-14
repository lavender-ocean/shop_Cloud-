package com.shop.product.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Sku {
    private Long id;
    private Long spuId;
    private String skuCode;
    private String name;
    private String specJson;
    private String image;
    private Long price;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}