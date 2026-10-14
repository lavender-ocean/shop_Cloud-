package com.shop.product.dto;

import lombok.Data;

@Data
public class SpuQuery {
    private long pageNum = 1;
    private long pageSize = 10;
    private String name;
    private Long categoryId;
    private Integer status;
}