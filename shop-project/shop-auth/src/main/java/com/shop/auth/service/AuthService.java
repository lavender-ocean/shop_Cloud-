//package com.shop.auth.service;
//
//import cn.hutool.crypto.digest.BCrypt;
//import com.shop.auth.dto.LoginDTO;
//import com.shop.auth.entity.SysUser;
//import com.shop.auth.mapper.SysUserMapper;
//import com.shop.auth.vo.LoginVO;
//import exception.BizException;
//import org.springframework.stereotype.Service;
//import result.ResultCode;
//import security.JwtUtils;
//
//@Service
//public class AuthService {
//
//    private final SysUserMapper sysUserMapper;
//    private final JwtUtils jwtUtils;
//
//    public AuthService(SysUserMapper sysUserMapper, JwtUtils jwtUtils) {
//        this.sysUserMapper = sysUserMapper;
//        this.jwtUtils = jwtUtils;
//    }
//
//    public LoginVO login(LoginDTO dto) {
//        SysUser user = sysUserMapper.selectByUsername(dto.username());
//        // 统一报"用户名或密码错误"，不区分是哪一个，避免账号枚举
//        if (user == null || !BCrypt.checkpw(dto.password(), user.getPassword())) {
//            throw new BizException(ResultCode.LOGIN_FAILED);
//        }
//        if (user.getStatus() == null || user.getStatus() != 1) {
//            throw new BizException(ResultCode.ACCOUNT_DISABLED);
//        }
//
//        String accessToken = jwtUtils.createAccessToken(user.getId(), user.getUsername(), user.getUserType());
//        String refreshToken = jwtUtils.createRefreshToken(user.getId(), user.getUsername(), user.getUserType());
//        sysUserMapper.updateLastLoginTime(user.getId());
//
//        return new LoginVO(accessToken, refreshToken, user.getId(), user.getUsername(), user.getUserType());
//    }
//}



package com.shop.auth.service;

import cn.hutool.crypto.digest.BCrypt;
import com.shop.auth.dto.LoginDTO;
import com.shop.auth.entity.SysPermission;
import com.shop.auth.entity.SysUser;
import com.shop.auth.mapper.SysPermissionMapper;
import com.shop.auth.mapper.SysRoleMapper;
import com.shop.auth.mapper.SysUserMapper;
import com.shop.auth.vo.LoginVO;
import com.shop.auth.vo.MenuVO;
import com.shop.auth.vo.UserInfoVO;
import constant.RedisKeyConstants;
import constant.SecurityConstants;
import context.LoginUser;
import context.UserContext;
import exception.BizException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import result.ResultCode;
import security.JwtProperties;
import security.JwtUtils;
import util.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final SysPermissionMapper sysPermissionMapper;

    private final SysRoleMapper sysRoleMapper;


    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectByUsername(dto.username());
        if (user == null || !BCrypt.checkpw(dto.password(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        sysUserMapper.updateLastLoginTime(user.getId());

        LoginVO loginVO = issueTokens(user.getId(), user.getUsername(), user.getUserType());

        cachePermissions(user.getId());



        return loginVO;
    }

    /** 刷新：校验 refresh 有效性 + 旋转（旧 refresh 立即失效） */
    public LoginVO refresh(String refreshToken) {
        Claims claims = parseQuietly(refreshToken);
        if (claims == null || !"refresh".equals(String.valueOf(claims.get("tokenType")))) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(String.valueOf(claims.get("userId")));
        String storedJti = redisTemplate.opsForValue().get(RedisKeyConstants.REFRESH_TOKEN + userId);
        if (storedJti == null || !storedJti.equals(claims.getId())) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        String username = String.valueOf(claims.get("username"));
        Integer userType = Integer.valueOf(String.valueOf(claims.get("userType")));

        LoginVO loginVO = issueTokens(userId, username, userType);

        cachePermissions(userId);

        return loginVO;
    }

    /** 登出：access 拉黑 + refresh 失效 */
    public void logout(String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return;
        }
        Claims claims = parseQuietly(authHeader.substring(SecurityConstants.BEARER_PREFIX.length()));
        if (claims == null) {
            return;
        }
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set(
                    RedisKeyConstants.TOKEN_BLACKLIST + claims.getId(), "1", Duration.ofMillis(remaining));
        }
        Long userId = Long.valueOf(String.valueOf(claims.get("userId")));
        redisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN + userId);

        redisTemplate.delete(RedisKeyConstants.USER_PERMISSIONS + userId);

    }

    private LoginVO issueTokens(Long userId, String username, Integer userType) {
        String accessToken = jwtUtils.createAccessToken(userId, username, userType);
        String refreshToken = jwtUtils.createRefreshToken(userId, username, userType);
        redisTemplate.opsForValue().set(
                RedisKeyConstants.REFRESH_TOKEN + userId,
                jwtUtils.parse(refreshToken).getId(),
                jwtProperties.getRefreshTokenTtl());
        return new LoginVO(accessToken, refreshToken, userId, username, userType);
    }

    private Claims parseQuietly(String token) {
        try {
            return jwtUtils.parse(token);
        } catch (Exception e) {
            return null;
        }
    }



    public UserInfoVO getUserInfo(Long userId) {
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(userId);
        List<SysPermission> perms = sysPermissionMapper.selectByUserId(userId);
        List<String> codes = perms.stream().map(SysPermission::getPermCode).distinct().toList();

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(userId);
        LoginUser loginUser = UserContext.get();
        if (loginUser != null) {
            vo.setUsername(loginUser.getUsername());
            vo.setUserType(loginUser.getUserType());
        }
        vo.setRoles(roles);
        vo.setPermissions(codes);
        vo.setMenus(buildMenuTree(perms));

        cachePermissions(userId);


        return vo;
    }

    /** 由 type=1 的菜单节点构建树 */
    private List<MenuVO> buildMenuTree(List<SysPermission> perms) {
        Map<Long, List<SysPermission>> byParent = perms.stream()
                .filter(p -> p.getType() != null && p.getType() == 1)
                .collect(Collectors.groupingBy(
                        p -> p.getParentId() == null ? 0L : p.getParentId()));
        return buildMenus(0L, byParent);
    }

    private List<MenuVO> buildMenus(Long parentId, Map<Long, List<SysPermission>> byParent) {
        List<MenuVO> result = new ArrayList<>();
        for (SysPermission p : byParent.getOrDefault(parentId, List.of())) {
            MenuVO vo = new MenuVO();
            vo.setId(p.getId());
            vo.setName(p.getPermName());
            vo.setPath(p.getPath());
            vo.setComponent(p.getComponent());
            vo.setIcon(p.getIcon());
            vo.setChildren(buildMenus(p.getId(), byParent));
            result.add(vo);
        }
        return result;
    }




    /** 权限码写入 Redis，供各服务做服务端鉴权 */
    private void cachePermissions(Long userId) {
        List<String> codes = sysPermissionMapper.selectByUserId(userId).stream()
                .map(SysPermission::getPermCode).distinct().toList();
        redisTemplate.opsForValue().set(
                RedisKeyConstants.USER_PERMISSIONS + userId,
                JsonUtils.toJson(codes),
                Duration.ofMinutes(30));
    }
}














