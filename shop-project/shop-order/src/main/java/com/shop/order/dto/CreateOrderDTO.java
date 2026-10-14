//package com.shop.order.dto;
//
//import jakarta.validation.Valid;
//import jakarta.validation.constraints.NotBlank;
//import jakarta.validation.constraints.NotEmpty;
//import lombok.Data;
//
//import java.util.List;
//
//@Data
//public class CreateOrderDTO {
//    @NotBlank(message = "幂等 token 不能为空")
//    private String idempotentToken;
//
//    @Valid
//    @NotEmpty(message = "订单项不能为空")
//    private List<OrderItemDTO> items;
//
//    @NotBlank(message = "收货人不能为空")
//    private String receiverName;
//    @NotBlank(message = "手机号不能为空")
//    private String receiverPhone;
//    private String receiverProvince;
//    private String receiverCity;
//    private String receiverDistrict;
//    private String receiverDetail;
//    private String remark;
//}

package com.shop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderDTO {
    @Valid
    @NotEmpty(message = "订单项不能为空")
    private List<OrderItemDTO> items;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    private String remark;
}
