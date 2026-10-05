package com.shop.auth.entity;

import lombok.Data;

@Data
public class SysPermission {
    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    private Integer type;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer status;
}