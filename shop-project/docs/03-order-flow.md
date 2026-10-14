# 03 · 订单主链路

本项目最核心的链路，绝大多数亮点都挂在这里。

---

## 1. 核心原则：分场景选一致性方案

| 场景 | 实际方案 | 为什么 |
|---|---|---|
| 下单同步段（锁库存 + 建单） | **Seata AT** | 跨服务、需原子、并发可接受 |
| 下单同步段（TCC 对比实现） | **显式 TCC** | 需把 Confirm 延后到支付回调 |
| 下单后长尾（积分/通知） | **本地消息表 + MQ** | 允许延迟、不阻塞用户 |
| 支付回调 → 订单状态 | **MQ 最终一致** | 回调必须快速返回 |
| 超时关单 | **本地消息表 → 延迟插件 → 消费者**（+ 兜底扫描） | 需可靠延时，但可容忍秒级误差 |
| 秒杀（后续亮点） | Redis Lua + MQ | Seata 全局锁扛不住 |

> 一句话：**同步必经、必须原子的短链路用 Seata；可延迟、可补偿的长尾用本地消息表 + MQ。**

---

## 2. 下单同步段（Seata AT）

`OrderService.createOrder` 标注 `@GlobalTransactional(name = "shop-create-order")`：

```
用户 → 网关 → order-service
   ① 幂等：Redis 原子 DEL 消费 token（失败 → 16003）
   ② 生成 orderNo；对每个 SKU：Feign → product 取价，组装快照
   ③ Feign → inventory.lock（Seata 分支，乐观更新防超卖）
   ④ 本地事务：insert order_info + order_item + order_status_log + local_message(×2)
   ⑤ 全局提交
```

**库存扣减（防超卖）**
```sql
UPDATE inventory SET available_stock = available_stock - #{qty}, locked_stock = locked_stock + #{qty}, version = version + 1
WHERE sku_id = #{skuId} AND available_stock >= #{qty}
-- 影响行数 = 0 → 抛 BizException(15001) → Seata 全局回滚
```

**Seata AT 原理（必背）**：两阶段——一阶段各分支**直接提交本地事务**并写 `undo_log`；二阶段全局提交删 `undo_log`，全局回滚则用 `undo_log` **反向补偿**。一阶段先抢**全局锁**防脏写。

**参与方**：order（TM + RM，本地写）、inventory（RM，远端写）。product 只读，非分支。所以 **`shop_order` 与 `shop_inventory` 都必须有 `undo_log` 表**。

---

## 3. 显式 TCC（与 AT 的对比实现）

同一业务（下单锁库存）另有一条**显式 TCC**链路，用于对比两种事务模式。

**入口**：`POST /api/order/tcc` → `OrderTccService.createOrderByTcc`（**无 `@GlobalTransactional`**）

```
① prepare：取价、组装（不写库）
② Try    ：Feign → /inner/inventory/tcc/try   预占库存（available↓ locked↑）+ 写 tcc_fence
③ 本地事务：saveOrderAndItems（@Transactional，只包本地库）
④ 失败 → Feign → /inner/inventory/tcc/cancel 补偿释放；补偿再失败 → 交给兜底 job
   成功 → 返回 orderNo，此时分布式上下文立即结束
⑤ 支付成功 → markPaid → /inner/inventory/tcc/confirm（锁定 → 真扣减）
```

**为什么用显式 TCC**：AT 的全局事务必须在下单这一次调用里走完 Try→Confirm/Cancel，**做不到把 Confirm 延后**；而用户支付可能等几十秒，AT 会让未支付订单长期持有全局锁。显式 TCC 把 Confirm 拆到支付回调这条独立链路。

**没有 TC 参与**：`xid = orderNo`（业务唯一键）、`branchId` 固定 `1L`，三阶段由 Java 代码自己驱动——所以 Seata 控制台里**看不到** TCC 演示单的全局事务。代价是**补偿要自己写**：Cancel 失败没有框架重试，靠 `TccSuspendedJobHandler` 扫 `tcc_fence` 兜底。

**`tcc_fence` 状态机**

| 值 | 状态 | 含义 |
|---|---|---|
| 0 | TRYING | Try 已占位 |
| 1 | COMMITTED | Try 业务成功 |
| 2 | ROLLBACKED | Cancel 已执行（含空回滚占位） |
| 3 | CONFIRMED | Confirm 已执行 —— 终态，禁止回滚 |

**三大坑的判定（`TccFenceService`）**

| 坑 | 触发 | 判定逻辑 |
|---|---|---|
| **空回滚** | Try 未到就收到 Cancel | `cancelBegin` 查不到记录 → 写 `status=2` 占位并跳过业务，**否则迟到的 Try 会锁住永远无人释放的库存** |
| **悬挂** | 已回滚的分支又收到 Try | `tryBegin` 查到 `status=2` → 返回 false 拒绝执行 |
| **幂等** | 同一分支重复 Try/Confirm/Cancel | 状态机 + `UK(xid, branch_id)`；`tryCommit` 的 `expectedStatuses` **必须含 `COMMITTED`**，否则重复 Try 会把 `rows=0` 误判为"已被回滚"→ 调用方 release 撤销预占 → **超卖** |
| **互斥** | Confirm 后又 Cancel / 反之 | `status=3` 时 `cancelBegin` 拒绝；`status=2` 时 `confirmBegin` 跳过 |

**悬挂兜底**：`TccSuspendedJobHandler`（XXL-JOB `tccSuspendedBranchHandler`）→ 拉 `/inner/inventory/tcc/stale`（返回长时间停在 Try 阶段、且带 `items_json` 的分支）→ 逐个查 `order_info`，**订单不存在**则重试 Cancel 释放库存。`items_json` 是 Try 时落库的 items 快照——没有它，兜底任务不知道当初锁了哪些 SKU，Cancel 会空转。

**AT vs 显式 TCC**

| 维度 | AT（`@GlobalTransactional`） | 显式 TCC |
|---|---|---|
| XID / branchId | Seata TC 分配 | 自己指定（`orderNo` / 常量 `1L`） |
| Confirm 时机 | 必须在下单链路内完成 | **可延后到支付回调** |
| 全局事务生命周期 | 到 Confirm 完成才结束 | Try + 落库完成即结束 |
| 长事务风险 | 高（未支付订单长期占全局锁） | 低 |
| 补偿 | 框架自动（`undo_log` / 自动 Cancel） | 自己写 catch + 兜底 job |
| 需要 TC 部署 | 是 | 否 |

> 两种模式通过 Nacos `shop.order.tx-mode: at | tcc` 切换，`markPaid` / `release` 按开关路由到 `inventoryClient` 或 `inventoryTccClient`。

---

## 4. 本地消息表（可靠投递）

RabbitMQ 没有 RocketMQ 的事务消息，故用本地消息表：

- `createOrder` 的**本地事务里**同时写两条 `local_message`：
  - `ORDER_CREATED`（payload = 订单 JSON）
  - `ORDER_TIMEOUT`（payload = `{orderNo, expireMillis}`）
- `LocalMessageJobHandler`（XXL-JOB `localMessageCompensateHandler`）：
  - 先 `releaseStaleClaims` 回收僵死（抢占超过 60s 未投递）
  - `LocalMessageClaimer` 在短事务里 `SELECT ... FOR UPDATE SKIP LOCKED` **原子抢占**
  - `LocalMessageSender.dispatchClaimed` 在**事务外**真正发送：
    - `ORDER_CREATED` → 发 `order.exchange` / `order.created`
    - `ORDER_TIMEOUT` → 发 `delay.exchange` / `order.delay`，并设置 **`x-delay = expireMillis - now`**
  - 成功 `markSent`；异常 `markRetry`（下轮重试）

> 这回答了经典问题："**如何保证本地事务与消息发送的原子性？**"——本地消息表 + 定时补偿。
> 拆分「抢占」与「投递」两步，是为了让持锁时间极短，且多实例不会重复投递。

---

## 5. 超时关单（延迟插件 + 扫描兜底）

**采用延迟插件 `x-delayed-message`**（而非 TTL+DLX）：
- 交换机组装类型 `x-delayed-message`，`x-delayed-type=direct`
- 消息头 `x-delay` 指定延时毫秒 → **任意延时、无队头阻塞**（TTL 队列会有队头阻塞问题）

**消费**：`OrderTimeoutConsumer` 监听 `order.timeout.queue` → `OrderService.closeOrder`：
```
@GlobalTransactional(name = "shop-close-order")
  ① 查订单，非待支付 → 直接 return（幂等）
  ② CAS：update order_info set status=5 where order_no=? and status=0（影响 0 行 → return）
  ③ Feign → inventory.release（库存侧自身幂等）
  ④ 写 order_status_log
```

**兜底扫描**：`OrderTimeoutJobHandler`（XXL-JOB `orderTimeoutScanHandler`，分片广播）扫
`status=0 and expire_time < now and MOD(id, shardTotal)=shardIndex` 自动关单。

> **两条链路互为兜底**，且终局都收敛到同一个 `closeOrder`，靠 `closeIfPending` 的 CAS（status 0→5）保证最多执行一次。
> ⚠️ 但两者**共用 XXL-JOB 这个调度器**（延迟消息的投递也是 XXL-JOB 驱动的），调度器整体挂掉时两条一起失效。

---

## 6. 支付链路

```
payment.create（需登录）
   ① Feign → order.getOrder(orderNo)：拿 payAmount / userId / status
   ② 越权校验：订单必须属于当前用户
   ③ 金额取自订单（绝不信任前端）
   ④ 已有待支付单则复用，否则新建 payment_record(待支付)
        ↓
payment.mock-pay（模拟第三方支付）
   ⑤ 组装回调报文 + 生成 sign = MD5(paymentNo + amount + secret)
        ↓
payment.callback（网关白名单，免 token）
   ⑥ 验签
   ⑦ CAS：update payment_record set status=1 where payment_no=? and status=0（幂等）
   ⑧ 至少一次投递 payment.success 事件到 MQ
        ↓
order 消费 payment.success → PaymentSuccessConsumer
   @GlobalTransactional(name = "shop-mark-paid")
   ⑨ CAS：order 0→1
   ⑩ Feign → inventory.confirm（锁定 → 真正出库）
```

**要点**：
- 回调是公开接口 → **白名单 + 验签**。
- 回调里只做"更支付记录 + 发消息"，改订单状态交给 MQ 消费（回调要秒回，避免第三方超时重推）。
- 支付成功事件用 **至少一次投递 + 消费端 CAS 幂等**（重复投递无害）。

---

## 7. MQ 拓扑（实际声明）

```
Exchanges
  order.exchange     (topic)              ── order.created ──▶ order.created.queue
  payment.exchange   (topic)              ── payment.success ─▶ payment.success.queue
  delay.exchange     (x-delayed-message)  ── order.delay ─────▶ order.timeout.queue
  retry.exchange     (topic)              ── retry.* ─────────▶ *.retry.queue（TTL 5s → 回原队列）
  dlx.exchange       (topic)              ── *.dlq ───────────▶ *.dlq

Queues
  order.created.queue    消费者：OrderCreatedConsumer（IdempotentConsumeGuard 去重）
  payment.success.queue  消费者：PaymentSuccessConsumer（markPaid）
  order.timeout.queue    消费者：OrderTimeoutConsumer（关单 + 释放库存）
  *.retry.queue          队列级 TTL 5s + DLX 回原队列（有限重试）
  *.dlq                  重试耗尽后的死信
```

常量集中定义在 `shop-common-core` 的 `MqConstants`，交换机/队列在 `shop-common-mq` 的 `ShopRabbitAutoConfiguration` 里声明。
消费端统一走 `MqRetrySupport.handle(...)`：成功 ack；失败未超次 → 重投 retry 队列并 **ack 原消息**；超次 → 投 DLQ 并 ack（**失败也必须 ack，否则无限 requeue**）。

---

## 8. 幂等体系（汇总）

| 环节 | 手段 |
|---|---|
| 下单 | Redis token 原子 DEL + `order_no` 唯一索引 |
| 支付回调 | `payment_no`/`third_trade_no` 唯一 + status CAS |
| MQ 消费 | 业务 CAS（markPaid / closeIfPending 影响行数判断）+ `IdempotentConsumeGuard` |
| 订单状态流转 | `update ... where status = 旧值` |
| 库存锁定/确认/释放 | `inventory_log` 唯一键 `(order_no, type, sku_id)`，**当闸门放在库存变更之前** |
| 库存 TCC | `tcc_fence` 唯一键 `(xid, branch_id)` + 状态机（空回滚/悬挂/幂等/互斥） |

**通用幂等组件 `@Idempotent`**：`IdempotentAspect`（AOP + Lua）已实现，网关 `IdempotentGlobalFilter` 做预检、服务端做原子消费（预约令牌模式）。

---

## 9. 消息可靠投递"三段论"

1. **生产者 → Broker**：本地消息表补发（+ `publisher-confirm-type: correlated`、`publisher-returns: true`）
2. **Broker 内部**：queue `durable` + 消息持久化
3. **Broker → 消费者**：**手动 ack + 有限重试 + 死信队列**（`MqRetrySupport`），业务层再做幂等

---

## 10. 验收结论

| 场景 | 期望 | 状态 |
|---|---|---|
| 正常下单 | 订单落库，库存 `available↓ / locked↑` | ✅ |
| 超卖下单 | 返回 15001，**订单不落库**（Seata 回滚），库存不变 | ✅ |
| 同 token 重复提交 | 16003 | ✅ |
| 超时未支付 | 自动关单（status=5）+ 库存释放 | ✅ |
| 支付成功 | 订单 0→1，库存 `locked→0 / total↓` | ✅ |
| 重复回调 | 幂等，无副作用 | ✅ |
| 显式 TCC 下单 | Try 预占 + 落库；Cancel 补偿；Confirm 延后到支付 | ✅ 已实测 |
