package com.shop.product.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Brand {
    private Long id;
    private String name;
    private String logo;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}