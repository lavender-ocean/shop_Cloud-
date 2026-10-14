////package com.shop.gateway.filter;
////
////import cn.hutool.core.text.AntPathMatcher;
////import constant.SecurityConstants;
////import io.jsonwebtoken.Claims;
////import io.jsonwebtoken.ExpiredJwtException;
////import lombok.RequiredArgsConstructor;
////import org.springframework.cloud.gateway.filter.GatewayFilterChain;
////import org.springframework.cloud.gateway.filter.GlobalFilter;
////import org.springframework.core.Ordered;
////import org.springframework.core.io.buffer.DataBuffer;
////import org.springframework.http.HttpStatus;
////import org.springframework.http.MediaType;
////import org.springframework.http.server.reactive.ServerHttpRequest;
////import org.springframework.http.server.reactive.ServerHttpResponse;
////import org.springframework.stereotype.Component;
////import org.springframework.util.StringUtils;
////import org.springframework.web.server.ServerWebExchange;
////import reactor.core.publisher.Mono;
////import result.Result;
////import result.ResultCode;
////import security.JwtUtils;
////import util.JsonUtils;
////
////import java.nio.charset.StandardCharsets;
////import java.util.List;
////
////
////@Component
////@RequiredArgsConstructor
////public class AuthGlobalFilter implements GlobalFilter, Ordered {
////
////    // ant路径匹配器，支持通配符 /**
////    private static final AntPathMatcher MATCHER = new AntPathMatcher();
////
////    //来做白名单
////    //登陆，刷新token令牌，支付回调不需要拦截
////    private static final List<String> WHITELIST = List.of(
////            "/api/auth/login",
////            "/api/auth/refresh",
////            "/api/payment/callback",
////            "/actuator/**"
////    );
////
////    /**
////     * 判断当前路径是否属于白名单，支持ant通配符如 /actuator/**
////     */
////    private boolean isWhitelist(String path) {
////        return WHITELIST.stream().anyMatch(p -> MATCHER.match(p, path));
////    }
////
////    private final JwtUtils jwtUtils;
////
////    /**
////     * 统一返回：401状态码 + 项目标准Result JSON响应
////     * 拦截不往下游转发，请求在这里直接结束
////     */
////    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
////        ServerHttpResponse response = exchange.getResponse();
////        // http状态码401 Unauthorized
////        response.setStatusCode(HttpStatus.UNAUTHORIZED);
////        // 设置响应类型为json
////        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
////        // 把Result对象序列化为json字符串
////        String body = JsonUtils.toJson(Result.fail(code));
////        // WebFlux要求返回DataBuffer字节缓冲区
////        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
////        // 把buffer写入response，返回Mono<Void>，任务结束，不继续过滤器链
////        return response.writeWith(Mono.just(buffer));
////    }
////
////
////    @Override
////    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
////        //1.先拿到request和path
////        ServerHttpRequest request = exchange.getRequest();
////        String path = request.getURI().getPath();
////
////        //2.强制删除外部传来的内部用户头！安全核心！
////        ServerHttpRequest.Builder builder = request.mutate()
////                .headers(h -> {
////                    h.remove(SecurityConstants.HEADER_USER_ID);
////                    h.remove(SecurityConstants.HEADER_USER_NAME);
////                    h.remove(SecurityConstants.HEADER_USER_TYPE);
////                });
////
////        //3.白名单接口直接放行
////        if (isWhitelist(path)) {
////            // 已经把伪造头删掉，直接放行走后面过滤器
////            return chain.filter(exchange.mutate().request(builder.build()).build());
////        }
////
////        //4.获取Authorization请求头
////        String auth = request.getHeaders().getFirst(SecurityConstants.AUTH_HEADER);
////        // 判断头是否存在，并且以 Bearer 开头，格式：Bearer eyJxxxxxxx
////        if (!StringUtils.hasText(auth) || !auth.startsWith(SecurityConstants.BEARER_PREFIX)) {
////            // 返回401未授权
////            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
////        }
////
////        //5.截取掉 Bearer 前缀，拿到纯token字符串
////        String token = auth.substring(SecurityConstants.BEARER_PREFIX.length());
////
////        //6.解析、校验JWT
////        Claims claims;
////        try {
////            claims = jwtUtils.parse(token);
////        } catch (ExpiredJwtException e) {
////            // token过期异常
////            return unauthorized(exchange, ResultCode.TOKEN_EXPIRED);
////        } catch (Exception e) {
////            // 签名篡改、格式错误等全部算token非法
////            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
////        }
////
////        // 7.解析成功，写入可信内部请求头，转发下游微服务
////        ServerHttpRequest mutated = builder
////                .header(SecurityConstants.HEADER_USER_ID, String.valueOf(claims.get("userId")))
////                .header(SecurityConstants.HEADER_USER_NAME, String.valueOf(claims.get("username")))
////                .header(SecurityConstants.HEADER_USER_TYPE, String.valueOf(claims.get("userType")))
////                .build();
////
////        // 使用修改后的request继续执行过滤器链，转发到shop‑auth / shop‑user等下游服务
////        return chain.filter(exchange.mutate().request(mutated).build());
////
////
////    }
////
////    @Override
////    public int getOrder() {
////        return -100;
////    }
////}
//
//package com.shop.gateway.filter;
//
//import constant.RedisKeyConstants;
//import constant.SecurityConstants;
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.ExpiredJwtException;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.core.Ordered;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.stereotype.Component;
//import org.springframework.util.AntPathMatcher;
//import org.springframework.util.StringUtils;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//import result.Result;
//import result.ResultCode;
//import security.JwtUtils;
//import util.JsonUtils;
//
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//
//@Component
//public class AuthGlobalFilter implements GlobalFilter, Ordered {
//
//    //ant路径匹配器
//    private static final AntPathMatcher MATCHER = new AntPathMatcher();
//    //白名单！！！
//    private static final List<String> WHITELIST = List.of(
//            "/api/auth/login", "/api/auth/refresh", "/api/payment/callback", "/actuator/**");
//
//    private final JwtUtils jwtUtils;
//    private final ReactiveStringRedisTemplate redisTemplate;
//
//    public AuthGlobalFilter(JwtUtils jwtUtils, ReactiveStringRedisTemplate redisTemplate) {
//        this.jwtUtils = jwtUtils;
//        this.redisTemplate = redisTemplate;
//    }
//
//    //网关的过滤器  响应式WebFlux
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        //先用exchange来拿到request
//        ServerHttpRequest request = exchange.getRequest();
//        //再用request来拿到path
//        String path = request.getURI().getPath();
//
//
////        自己放置这些信息，不相信前端传入的
//        //`mutate()` 返回一个**Builder 建造者对象**。
//        //把原来旧的 request 复制一份，你可以对 builder 增删 header、path、query，
//        // 最后调用 `.build()` 生成**全新的 ServerHttpRequest 实例**。
//        //> 原 request 保持不变，WebFlux 设计上对象不可变，防止多线程并发修改问题。
//        ServerHttpRequest.Builder builder = request.mutate()
//                .headers(h -> {
//                    h.remove(SecurityConstants.HEADER_USER_ID);
//                    h.remove(SecurityConstants.HEADER_USER_NAME);
//                    h.remove(SecurityConstants.HEADER_USER_TYPE);
//                });
//
////        查看是否在白名单里面
//        if (isWhitelist(path)) {
//            return chain.filter(exchange.mutate().request(builder.build()).build());
//        }
//
//        //从请求request中拿到auth
//        String auth = request.getHeaders().getFirst(SecurityConstants.AUTH_HEADER);
//        if (!StringUtils.hasText(auth) || !auth.startsWith(SecurityConstants.BEARER_PREFIX)) {
//            //Bearer来由前端来放置
//            //没有就是不允许请求继续进行
//            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
//        }
//        //拿到令牌
//        String token = auth.substring(SecurityConstants.BEARER_PREFIX.length());
//
//        Claims claims;
//        try {
//            claims = jwtUtils.parse(token);
//        } catch (ExpiredJwtException e) {
//            return unauthorized(exchange, ResultCode.TOKEN_EXPIRED);
//        } catch (Exception e) {
//            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
//        }
//
//        //- `exchange`（ServerWebExchange）同样不可变；
//        //- `exchange.mutate()` 拿到 Exchange 的 builder；
//        //- `.request(mutated)`：把刚刚构造好的**新 request 设置进 exchange**；
//        //- `.build()`：生成完整全新的 `ServerWebExchange`。
//        //
//        //>
//        //> 网关过滤器不能直接修改老 exchange，必须构造新 exchange 交给 chain。
//
//        //检查是否在黑名单里面
//        return redisTemplate.hasKey(RedisKeyConstants.TOKEN_BLACKLIST + claims.getId())
//                .defaultIfEmpty(false)
//                .flatMap(blacklisted -> {
//                    if (Boolean.TRUE.equals(blacklisted)) {
//                        //在黑名单中就直接返回，不允许继续访问
//                        return unauthorized(exchange, ResultCode.TOKEN_INVALID);
//                    }
////                    正常放行通过
//                    ServerHttpRequest mutated = builder
//                            .header(SecurityConstants.HEADER_USER_ID, String.valueOf(claims.get("userId")))
//                            .header(SecurityConstants.HEADER_USER_NAME, String.valueOf(claims.get("username")))
//                            .header(SecurityConstants.HEADER_USER_TYPE, String.valueOf(claims.get("userType")))
//                            .build();
//                    return chain.filter(exchange.mutate().request(mutated).build());
//                });
//    }
//
//    //判断路径是不是在白名单里面 在---->放行
//    private boolean isWhitelist(String path) {
//        //用ant路径匹配器来做匹配
//        return WHITELIST.stream().anyMatch(p -> MATCHER.match(p, path));
//    }
//
//    //Mono来提交规则 没有经得住校验的，就直接response返回，而不是chain.filter能继续传下去
//    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
//        ServerHttpResponse response = exchange.getResponse();
//        response.setStatusCode(HttpStatus.UNAUTHORIZED);
//        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
//        String body = JsonUtils.toJson(Result.fail(code));
//        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
//        return response.writeWith(Mono.just(buffer));
//    }
//
//    //设置执行顺序的优先级
//    @Override
//    public int getOrder() {
//        return -100;
//    }
//}




package com.shop.gateway.filter;

import constant.RedisKeyConstants;
import constant.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import result.Result;
import result.ResultCode;
import security.JwtUtils;
import util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> WHITELIST = List.of(
            "/api/auth/login", "/api/auth/refresh", "/api/payment/callback", "/actuator/**");

    private final JwtUtils jwtUtils;
    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthGlobalFilter(JwtUtils jwtUtils, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ① 无条件剥离入站伪造的内部头（含权限头！）
        ServerHttpRequest.Builder builder = request.mutate()
                .headers(h -> {
                    h.remove(SecurityConstants.HEADER_USER_ID);
                    h.remove(SecurityConstants.HEADER_USER_NAME);
                    h.remove(SecurityConstants.HEADER_USER_TYPE);
                    h.remove(SecurityConstants.HEADER_USER_PERMS);   // ★ 权限头也要剥
                });

        if (isWhitelist(path)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String auth = request.getHeaders().getFirst(SecurityConstants.AUTH_HEADER);
        if (!StringUtils.hasText(auth) || !auth.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
        }
        String token = auth.substring(SecurityConstants.BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtUtils.parse(token);
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, ResultCode.TOKEN_EXPIRED);
        } catch (Exception e) {
            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
        }

        return redisTemplate.hasKey(RedisKeyConstants.TOKEN_BLACKLIST + claims.getId())
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("[网关] 黑名单校验失败（Redis 异常），降级放行: {}", e.getMessage());
                    return Mono.just(false);          // fail-open：Redis 故障不该让全站瘫痪
                })
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(exchange, ResultCode.TOKEN_INVALID);
                    }
                    builder.header(SecurityConstants.HEADER_USER_ID, String.valueOf(claims.get("userId")));
                    builder.header(SecurityConstants.HEADER_USER_NAME, String.valueOf(claims.get("username")));
                    builder.header(SecurityConstants.HEADER_USER_TYPE, String.valueOf(claims.get("userType")));

                    // ★ 权限：从 token claim 注入（有权限才注入；无权限则不注入，下游兜底结果一致）
                    String perms = joinPermissions(claims.get("perms"));
                    if (StringUtils.hasText(perms)) {
                        builder.header(SecurityConstants.HEADER_USER_PERMS, perms);
                    }
                    return chain.filter(exchange.mutate().request(builder.build()).build());
                });
    }

    @SuppressWarnings("unchecked")
    private String joinPermissions(Object perms) {
        if (!(perms instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        return String.join(",", ((List<Object>) list).stream().map(String::valueOf).toList());
    }

    private boolean isWhitelist(String path) {
        return WHITELIST.stream().anyMatch(p -> MATCHER.match(p, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = JsonUtils.toJson(Result.fail(code));
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}