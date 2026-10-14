package com.shop.auth.controller;


import com.shop.auth.dto.LoginDTO;
import com.shop.auth.dto.RefreshDTO;
import com.shop.auth.vo.LoginVO;
import com.shop.auth.service.AuthService;
import com.shop.auth.vo.UserInfoVO;
import constant.SecurityConstants;
import context.LoginUser;
import context.UserContext;
import exception.BizAssert;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import result.Result;
import result.ResultCode;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {

        this.authService = authService;
    }

    //登陆
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {

        return Result.success(authService.login(dto));
    }

    /** 验证"服务端上下文"：需要网关透传 X-User-* 头 */
//    @GetMapping("/me")
//    public Result<LoginUser> me() {
//
//        return Result.success(UserContext.get());
//    }

    //刷新令牌
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody RefreshDTO dto) {
        return Result.success(authService.refresh(dto.refreshToken()));
    }

    //用户登出
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = SecurityConstants.AUTH_HEADER, required = false) String auth) {
        authService.logout(auth);
        return Result.success();
    }

    //获取用户信息
    @GetMapping("/userinfo")
    public Result<UserInfoVO> userinfo() {
        //从ThreadLocal来取出信息
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
        return Result.success(authService.getUserInfo(userId));
    }

}