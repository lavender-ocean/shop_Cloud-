package com.shop.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddressDTO {
    private Long id;                 // 编辑时传
    @NotBlank(message = "收货人不能为空")
    private String receiverName;
    @NotBlank(message = "手机号不能为空")
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    @NotBlank(message = "详细地址不能为空")
    private String detailAddress;
    private String tag;
    private Integer isDefault;
}