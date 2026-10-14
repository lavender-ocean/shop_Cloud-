package com.shop.cart.vo;

import lombok.Data;

/** 购物车展示模型：商品服务实时数据 + 用户意图 拼装结果 */
@Data
public class CartItemVO {
    private Long skuId;
    private Long spuId;
    private String spuName;
    private String skuName;
    private String image;
    private String specJson;
    private Long price;
    private Integer quantity;
    private Boolean selected;
    private Long addTime;
    /** false = SKU 已删除/已下架 */
    private Boolean valid;
    private String invalidReason;
}