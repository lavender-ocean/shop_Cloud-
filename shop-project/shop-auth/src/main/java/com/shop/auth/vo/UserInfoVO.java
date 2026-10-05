package com.shop.auth.vo;

import lombok.Data;
import java.util.List;

@Data
public class UserInfoVO {
    private Long userId;
    private String username;
    private Integer userType;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuVO> menus;
}