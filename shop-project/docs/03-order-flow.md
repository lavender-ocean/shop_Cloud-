# 03 · 订单主链路（C）

本文件是整个项目的**脊椎**，绝大多数亮点挂在链路上。

---

## C-1 核心原则：分场景选一致性方案

| 场景 | 方案 | 为什么 |
|---|---|---|
| 普通下单（同步段：扣库存 / 锁券 / 建单） | **Seata AT** | 跨服务、需原子、并发可接受 |
| 下单后长尾（积分 / 通知 / 统计） | **本地消息表 + MQ** | 允许延迟、绝不阻塞用户 |
| 支付回调 → 改订单状态 | **MQ 最终一致** | 回调必须快速返回 |
| 超时关单（系统触发、低频） | **Seata AT** | 需强一致，低频可接受 |
| 秒杀（后续亮点） | **Redis Lua + MQ** | Seata 全局锁扛不住 |

> 结论：**同步必经、必须原子的短链路用 Seata；可延迟、可补偿的长尾用本地消息表 + MQ；超高并发的秒杀放弃强一致。**

---

## C-2 下单同步段（Seata AT 全局事务）

```
用户 → 网关 → order-service
             │  @GlobalTransactional (TM)
             ├─1 幂等校验：Redis token(Lua) + order_no 唯一索引兜底
             ├─2 Feign → product   校验 SKU / 取价（多级缓存）
             ├─3 Feign → inventory 扣库存   ← AT 分支，乐观锁
             ├─4 Feign → promotion 锁定优惠券 ← AT 分支
             ├─5 本地事务：insert order_info + order_item + local_message
             └─ Seata 全局提交
             ▼
        [事务提交后] 投递 MQ：
             ├─ delay.exchange → 超时关单
             └─ order.exchange → 积分 / 通知
```

**Seata AT 原理：**
- 两阶段：一阶段各分支提交本地事务并写 `undo_log`；二阶段全局提交删 `undo_log`，回滚则用 `undo_log` 反向补偿。
- 全局锁：一阶段先抢全局锁再提交，防脏写。
- 隔离级别：默认读未提交 + 全局锁，非事务读可用 `@GlobalLock`。

**库存扣减（防超卖）：**
```sql
update inventory set available_stock = available_stock - #{n}, version = version + 1
where sku_id = #{skuId} and available_stock >= #{n};
-- 影响行数=0 → 库存不足 → 抛异常 → 全局回滚
```

---

## C-3 本地消息表（RabbitMQ 无事务消息）

```sql
local_message
  id, msg_id UK, biz_type, biz_no, payload JSON,
  status TINYINT,                        -- 0待投递 1已投递 2失败
  retry_count, next_retry_time, create_time
  IDX(status, next_retry_time)
```

1. 本地事务里同事务写 `local_message`（status=0）。
2. 事务提交后立即尝试投递（publisher confirm）。
3. 定时任务扫描 `status=0` 补发，confirm 成功后置 1。
4. 下游按 `msg_id` 幂等。

> 回答经典问题："如何保证本地事务与消息发送的原子性？"——本地消息表 + 定时补偿。

---

## C-4 支付链路

```
用户 → payment-service：创建 payment_record(待支付) → Mock 收银台
Mock 第三方 → 回调 POST /api/payment/callback   ← 网关白名单
   ├─1 验签
   ├─2 幂等：payment_no / third_trade_no 唯一索引 + status CAS
   ├─3 更新 payment_record = 成功
   └─4 写 local_message(payment.success) → payment.exchange
                  ▼
        order-service 消费 → CAS: update order set status=1 where order_no=? and status=0
                  ▼
        user 加积分 / notification 通知
```

- 回调是公开接口，必须**网关白名单 + 验签**。
- 回调里只做"更支付记录 + 发消息"，改订单状态交给 MQ。

---

## C-5 超时关单（延时 + 死信）

**① 延时队列（用延迟插件 `x-delayed-message`）**
```
下单 → delay.exchange(x-delayed-message) → order.timeout.queue
   → order-service 消费：
        if 订单 status == 0: [Seata AT] 关单 + inventory 归还 + promotion 释放券
        else: 忽略
```
> 采用**延迟插件**（非队列级 TTL + DLX）：支持任意延时、无队头阻塞。

**② 业务死信队列（消费重试耗尽兜底）**
```
business.exchange → order.queue → 失败 nack → 重试 → 耗尽 → order.dlq → 告警 / 补偿
```

**双重保险**：XXL-JOB 定时扫 `status=0 and expire_time < now` 兜底。

---

## C-6 关单 / 退款回滚（跨服务，Seata AT）

关单需同时改 `order`（状态）+ `inventory`（归还）+ `promotion`（释放券），系统触发、低频，用 Seata AT。
**库存归还幂等**：靠 `inventory_log` 判断该 `order_no` 是否已有归还记录，防重复归还（虚增）。

---

## C-7 MQ 拓扑

```
Exchanges
  order.exchange    (topic)   ── order.created ──▶ order.created.queue
  payment.exchange  (topic)   ── payment.success ─▶ payment.success.queue
  delay.exchange    (x-delayed-message) ── order.delay ──▶ order.timeout.queue
  dlx.exchange      (direct)  ◀── 重试耗尽

Queues
  order.created.queue    消费者：积分 / 通知
  payment.success.queue  消费者：order-service
  order.timeout.queue    消费者：order-service（关单）
  order.dlq              重试耗尽兜底
```

> 全部常量集中在 `shop-common-mq` 的 `MqConstants`。

---

## C-8 幂等体系

| 环节 | 幂等手段 |
|---|---|
| 下单 | Redis token（Lua GET+DEL）+ `order_no` 唯一索引 |
| 支付回调 | `payment_no` / `third_trade_no` 唯一索引 + status CAS |
| MQ 消费 | `msg_id` 去重（Redis SETNX / 去重表） |
| 状态流转 | CAS：`update ... where status = 旧值` |
| 库存归还 | `inventory_log` 唯一记录 |

> 统一封装 `@Idempotent` 注解（AOP + Redis），三处复用 → 可长成"通用幂等组件"亮点。

---

## C-9 状态机与 CAS

```sql
update order_info set status = 1, pay_time = now()
where order_no = #{orderNo} and status = 0;
-- 影响行数=0 → 状态已被改 → 幂等忽略 / 抛错
```

支付回调与超时关单可能并发到达，**必须 CAS**，否则出现"刚关单又变已支付"。

---

## C-10 消息可靠投递"三段论"

1. **生产者 → Broker**：publisher confirm + return + 本地消息表补发
2. **Broker 内部**：queue `durable` + message `persistent`
3. **Broker → 消费者**：手动 ack + 消费幂等 + 重试 + 死信队列

---

## C-11 验收

1. 下单后 `available_stock` 正确扣减、`locked_stock` 正确增加。
2. 支付回调后订单变已支付，**重复回调无副作用**。
3. 超时（改短）订单自动关闭且库存/券正确回滚。
4. 制造消费异常，消息进 DLQ 且能被兜底任务捞回。
