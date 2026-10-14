# 06 · 进度与后续计划

## 1. 已完成（Step 1 ~ 20）

| # | 内容 | 产出 |
|---|---|---|
| 1 | 中间件编排 | docker-compose（mysql/redis/nacos/rabbitmq/sentinel/xxl-job/seata）全部 healthy |
| 2 | 父 pom + shop-common 7 模块骨架 | `mvn install` 全绿 |
| 3 | shop-common-core | Result / ResultCode / BizException / BizAssert / UserContext / 常量 / 枚举 / 工具 |
| 4 | shop-common-web | TraceIdFilter / GlobalExceptionHandler / Jackson(Long→String) / OpenAPI |
| 5 | shop-common-mybatis | MP 拦截器（分页/乐观锁/防全表）+ BaseEntity + 自动填充 |
| 6 | shop-user 本地跑通 | 统一返回 / 全局异常 / traceId / 数据源 |
| 7 | 接入 Nacos | 服务注册 + 配置中心（common + 私有），热刷新 |
| 8 | common-security + shop-auth | JwtUtils / AuthInterceptor / 登录发 token / AdminSeeder |
| 9 | shop-gateway | 路由 + 全局鉴权 + 用户头注入 + 防伪造 + traceId |
| 10 | 前端竖直切片 | pnpm workspace + request + 登录页 → 网关 → user |
| 11 | 刷新 + 登出黑名单 | common-redis、refresh 旋转、网关黑名单校验 |
| 12 | RBAC 后端 | 角色/权限/菜单表 + 种子 + `/userinfo`（角色+权限码+菜单树） |
| 13 | 前端动态菜单 | 动态路由 + 递归菜单 + `v-permission` |
| 14 | 服务端细粒度鉴权 | `@RequirePermission` + PermissionLoader(Redis) + PermissionInterceptor |
| 15 | shop-product（类目） | 类目 CRUD + 树 + 分页 + 权限 |
| 16 | shop-product（SPU/SKU） | 多表级联事务 + 超市字段 + 上下架 |
| 17 | shop-inventory | 三段式库存 + 锁定/确认/释放 + 流水 + 幂等 + 防超卖 |
| 18a | order 下单同步段 | Seata AT（锁库存 + 建单）+ 幂等 token |
| 18b | 本地消息表 + 超时关单 | 消息投递器 + 延迟插件 + 关单消费者 + 兜底扫描 |
| 18c | shop-payment | Mock 支付 + 回调验签/幂等 + 支付成功事件 → markPaid |
| 19 | shop-cart | Redis Hash 购物车 |
| 20 | 管理端商品管理页 | 类目筛选 + SPU 分页/增删改 + SKU 子表 |

**核心闭环已打通**：下单(Seata) → 支付(MQ 最终一致) → 超时关单(延时+兜底)。

---

## 2. 待办（按优先级）

### P1 · 补齐业务闭环
- [ ] `shop-user` 业务表：`user_profile` / `user_address` / `user_member`，注册（auth→MQ→建 profile）、地址管理
- [ ] 下单收货地址改为从 `shop-user` 取（当前由前端传入快照）
- [ ] order 查询类接口：`/api/order/page`（我的订单）、`/api/order/{orderNo}`（详情）、取消订单
- [ ] `POST /api/order/from-cart`：购物车选中项结算
- [ ] 管理端订单列表页（发货 / 取消）
- [ ] 前端用户端 `apps/mall`：商品浏览 / 购物车 / 下单 / 支付 / 订单

### P2 · 工程完善
- [ ] 消费端**手动 ack + 重试 + 死信队列**（`order.dlq`）
- [ ] XXL-JOB 接入（替代 `@Scheduled`：超时兜底、消息补偿、对账）
- [ ] Sentinel 规则持久化 + Feign 降级（`fallbackFactory`）
- [ ] nginx 反代 + 生产构建部署
- [ ] 包名治理：common 顶层包 → `com.shop.common.*`
- [ ] 参数校验/越权/脱敏等安全项复查

### P3 · 亮点（M4，见 07 文档）
- [ ] 通用幂等组件 `@Idempotent`
- [ ] 秒杀（Redis Lua + MQ 异步创单）
- [ ] 多级缓存（Caffeine + Redis + 逻辑过期 + 热点探测）
- [ ] 可观测（Tracing + Prometheus + Grafana）+ 压测数据
- [ ] TCC（与 AT 对比）
- [ ] 分库分表 / Canal → ES

---

## 3. 排期参考

| 阶段 | 工期 |
|---|---|
| P1 缺口 | ~2 周 |
| P2 工程完善 | ~1 周 |
| P3 亮点（每个 3~7 天） | 视选择 |
