
package com.shop.inventory.tcc;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.shop.api.dto.LockItemDTO;
import com.shop.api.dto.StockTccReq;
import com.shop.inventory.mapper.TccFenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import util.JsonUtils;

import java.util.List;

/**
 * TCC 事务控制：解决 空回滚 / 悬挂 / 幂等 三大问题。
 * 状态机：0 TRYING → 1 COMMITTED → 2 ROLLBACKED
 *                             ↘ 3 CONFIRMED（终态，禁止回滚）
 */
@Slf4j
@Service
public class TccFenceService {

    public static final int STATUS_TRYING = 0;
    public static final int STATUS_COMMITTED = 1;
    public static final int STATUS_ROLLBACKED = 2;
    public static final int STATUS_CONFIRMED = 3;

    private final TccFenceMapper tccFenceMapper;

    public TccFenceService(TccFenceMapper tccFenceMapper) {
        this.tccFenceMapper = tccFenceMapper;
    }

    /**
     * Try 开始：占位 + 检查是否"悬挂"。
     * @return true = 可以执行业务 Try；false = 悬挂，必须拒绝
     */

    public boolean tryBegin(String xid, long branchId, String businessKey, String itemsJson) {
        TccFence exist = tccFenceMapper.selectByXidBranch(xid, branchId);
        if (exist != null) {
            return isTryAllowed(exist);
        }
        try {
            tccFenceMapper.insert(
                    buildFence(xid, branchId, "try", STATUS_TRYING, businessKey, itemsJson));
            return true;
        } catch (DuplicateKeyException e) {
            TccFence now = tccFenceMapper.selectByXidBranchForUpdate(xid, branchId);
            if (now == null) {
                log.error("[TCC] fence 冲突但读不到记录，保守拒绝。xid={}, branch={}", xid, branchId);
                return false;
            }
            return isTryAllowed(now);
        }
    }


    private boolean isTryAllowed(TccFence fence) {
        if (fence.getStatus() == STATUS_ROLLBACKED) {
            log.warn("[TCC] 检测到悬挂：Cancel 已先执行，拒绝 Try。xid={}, branch={}",
                    fence.getXid(), fence.getBranchId());
            return false;
        }
        if (fence.getStatus() == STATUS_CONFIRMED) {
            log.warn("[TCC] 分支已 Confirm，重复 Try 拒绝。xid={}, branch={}",
                    fence.getXid(), fence.getBranchId());
            return false;
        }
        return true;   // TRYING / COMMITTED → 重复 Try，幂等放行
    }

    /**
     * Try 业务成功：标记为"已提交"。
     *
     * ★ 关键修复：expectedStatuses 必须包含 COMMITTED。
     *   只写 [TRYING] 的话，重复 Try 会把 rows=0 误判成"已被回滚"，
     *   调用方随即 release 撤销预占 → 订单在、库存没锁 → 超卖。
     *
     * @return false = 并发下已被 Cancel 回滚（调用方需回滚自己的业务）
     */
    public boolean tryCommit(String xid, long branchId) {
        int rows = tccFenceMapper.updateStatus(xid, branchId, STATUS_COMMITTED, "try",
                List.of(STATUS_TRYING, STATUS_COMMITTED));
        if (rows == 0) {
            log.warn("[TCC] Try 提交时发现分支已被回滚，需回滚业务。xid={}, branch={}", xid, branchId);
            return false;
        }
        return true;
    }

    /**
     * Confirm 开始：防空 Confirm、防覆盖已回滚分支。
     * @return true = 可以执行确认扣减；false = 跳过
     */
    public boolean confirmBegin(String xid, long branchId, String businessKey) {
        TccFence exist = tccFenceMapper.selectByXidBranch(xid, branchId);
        if (exist == null) {
            // 空 Confirm：Try 未到达。占位为 CONFIRMED，后续迟到的 Try 会被悬挂逻辑拒绝（但它已确认，无库存可扣）
            log.warn("[TCC] 空 Confirm：Try 未到达，记录占位。xid={}, branch={}", xid, branchId);
            saveFenceQuietly(xid, branchId, "confirm", STATUS_CONFIRMED, businessKey);
            return false;
        }
        if (exist.getStatus() == STATUS_ROLLBACKED) {
            log.warn("[TCC] 分支已回滚，Confirm 忽略。xid={}, branch={}", xid, branchId);
            return false;   // 已回滚 → 不能再确认
        }
        if (exist.getStatus() == STATUS_CONFIRMED) {
            log.info("[TCC] Confirm 重复调用，幂等跳过。xid={}, branch={}", xid, branchId);
            return false;
        }
        return true;   // TRYING / COMMITTED → 正常确认
    }

    /** Confirm 业务成功：置为 CONFIRMED（终态） */
    public void confirmCommit(String xid, long branchId) {
        tccFenceMapper.updateStatus(xid, branchId, STATUS_CONFIRMED, "confirm",
                List.of(STATUS_TRYING, STATUS_COMMITTED));
    }

    /**
     * Cancel 开始：区分"空回滚"与"正常回滚"。
     * @return true = 需要执行业务回滚；false = 跳过（空回滚 / 幂等 / 已确认）
     */
    public boolean cancelBegin(String xid, long branchId, String businessKey) {
        TccFence exist = tccFenceMapper.selectByXidBranch(xid, branchId);
        if (exist == null) {
            // 防空回滚：Try 还没到。占位为已回滚，之后迟到的 Try 会被悬挂检测拦住。
            // 并发下两个 Cancel 同时到这里 → 一个成功、一个撞唯一键，两边都返回 false，结果一致。
            log.warn("[TCC] 空回滚：Try 未到达，记录占位。xid={}, branch={}", xid, branchId);
            saveFenceQuietly(xid, branchId, "cancel", STATUS_ROLLBACKED, businessKey);
            return false;
        }
        if (exist.getStatus() == STATUS_ROLLBACKED) {
            log.info("[TCC] Cancel 重复调用，幂等跳过。xid={}, branch={}", xid, branchId);
            return false;
        }
        if (exist.getStatus() == STATUS_CONFIRMED) {
            log.warn("[TCC] 分支已 Confirm，拒绝回滚。xid={}, branch={}", xid, branchId);
            return false;   // ★ 已确认出库，不能再回滚
        }
        return true;   // TRYING / COMMITTED → 正常回滚
    }

    /** Cancel 业务回滚成功：标记为"已回滚"（CONFIRMED 不在期望集合内，无法覆盖） */
    public void cancelCommit(String xid, long branchId) {
        tccFenceMapper.updateStatus(xid, branchId, STATUS_ROLLBACKED, "cancel",
                List.of(STATUS_TRYING, STATUS_COMMITTED));
    }


    private TccFence buildFence(String xid, long branchId, String action,
                                int status, String businessKey, String itemsJson) {
        TccFence fence = new TccFence();
        fence.setId(IdWorker.getId());
        fence.setXid(xid);
        fence.setBranchId(branchId);
        fence.setActionName(action);
        fence.setStatus(status);
        fence.setBusinessKey(businessKey);
        fence.setItemsJson(itemsJson);
        return fence;
    }




    private void saveFenceQuietly(String xid, long branchId, String action,
                                  int status, String businessKey) {
        try {
            tccFenceMapper.insert(
                    buildFence(xid, branchId, action, status, businessKey, null));
        } catch (DuplicateKeyException e) {
            log.info("[TCC] fence 记录已存在（并发），忽略。xid={}, branch={}", xid, branchId);
        }
    }

    /** 列出长时间停留在 Try 阶段的分支（含 items），供悬挂兜底任务重试 Cancel */
    public List<StockTccReq> listStaleBranches(int minutes, int limit) {
        return tccFenceMapper.selectStaleTry(
                        List.of(STATUS_TRYING, STATUS_COMMITTED), minutes, limit)
                .stream()
                .map(f -> new StockTccReq(f.getBusinessKey(),
                        JsonUtils.parse(f.getItemsJson(),
                                new TypeReference<List<LockItemDTO>>() {})))
                .toList();
    }
}



















































































//package com.shop.inventory.tcc;
//
//import com.baomidou.mybatisplus.core.toolkit.IdWorker;
//import com.shop.inventory.mapper.TccFenceMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.dao.DuplicateKeyException;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
///**
// * TCC 事务控制：解决 空回滚 / 悬挂 / 幂等 三大问题。
// * 状态机：0(TRYING) → 1(COMMITTED) → 2(ROLLBACKED)
// */
//@Slf4j
//@Service
//public class TccFenceService {
//
//    //try执行中
//    public static final int STATUS_TRYING = 0;
//    //try执行完成，之后可以去confirm
//    public static final int STATUS_COMMITTED = 1;
//    //已经回滚完成
//    public static final int STATUS_ROLLBACKED = 2;
//
//    private final TccFenceMapper tccFenceMapper;
//
//    public TccFenceService(TccFenceMapper tccFenceMapper) {
//
//
//        this.tccFenceMapper = tccFenceMapper;
//    }
//
//    /**
//     * Try 开始：占位 + 检查是否"悬挂"。
//     * @return true = 可以执行业务 Try；false = **悬挂**，必须拒绝执行
//     */
////    1. 查询该全局事务分支的防护记录
////    2. 如果记录存在并且状态是ROLLBACKED(2) → 悬挂：Cancel 已经跑完，Try 才过来，返回false拒绝执行业务
////    3. 如果记录存在但不是回滚状态：属于重试请求，幂等放行返回true
////    4. 如果记录不存在：插入新 fence 记录，状态TRYING(0)，返回true允许执行业务 Try
////    悬挂场景：网络乱序，Cancel 请求先到达，Try 请求晚到。如果不拦截，Try 会继续执行业务，造成数据不一致
//
//    //在这里 设置 为 xid 就是 orderNo 就是 businessKey
//    public boolean tryBegin(String xid, long branchId, String businessKey) {
////        首先去找tcc的数据库的表中有没有这个 try的记录
//        TccFence exist = tccFenceMapper.selectByXidBranch(xid, branchId);
//        if (exist != null) {
//            // 去检查 是什么情况！！！！ 如果是 status = 2 那么就说明 cancel 先执行了我靠 就不能再try了
//            if (exist.getStatus() == STATUS_ROLLBACKED) {
//                log.warn("[TCC] 检测到悬挂：Cancel 已先执行，拒绝 Try。xid={}, branch={}", xid, branchId);
//                return false;   //  防悬挂
//            }
//            // 如果 tcc的数据库的表中 有这个 记录了 ，说明 是幂等！！！！ 我靠直接放行
//            return true;        // 重复 Try → 幂等放行
//        }
//        //去写到 DB中 设置 status = 0
//        saveFence(xid, branchId, "try", STATUS_TRYING, businessKey);
//        return true;
//    }
//
//    /**
//     * Try 业务成功：标记为"已提交"。
//     * @return false = 并发下已被 Cancel 回滚（调用方需回滚自己的业务）
//     */
//    //try的业务成功 标记为已经提交
//    public boolean tryCommit(String xid, long branchId) {
//        //只有当状态为0 时，之后try已经执行完成------- 时，才允许去做更新 说明现在是已经提交 设置为1
//        int rows = tccFenceMapper.updateStatus(xid
//                , branchId, STATUS_COMMITTED, "try", List.of(STATUS_TRYING));
//        if (rows == 0) {
//            //如果更新失败， 说明状态不对 ， 不是0 那么就是 回滚了！！！！就不能做commit！！！不能改成1了
//            log.warn("[TCC] Try 提交时发现分支已被回滚，需回滚业务。xid={}, branch={}", xid, branchId);
//            return false;
//        }
//        return true;
//    }
//
//    /**
//     * Cancel 开始：区分"空回滚"与"正常回滚"。
//     * @return true = 需要执行业务回滚；false = **跳过**（空回滚 / 重复 Cancel 幂等）
//     */
//    public boolean cancelBegin(String xid, long branchId, String businessKey) {
//        //先来看这个记录中有没有try的记录啊
//        //先try之后，才能回滚！！！！
//        TccFence exist = tccFenceMapper.selectByXidBranch(xid, branchId);
//        if (exist == null) {
//            //这个就是属于空回滚了
//            // 防空回滚：Try 还没到 → 插一条"已回滚"占位，直接成功返回
//            log.warn("[TCC] 空回滚：Try 未到达，直接记录占位。xid={}, branch={}", xid, branchId);
//            //直接把status = 2 直接站位
//            saveFence(xid, branchId, "cancel", STATUS_ROLLBACKED, businessKey);
//            return false;
//        }
//        if (exist.getStatus() == STATUS_ROLLBACKED) {
//            //如果遇到了 status = 2 那就是幂等了，不能多次的回滚 直接 false
//            log.info("[TCC] Cancel 重复调用，幂等跳过。xid={}, branch={}", xid, branchId);
//            return false;   //  幂等
//        }
//
//        //现在才运行正常的回滚   --- 就是try正常执行了
//
//        return true;        // 正常回滚
//    }
//
//    /** Cancel 业务回滚成功：标记为"已回滚" */
//    public void cancelCommit(String xid, long branchId) {
//        //就是只有允许状态为 0,1 的时候 才能去回滚！！！
//        tccFenceMapper.updateStatus(xid, branchId, STATUS_ROLLBACKED, "cancel",
//                List.of(STATUS_TRYING, STATUS_COMMITTED));
//    }
//
//    //把记录写进到表中 ---- 只有try到来 / cancel 到来才能去执行 其他的就是去校验就行 可以读 不用写
//    private void saveFence(String xid, long branchId, String action, int status, String businessKey) {
//        TccFence fence = new TccFence();
//        fence.setId(IdWorker.getId());
//        fence.setXid(xid);
//        fence.setBranchId(branchId);
//        fence.setActionName(action);
//        fence.setStatus(status);
//        fence.setBusinessKey(businessKey);
//        try {
//            tccFenceMapper.insert(fence);
//        } catch (DuplicateKeyException e) {
//            // 并发下已被其它线程插入，忽略即可（唯一键兜底）
//            log.info("[TCC] fence 记录已存在（并发），忽略。xid={}, branch={}", xid, branchId);
//        }
//    }
//}