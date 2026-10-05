# 06 · 开工计划（F）

> 约定：**文档由我维护；代码由你编写**，我按步骤在对话里输出代码内容。

---

## F-1 仓库总体布局

```
shop_Cloud/
├── pom.xml                 # 后端父 pom
├── shop-common/            # 7 子模块
├── shop-api/
├── shop-gateway/  shop-auth/  shop-user/ ...
├── web/                    # 前端 pnpm workspace
│   ├── pnpm-workspace.yaml
│   ├── apps/mall/  apps/admin/
│   └── packages/shared/
├── infra/                  # 中间件编排
│   ├── docker-compose.yml
│   ├── mysql/init/*.sql
│   ├── rabbitmq/enabled_plugins
│   ├── nacos/  seata/
└── docs/
```

---

## F-2 里程碑

| 里程碑 | 内容 | 完成标志 |
|---|---|---|
| M0 环境+骨架 | infra、父 pom、common、统一规范 | 空服务返回标准 `Result` |
| M1 基础能力 | Nacos、Gateway、auth、Feign、Sentinel | 网关鉴权 + Feign 调通 |
| M2 核心闭环 | user/product/cart/inventory/order/payment | 下单→支付→关单全绿 |
| M3 前端双端 | mall + admin、登录、动态权限、nginx | 双端登录+核心页可用 |
| M4 亮点 | TCC、秒杀、多级缓存、幂等组件、可观测 | 每个亮点有量化数据 |

---

## F-3 分步指引（Step by Step）

> 每一步完成后都有"验证点"，通过再进入下一步。

### Step 1 · 中间件跑起来
1. `infra/docker-compose.yml`：mysql / redis / nacos / rabbitmq / sentinel / seata / xxl-job
2. `infra/rabbitmq/enabled_plugins`：启用延迟插件
3. `infra/mysql/init/`：7 个业务 schema + `undo_log` + `nacos_config`
4. `docker compose up -d`
- **验证**：全容器 healthy；RabbitMQ 能选 `x-delayed-message`；Nacos 控制台可登录。

### Step 2 · 父 pom + common 骨架
5. 根 `pom.xml`：BOM + pluginManagement（JDK17）
6. `shop-common` 7 模块 + `AutoConfiguration.imports`
7. `core`：Result / ResultCode / BizException / BizAssert / UserContext / 常量
8. `web`：GlobalExceptionHandler / TraceIdFilter / Jackson
9. `mybatis`：MP 拦截器 / BaseEntity / 自动填充
- **验证**：`mvn clean install` 全绿。

### Step 3 · 首个服务打通 + Nacos
10. `shop-user`：启动类 + `GET /api/user/ping`
11. 接入 Nacos（`spring.config.import`），上传共享/私有配置
- **验证**：返回标准 `Result`；异常返回规范错误码；traceId 贯穿；配置来自 Nacos。

### Step 4 · 竖直切片：登录链路
12. `shop-gateway` 路由 + 全局鉴权过滤器（白名单）
13. `shop-auth`：登录、JWT 签发、refresh
14. `shop-api`：定义 auth Feign 契约
15. 前端 `web/` 初始化 + `shared/request.ts` + admin 登录页
- **验证**：`admin 登录页 → Vite proxy → Gateway → auth → user 返回用户信息 → 前端展示`。

### Step 5 · 铺开业务服务
16. product / inventory / cart 基础 CRUD + 查询
17. order 下单（Seata AT）+ 库存扣减
18. promotion 券表与锁定
- **验证**：M2 验收脚本。

### Step 6 · 支付与关单
19. payment Mock 支付 + 回调幂等
20. 延迟插件 + 超时关单 + 库存/券回滚
21. 本地消息表 + 定时补偿
22. XXL-JOB 兜底扫描
- **验证**：C-11 验收。

### Step 7 · 前端双端完善
23. mall 商品/购物车/订单页
24. admin 商品/库存/订单/系统管理 + 动态权限
25. nginx 反代
- **验证**：E-10 验收。

### Step 8 · 亮点注入
26. TCC（Seata）替代/A-B 对比
27. 秒杀（Redis Lua + MQ）
28. 多级缓存（Caffeine + Redis + 逻辑过期 + 热点探测）
29. 通用幂等组件 `@Idempotent`
30. 可观测（Tracing + Prometheus + Grafana）+ 压测数据
- **验证**：每个亮点有量化指标。

---

## F-4 执行策略

**先打通一条竖直切片**，不先把所有服务建完再联调：
```
mall 登录页 → Vite proxy → Gateway → auth 签发 JWT → user 返回用户信息 → 前端展示
```
这条线跑通，Nacos / Gateway / JWT / Feign / 前端代理 / 统一返回全部验证完毕，之后横向铺开是重复劳动，风险极低。

---

## F-5 排期参考（单人全职）

| 里程碑 | 工期 |
|---|---|
| M0 | 3~5 天 |
| M1 | ~1 周 |
| M2 | 2~3 周 |
| M3 | ~1 周 |
| M4（每个亮点） | 3~7 天 |
