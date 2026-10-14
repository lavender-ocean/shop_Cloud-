# shop_Cloud · 线上超市微服务电商平台

> 基于 **Spring Cloud Alibaba + Vue3** 的线上超市电商平台，按业务边界拆分为 8 个微服务，重点实践**分布式一致性、并发正确性、消息可靠性与缓存一致性**。个人项目，本地 Docker 部署，支付为 Mock。

---

## 架构与模块

```
shop_Cloud
├── shop-project/                 # 后端（Maven 多模块）
│   ├── shop-common/              # 7 个 starter 化公共模块
│   │   ├── shop-common-core        # 统一返回 / 异常 / 常量 / 上下文 / 工具
│   │   ├── shop-common-web         # 全局异常处理 / traceId
│   │   ├── shop-common-mybatis     # MP 分页 / 雪花 ID / 脚手架
│   │   ├── shop-common-redis       # 多级缓存（Caffeine+Redis）/ 幂等组件
│   │   ├── shop-common-feign       # Feign 出站拦截器（traceId + 用户身份透传）
│   │   ├── shop-common-mq          # 交换机 / 队列 / 重试 / 死信声明
│   │   └── shop-common-security     # JWT / 入站鉴权 / 细粒度权限
│   ├── shop-api/                 # 跨服务 DTO 与 Feign 契约
│   ├── shop-gateway/             # 网关：路由 / 鉴权 / 幂等预检         :8080
│   ├── shop-auth/                # 认证：登录 / 双 Token / RBAC         :8081
│   ├── shop-user/                # 用户与收货地址                      :8082
│   ├── shop-product/             # 类目 / 品牌 / SPU / SKU             :8083
│   ├── shop-inventory/            # 库存：三段式 + 流水 + TCC fence     :8084
│   ├── shop-cart/                # 购物车（Redis）                     :8085
│   ├── shop-order/               # 订单：下单 / 状态机 / 超时关单        :8086
│   ├── shop-payment/             # 支付（Mock）+ 回调                   :8087
│   ├── infra/                    # docker-compose 与中间件初始化
│   ├── bench/                    # 压测脚本与数据（JMeter / Node）
│   └── docs/                     # 设计文档、审计与压测报告
└── front/                        # 前端（pnpm workspace 双应用）
    ├── apps/admin/               # 管理端
    ├── apps/mall/                # 商城端
    └── packages/shared/          # 共享请求层与类型
```

**约定**：一服务一库、**服务间不 join**；路径分三类 —— `/api/**`（用户端）、`/admin/**`（管理端）、`/inner/**`（服务间调用，网关不暴露）。

---

## 技术栈

| 分类 | 技术 |
|---|---|
| 基础 | JDK 17 · Spring Boot 3.2 · Spring Cloud 2023 · Spring Cloud Alibaba 2023 |
| 注册 / 配置 | Nacos |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 调用 / 容错 | OpenFeign · Sentinel（流控、降级、网关路由限流） |
| 分布式事务 | **Seata AT** + **手写显式 TCC**（`tcc_fence` 状态机），开关切换 |
| 消息 | RabbitMQ（延迟插件、本地消息表、手动 ACK + 有限重试 + 死信） |
| 调度 | XXL-JOB（分片广播 + `FOR UPDATE SKIP LOCKED` 抢占 + 僵死回收） |
| 持久化 | MyBatis（XML 手写 SQL）+ MyBatis-Plus（仅分页 / 雪花 ID） |
| 缓存 | Redis · Caffeine（两级缓存 + 失效日志精确失效） |
| 前端 | Vue3 · Vite · TS · Pinia · Element Plus（pnpm workspace） |

---

## 核心亮点

- **同一业务做了 AT 与显式 TCC 两套对照实现**：AT 覆盖"锁库存 + 建单"同步短链路；手写显式 TCC 用 `tcc_fence` 状态机解决**空回滚 / 悬挂 / 幂等 / 确认-回滚互斥**，并把 Confirm 延后到支付回调。✅ 实测 `try→confirm`(`1→3`)、`try→cancel`(`1→2`) 与库存变动严格一致。
- **把幂等闸门从"查"改成"写"**：唯一键流水前置为闸门（先写流水、成功才改库存）+ 条件更新防超卖 + 多 SKU 排序加锁防死锁。✅ 压测 **200 并发抢 10 库存 → 成功恰好 10**，系统错误 0，库存/流水三方零差异。
- **多实例协调**：XXL-JOB 分片广播 + `FOR UPDATE SKIP LOCKED` 短事务抢占。✅ 实测 2 个实例处理 100 条本地消息，**无重复无遗漏**。
- **消息可靠性**：定位并修复了一个让整条异步链路"静默失效"的消息二次编码缺陷；补齐本地消息表 + 手动 ack + 有限重试 + 死信，投递语义提升到**"至少一次"**。
- **多级缓存一致性**：用带序号的失效日志 + 轮询精确重放替代不可靠的 Pub/Sub，日志裁剪时回退全量失效以保证不脏读。✅ 商品详情 **5,023 QPS / p99 61ms**。

### 压测数据（单机 20 核，压测器与应用同机 —— 绝对值偏低，仅反映相对趋势与正确性）

| 场景 | 并发 | QPS | p50 | p99 | 系统错误率 |
|---|---|---|---|---|---|
| 商品列表分页 | 50 | 3,105 | 15 ms | 39 ms | 0% |
| 商品详情（多级缓存） | 100 | 5,023 | 17 ms | 61 ms | 0% |
| 库存查询 | 100 | 4,528 | 20 ms | 59 ms | 0% |
| 下单主链路（含 Seata） | 20 | 164.9 | 104 ms | 332 ms | 0% |

> 完整报告见 [`shop-project/docs/10-load-test-report.md`](shop-project/docs/10-load-test-report.md)。

---

## 快速开始

```bash
# 1. 启动中间件（MySQL / Redis / Nacos / RabbitMQ / Seata / XXL-JOB）
cd shop-project/infra && docker compose up -d

# 2. 构建
cd .. && mvn -T 1C clean install -DskipTests

# 3. 启动服务（注意必须带 UTF-8，否则 JDK17 读 Nacos 的 UTF-8 配置会抛 MalformedInputException）
java -Dfile.encoding=UTF-8 -jar shop-gateway/target/shop-gateway-1.0.0.jar
# 其余 7 个服务同理

# 4. 前端
cd ../../front && pnpm install && pnpm dev:admin   # 或 pnpm dev:mall
```

> 配置集中在 Nacos（namespace `dev`）；各服务本地 `application.yml` 只保留 `server.port` 与 Nacos 连接信息。

---

## 文档

| 文档 | 内容 |
|---|---|
| [01-skeleton](shop-project/docs/01-skeleton.md) | 工程骨架、模块划分、统一返回/异常 |
| [02-database](shop-project/docs/02-database.md) | 各库表结构、索引、状态机 |
| [03-order-flow](shop-project/docs/03-order-flow.md) | 订单主链路：Seata AT / 显式 TCC / 本地消息表 / 超时关单 |
| [04-infrastructure](shop-project/docs/04-infrastructure.md) | 中间件编排与端口 |
| [05-frontend](shop-project/docs/05-frontend.md) | 前端 monorepo 与请求层 |
| [08-audit-findings](shop-project/docs/08-audit-findings.md) | 代码审计与实测验收台账（含待修清单） |
| [09-load-test-plan](shop-project/docs/09-load-test-plan.md) | 压测计划 |
| [10-load-test-report](shop-project/docs/10-load-test-report.md) | 压测报告（6 个场景实测） |

---

## 说明

- 本项目为**个人学习与作品集项目**，非生产系统；支付为 Mock。
- 所有性能数字均为**单机实测**（应用、中间件、压测器同机），**不代表生产容量**。
