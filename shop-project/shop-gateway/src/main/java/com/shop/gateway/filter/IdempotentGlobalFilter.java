package com.shop.gateway.filter;

import constant.RedisKeyConstants;
import constant.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import result.Result;
import result.ResultCode;
import util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 幂等 token 预检：对需要幂等的写接口，要求携带有效 token，否则快速拒绝。
 * 只"检查存在"，不消费 —— 真正的原子消费在服务层 @Idempotent。
 */

// 模型：先申请幂等 Token，后消费 Token（预约令牌模式）

// 流程：**申请 Token → 携带 Token 发起写请求 → 业务成功消费 Token**
//1. 前端主动调用接口获取幂等 token（申请，存入 Redis）
//2. 写请求 Header 带上 token 过网关预检
//3. 后端业务执行成功，删除 Redis key（消费令牌）

//> 注意：网关只做**存在性预检，不消费**；消费发生在业务服务。
@Slf4j
@Component
public class IdempotentGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    //对于需要进行幂等校验的路径进行检查是否携带了对应的token！！！
    /** 需要携带幂等 token 的路径（仅对 POST 生效） */
    private static final List<String> PATHS = List.of("/api/order");

    private final ReactiveStringRedisTemplate redisTemplate;

    public IdempotentGlobalFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //同样拿到request和path
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

//      判断当前请求是否需要做幂等校验
        //如果这个路径被放置在了  PATHS中，那就是需要去做幂等校验
        boolean needToken = HttpMethod.POST.equals(request.getMethod())
                && PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
        if (!needToken) {
            //没有token直接放行 --- 不需要幂等
            return chain.filter(exchange);
        }

        //开始幂等校验
        //从请求头中获取幂等令牌
        String token = request.getHeaders().getFirst(SecurityConstants.HEADER_IDEMPOTENT_TOKEN);
        if (!StringUtils.hasText(token)) {
            //没有携带幂等令牌，直接拒绝
            return reject(exchange, "缺少幂等 token");
        }
        // 校验幂等token是否存在于Redis：存在说明是首次合法请求，放行；不存在判定为重复提交，拒绝
        return redisTemplate.hasKey(RedisKeyConstants.IDEMPOTENT + token)
                //默认返回false
                //Redis响应为空时兜底返回false，避免空信号导致流程异常
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("[网关] 黑名单校验失败（Redis 异常），降级放行: {}", e.getMessage());
                    return Mono.just(false);          // fail-open：Redis 故障不该让全站瘫痪
                })
                .flatMap(exists -> exists ?
                        chain.filter(exchange) : reject(exchange, "请勿重复提交"));
    }

    //拒绝访问
    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.CONFLICT);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = JsonUtils.toJson(Result.fail(ResultCode.DUPLICATE_SUBMIT, message));
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -90;   // 优先级 在 AuthGlobalFilter(-100) 之后
    }
}