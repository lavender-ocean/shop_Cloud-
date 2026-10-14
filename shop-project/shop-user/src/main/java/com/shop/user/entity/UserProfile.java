package com.shop.user.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserProfile {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private String realName;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}