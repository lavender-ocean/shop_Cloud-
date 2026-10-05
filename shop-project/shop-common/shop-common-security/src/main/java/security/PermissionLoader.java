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
public class PermissionLoader {

    private final StringRedisTemplate redisTemplate;

    public PermissionLoader(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Set<String> load(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        String json = redisTemplate.opsForValue().get(RedisKeyConstants.USER_PERMISSIONS + userId);
        if (!StringUtils.hasText(json)) {
            return Collections.emptySet();
        }
        List<String> codes = JsonUtils.parse(json, new TypeReference<List<String>>() {});
        return new HashSet<>(codes);
    }
}