package com.shop.cart.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 购物车存储模型（Redis Hash value）。
 * 只存"引用 + 用户意图"，不冗余任何商品数据 —— 商品信息一律 list 时回源。
 */
@Data
public class CartItem implements Serializable {
    private Long skuId;
    private Integer quantity;
    private Boolean selected;
    private Long addTime;
}