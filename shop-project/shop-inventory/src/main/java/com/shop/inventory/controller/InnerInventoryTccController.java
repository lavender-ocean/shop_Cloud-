package com.shop.inventory.controller;

import com.shop.api.dto.StockTccReq;
import com.shop.inventory.tcc.TccFenceService;
import com.shop.inventory.tcc.TccInventoryService;
import org.springframework.web.bind.annotation.*;
import result.Result;

import java.util.List;

/** 内部 TCC 接口（不经网关） */
@RestController
@RequestMapping("/inner/inventory/tcc")
public class InnerInventoryTccController {

    private static final int STALE_MINUTES = 10;

    private final TccInventoryService tccInventoryService;
    private final TccFenceService tccFenceService;

    public InnerInventoryTccController(TccInventoryService tccInventoryService,
                                       TccFenceService tccFenceService) {
        this.tccInventoryService = tccInventoryService;
        this.tccFenceService = tccFenceService;
    }

    @PostMapping("/try")
    public Result<Void> tryLock(@RequestBody StockTccReq req) {
        tccInventoryService.tryLock(req);
        return Result.success();
    }

    @PostMapping("/confirm")
    public Result<Void> confirm(@RequestBody StockTccReq req) {
        tccInventoryService.confirm(req);
        return Result.success();
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody StockTccReq req) {
        tccInventoryService.cancel(req);
        return Result.success();
    }

    /** 供悬挂兜底任务拉取"长时间停在 Try 阶段"的分支（含 items） */
    @GetMapping("/stale")
    public Result<List<StockTccReq>> staleBranches(@RequestParam(defaultValue = "100") int limit) {
        return Result.success(tccFenceService.listStaleBranches(STALE_MINUTES, limit));
    }
}