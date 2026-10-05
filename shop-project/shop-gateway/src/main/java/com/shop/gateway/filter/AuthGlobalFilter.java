//package com.shop.gateway.filter;
//
//import cn.hutool.core.text.AntPathMatcher;
//import constant.SecurityConstants;
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.ExpiredJwtException;
//import lombok.RequiredArgsConstructor;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.core.Ordered;
//import org.springframework.core.io.buffer.DataBuffer;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.http.server.reactive.ServerHttpResponse;
//import org.springframework.stereotype.Component;
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
//
//@Component
//@RequiredArgsConstructor
//public class AuthGlobalFilter implements GlobalFilter, Ordered {
//
//    // ant路径匹配器，支持通配符 /**
//    private static final AntPathMatcher MATCHER = new AntPathMatcher();
//
//    //来做白名单
//    //登陆，刷新token令牌，支付回调不需要拦截
//    private static final List<String> WHITELIST = List.of(
//            "/api/auth/login",
//            "/api/auth/refresh",
//            "/api/payment/callback",
//            "/actuator/**"
//    );
//
//    /**
//     * 判断当前路径是否属于白名单，支持ant通配符如 /actuator/**
//     */
//    private boolean isWhitelist(String path) {
//        return WHITELIST.stream().anyMatch(p -> MATCHER.match(p, path));
//    }
//
//    private final JwtUtils jwtUtils;
//
//    /**
//     * 统一返回：401状态码 + 项目标准Result JSON响应
//     * 拦截不往下游转发，请求在这里直接结束
//     */
//    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
//        ServerHttpResponse response = exchange.getResponse();
//        // http状态码401 Unauthorized
//        response.setStatusCode(HttpStatus.UNAUTHORIZED);
//        // 设置响应类型为json
//        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
//        // 把Result对象序列化为json字符串
//        String body = JsonUtils.toJson(Result.fail(code));
//        // WebFlux要求返回DataBuffer字节缓冲区
//        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
//        // 把buffer写入response，返回Mono<Void>，任务结束，不继续过滤器链
//        return response.writeWith(Mono.just(buffer));
//    }
//
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        //1.先拿到request和path
//        ServerHttpRequest request = exchange.getRequest();
//        String path = request.getURI().getPath();
//
//        //2.强制删除外部传来的内部用户头！安全核心！
//        ServerHttpRequest.Builder builder = request.mutate()
//                .headers(h -> {
//                    h.remove(SecurityConstants.HEADER_USER_ID);
//                    h.remove(SecurityConstants.HEADER_USER_NAME);
//                    h.remove(SecurityConstants.HEADER_USER_TYPE);
//                });
//
//        //3.白名单接口直接放行
//        if (isWhitelist(path)) {
//            // 已经把伪造头删掉，直接放行走后面过滤器
//            return chain.filter(exchange.mutate().request(builder.build()).build());
//        }
//
//        //4.获取Authorization请求头
//        String auth = request.getHeaders().getFirst(SecurityConstants.AUTH_HEADER);
//        // 判断头是否存在，并且以 Bearer 开头，格式：Bearer eyJxxxxxxx
//        if (!StringUtils.hasText(auth) || !auth.startsWith(SecurityConstants.BEARER_PREFIX)) {
//            // 返回401未授权
//            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
//        }
//
//        //5.截取掉 Bearer 前缀，拿到纯token字符串
//        String token = auth.substring(SecurityConstants.BEARER_PREFIX.length());
//
//        //6.解析、校验JWT
//        Claims claims;
//        try {
//            claims = jwtUtils.parse(token);
//        } catch (ExpiredJwtException e) {
//            // token过期异常
//            return unauthorized(exchange, ResultCode.TOKEN_EXPIRED);
//        } catch (Exception e) {
//            // 签名篡改、格式错误等全部算token非法
//            return unauthorized(exchange, ResultCode.TOKEN_INVALID);
//        }
//
//        // 7.解析成功，写入可信内部请求头，转发下游微服务
//        ServerHttpRequest mutated = builder
//                .header(SecurityConstants.HEADER_USER_ID, String.valueOf(claims.get("userId")))
//                .header(SecurityConstants.HEADER_USER_NAME, String.valueOf(claims.get("username")))
//                .header(SecurityConstants.HEADER_USER_TYPE, String.valueOf(claims.get("userType")))
//                .build();
//
//        // 使用修改后的request继续执行过滤器链，转发到shop‑auth / shop‑user等下游服务
//        return chain.filter(exchange.mutate().request(mutated).build());
//
//
//    }
//
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

        ServerHttpRequest.Builder builder = request.mutate()
                .headers(h -> {
                    h.remove(SecurityConstants.HEADER_USER_ID);
                    h.remove(SecurityConstants.HEADER_USER_NAME);
                    h.remove(SecurityConstants.HEADER_USER_TYPE);
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
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        return unauthorized(exchange, ResultCode.TOKEN_INVALID);
                    }
                    ServerHttpRequest mutated = builder
                            .header(SecurityConstants.HEADER_USER_ID, String.valueOf(claims.get("userId")))
                            .header(SecurityConstants.HEADER_USER_NAME, String.valueOf(claims.get("username")))
                            .header(SecurityConstants.HEADER_USER_TYPE, String.valueOf(claims.get("userType")))
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
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