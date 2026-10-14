
package com.shop.inventory.tcc;

import com.shop.api.dto.StockTccReq;
import com.shop.inventory.dto.LockItem;
import com.shop.inventory.dto.StockLockDTO;
import com.shop.inventory.service.InventoryService;
import exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import result.ResultCode;
import util.JsonUtils;

/**
 * 库存显式 TCC 三阶段。
 *   Try     = 预占（可用 → 锁定）
 *   Confirm = 确认出库（锁定 → 扣减）
 *   Cancel  = 释放（锁定 → 可用）
 *
 * fence 负责 空回滚 / 悬挂 / 幂等；底层 lock/confirm/release 靠 inventory_log 唯一键自身幂等。
 *
 * ★ 加了 @Transactional：tcc_fence 与 inventory / inventory_log 在同一个库，
 *   三步可以放进一个本地事务，保证"占位 + 库存变更 + 状态推进"原子。
 */
@Slf4j
@Service
public class TccInventoryService {

    /** 显式 TCC 下用 orderNo 作为事务标识，库存分支固定为 1 */
    private static final long BRANCH_INVENTORY = 1L;

    private final TccFenceService tccFenceService;
    private final InventoryService inventoryService;

    public TccInventoryService(TccFenceService tccFenceService, InventoryService inventoryService) {
        this.tccFenceService = tccFenceService;
        this.inventoryService = inventoryService;
    }

    /** Try：预占库存 */
    @Transactional(rollbackFor = Exception.class)
    public void tryLock(StockTccReq req) {
        // 把 items 快照落进 fence，供"建单失败且 Cancel 也失败"时兜底释放
        String itemsJson = JsonUtils.toJson(req.getItems());

        // 1. 防悬挂 + 占位
        if (!tccFenceService.tryBegin(
                req.getOrderNo(), BRANCH_INVENTORY, req.getOrderNo(), itemsJson)) {
            throw new BizException(ResultCode.PARAM_ERROR, "检测到悬挂：该分支已被回滚，拒绝预占");
        }
        // 2. 业务 Try
        inventoryService.lock(toLockDTO(req));
        // 3. 标记 Try 成功；若并发下已被 Cancel 回滚 → 撤销自己的预占
        if (!tccFenceService.tryCommit(req.getOrderNo(), BRANCH_INVENTORY)) {
            inventoryService.release(toLockDTO(req));
            throw new BizException(ResultCode.PARAM_ERROR, "并发下分支已回滚，已撤销预占");
        }
    }

    /** Confirm：确认出库（防空 Confirm + 防覆盖已回滚 + 幂等） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(StockTccReq req) {
        if (!tccFenceService.confirmBegin(req.getOrderNo(), BRANCH_INVENTORY, req.getOrderNo())) {
            return;   // 空 Confirm / 已回滚 / 重复 Confirm → 直接成功返回
        }
        inventoryService.confirm(toLockDTO(req));
        tccFenceService.confirmCommit(req.getOrderNo(), BRANCH_INVENTORY);
    }

    /** Cancel：释放库存（防空回滚 + 幂等 + 防覆盖已确认） */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(StockTccReq req) {
        if (!tccFenceService.cancelBegin(req.getOrderNo(), BRANCH_INVENTORY, req.getOrderNo())) {
            return;   // 空回滚 / 重复 Cancel / 已 Confirm → 直接成功返回
        }
        inventoryService.release(toLockDTO(req));
        tccFenceService.cancelCommit(req.getOrderNo(), BRANCH_INVENTORY);
    }

    private StockLockDTO toLockDTO(StockTccReq req) {
        StockLockDTO dto = new StockLockDTO();
        dto.setOrderNo(req.getOrderNo());
        dto.setItems(req.getItems().stream().map(src -> {
            LockItem item = new LockItem();
            item.setSkuId(src.getSkuId());
            item.setQuantity(src.getQuantity());
            return item;
        }).toList());
        return dto;
    }
}




















































































//package com.shop.inventory.tcc;
//
//import com.shop.api.dto.StockTccReq;
//import com.shop.inventory.dto.LockItem;
//import com.shop.inventory.dto.StockLockDTO;
//import com.shop.inventory.service.InventoryService;
//import exception.BizException;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import result.ResultCode;
//
///**
// * 库存的 TCC 三阶段（显式 TCC）：
// *   Try     = 预占（可用 → 锁定）
// *   Confirm = 确认出库（锁定 → 扣减）
// *   Cancel  = 释放（锁定 → 可用）
// * fence 负责解决 空回滚 / 悬挂 / 幂等；底层 lock/confirm/release 自身也幂等（inventory_log 唯一键）。
// */
//@Slf4j
//@Service
//public class TccInventoryService {
//
//    /** 显式 TCC 下用 orderNo 作为事务标识，库存分支固定为 1 */
//    private static final long BRANCH_INVENTORY = 1L;
//
//    private final TccFenceService tccFenceService;
//    private final InventoryService inventoryService;
//
//    public TccInventoryService(TccFenceService tccFenceService, InventoryService inventoryService) {
//        this.tccFenceService = tccFenceService;
//        this.inventoryService = inventoryService;
//    }
//
//    /** Try：预占库存 */
//    //1.预占库存的时候 先去 try
//    //2.然后去执行业务 --- mapper
//    //3.业务执行成功了 --- 去commit
//    //4.失败了 --- cancel
//    //5.commit失败了 --- cancel
//    public void tryLock(StockTccReq req) {
//        //  1.防悬挂 + 占位
//        //占位就是 设置status 嘛
//        boolean canProceed = tccFenceService.tryBegin(req.getOrderNo(), BRANCH_INVENTORY, req.getOrderNo());
//        if (!canProceed) {
//            throw new BizException(ResultCode.PARAM_ERROR, "检测到悬挂：该分支已被回滚，拒绝预占");
//        }
//        // 2. 业务 Try
//        //去执行业务
//        inventoryService.lock(toLockDTO(req));
//        // 3. 标记 Try 成功；若并发下已被 Cancel 回滚 → 撤销自己的预占
//        //然后去标记 try成功了 然后去 commit ，如果 commit失败，那么
//        if (!tccFenceService.tryCommit(req.getOrderNo(), BRANCH_INVENTORY)) {
//            //commit失败了，释放预占用的库存
//            inventoryService.release(toLockDTO(req));
//            throw new BizException(ResultCode.PARAM_ERROR, "并发下分支已回滚，已撤销预占");
//        }
//    }
//
//    /** Confirm：确认出库（幂等由 inventory_log 保证） */
//    //如果遇到网络抖动  Try 成功后，Confirm 阶段网络抖动，Confirm 调用失败
//    public void confirm(StockTccReq req) {
//        //确认出库
//        inventoryService.confirm(toLockDTO(req));
//    }
//
//    /** Cancel：释放库存（防空回滚 + 幂等） */
//    public void cancel(StockTccReq req) {
//        //去cancel
//        boolean needRollback = tccFenceService.cancelBegin(
//                req.getOrderNo(), BRANCH_INVENTORY, req.getOrderNo());
//        if (!needRollback) {
//            return;   // 空回滚 / 重复 Cancel → 直接成功
//        }
//        //实际的业务mapper 还是 在这里
//        inventoryService.release(toLockDTO(req));
//        tccFenceService.cancelCommit(req.getOrderNo(), BRANCH_INVENTORY);
//    }
//
//    private StockLockDTO toLockDTO(StockTccReq req) {
//        StockLockDTO dto = new StockLockDTO();
//        dto.setOrderNo(req.getOrderNo());
//        dto.setItems(req.getItems().stream().map(src -> {
//            LockItem item = new LockItem();
//            item.setSkuId(src.getSkuId());
//            item.setQuantity(src.getQuantity());
//            return item;
//        }).toList());
//        return dto;
//    }
//}