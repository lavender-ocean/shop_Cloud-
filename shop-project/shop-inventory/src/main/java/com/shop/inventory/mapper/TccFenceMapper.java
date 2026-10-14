package com.shop.inventory.mapper;

import com.shop.inventory.tcc.TccFence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TccFenceMapper {

    TccFence selectByXidBranch(@Param("xid") String xid, @Param("branchId") Long branchId);

    /** 锁定读：拿到最新已提交状态，避开 RR 快照读看不到并发提交的问题 */
    TccFence selectByXidBranchForUpdate(@Param("xid") String xid, @Param("branchId") Long branchId);

    int insert(TccFence fence);

    int updateStatus(@Param("xid") String xid,
                     @Param("branchId") Long branchId,
                     @Param("status") Integer status,
                     @Param("actionName") String actionName,
                     @Param("expectedStatuses") List<Integer> expectedStatuses);

    /** 扫描"长时间停留在 Try 阶段"的分支，供悬挂兜底任务使用 */
    List<TccFence> selectStaleTry(@Param("statuses") List<Integer> statuses,
                                  @Param("minutes") int minutes,
                                  @Param("limit") int limit);
}


















































//package com.shop.inventory.mapper;
//
//import com.shop.inventory.tcc.TccFence;
//import org.apache.ibatis.annotations.Param;
//
//import java.util.List;
//
////这是 TCC‑Fence 事务防坑表
//// 作用：解决 TCC 三大问题：空回滚、悬挂、幂等。
//public interface TccFenceMapper {
//
//    // 根据全局XID + 分支事务ID查询一条fence记录
//    TccFence selectByXidBranch(@Param("xid") String xid, @Param("branchId") Long branchId);
//
//    // 插入一条fence记录（Try阶段写入）
//    int insert(TccFence fence);
//
//
//
//    /** 条件更新：仅当当前状态在 expectedStatuses 内才改（防并发覆盖） */
//    //status in （） ---- 这样来取判断范围
//    int updateStatus(@Param("xid") String xid,
//                     @Param("branchId") Long branchId,
//                     @Param("status") Integer status,
//                     @Param("actionName") String actionName,        // ← 补上
//                     @Param("expectedStatuses") List<Integer> expectedStatuses);
//
//}