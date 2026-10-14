package com.shop.cart.vo;

import lombok.Data;

import java.util.List;

@Data
public class CartVO {
    private List<CartItemVO> items;
    private int totalQuantity;      // 有效商品数量
    private int selectedQuantity;   // 有效且已选数量
    private long selectedAmount;    // 有效且已选金额(分)
}