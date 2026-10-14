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


    //登陆
    public LoginVO login(LoginDTO dto) {
        //根据前端传来的用户名去找到这个用户
        SysUser user = sysUserMapper.selectByUsername(dto.username());


        //去做密码的校验
//        安全设计：**无论账号不存在，还是密码错，抛出同一个异常`LOGIN_FAILED`**，不区分报错，防止攻击者枚举账号。
        //！！！！！！！！！！！！！！！！！！！！！！！！！


        if (user == null || !BCrypt.checkpw(dto.password(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        //判断校验用户的状态！是否已经被禁用
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        //更新最近登陆时间 --- 就是现在
        sysUserMapper.updateLastLoginTime(user.getId());

        //签发token accessToken+refreshToken！！！
        LoginVO loginVO = issueTokens(user.getId(), user.getUsername(), user.getUserType());

        //把用户的权限码写入redis中，方便做权限的校验判断！！！！！！！！！
        cachePermissions(user.getId());

        return loginVO;
    }

    /** 刷新：校验 refresh 有效性 + 旋转（旧 refresh 立即失效） */
    public LoginVO refresh(String refreshToken) {
        //解析refreshToken
        Claims claims = parseQuietly(refreshToken);
        //如果解析失败/token的类型不是refresh！！！就抛出异常：说登陆凭证无效
        if (claims == null || !"refresh".equals(String.valueOf(claims.get("tokenType")))) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        //从token中拿到userId
        Long userId = Long.valueOf(String.valueOf(claims.get("userId")));
        //从redis中存的key，拿到JTI
        String storedJti = redisTemplate.opsForValue().get(RedisKeyConstants.REFRESH_TOKEN + userId);
        if (storedJti == null || !storedJti.equals(claims.getId())) {
            //继续做JTI的校验
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        String username = String.valueOf(claims.get("username"));
        Integer userType = Integer.valueOf(String.valueOf(claims.get("userType")));

        //签发新的refreshToken和accessToken
        LoginVO loginVO = issueTokens(userId, username, userType);

        //继续存入用户的权限码
        cachePermissions(userId);

        return loginVO;
    }

    /** 登出：access 拉黑 + refresh 失效 */
    //用户登出时：要把accessToken拉黑，并且干掉refreshToken
    public void logout(String authHeader) {
        if (!StringUtils.hasText(authHeader) ||
                !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return;
        }
        //Bearer:令牌的持有者 拿着token！！！
        Claims claims = parseQuietly(authHeader.substring(SecurityConstants.BEARER_PREFIX.length()));
        if (claims == null) {
            return;
        }
        //计算剩余的有效时间 --- 就是说remaining = **这个 accessToken 还剩下多少毫秒才过期

        //为什么不能直接无脑拉黑存入 Redis？
        //accessToken 本身是**短期令牌**，比如有效期 30 分钟。
        //场景：用户的 accessToken 已经只剩 100ms 就要过期了，此时用户执行登出。
        //方案 A：**不做 remaining 判断，直接写入黑名单，永久有效（不设置 TTL）**
        //- 这条黑名单 key 会永远留在 Redis 里面，直到手动删。
        //大量用户登出，Redis 黑名单会无限堆积垃圾 key，内存越吃越多。
        //方案 B：**现在代码的做法：只存剩余存活时间作为 TTL**
        //> token 还没过期：把这个 jti 放入黑名单，TTL = token 剩余有效期。
        //> token 已经过期（remaining ≤0）：直接不往 Redis 写任何数据。JWT 本身已经失效，没必要拉黑。
        //JWT 自己已经过期之后，就算不进黑名单，网关鉴权直接就拒绝这个请求，**不需要再存 Redis 记录**。
        //> 核心：**黑名单 key 只需要存活到 token 原本过期的那一刻就够了，不需要永久保存。**


        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            //拉黑
            redisTemplate.opsForValue().set(
                    RedisKeyConstants.TOKEN_BLACKLIST + claims.getId()
                    , "1", Duration.ofMillis(remaining));
        }


        Long userId = Long.valueOf(String.valueOf(claims.get("userId")));

        //用户登出的时候，删除refreshToken和权限集合
        redisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN + userId);
        redisTemplate.delete(RedisKeyConstants.USER_PERMISSIONS + userId);

    }
    /** 签发 token：access 内嵌权限；refresh 不带权限（刷新时重查） */
    private LoginVO issueTokens(Long userId, String username, Integer userType) {
        List<String> permissions = loadPermissions(userId);

        String accessToken = jwtUtils.createAccessToken(userId, username, userType, permissions);
        String refreshToken = jwtUtils.createRefreshToken(userId, username, userType);
        redisTemplate.opsForValue().set(
                RedisKeyConstants.REFRESH_TOKEN + userId,
                jwtUtils.parse(refreshToken).getId(),
                jwtProperties.getRefreshTokenTtl());

        // 兜底通道：仍写 Redis（供"header 缺失"的内部调用使用），TTL 30 分钟
        cachePermissions(userId, permissions);
        return new LoginVO(accessToken, refreshToken, userId, username, userType);
    }

    private List<String> loadPermissions(Long userId) {
        return sysPermissionMapper.selectByUserId(userId).stream()
                .map(SysPermission::getPermCode).distinct().toList();
    }

    private void cachePermissions(Long userId, List<String> permissions) {
        redisTemplate.opsForValue().set(
                RedisKeyConstants.USER_PERMISSIONS + userId,
                JsonUtils.toJson(permissions),
                Duration.ofMinutes(30));
    }

    //签发新的accessToken，并且覆盖refreshToken
//    private LoginVO issueTokens(Long userId, String username, Integer userType) {
//        String accessToken = jwtUtils.createAccessToken(userId, username, userType,);
//        String refreshToken = jwtUtils.createRefreshToken(userId, username, userType);

//        Redis key：`refresh_token:{userId}`，存的是 JWT 的`jti`（JWT 唯一编号）。
//> 作用：刷新令牌的时候做校验；用户修改密码 / 登出直接删除这个 Redis key，refresh 立刻失效。

        //在生成全新的refreshToken之后，Redis 里，直接用**新 refresh 的 jti 覆盖原有 userId 对应的 value**\
        //每次刷新，直接set覆盖掉
//        redisTemplate.opsForValue().set(
//                RedisKeyConstants.REFRESH_TOKEN + userId, //每一次的key是一样的，所以可以正常的去覆盖掉
//                jwtUtils.parse(refreshToken).getId(),//拿到JTI
//                jwtProperties.getRefreshTokenTtl());
//        return new LoginVO(accessToken, refreshToken, userId, username, userType);
//    }

    //解析JWTtoken
    private Claims parseQuietly(String token) {
        try {
            return jwtUtils.parse(token);
        } catch (Exception e) {
            return null;
        }
    }


    //根据userId来获取出user全部的东西
    public UserInfoVO getUserInfo(Long userId) {
        //这个用户的角色编码
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(userId);
        //这个用户拥有的权限集合
        List<SysPermission> perms = sysPermissionMapper.selectByUserId(userId);

        List<String> codes = perms.stream().map(SysPermission::getPermCode).distinct().toList();

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(userId);
        //从UserContext中拿到loginUser登陆的用户的基本信息
        LoginUser loginUser = UserContext.get();
        if (loginUser != null) {
            vo.setUsername(loginUser.getUsername());
            vo.setUserType(loginUser.getUserType());
        }
        vo.setRoles(roles);
        vo.setPermissions(codes);
        vo.setMenus(buildMenuTree(perms));
        //把权限码写入redis
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














