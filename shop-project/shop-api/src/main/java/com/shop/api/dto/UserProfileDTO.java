package com.shop.api.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String realName;
}