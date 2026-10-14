package com.shop.product.dto;

import lombok.Data;

@Data
public class CategoryQuery {
    private long pageNum = 1;
    private long pageSize = 10;
    private String name;
}