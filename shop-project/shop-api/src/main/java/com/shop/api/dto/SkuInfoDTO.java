package com.shop.api.dto;

import lombok.Data;

@Data
public class SkuInfoDTO {
    private Long id;
    private Long spuId;
    private String spuName;
    private String name;
    private String specJson;
    private String image;
    private Long price;
    private Integer status;
}