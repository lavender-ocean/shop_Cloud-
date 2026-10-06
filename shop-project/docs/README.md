# shop_Cloud · 线上超市分布式电商平台

> 一句话：基于 **Spring Cloud Alibaba + Vue3** 的线上超市电商平台，支付使用 Mock 模拟，用于秋招作品集。

---

## 1. 目标与定位

- **领域**：线上超市（商超 / 生鲜）。
- **目标**：先做出**可跑通、可演示**的底座（下单 → 支付 → 超时关单闭环 + 管理端），再注入高含金量亮点。
- **约束**：一个人全职开发；**仅本地 Docker**，无公网演示。
- **面试定位**：追求"可演示 + 可防守的深水亮点"，**反对技术堆砌**。

---

## 2. 设计原则

1. **骨架求全，亮点求深**——底座完整，亮点少而精。
2. **每个技术点能回答三问**：为什么用它 / 不用会怎样 / 代价是什么。
3. **分场景选方案**——不搞"一条链路一个方案"，尤其是分布式一致性。
4. **在正确的层级用正确的技术**——WebFlux 只在网关，业务用 MVC。
5. **量化**——每个亮点要有数据（QPS / p99 / 命中率 / 降级前后对比）。

---

## 3. 技术栈

| 分类 | 技术 |
|---|---|
| 基础 | JDK17、Spring Boot 3.2、Spring Cloud 2023、Spring Cloud Alibaba 2023 |
| 注册/配置 | Nacos |
| 网关 | Spring Cloud Gateway（WebFlux） |
| 调用/容错 | OpenFeign、Sentinel |
| 事务 | Seata（先 AT，TCC 作亮点） |
| 消息 | RabbitMQ（延迟插件、死信队列） |
| 缓存 | Redis、Redisson、Lua、Caffeine |
| 持久化 | MySQL + 原生 MyBatis（分页/雪花ID/自动填充用 MyBatis-Plus） |
| 定时 | XXL-JOB |
| 前端 | Vue3 + Vite + TS + Pinia + Vue Router + Element Plus + nginx |
| 文档 | Knife4j |

---

## 4. 服务划分

| 类型 | 服务 | 职责 |
|---|---|---|
| 平台 | gateway | 路由 / 鉴权 / 限流 / 灰度 |
| 平台 | auth | 登录 / JWT / RBAC |
| 业务 | user | 用户 / 地址 / 会员积分 |
| 业务 | product | 类目 / 品牌 / SPU / SKU |
| 业务 | inventory | 库存 / 扣减 / 锁定 |
| 业务 | cart | 购物车（Redis） |
| 业务 | order | 订单 / 状态机 / 超时关单 |
| 业务 | payment | Mock 支付 / 回调 / 对账 |
| 业务 | promotion | 优惠券 / 秒杀（底座留表） |

> 管理端**不单独拆服务**，用 `/admin/**` 路径 + RBAC 区分。

---

## 5. 里程碑

| 里程碑 | 内容 |
|---|---|
| M0 环境 + 骨架 | 中间件、父 pom、common、统一规范 |
| M1 基础能力 | Nacos、Gateway、auth、Feign、Sentinel |
| M2 核心闭环 | user/product/cart/inventory/order/payment |
| M3 前端双端 | mall + admin、登录、动态权限、nginx |
| M4 亮点 | TCC、秒杀、多级缓存、幂等组件、可观测 |

---

## 6. 文档索引

| 文档 | 内容 |
|---|---|
| [01-skeleton.md](01-skeleton.md) | 工程骨架（A）：版本矩阵、多模块结构、common 清单、统一返回/异常/错误码 |
| [02-database.md](02-database.md) | 数据库设计（B）：规范、7 个 schema、关键表与索引 |
| [03-order-flow.md](03-order-flow.md) | 订单主链路（C）：一致性方案、Seata、本地消息表、延时/死信、幂等 |
| [04-infrastructure.md](04-infrastructure.md) | 中间件编排（D）：docker-compose、Nacos 配置分层、Boot3 配置写法 |
| [05-frontend.md](05-frontend.md) | 前端方案（E）：monorepo、request 封装、动态权限、nginx |
| [06-roadmap.md](06-roadmap.md) | 开工计划（F）：逐步指引、里程碑、验收 |
