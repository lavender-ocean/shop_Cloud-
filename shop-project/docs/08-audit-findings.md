# 08 · 代码审计与实测验收记录

> 本文是**工程事实台账**：记录了全量代码审计（4 路并行）+ 全接口 HTTP 实测 + 数据一致性核对的结果。
> 与 `07-interview-highlights.md` 的区别：07 是"面试怎么讲"，08 是"实际是什么状态、怎么验证的"。
> **面试前请以本文的状态为准** —— 07 里凡是与此冲突的，以此文为准。

---

## 0. 审计方法与环境

| 项 | 内容 |
|---|---|
| 代码规模 | `shop-project` 下 **8 个业务服务 + 7 个 common 模块 + shop-api**，约 **192 个 Java 文件 / 1.1 万行** |
| 审计方式 | 4 路并行只读审计（common / gateway+auth+user / product+cart / order+inventory+payment），产出接口清单 + 缺陷清单，逐条人工复核 |
| 实测方式 | 启动全部 8 个服务，用 HTTP 逐个调用接口，读 DB / RabbitMQ / XXL-JOB 状态做交叉验证 |
| 环境 | 单机 · MySQL 3307 / Redis 6379 / RabbitMQ 5672(vhost `/shop`) / Nacos 8848 / Seata 8091 / XXL-JOB 8888 |
| 关键凭证 | MySQL `root/<见 infra/.env>` · RabbitMQ `shop/<见 infra/.env>`(vhost `/shop`) · XXL-JOB `admin/<弱口令，建议修改>` · 管理员 `admin/admin123` |
| 服务端口 | gateway 8080 / auth 8081 / user 8082 / product 8083 / inventory 8084 / cart 8085 / order 8086 / payment 8087 |

---

## 1. ✅ 已修复并验证（14 项）

| # | 问题 | 位置 | 根因 | 验证方式与结果 |
|---|---|---|---|---|
| 1 | **14 个接口全部返回 `10999 系统繁忙`** | 父 `pom.xml` 缺 `<parameters>true</parameters>` | 自定义 parent POM 没继承 `spring-boot-starter-parent`；Spring 6.1 移除了 `LocalVariableTableParameterNameDiscoverer` → 未显式命名的 `@RequestParam`/`@PathVariable` 拿不到参数名 | `/api/user/param`、`/api/order/page`、`DELETE /api/cart/{skuId}`、`/inner/order/{orderNo}` 等全部恢复 200 |
| 2 | **下单 100% 失败（`10003`）** | `shop-user` `InnerUserController:36` | `BeanUtils.copyProperties(userAddressDTO, userAddress)` **参数反序** → DTO 恒为空 → 下游 `userId.equals(address.getUserId())` 失败 | `/inner/user/address/{id}` 返回完整数据；`POST /api/order` 成功 |
| 3 | **TCC 悬挂兜底无法释放库存** | `tcc_fence` 缺 `items_json` 列 | `selectStaleTry` 依赖该列；且 fence 没存 Try 时的 items，兜底时不知道锁了哪些 SKU | DDL 已加列；实测 `/inner/inventory/tcc/stale` 返回带 items 的完整请求 |
| 4 | **支付成功事件永久丢失（零补偿）** | `PaymentService.handleCallback` | 方法无 `@Transactional`，`markSuccess` 已提交后 `convertAndSend` 抛异常 → 记录已成功但事件没发；且模块无本地消息表 | 加 `@Transactional`（`mockPay` 也加，因自调用）；MQ 抖动时整体回滚可重试 |
| 5 | **`mockPay` 可替他人支付** | `PaymentService.mockPay` | 只按 `paymentNo` 查记录、不校验归属 | 加 `userId.equals(record.getUserId())` 越权校验 |
| 6 | **回调金额未与记录比对** | `PaymentService.handleCallback` | 只验签不比对金额；签名拼接无分隔符（`PAY1`+`23` 与 `PAY12`+`3` 同签名） | 加金额一致性校验 + 签名改 `\|` 分隔 |
| 7 | **`FeignTraceInterceptor` NPE + 权限头格式错** | `shop-common-feign` | `user.getPermissions().toString()`：permissions 为 null 时 NPE；`List.toString()` 产出 `"[a, b]"` 与下游 `,` split 格式不符 | 改 `String.join(",", permissions)` + 判空 |
| 8 | **任一缓存 key 失效 → 清空所有实例的整个 L1** | `MultiLevelCache.evict/pollOnce` | 只有全局版本号 + `invalidateAll`，信息量不足以知道该清哪个 key | 改"失效日志（ZSET+序号）+ 轮询精确重放 + 截断水位保守全清" |
| 9 | **整条 MQ 异步链路静默失效** | `ShopRabbitAutoConfiguration.jacksonMessageConverter` | 生产者传已 `toJson` 的 `String`，`Jackson2JsonMessageConverter` 二次序列化 → body 变 `"{\"...\"}"` → 消费端必然失败 | 删掉转换器（用默认 `SimpleMessageConverter`）；实测订单 `0→1`、`inventory_log` 出现 `type=3`、DLQ 不再增长 |
| 10 | **`createOrder` 自调用致 `@Transactional` 失效** | `OrderService:203` | `this.saveOrderAndItems(...)` 不走代理 | 抽出 `OrderPersistService`，跨 Bean 调用 |
| 11 | **`tx-mode` 开关是死代码 → TCC 的 Confirm 走错端点** | `OrderService.markPaid/cancel/closeOrder` | `isTcc()` 读全局开关，但 `/api/order/tcc` 入口硬编码 TCC → "Try 走 TCC / Confirm 走 AT" → fence 永不推进 | 三个路由方法接线 + `assertTxMode` 入口防呆；实测 fence `try/1 → confirm/3`、`try/1 → cancel/2` |
| 12 | **`/admin/product/spu/sku/{id}` 的 `spuName` 恒为 null** | `AdminSpuController.getSku` | 与 `SkuController` 两套实现，前者只 `BeanUtils.copyProperties` 不查 SPU 名；且**漏配 `@RequirePermission`** | 复用 `skuMapper.selectInfoByIds`（带 join）+ 补权限码；两接口返回值一致 |
| 13 | **`PUT /admin/product/category` 强制要求 `parentId`** | `CategoryDTO.parentId` 的 `@NotNull` | 更新复用了同一 DTO 但不用 `parentId`，却被一起校验 | 校验搬到 `add()`；顺带补"父级必须存在"（消除孤儿类目节点） |
| 14 | **TCC 悬挂兜底任务未启动** | XXL-JOB `tccSuspendedBranchHandler` | `triggerStatus=0`（注册了但没启动） | 已启动，`triggerStatus=1` |

### 另外修掉的（前端）
| # | 问题 | 位置 | 修复 |
|---|---|---|---|
| 15 | `pnpm dev` 下 `/admin/**` 请求 404 | `front/apps/admin/vite.config.ts` | proxy 补 `/admin` 前缀 |
| 16 | 购物车存商品快照，改价/下架不更新 | `CartService` | 改成"只存 skuId+数量+勾选，商品信息每次批量回源"（方案 A） |
| 17 | 失效商品仍可勾选/计入合计 | `mall` Cart.vue / Checkout.vue | 灰显 + 禁选 + 只统计有效项 |

---

## 2. 🔴 待修 · 安全加固（P1）

| # | 问题 | 位置 | 影响 | 建议修法 |
|---|---|---|---|---|
| P1-1 | **Redis 挂 → 全站 500** | `AuthGlobalFilter:353`、`IdempotentGlobalFilter:80` | `hasKey` 只有 `defaultIfEmpty(false)`，**无 `onErrorResume`**；Redis 故障时所有需鉴权请求 500 | 加 `onErrorResume` → **fail-open（降级放行）+ 告警**；黑名单只是增强，真正兜底是 JWT 自身过期 |
| P1-2 | **JWT 密钥有硬编码默认值** | `JwtProperties:13` | 默认值 54 字节，未在 Nacos 覆盖时应用**正常启动**，全网可用公开密钥伪造 token | 删默认值，改 `@NotBlank` + 类上 `@Validated` → 缺失时启动失败 |
| P1-3 | **`AdminSeeder` 弱口令 + 无环境守卫** | `AdminSeeder:62,68` | `admin/admin123` 硬编码，`CommandLineRunner` 每次启动无条件执行，无 `@Profile` | 加 `@Profile({"dev","test"})` + 密码从配置读 |
| P1-4 | **`cache.evict()` 在 `@Transactional` 内** | `SpuService:233/242/251` | "失效与提交之间的窗口"里并发请求可能把 DB 旧值回填进缓存（TTL 10 分钟） | 移到 `afterCommit`（`TransactionSynchronization`）|
| P1-5 | **`@Idempotent` 在 `assertTxMode` 之前消费 token** | `OrderController` 两个下单入口 | 调错入口时 token 被白白消费 | 前端先查 `GET /api/order/tx-mode`（已提供），从源头避免调错 |
| P1-6 | **`SpuService.update()` 重建 SKU → skuId 全变** | `SpuService:230-231` | 编辑商品后：购物车项静默变"已失效"、`inventory` 行成孤儿、新 SKU 无库存 | 设计问题，需讨论（增量更新 or SKU id 复用）|
| P1-7 | **MQ 不识别"毒消息"** | `MqRetrySupport:46-58` | 反序列化失败这类**永久性错误**也重试 3 次才进死信 | 沿 cause 链找 `JsonProcessingException` → 直接进 DLQ |
| P1-8 | **`publisher-returns` 配了但没人处理回执** | 全局 | 不可路由的消息**静默丢弃** | 加 `ReturnsCallback` 打错误日志 + 告警 |
| P1-9 | **雪花 workerId/datacenterId 配置全被注释** | `ShopMybatisAutoConfiguration:44-49` | 依赖 MP 默认（MAC+PID 哈希 %32），同宿主多实例**有碰撞可能** | 显式配置，无默认值（缺失即启动失败）|
| P1-10 | **`/inner/**` 无任何鉴权** | 各 Inner 控制器 | 内网端口若可达即 IDOR（枚举任意地址/资料/库存） | 靠网络策略保证；或加内部鉴权头校验 |

---

## 3. 🟡 待修 · 质量与可维护性（P2）

| # | 问题 | 说明 |
|---|---|---|
| P2-1 | **死代码量约 45%** | `OrderService` 866 行里 473 行注释；`CartService` 429/169；`SpuService` 292/137；`InventoryService` 525/260；`AuthGlobalFilter` 397/273；`MultiLevelCache` 375/199 |
| P2-2 | **确认无人使用的类** | `CommonConstants`、`BaseEntity`（无子类）、`SnowflakeAutoConfiguration`（整文件注释）、`IdGenerator`、`DeletedFlag`/`OrderStatus`/`PayStatus` 枚举、`ResultCode` 6 个未用常量、`RedisKeyConstants` 5 个未用 key、**`redisson-spring-boot-starter` 依赖**（零引用）|
| P2-3 | **`mybatis-plus-core` 版本不一致** | `shop-order/pom.xml` 直接引 `3.5.14`，starter 是 `3.5.7` → 删掉直接依赖 |
| P2-4 | **源码目录混入 `.md` 笔记** | `shop-*/src/main/java/**/*.md` 十几份（含 `大兜底计划.md`、`显示tcc.md`）→ 挪到 `docs/` |
| P2-5 | **重复实现** | "按 id 查 SKU" 原有 4 份（已修 2 份）；建议合并到一处 |
| P2-6 | **`Result.success(null)` 序列化后无 `data` 字段** | 与有值时结构不一致 |
| P2-7 | **`BizAssert` 缺 3 参 `notNull`** | 只有 `isTrue` 有带 message 的重载 |
| P2-8 | **文档与实现不一致** | docs 写重复提交返回 `16003`，实测是 `10006`（`ORDER_DUPLICATE_SUBMIT` 未被使用）|
| P2-9 | **品牌模块残缺** | 只有 `list`，无增删改；无 `BrandService`；`/api` 下无品牌接口 |
| P2-10 | **`CategoryService.tree()` 硬编码 `status=1`** | 管理端看不到停用类目，也无法在树上编辑 |
| P2-11 | **空类 `TccFenceServiceTest` 放在 `src/main/java`** | 会被打进生产包 |
| P2-12 | **`CategoryDTO` 校验提示文案** | 现在返回通用"参数校验失败"，建议换 `BizAssert.isTrue(..., message)` 给精确提示 |
| P2-13 | **未使用 import / 重复 import** | `SpuController:5`、`AdminSpuController:17`（重复 `Sku`）、`ResultCode:3`、`OrderAggregateService:33` |

---

## 4. ✅ 全接口实测结果（约 65 个端点）

| 模块 | 端点数 | 结果 |
|---|---|---|
| auth（login / refresh / logout / userinfo） | 4 | ✅ 全通过 |
| user（含 6 个异常验证接口） | 13 | ✅ 全通过 |
| user `/inner` | 2 | ✅ 全通过 |
| product 用户端 | 5 | ✅ 全通过 |
| product 管理端（含 SPU / 类目 CRUD） | 11 | ✅ 全通过 |
| product `/inner` | 2 | ✅ 全通过 |
| cart | 6 | ✅ 全通过 |
| inventory（查询 / 分页 / 入库） | 4 | ✅ |
| inventory `/inner`（lock/confirm/release/tcc/stale） | 8 | ✅ 全通过 |
| order（token / tx-mode / page / detail / aggregate / cancel / 下单 ×2） | 8 | ✅ 全通过 |
| order `/admin`（page / detail / ship） | 3 | ✅ 全通过 |
| order `/inner` | 1 | ✅ |
| payment（create / mock-pay / callback） | 3 | ✅ 全通过 |

**未单独跑测的 2 个**：`POST /admin/inventory/init`（需一个未初始化库存的 SKU）、`POST /inner/inventory/tcc/confirm`（已在 TCC 支付链路中间接验证 ✅）。

### 端到端链路
| 场景 | 结果 |
|---|---|
| **AT 下单→支付** | `available 97→96 / locked 0→1` → `status=1`、`total 96`；流水 `type=2 → type=3`；**`tcc_fence` 无此单** ✅ |
| **AT 下单→取消** | `status=4`；流水 `type=2 → type=4`；库存完整归还 ✅ |
| **TCC Try→Confirm** | `fence try/1 → confirm/3`；`total −1`、`locked 归 0` ✅ |
| **TCC Try→Cancel** | `fence try/1 → cancel/2`；`available +2`、`locked 归 0` ✅ |
| **超时关单双保险** | 延迟消息进 DLQ，扫描兜底关单 `status=5` 并归还库存 ✅ |
| **支付回调** | 正确签名→推进；重复回调→幂等；错误签名→`17001`；已支付单 mock-pay→`17001` ✅ |
| **鉴权** | `logout` 后旧 token → `401 + 12003`；无权限码 → `10003` ✅ |
| **模式防呆** | 入口与开关不匹配 → `10001`（双向）✅ |

---

## 5. 数据一致性核对（可复用的自检 SQL）

> 这是最能"自证"的一组检查，建议每次改动后都跑一遍。

```sql
-- ① 库存账目：total = available + locked，gap 必须恒为 0
SELECT sku_id, total_stock, available_stock, locked_stock,
       (total_stock - available_stock - locked_stock) AS gap
FROM shop_inventory.inventory WHERE deleted = 0;

-- ② 三方交叉：实际 locked == 未支付订单锁定总量 == 流水净额(lock-confirm-release)
SELECT i.sku_id, i.locked_stock AS 实际locked,
  COALESCE((SELECT SUM(ol.quantity) FROM shop_inventory.inventory_log ol
            JOIN shop_order.order_info oi ON oi.order_no = ol.order_no
            WHERE ol.sku_id=i.sku_id AND ol.type=2 AND oi.status=0),0) AS 未支付订单锁定,
  COALESCE((SELECT SUM(ol.quantity) FROM shop_inventory.inventory_log ol
            WHERE ol.sku_id=i.sku_id AND ol.type=2),0)
- COALESCE((SELECT SUM(ol.quantity) FROM shop_inventory.inventory_log ol
            WHERE ol.sku_id=i.sku_id AND ol.type=3),0)
- COALESCE((SELECT SUM(ol.quantity) FROM shop_inventory.inventory_log ol
            WHERE ol.sku_id=i.sku_id AND ol.type=4),0) AS 流水净额
FROM shop_inventory.inventory i WHERE i.deleted = 0;

-- ③ 幂等流水不得重复（应为空）
SELECT order_no, type, sku_id, COUNT(*) c FROM shop_inventory.inventory_log
WHERE order_no IS NOT NULL GROUP BY order_no, type, sku_id HAVING c > 1;

-- ④ 已支付订单必有 confirm 流水（应为空）
SELECT oi.order_no FROM shop_order.order_info oi
WHERE oi.status IN (1,2) AND NOT EXISTS
  (SELECT 1 FROM shop_inventory.inventory_log l WHERE l.order_no=oi.order_no AND l.type=3);

-- ⑤ 已取消/关闭订单必有 release 流水（应为空）
SELECT oi.order_no FROM shop_order.order_info oi
WHERE oi.status IN (4,5) AND NOT EXISTS
  (SELECT 1 FROM shop_inventory.inventory_log l WHERE l.order_no=oi.order_no AND l.type=4);
```

**实测结果**：①② 三方全部 `0` 差异；③④⑤ 均返回空。

---

## 6. 排查方法备忘（怎么发现的）

| 现象 | 定位路径 |
|---|---|
| 某接口返回 `10999 系统繁忙` | 全局兜底只给"系统繁忙"，看不到异常类型 → **单独起一个实例、日志落文件**，抓真实堆栈（本次就是这么找到 `-parameters` 的） |
| "支付成功但订单不动" | `payment_record.status`（已成功）→ `order_info.status`（未变）→ `order_status_log`（无 markPaid 记录）→ **RabbitMQ 队列深度**（`*.dlq` 有消息）→ **读死信消息头**（`x-error` / `__TypeId__` / payload 形态）|
| 想知道"读到的数据对不对" | 写跨表/跨库的一致性核对 SQL（见 §5），而不是肉眼看 |
| 判断 XXL-JOB 是否真在跑 | 管理 API `/jobinfo/pageList` 看 `triggerStatus`，`/joblog/pageList` 看 `triggerCode`/`handleCode` |

**两个排查陷阱**：
1. `rabbitmqctl list_queues` 默认查 vhost `/`，而本项目 vhost 是 **`/shop`** → 会误判"队列不存在"；用管理 API `/api/queues/%2Fshop`。
2. 用 Git Bash 的 `curl` 发**中文 JSON** 会按 GBK 编码，服务端按 UTF-8 解析失败 → 报 `10001 请求体格式错误`。**这是测试端问题，不是服务端 bug**；测中文用浏览器或 `--data-binary @file`（UTF-8 文件）。

---

## 7. ❌ 未实现（不要写进简历 / 不要在面试里讲）

| 项 | 实际情况 |
|---|---|
| 灰度发布 | 全仓无自定义 `ReactorServiceInstanceLoadBalancer` / 灰度分流代码 |
| 可观测（SkyWalking / Prometheus / Grafana） | 只有 `spring-boot-starter-actuator`；**没引 SkyWalking，没引 micrometer/prometheus** |
| Redisson（看门狗 / RedLock / fencing token） | `RedissonClient` 在代码里**零引用**（依赖是否引入都无关） |
| 秒杀 | 微服务版不做（口径：秒杀是单点极致问题，已在单体项目覆盖）|
| Seata 全局锁防脏写并发实验 | AT 用了，但**那个对比实验没做** —— 别说"用实验证明" |
| "200 并发抢 10 成功恰为 10" | 防超卖 SQL 是对的，但**这个脚本没跑过** |

---

## 8. 下一步待办（按优先级）

```
P0  ✅ 已完成（见 §1）
P1  ① 网关 Redis 容错 fail-open      ② JWT 密钥去掉默认值
    ③ AdminSeeder 加 @Profile        ④ cache.evict 移到 afterCommit
    ⑤ MQ 毒消息识别 + ReturnsCallback ⑥ 雪花 workerId 配置
P2  ⑦ 清死代码（45%）⑧ 删无用类/依赖 ⑨ 合并重复实现 ⑩ .md 归位
    ⑪ 统一 mybatis-plus 版本
压测  ⑫ 跑 §4.2 的压测项，把真实数字填进 docs/07 第 4.2 节
文档  ⑬ docs/02-database.md 补 order_info.tx_mode（若加了）；docs/03 §10 验收结论按 §4 回填
```
