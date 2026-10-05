package com.shop.auth.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVO {
    private Long id;
    private String name;
    private String path;
    private String component;
    private String icon;
    private List<MenuVO> children = new ArrayList<>();
}