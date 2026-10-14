package com.shop.order.vo;

import com.shop.api.dto.SkuInfoDTO;
import com.shop.order.entity.OrderItem;
import lombok.Data;

@Data
public class OrderItemVO {
    /** 下单时的快照（本地数据，一定有） */
    private OrderItem snapshot;
    /** 商品最新信息（远程，可能为 null = 降级） */
    private SkuInfoDTO latestSku;
    /** 当前可用库存（远程，null = 未知） */
    private Integer availableStock;
}