package security;

import com.fasterxml.jackson.core.type.TypeReference;
import constant.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import util.JsonUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 从 Redis 读取用户权限码（auth 登录时预热） */

// 核心：这是**缓存设计（读多写少场景）

//> 用户登录之后，权限不是每次鉴权都查询 MySQL，**把权限放到 Redis 做缓存，DB 作为数据源。**
//调用时机
//
//1. 用户登录成功：查询数据库，查出该用户的权限编码集合，
// 序列化 JSON，**写入 Redis 预热**（就是注释写的 `auth登录时预热`）
//2. 之后每次接口鉴权：直接调用 `PermissionLoader.load(userId)`，
// 读取 Redis 拿到权限码集合，**不去访问数据库**。

//给DB减压
public class PermissionLoader {

    private final StringRedisTemplate redisTemplate;

    public PermissionLoader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //问题！！！！！！！！！！！
    //1.没做redis的三大问题
    //2.用户登出之后必须删除redis的key 不能永久的存放在 redis中  ---- 已经ok auth中解决了


//    只有"主动登出"才删。以下都不走 logout：
//    token 过期、用户关页面、管理员禁用/删除用户、改密码 → 最长 30 分钟内仍持有旧权限。
//    更隐蔽的脱节：网关只做"验签 + 黑名单"，不查用户状态。
//    所以管理员把用户禁用后，他手里的 access token 仍然有效，
//    能继续调接口直到过期（≤30 分钟）。要治，得让网关/服务能感知"账号状态"，或在禁用时把该用户所有 token 拉黑。


    //3.在更新权限之后，要删除redis的数据 ---->  那么现在就挂了！！！因为没有去查找DB去回填！！！！！！！！！
    public Set<String> load(Long userId) {
        if (userId == null) {
            //userId 是null 那都没登陆
            return Collections.emptySet();
        }
        //通过 redis去找有没有这个key
        String json = redisTemplate.opsForValue().get(RedisKeyConstants.USER_PERMISSIONS + userId);
        if (!StringUtils.hasText(json)) {
            return Collections.emptySet();
        }
        //把string转为list<String>
        List<String> codes = JsonUtils.parse(json, new TypeReference<List<String>>() {});
        return new HashSet<>(codes);
    }
}