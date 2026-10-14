package com.shop.gateway.filter;

import constant.SecurityConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;


@Component
public class TraceIdGlobalFilter implements GlobalFilter , Ordered {


    //最先由网关来做这个traceId的签发
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1.从请求头拿到上游传来的traceId
        String traceId = exchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_TRACE_ID);

        //2.如果拿不到，代表是全新请求，生成uuid作为traceId
        if(traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        //3.把traceId放进请求头
        exchange.getResponse().getHeaders().set(SecurityConstants.HEADER_TRACE_ID, traceId);

        // 重点：Gateway里面 request 对象是不可修改的！不能直接header.add()
        // mutate()：复制一份旧request，新增header，生成新的request对象

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(SecurityConstants.HEADER_TRACE_ID, traceId)
                .build();


        //4.把新request交给过滤器链，继续执行后续逻辑（鉴权、转发）
        return chain.filter(exchange.mutate().request(mutated).build());

    }

    @Override
    public int getOrder() {
        return -200;
    }
}
