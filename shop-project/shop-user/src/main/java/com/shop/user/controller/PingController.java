package com.shop.user.controller;


import exception.BizException;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import result.Result;
import result.ResultCode;
import security.RequirePermission;


@RefreshScope // 核心！开启这个Bean的配置热刷新
@Validated
@RestController
@RequestMapping("/api/user")
public class PingController {


    @Value("${demo.message:default}")
    private String demoMessage;

    @GetMapping("/config")
    public Result<String> config() {
        return Result.success(demoMessage);
    }


    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    /** 验证业务异常 → 规范错误码 */
    @GetMapping("/biz-error")
    public Result<Void> bizError() {
        throw new BizException(ResultCode.STOCK_NOT_ENOUGH);
    }

    /** 验证系统异常 → 兜底 10999，且不泄露堆栈 */
    @GetMapping("/sys-error")
    public Result<Void> sysError() {
        throw new RuntimeException("boom");
    }

    /** 验证参数校验异常 → 10001 */
    @GetMapping("/param")
    public Result<Void> paramCheck(@RequestParam @Min(value = 1, message = "num 必须大于 0") Integer num) {
        return Result.success();
    }


    /** admin 有 sys:user:list → 放行 */
    @GetMapping("/admin-only")
    @RequirePermission("sys:user:list")
    public Result<String> adminOnly() {
        return Result.success("you have sys:user:list");
    }

    /** 谁都没有这个权限 → 拒绝（10003） */
    @GetMapping("/need-unknown")
    @RequirePermission("not:exist")
    public Result<String> needUnknown() {
        return Result.success("should not reach");
    }


}