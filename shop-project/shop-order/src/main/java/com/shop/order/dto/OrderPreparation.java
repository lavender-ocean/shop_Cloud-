package com.shop.order.dto;

import com.shop.api.dto.LockItemDTO;
import com.shop.order.entity.OrderInfo;
import com.shop.order.entity.OrderItem;
import lombok.Data;

import java.util.List;

@Data
public class OrderPreparation {
    private String orderNo;
    private OrderInfo order;
    private List<OrderItem> items;
    private List<LockItemDTO> lockItems;
}