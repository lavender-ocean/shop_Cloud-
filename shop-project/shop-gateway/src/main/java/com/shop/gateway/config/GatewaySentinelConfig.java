package com.shop.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import result.Result;
import result.ResultCode;
import util.JsonUtils;

/**
 * 网关被 Sentinel 限流时，返回与其他服务一致的 Result JSON（默认是空 429）。
 *
 * 关键点：BlockRequestHandler 返回 Mono<ServerResponse>，
 *        必须用 ServerResponse.bodyValue(...) 构建，不能用 response.writeWith(...)。
 */
@Configuration
public class GatewaySentinelConfig {

    @PostConstruct
    public void init() {
        BlockRequestHandler handler = (exchange, t) -> {
            String body = JsonUtils.toJson(Result.fail(ResultCode.RATE_LIMIT));
            return ServerResponse
                    .status(HttpStatus.TOO_MANY_REQUESTS)        // HTTP 429
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);                            // 返回 Mono<ServerResponse>
        };
        GatewayCallbackManager.setBlockHandler(handler);
    }
}