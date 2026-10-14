
package com.shop.inventory.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.inventory.dto.LockItem;
import com.shop.inventory.dto.StockChangeDTO;
import com.shop.inventory.dto.StockLockDTO;
import com.shop.inventory.entity.Inventory;
import com.shop.inventory.entity.InventoryLog;
import com.shop.inventory.mapper.InventoryLogMapper;
import com.shop.inventory.mapper.InventoryMapper;
import exception.BizAssert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import result.PageResult;
import result.ResultCode;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class InventoryService {

    private static final int TYPE_INCREASE = 1;
    private static final int TYPE_LOCK = 2;
    private static final int TYPE_CONFIRM = 3;
    private static final int TYPE_RELEASE = 4;

    /**
     * 幂等设计（三条铁律）：
     *   1. 唯一键 uk_biz(order_no, type, sku_id) 是唯一的幂等闸门，DB 原子保证；
     *   2. 闸门必须放在【库存变更之前】——先写流水，成功才动库存。
     *      反例：先 update 再 insert 并 catch 唯一键异常，InnoDB 只回滚那一条 insert，
     *      事务里的 update 会提交 → 重复扣减；
     *   3. 库存变更失败（rows=0）就抛异常，让整个事务回滚，连带撤销刚插入的流水。
     */

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public InventoryService(InventoryMapper inventoryMapper, InventoryLogMapper inventoryLogMapper) {
        this.inventoryMapper = inventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
    }

    public Inventory getBySkuId(Long skuId) {
        return inventoryMapper.selectBySkuId(skuId);
    }

    public PageResult<Inventory> page(long pageNum, long pageSize, Long skuId) {
        Page<Inventory> page = new Page<>(pageNum, pageSize);
        List<Inventory> records = inventoryMapper.selectPage(page, skuId);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    public PageResult<InventoryLog> logPage(long pageNum, long pageSize, Long skuId) {
        Page<InventoryLog> page = new Page<>(pageNum, pageSize);
        List<InventoryLog> records = inventoryLogMapper.selectPage(page, skuId);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    /** 初始化库存 */
    @Transactional(rollbackFor = Exception.class)
    public void init(StockChangeDTO dto) {
        BizAssert.isTrue(inventoryMapper.selectBySkuId(dto.getSkuId()) == null,
                ResultCode.PARAM_ERROR, "该 SKU 库存已存在");

        Inventory inv = new Inventory();
        inv.setId(IdWorker.getId());
        inv.setSkuId(dto.getSkuId());
        inv.setTotalStock(dto.getQuantity());
        inv.setAvailableStock(dto.getQuantity());
        inv.setLockedStock(0);
        inv.setWarnStock(dto.getWarnStock() == null ? 0 : dto.getWarnStock());
        inventoryMapper.insert(inv);

        writeLog(dto.getSkuId(), null, TYPE_INCREASE, dto.getQuantity());
    }

    /** 入库 */
    @Transactional(rollbackFor = Exception.class)
    public void increase(StockChangeDTO dto) {
        int rows = inventoryMapper.increase(dto.getSkuId(), dto.getQuantity());
        BizAssert.isTrue(rows == 1, ResultCode.PARAM_ERROR, "库存未初始化");
        writeLog(dto.getSkuId(), null, TYPE_INCREASE, dto.getQuantity());
    }

    /** 下单锁定（幂等 + 防超卖） */
    @Transactional(rollbackFor = Exception.class)
    public void lock(StockLockDTO dto) {
        for (LockItem item : sortBySkuId(dto.getItems())) {
            // 悬挂检测：release/取消已先执行 → 拒绝锁定（否则这笔锁永远没人释放）
            if (inventoryLogMapper.countByBiz(dto.getOrderNo(), TYPE_RELEASE, item.getSkuId()) > 0) {
                log.warn("[库存] 检测到悬挂：release 已先执行，拒绝 lock。orderNo={}, skuId={}",
                        dto.getOrderNo(), item.getSkuId());
                throw new exception.BizException(ResultCode.PARAM_ERROR, "流程乱序：释放早于锁定");
            }

            // ★ 幂等闸门：先写流水。唯一键冲突 = 该动作已完成 → 完全不碰库存
            if (isDuplicate(() -> writeLog(item.getSkuId(), dto.getOrderNo(),
                    TYPE_LOCK, item.getQuantity()))) {
                log.info("[库存] lock 幂等命中。orderNo={}, skuId={}",
                        dto.getOrderNo(), item.getSkuId());
                continue;
            }

            int rows = inventoryMapper.lock(item.getSkuId(), item.getQuantity());
            // 库存不足 → 抛异常 → 整个事务回滚（连带撤销刚插入的流水）
            BizAssert.isTrue(rows == 1, ResultCode.STOCK_NOT_ENOUGH);
        }
    }

    /** 支付确认扣减 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(StockLockDTO dto) {
        for (LockItem item : sortBySkuId(dto.getItems())) {
            if (isDuplicate(() -> writeLog(item.getSkuId(), dto.getOrderNo(),
                    TYPE_CONFIRM, item.getQuantity()))) {
                log.info("[库存] confirm 幂等命中。orderNo={}, skuId={}",
                        dto.getOrderNo(), item.getSkuId());
                continue;
            }
            int rows = inventoryMapper.confirm(item.getSkuId(), item.getQuantity());
            // 锁定库存不足 = 真正的业务错误（Try 没成功），必须让上游知道
            BizAssert.isTrue(rows == 1, ResultCode.PARAM_ERROR, "锁定库存不足，无法确认");
        }
    }

    /** 取消/超时释放（幂等 + 防空回滚） */
    @Transactional(rollbackFor = Exception.class)
    public void release(StockLockDTO dto) {
        for (LockItem item : sortBySkuId(dto.getItems())) {
            if (isDuplicate(() -> writeLog(item.getSkuId(), dto.getOrderNo(),
                    TYPE_RELEASE, item.getQuantity()))) {
                log.info("[库存] release 幂等命中。orderNo={}, skuId={}",
                        dto.getOrderNo(), item.getSkuId());
                continue;
            }

            int rows = inventoryMapper.release(item.getSkuId(), item.getQuantity());
            if (rows == 0) {
                // ★ 空回滚 / 状态不匹配：不抛异常，避免 MQ 无限重试。
                //   流水已经写入 → 后续若 lock 才到，lock 侧的悬挂检测会拦住它。
                log.warn("[库存] release 空回滚或状态不匹配，已记录流水并跳过。orderNo={}, skuId={}",
                        dto.getOrderNo(), item.getSkuId());
            }
        }
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 统一加锁顺序：按 skuId 升序。避免两笔订单 item 顺序相反导致 AB-BA 死锁。
     */
    private List<LockItem> sortBySkuId(List<LockItem> items) {
        return items.stream().sorted(Comparator.comparing(LockItem::getSkuId)).toList();
    }

    /** 执行一次"幂等闸门"写入；唯一键冲突返回 true（= 该动作已执行过） */
    private boolean isDuplicate(Runnable gate) {
        try {
            gate.run();
            return false;
        } catch (DuplicateKeyException e) {
            return true;
        }
    }

    private void writeLog(Long skuId, String orderNo, int type, int quantity) {
        InventoryLog log = new InventoryLog();
        log.setId(IdWorker.getId());
        log.setSkuId(skuId);
        log.setOrderNo(orderNo);
        log.setType(type);
        log.setQuantity(quantity);
        inventoryLogMapper.insert(log);
    }
}


























































































//package com.shop.inventory.service;
//
//import com.baomidou.mybatisplus.core.toolkit.IdWorker;
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.shop.inventory.dto.LockItem;
//import com.shop.inventory.dto.StockChangeDTO;
//import com.shop.inventory.dto.StockLockDTO;
//import com.shop.inventory.entity.Inventory;
//import com.shop.inventory.entity.InventoryLog;
//import com.shop.inventory.mapper.InventoryLogMapper;
//import com.shop.inventory.mapper.InventoryMapper;
//import exception.BizAssert;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.dao.DuplicateKeyException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import result.PageResult;
//import result.ResultCode;
//
//import java.util.Comparator;
//import java.util.List;
//
//@Slf4j
//@Service
//public class InventoryService {
//
//    /** 流水类型 */
//    //增加库存
//    private static final int TYPE_INCREASE = 1;
//    //下单 --- 占住库存
//    private static final int TYPE_LOCK = 2;
//    //确认支付 --- 减去预占库存
//    private static final int TYPE_CONFIRM = 3;
//    //释放库存锁
//    private static final int TYPE_RELEASE = 4;
//
//    private final InventoryMapper inventoryMapper;
//    private final InventoryLogMapper inventoryLogMapper;
//
//    public InventoryService(InventoryMapper inventoryMapper, InventoryLogMapper inventoryLogMapper) {
//        this.inventoryMapper = inventoryMapper;
//        this.inventoryLogMapper = inventoryLogMapper;
//    }
//
//    //根据skuId 来获取 sku信息
//    public Inventory getBySkuId(Long skuId) {
//
//        return inventoryMapper.selectBySkuId(skuId);
//    }
//
//    //分页查询
//    public PageResult<Inventory> page(long pageNum, long pageSize, Long skuId) {
//        Page<Inventory> page = new Page<>(pageNum, pageSize);
//        List<Inventory> records = inventoryMapper.selectPage(page, skuId);
//        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
//    }
//
//    //流水表分页查询
//    public PageResult<InventoryLog> logPage(long pageNum, long pageSize, Long skuId) {
//        Page<InventoryLog> page = new Page<>(pageNum, pageSize);
//        List<InventoryLog> records = inventoryLogMapper.selectPage(page, skuId);
//        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
//    }
//
//    /** 初始化库存 */
//    //商品上架
//    //同时改 库存表和流水表
//    @Transactional(rollbackFor = Exception.class)
//    public void init(StockChangeDTO dto) {
//        //去判断 inventory里面有没有 这个 skuId 如果有了skuId 那就抛出异常
//        BizAssert.isTrue(inventoryMapper.selectBySkuId(dto.getSkuId()) == null,
//                ResultCode.PARAM_ERROR, "该 SKU 库存已存在");
//
//        Inventory inv = new Inventory();
//        inv.setId(IdWorker.getId());
//        inv.setSkuId(dto.getSkuId());
//        inv.setTotalStock(dto.getQuantity());
//        inv.setAvailableStock(dto.getQuantity());
//        inv.setLockedStock(0);
//        inv.setWarnStock(dto.getWarnStock() == null ? 0 : dto.getWarnStock());
//        //商品上架  插入进 库存表中
//        inventoryMapper.insert(inv);
//        //写入流水表中
//        writeLog(dto.getSkuId(), null, TYPE_INCREASE, dto.getQuantity());
//    }
//
//    /** 入库 */
//    @Transactional(rollbackFor = Exception.class)
//    public void increase(StockChangeDTO dto) {
//        //增加库存
//        int rows = inventoryMapper.increase(dto.getSkuId(), dto.getQuantity());
//        //判断是否可以 成功increase ----> 为1 则增加成功 ----> 不为1 ------> 则添加失败（表里没有这个商品）
//        BizAssert.isTrue(rows == 1, ResultCode.PARAM_ERROR, "库存未初始化");
//        //写入流水表中
//        writeLog(dto.getSkuId(), null, TYPE_INCREASE, dto.getQuantity());
//    }
//
//    /** 下单锁定（幂等 + 防超卖） */
////    @Transactional(rollbackFor = Exception.class)
////    public void lock(StockLockDTO dto) {
////        for (LockItem item : dto.getItems()) {
////            //老订单的流水会不会卡住新的下单？    !!!!!!!!!!!!  问题
////            //不会
////            //因为每次来的时候是要求新的订单号的
////
////            //不同订单之间完全隔离，旧订单流水不会影响新订单。幂等是【按 订单号+sku+操作类型】粒度，不是按sku全局粒度
////
//////            只有同一个订单重复操作才会被自己的流水拦截；新订单全新orderNo不受任何历史记录影响
////
////
////
////            //先去查流水表，看看有没有操作过了 ----- 看的是固定的这一单的 不会查到别人的
////            //操作类型是：加锁预占 lock
////            if (inventoryLogMapper.countByBiz(dto.getOrderNo(), TYPE_LOCK, item.getSkuId()) > 0) {
////                // 已锁定过，幂等跳过
////                continue;
////            }
////            //加锁预占库存
////            int rows = inventoryMapper.lock(item.getSkuId(), item.getQuantity());
////            //如果有库存 那么就是预占成功， 如果没有 就是rows！=1 ------> 去抛异常
////            BizAssert.isTrue(rows == 1, ResultCode.STOCK_NOT_ENOUGH);
////            //写入流水表 ------ 在for循环中一条一条写
////            writeLog(item.getSkuId(), dto.getOrderNo(), TYPE_LOCK, item.getQuantity());
////        }
////    }
//
//// 1. 问题 1：多 SKU 并发死锁：A 版本完全没处理，B 修复
////
////    场景：
////
////    线程 1：订单 A 同时扣 SKU‑100、SKU‑200
////    线程 2：订单 B 同时扣 SKU‑200、SKU‑100
////
////    A 版本直接按前端传入顺序处理：
//
////    T1：锁住 sku100 → 请求锁 sku200
////    T2：锁住 sku200 → 请求锁 sku100
////    数据库发生死锁，MySQL 直接检测死锁，回滚其中一个事务。
////
////    B 版本：全部 lockItem 按照 skuId 升序排序。
////    不管前端传进来 SKU 是什么顺序，服务端统一排序，所有事务访问 SKU 资源顺序完全一致，从业务层面彻底消除 AB‑BA 死锁。
////
////
////    面试高频考点：批量操作多行数据，一定要固定访问顺序，避免数据库死锁。
//
//    @Transactional(rollbackFor = Exception.class)
//    public void lock(StockLockDTO dto) {
//        // 统一加锁顺序，避免 AB-BA 死锁
//        List<LockItem> items = dto.getItems().stream()
//                .sorted(Comparator.comparing(LockItem::getSkuId))
//                .toList();
//
//        for (LockItem item : items) {
//            //  唯一键当幂等闸门，必须在库存变更之前
//            try {
//                //往流水表中写这次要进行的操作，如果流水表中已经记录了，就是幂等 直接 防幂等
//                writeLog(item.getSkuId(), dto.getOrderNo(), TYPE_LOCK, item.getQuantity());
//            } catch (DuplicateKeyException e) {
//                // 流水已存在 → 动作已完成 → 完全不碰库存
//                log.info("lock 幂等命中，orderNo={}, skuId={}", dto.getOrderNo(), item.getSkuId());
//                continue;
//            }
//
//            //去预占库存
//            int rows = inventoryMapper.lock(item.getSkuId(), item.getQuantity());
//            // rows==0 是真正的业务错误；抛异常 → 整事务回滚 → 连带把刚插入的流水撤销
//            BizAssert.isTrue(rows == 1, ResultCode.STOCK_NOT_ENOUGH);
//        }
//    }
//
//    /** 支付确认扣减 */
//    @Transactional(rollbackFor = Exception.class)
//    public void confirm(StockLockDTO dto) {
//        for (LockItem item : dto.getItems()) {
//            //同样去查看流水表里面有没有这个流水的记录
//            if (inventoryLogMapper.countByBiz(dto.getOrderNo(), TYPE_CONFIRM, item.getSkuId()) > 0) {
//                //如果有就防幂等 给 跳过
//                continue;
//            }
//            //在支付确认之后 ， 就 真实去扣减库存 ， 真正出库 ，同时 扣减锁定库存的数量（因为出库了）
//            int rows = inventoryMapper.confirm(item.getSkuId(), item.getQuantity());
//            //判断锁定库存是否够
//
////            出现rows=0的几种真实场景：
////
////            1.重复调用confirm接口 订单A已经confirm执行成功，locked_stock已经扣掉；
////            再重复调用confirm，此时数据库locked_stock < qty，where条件不满足，更新行数0。
////            虽然前面有流水幂等判断countByBiz(orderNo,TYPE_CONFIRM,skuId)>0 continue，
////            但是幂等判断和update不是原子！
////            并发：两个confirm请求同时进来，都查询流水，都没查到记录，都越过if判断，
////            进入update；第一个update成功扣减locked_stock；
////            第二个执行update时 locked_stock已经不够，rows=0抛出异常。
//
//
//
//            //  /////////////////////////////////////////////////////////////
//        // 但是还是非原子的还是会有深层的隐患！！！！！！！！！！！！！！！！！！！！！在md文档中！！！！！！！！！！！！！
////
////            2.补偿/人工数据库篡改 数据库手动把locked_stock改小，
////            业务代码正常流程触发confirm，where条件不命中。
//
//            BizAssert.isTrue(rows == 1, ResultCode.PARAM_ERROR, "锁定库存不足，无法确认");
//            //写入流水表中
//            writeLog(item.getSkuId(), dto.getOrderNo(), TYPE_CONFIRM, item.getQuantity());
//        }
//    }
//
//    /** 取消/超时释放（幂等，防重复归还导致库存虚增） */
//    @Transactional(rollbackFor = Exception.class)
//    public void release(StockLockDTO dto) {
//        for (LockItem item : dto.getItems()) {
//            //同样现在流水表中防幂等
//            if (inventoryLogMapper.countByBiz(dto.getOrderNo(), TYPE_RELEASE, item.getSkuId()) > 0) {
//                continue;
//            }
//            //用户取消/超时未支付 --- 释放锁定的库存 ----增加可购买的库存
//            int rows = inventoryMapper.release(item.getSkuId(), item.getQuantity());
//
////            rows=0触发断言。
////
////            场景：
////
////            release接口重复并发调用 两个release请求同时进来
////            ，countByBiz都查不到TYPE_RELEASE流水，跳过幂等判断。
////            第一个update成功：locked_stock减去数量；
////            第二个再执行update，locked_stock已经小于qty，where条件不满足，rows=0抛异常。
////
////            业务乱序调用 业务流程错乱：先调用release释放库存，还没有执行lock锁定库存。
////            此时locked_stock是0，执行release，where条件不匹配。
////
////            例如：MQ重复消息，顺序错乱；cancel消息先到，lock下单消息后到。
////
////            已经confirm确认过库存 订单已经支付confirm，locked_stock已经被扣减；
////            再执行release取消订单，locked_stock不足，更新返回0。
////
//
//            //判断是否执行成功
//            BizAssert.isTrue(rows == 1, ResultCode.PARAM_ERROR, "锁定库存不足，无法释放");
//            //写入流水表中
//            writeLog(item.getSkuId(), dto.getOrderNo(), TYPE_RELEASE, item.getQuantity());
//        }
//    }
//
//    //写入数据进流水表
//    private void writeLog(Long skuId, String orderNo, int type, int quantity) {
//        InventoryLog log = new InventoryLog();
//        log.setId(IdWorker.getId());
//        log.setSkuId(skuId);
//        log.setOrderNo(orderNo);
//        log.setType(type);
//        log.setQuantity(quantity);
//        inventoryLogMapper.insert(log);
//    }
//}