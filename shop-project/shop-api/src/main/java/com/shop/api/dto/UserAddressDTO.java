package com.shop.api.dto;

import lombok.Data;

/**
 * 用户地址契约 DTO。
 * 注意：这是"跨服务"的契约 —— 只暴露 order 需要的字段，
 * 不包含 createTime / deleted / createBy 等审计字段。
 */
@Data
public class UserAddressDTO {
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer isDefault;
}