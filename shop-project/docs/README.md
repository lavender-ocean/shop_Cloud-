# shop_Cloud · 线上超市分布式电商平台

> 基于 **Spring Cloud Alibaba + Vue3** 的线上超市电商平台，支付使用 Mock 模拟。用于秋招作品集。

---

## 1. 定位

- **领域**：线上超市（商超 / 生鲜）。
- **目标**：先做**可跑通、可演示**的底座（下单 → 支付 → 超时关单闭环 + 管理端），再注入高含金量亮点。
- **约束**：一个人全职开发；**仅本地 Docker**，无公网演示。
- **设计取向**：追求"可演示 + 可防守的深度亮点"，**反对技术堆砌**。

## 2. 设计原则

1. 骨架求全，亮点求深。
2. 每个技术点能回答：**为什么用 / 不用会怎样 / 代价是什么**。
3. **分场景选一致性方案**（不搞"一条链路一个方案"）。
4. 在正确的层级用正确的技术（WebFlux 只在网关，业务用 MVC）。
5. 量化（压测数据、命中率、降级前后对比）。

---

## 3. 技术栈（实际使用）

| 分类 | 技术 |
|---|---|
| 基础 | JDK17、Spring Boot 3.2、Spring Cloud 2023、Spring Cloud Alibaba 2023 |
| 注册/配置 | Nacos（namespace `dev`，group `DEFAULT_GROUP`） |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 调用/容错 | OpenFeign（+ 透传拦截器）、Sentinel |
| 事务 | **Seata AT**（`@GlobalTransactional`）+ **手写显式 TCC**（`tcc_fence` 状态机），用开关切换 |
| 消息 | RabbitMQ（**延迟插件 `x-delayed-message`**、本地消息表、手动 ack + 有限重试 + 死信） |
| 调度 | **XXL-JOB**（分片广播 + `SELECT ... FOR UPDATE SKIP LOCKED` 抢占 + 僵死回收） |
| 持久化 | **原生 MyBatis（XML 写 SQL）** + MyBatis-Plus（分页 / 雪花 ID） |
| 缓存 | Redis（StringRedisTemplate）、**Caffeine（L1 多级缓存）**、Lua |
| 前端 | Vue3 + Vite + TS + Pinia + Vue Router + Element Plus（pnpm workspace 双应用：admin / mall） |
| 文档 | Knife4j |

> ⚠️ `redisson-spring-boot-starter` 依赖引入了但**代码里零引用**，列在待清理项（见 [08](08-audit-findings.md) P2-2）。

---

## 4. 服务与端口（实际）

| 服务 | 端口 | 状态 | 说明 |
|---|---|---|---|
| shop-gateway | 8080 | ✅ | 路由 / JWT 鉴权 / 黑名单 / 用户头注入 |
| shop-auth | 8081 | ✅ | 登录 / JWT / 刷新 / 登出 / RBAC / 菜单 |
| shop-user | 8082 | ✅ | 用户资料 / 收货地址 CRUD + 内部接口 |
| shop-product | 8083 | ✅ | 类目 / 品牌 / SPU / SKU |
| shop-inventory | 8084 | ✅ | 库存锁定 / 确认 / 释放 + 流水 |
| shop-cart | 8085 | ✅ | Redis 购物车 |
| shop-order | 8086 | ✅ | 下单（Seata）/ 超时关单 / 支付事件消费 |
| shop-payment | 8087 | ✅ | Mock 支付 + 回调 |

中间件：MySQL `3307`、Redis `6379`、Nacos `8848`、RabbitMQ `5672/15672`、Sentinel `8858`、Seata `8091/7091`、XXL-JOB `8888`（**已接入，3 个任务在跑**）。

> ⚠️ **RabbitMQ 的 vhost 是 `/shop`**（不是默认 `/`）；用 `rabbitmqctl list_queues` 查默认 vhost 会看到空，排查时用管理 API `/api/queues/%2Fshop`。
> ⚠️ 用 `java -jar` 启动必须加 **`-Dfile.encoding=UTF-8`**，否则 JDK17 在中文 Windows 下按 GBK 读 Nacos 的 UTF-8 YAML 会抛 `MalformedInputException`。

---

## 5. 里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 环境 + 骨架 | 中间件、父 pom、common、统一规范 | ✅ |
| M1 基础能力 | Nacos、Gateway、auth(JWT+RBAC)、Feign | ✅ |
| M2 核心闭环 | product / inventory / cart / order / payment | ✅ |
| M3 前端 | 管理端（登录/动态菜单/商品管理）+ 商城端 mall | ✅ |
| M4 亮点 | **AT / 显式 TCC 双模式**、多级缓存一致性、幂等组件、多实例调度、超时关单双保险 | ✅（秒杀不做）|
| M5 收尾 | 代码审计与实测验收（[08](08-audit-findings.md)）、压测（[09](09-load-test-plan.md) / [10](10-load-test-report.md)）、死代码清理 | 🟡 进行中 |

---

## 6. 文档索引

| 文档 | 内容 |
|---|---|
| [01-skeleton.md](01-skeleton.md) | 工程骨架：版本、模块、common、统一返回/异常 |
| [02-database.md](02-database.md) | 数据库：各 schema / 表 / 索引 / 状态机 |
| [03-order-flow.md](03-order-flow.md) | 订单主链路：Seata / 本地消息表 / 延时关单 / 支付 |
| [04-infrastructure.md](04-infrastructure.md) | 中间件编排：compose / Nacos 配置 / 启动顺序 |
| [05-frontend.md](05-frontend.md) | 前端：monorepo / request / 动态菜单 |
| [06-roadmap.md](06-roadmap.md) | 已完成步骤与后续计划 |
| [08-audit-findings.md](08-audit-findings.md) | **审计与实测验收台账**：已修/待修问题清单、全接口实测结果、数据一致性自检 SQL、排查方法 |
| [09-load-test-plan.md](09-load-test-plan.md) | **压测计划**：环境/工具/指标定义/6 个场景/数据准备/前置检查/防自欺欺人 8 条 |
| [10-load-test-report.md](10-load-test-report.md) | **压测报告**：环境快照、吞吐延迟、并发正确性、多实例分片、资源与瓶颈、一致性复核 |
