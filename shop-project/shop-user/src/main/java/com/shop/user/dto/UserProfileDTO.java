package com.shop.user.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserProfileDTO {
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private String realName;
}