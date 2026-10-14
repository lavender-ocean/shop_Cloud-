# bench · 压测资产

> 计划：[docs/09-load-test-plan.md](../docs/09-load-test-plan.md) · 报告：[docs/10-load-test-report.md](../docs/10-load-test-report.md)

## 工具

| 工具 | 版本 | 说明 |
|---|---|---|
| JMeter | 5.6.3（`G:\Jmeter\apache-jmeter-5.6.3`）| 吞吐型压测，产出 HTML Dashboard |
| Node.js | v22.12.0 | 并发正确性验证（需要精确计数 + 联动 DB 校验）|

> **分工原因**：JMeter 适合"要 QPS/p99/报告"的场景；而 T4「200 抢 10 必须成功 10」要的是**硬计数**，且要联动 SQL 校验，用 Node 更直接。
> ⚠️ 用 Node 做真并发压测**不要用 `fetch`** —— undici 默认连接池会限制并发度，压不出争抢。本项目用 `node:http` + `maxSockets`。

## 目录

```
bench/
├── data/       # 数据准备与清理 SQL
├── jmx/        # JMeter 测试计划
├── node/       # Node 压测脚本
├── tokens/     # 幂等 token 池（CSV，供 T3 参数化）
└── results/    # .jtl 原始结果 + HTML Dashboard
```

## 已完成的脚本

| 脚本 | 用途 |
|---|---|
| `jmx/smoke-ping.jmx` | 工具链冒烟（2 线程 × 20 循环打免鉴权端点）✅ 已验证 |
| `node/load.mjs` | **通用压测脚本**：多轮并发梯度、登录拿 token、`preRequest` 每次取一次性令牌、p50~p99 统计、业务码分类 |
| `node/t4-oversell.mjs` | **T4 防超卖**：N 并发抢 M 库存，输出成功数/失败码分布/延迟分位 |
| `run-t4.sh` | T4 一键跑：清理 → init 库存 → 并发抢 → DB 三方校验 → 清理 |
| `run-t3.sh` | **T3 下单**一键跑：记录库存基线 → 加库存 → 压测 → 校验建单数 → 清理 → **恢复基线** |
| `run-t6.sh` | **T6 多实例分片**一键跑：造 100 条本地消息 → 触发分片广播 → 校验无重复 → 清理 |
| `data/seed-inventory-multisku.sql` | T5 用：50 个库存 SKU |
| `data/seed-local-message-100.sql` | T6 用：100 条待投递本地消息 |
| `data/cleanup-orders.sql` | T3 清理：**按 `remark='LOADTEST'`** 删订单，并**归还 `locked_stock` 计数** |
| `data/cleanup.sql` / `cleanup-local-message.sql` | 清理专用测试 SKU / T6 消息 |
| `node/scenarios/*.json` | 各场景的配置（并发梯度、请求、业务码分类） |

> ⚠️ **`data/cleanup-orders.sql` 的关键点**：`inventory.locked_stock` 是**计数列**，删明细流水不会让它变小。必须先算锁量、再删、再 `locked -= n / available += n`。第一次写错导致库存被"假性占住"。

## 怎么跑

```bash
# 前置：8 个服务已启动（带 -Dfile.encoding=UTF-8）
cd shop-project

# T4 防超卖（默认 200 抢 10，跑完自动清理）
bash bench/run-t4.sh

# 自定义参数：node <port> <skuId> <stock> <concurrency>
node bench/node/t4-oversell.mjs 8084 900001 10 200

# JMeter 非 GUI 模式 + 生成 HTML 报告
jmeter -n -t bench/jmx/t1-spu-page.jmx \
       -l bench/results/t1.jtl \
       -e -o bench/results/t1-report
```

## ⚠️ 压测注意事项（务必遵守）

1. **同机压测**：压测工具与被压服务共享 20 核 CPU。**绝对吞吐数字偏低，只用于相对趋势/正确性/瓶颈定位，不代表生产容量。**
2. **压测机不能成为瓶颈**：观察压测进程 CPU，**> 50% 结果即不可信**。
3. **JVM 预热**：前 30 秒（JIT/类加载/连接池填充）数据丢弃。
4. **至少 3 轮报中位数**，不要只报最好成绩。
5. **错误率要分类**：`15001 库存不足`、`10006 重复提交` 是**业务正确拒绝**，不算故障。
6. **记录配置快照**：commit hash + 连接池/线程池参数 + `shop.order.tx-mode`。否则结果不可复现。
7. **⚠️ 打同一个 SKU 会被行锁串行化**：同 SKU 高并发测到的是**行锁排队**，不是连接池能力。要考察连接池必须 **多 SKU 分散热点**（见 [10](../docs/10-load-test-report.md) §3.3）。
8. **压测数据必须清理干净**，否则污染 `docs/08` §5 的数据一致性核对。
