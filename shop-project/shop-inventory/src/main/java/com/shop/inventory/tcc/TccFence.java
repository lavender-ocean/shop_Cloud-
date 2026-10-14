package com.shop.inventory.tcc;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccFence {
    private Long id;
    private String xid;   //XID
    private Long branchId; //分支ID
    private String actionName;
    /** 0 TRYING / 1 COMMITTED / 2 ROLLBACKED / 3 CONFIRMED */
    private Integer status;
    private String businessKey;  //业务key
    /** Try 请求的 items 快照(JSON)，悬挂兜底时据此释放库存 */
    private String itemsJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}