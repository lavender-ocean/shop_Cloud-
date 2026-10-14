# 09 · 压测计划

> **状态：待执行**（需项目 owner 批准后开跑）
> 执行结果写入 [10-load-test-report.md](10-load-test-report.md)
> 脚本与原始数据在 `shop-project/bench/`

---

## 0. 目标与非目标

### 目标（要拿到的数字）
1. **核心链路吞吐与延迟**：下单、商品查询的 QPS / p99 / 错误率
2. **并发正确性**：200 并发抢 10 库存，**成功数必须恰好为 10**
3. **多级缓存的量化收益**：Redis 缓存命中率
4. **多实例调度正确性**：分片任务无重复处理
5. **瓶颈定位**：找出真正的瓶颈（是业务代码？连接池？DB？）

### 非目标（明确不做）
- ❌ 不做容量规划 / 长时间稳定性测试（个人项目不值得，也没意义）
- ❌ 不做多机分布式压测（只有一台机器）
- ❌ 不追求"好看的绝对数字" —— **同机压测的绝对值必然偏低，只保证相对趋势与正确性**

---

## 1. 环境与拓扑

| 项 | 值 |
|---|---|
| 机器 | 单机，**20 逻辑核** |
| 被压系统 | 8 个 JVM 服务 + MySQL + Redis + RabbitMQ + Nacos + Seata + XXL-JOB |
| 压测机 | **同一台机器**（压测工具与业务系统抢 CPU/内存）|
| 服务端口 | gateway 8080 / auth 8081 / user 8082 / product 8083 / inventory 8084 / cart 8085 / order 8086 / payment 8087 |
| 事务模式 | 记录压测时的 `shop.order.tx-mode` 值（at / tcc）|

### ⚠️ 同机压测的声明（**必须写进报告**）
> 压测工具与被压服务运行在同一台机器上，共享 20 核 CPU、内存与磁盘 IO。因此**绝对吞吐数字会明显低于独立压测机的结果**，本文档的数字仅用于：① 相对趋势对比（如加缓存前/后）；② 并发正确性验证；③ 瓶颈定位。**不代表生产容量。**

---

## 2. 工具选型与验证结果

| 工具 | 版本 | 位置 | 状态 |
|---|---|---|---|
| **JMeter** | 5.6.3 | `G:\Jmeter\apache-jmeter-5.6.3` | ✅ 已验证 |
| Node.js | v22.12.0 | 系统 PATH | ✅ |
| JDK | 17（`JAVA_HOME=C:\Program Files\Java\jdk-17`）| — | ✅ JMeter 依赖 |

### 工具链验证（已完成）
```
jmeter -n -t bench/jmx/smoke-ping.jmx -l results/smoke.jtl -e -o results/smoke-report
→ summary = 40 in 00:00:01 = 67.3/s  Avg: 3ms  Max: 41ms  Err: 0 (0.00%)
→ HTML Dashboard 生成成功（index.html / statistics.json / content/）
```
> 冒烟结果留在 `bench/results/smoke-report/` 作为工具链可用性证据。

### 分工（为什么不是二选一）

| 用 JMeter | 用 Node |
|---|---|
| 要 **QPS / p99 / 错误率** 的吞吐型压测（T1/T2/T3/T5）| 要**精确计数 + 联动 DB 校验**（T4/T6）|
| 需要 **CSV 参数化**（幂等 token 池）| 一次性并发脚本，几秒出结果 |
| 要产出**可提交、可截图**的 HTML 报告 | 需要统计"成功数恰好等于 N"这种硬断言 |

---

## 3. 指标定义（怎么算才不算耍流氓）

| 指标 | 定义 | 注意 |
|---|---|---|
| **吞吐 QPS** | 有效样本数 ÷ 有效时长；**剔除 ramp-up 与预热期** | 别拿峰值瞬时值 |
| **延迟 p50/p90/p95/p99** | JMeter HTML Dashboard 自带 | **只看 p99，不看平均值**（平均值会掩盖长尾）|
| **错误率** | **必须分类统计** ↓ | — |
| ↳ 系统错误 | 5xx / 连接超时 / `code=10999` | 这才是"真错误" |
| ↳ 业务拒绝 | `15001` 库存不足、`10006` 重复提交 | **预期内**，断言里要当成功处理，否则错误率虚高 |
| **资源指标** | 应用 CPU、`HikariCP` active/pending、Redis `keyspace_hits/misses`、MQ 队列深度、MySQL `Threads_connected` | 用来定位瓶颈 |

---

## 4. 测试场景

### T1 · 商品列表分页（读基线）

| 项 | 值 |
|---|---|
| 端点 | `GET /api/product/spu/page?pageNum=1&pageSize=10`（经网关，需 token）|
| 并发梯度 | **50 → 100**，每档持续 60s，ramp-up 10s |
| 数据 | 走 DB 分页（**不命中多级缓存**，因为 `page()` 不走缓存）|
| 指标 | QPS、p99、错误率 |
| 通过标准 | 错误率（系统类）= 0 |

### T2 · 商品详情（**多级缓存**）

| 项 | 值 |
|---|---|
| 端点 | `GET /api/product/spu/{spuId}`（`SpuService.detail` → Caffeine L1 + Redis L2）|
| 并发 | **100**，持续 60s |
| 数据 | 单 SPU（现只有 1 个）或 500 SPU + CSV 喂 id |
| 指标 | QPS、p99、**Redis 命中率** |
| 命中率取法 | 压测前后各取一次 `redis-cli -a <pwd> info stats` 的 `keyspace_hits/keyspace_misses`，差值算命中率 |
| 通过标准 | 命中率 > 90%，p99 < T1 的 p99 |

> **T2b 冷热对比（可选，更严谨但要造数据）**：造 500 个 SPU，用 CSV 喂 id；第一轮每个 id 只打一次（全部回源 DB），第二轮同一批 id 再打（全部命中）。

### T3 · 下单主链路（含 Seata）

| 项 | 值 |
|---|---|
| 端点 | `POST /api/order`（需 `Authorization` + `Idempotent-Token` 头）|
| 并发 | **20 → 50**，持续 60s |
| 数据 | 专用压测 SKU（库存 10 万）+ 幂等 token 池（CSV，5000 个）+ addressId |
| 指标 | QPS、p99、错误率（**分类**）、**实际建单数 vs 成功数是否一致** |
| 通过标准 | 系统错误率 < 1%；`order_info` 新增行数 == 成功样本数 |
| 副作用 | **会真建订单**，跑完必须执行清理脚本 |

> ⚠️ 这条链路最重（网关 → order → product → inventory → Seata → DB），也是**最可能压出瓶颈**的地方。

### T4 · 防超卖（**并发正确性** ⭐）

| 项 | 值 |
|---|---|
| 工具 | **Node**（`bench/node/t4-oversell.mjs`）|
| 目标 | `POST /inner/inventory/lock`（直连 8084，剥掉网关与订单逻辑）|
| 方式 | 专用 SKU 库存 **10**，**200 个并发请求**，每个用不同 orderNo、各锁 1 件 |
| 指标 | **成功数（code=0）**、失败返回码分布、`inventory` 终态、`inventory_log` 行数 |
| 通过标准（硬性）| **成功数 == 10** 且 `available=0` 且 `locked=10` 且流水恰好 10 条 `type=2` |
| 清理 | 跑完删掉该 SKU 的 inventory / inventory_log 测试数据 |

### T5 · 库存 lock 单接口（库存层极限 + **连接池两轮对比**）

| 项 | 值 |
|---|---|
| 端点 | `POST /inner/inventory/lock`（直连 8084）|
| 并发 | **100**，持续 60s |
| 数据 | ⚠️ **必须是 50~100 个不同的 SKU，随机打**（每个库存充足）|
| 指标 | QPS、p99 |
| 目的 | 剥掉网关/订单逻辑，看**库存层自身**的极限 |
| **两轮对比** | 第 1 轮 HikariCP 默认（`maximum-pool-size=10`）→ 第 2 轮调到 50；对比 p99 |

> ⚠️ **为什么必须多 SKU**：T4 实测发现，**打同一个 SKU 时 200 个请求会被 InnoDB 行锁完全串行化**（见 [10](10-load-test-report.md) §3.3）。那样测到的是行锁，不是连接池。
> 只有**分散到不同 SKU**（无行锁争抢），p99 才真正反映连接池/线程池的能力 —— 这样"连接池 10 vs 50"的两轮对比才有意义。

### T6 · 多实例分片任务（协调正确性）

| 项 | 值 |
|---|---|
| 方式 | 临时起**第二个 order 实例**（`--server.port=18086`），XXL-JOB 分片广播自动分 0/1 |
| 数据 | 造 100 条待投递的 `local_message`（`status=0`）|
| 指标 | 处理总数是否 == 100、**有无重复投递**（按 `local_message.id` 去重校验）|
| 通过标准 | 合计处理 == 100 且无重复 |

---

## 5. 数据准备

| 项 | 内容 | 脚本 |
|---|---|---|
| 专用压测 SKU | `skuId = 900001`，`init` 库存 **100000**（T5 用 100 万）| `bench/data/seed-pressure-sku.sql` |
| 幂等 token 池 | 调 `GET /api/order/token` × 5000 → `order-tokens.csv` | `bench/data/gen-tokens.sh` |
| 收货地址 | 已有 `2109660387913506818` 可复用（或新建一个专用于压测）| — |
| 500 个 SPU | 仅 T2b 需要 | `bench/data/seed-spu-500.sql` |
| 100 条待投递消息 | 仅 T6 需要 | `bench/data/seed-local-message-100.sql` |
| 清理 | 删压测订单 / 流水 / inventory 测试行 | `bench/data/cleanup.sql` |

> 压测产生的数据**必须可完全清理**，否则会污染后续的数据一致性核对。

---

## 6. 执行顺序与时间预算

```
① 前置检查（§7 清单逐项打勾）                          ~10 min
② 数据准备（压测 SKU + token 池）                      ~5 min
③ T4 防超卖（Node，几秒出结果，零副作用，最值钱先拿下）   ~2 min
④ T1 / T5（纯读 + 内部接口，无副作用）                 ~10 min
⑤ T2（读，含 Redis 命中率）                            ~5 min
⑥ T3 下单（会写数据，放最后）+ 清理                     ~15 min
⑦ T6 多实例（需起第二个实例）                          ~10 min
⑧ 回填数字到 10-load-test-report.md 与 07 §4.1          ~10 min
```
**总计约 1 小时**（不含 500 SPU 造数与服务重启）。

---

## 7. 前置检查清单（开跑前逐项打勾）

```
[ ] 8 个服务全部 UP（8080-8087）
[ ] 所有服务启动时带 -Dfile.encoding=UTF-8
[ ] 记录当前 git commit hash（结果可复现的前提）
[ ] 记录 shop.order.tx-mode 的值（at / tcc）
[ ] ⚠️ 确认/记录 HikariCP maximum-pool-size（默认 10，极可能是瓶颈）
[ ] 确认 Tomcat max-threads（默认 200）
[ ] 压测专用 SKU 已 init 且库存充足
[ ] 幂等 token 池已生成（≥ 6000 个，多备）
[ ] 压测账号 admin 可登录、addressId 有效
[ ] MySQL / Redis / RabbitMQ 水位正常（无积压）
[ ] JMeter 已用 -Xmx 限制（建议 1g），避免压测机自己成为瓶颈
```

---

## 8. 风险与"防自欺欺人"8 条

1. **同机压测** → 绝对值偏低，**必须声明**，只讲相对趋势与正确性。
2. **压测机可能成为瓶颈** → 压测时观察 JMeter 进程 CPU，**> 50% 说明压测机出力了，结果不可信**。
3. **JVM 预热** → 前 30 秒（JIT / 类加载 / 连接池填充）数据**丢弃**，报稳定期。
4. **别只报一次最好成绩** → 每档降**跑 3 轮**，报中位数。
5. **⚠️ HikariCP 默认只有 10 个连接** → 50 并发下单会被池子卡住，p99 直接爆。**要么压测前调大，要么把"连接池是瓶颈"本身写成结论**。
6. **错误率要分类** → `15001 库存不足` 是业务正确拒绝，不是故障。断言里要区分。
7. **同时看中间件** → Redis 内存、MQ 队列深度、MySQL `Threads_connected` / `SHOW PROCESSLIST`，瓶颈可能在 DB 而不是应用。
8. **记录配置快照** → commit hash + 连接池/线程池参数 + tx-mode。**否则结果不可复现**。

---

## 9. 产出物与目录

```
shop-project/bench/
├── README.md                    # 环境 / 方法 / 结论摘要 / 注意事项
├── data/
│   ├── seed-pressure-sku.sql
│   ├── gen-tokens.sh
│   ├── seed-spu-500.sql         # 仅 T2b
│   ├── seed-local-message-100.sql  # 仅 T6
│   └── cleanup.sql
├── jmx/
│   ├── smoke-ping.jmx           # ✅ 已完成（工具链验证）
│   ├── t1-spu-page.jmx
│   ├── t2-spu-detail.jmx
│   ├── t3-create-order.jmx      # CSV 参数化 token 池
│   └── t5-inventory-lock.jmx
├── node/
│   ├── t4-oversell.mjs          # 200 抢 10 + 自动查 DB 校验
│   └── t6-shard-check.mjs       # 分片去重校验
├── tokens/order-tokens.csv
└── results/                     # .jtl + HTML Dashboard
```

---

## 10. 验收标准（什么算"压测通过"）

| 场景 | 硬性标准 |
|---|---|
| T4 防超卖 | **成功数恰为 10**；`available=0` / `locked=10`；流水恰 10 条 → **任一不满足即视为发现 BUG** |
| T1 / T2 / T5 | 系统错误率 = 0；p99 记录为基线（T2 命中率 > 90%）|
| T3 下单 | 系统错误率 < 1%；`order_info` 新增行数 == 成功样本数 |
| T6 分片 | 合计处理数 == 待处理数，且无重复 |

> 如果 T4 没得到 10（比如得到 11 或 9），**说明库存并发控制有问题，要回头查 `InventoryService` 的 insert-first 闸门**，而不是改数字。

---

## 11. 结果填写模板（交给 10 号文档）

```
【环境快照】commit=______  tx-mode=___  hikari.max-pool=___  tomcat.max-threads=___
【T1 商品列表】 50并发 → QPS=___  p99=___ms  系统错误=__%
              100并发 → QPS=___  p99=___ms  系统错误=__%
【T2 商品详情】100并发 → QPS=___  p99=___ms  Redis 命中率=__%
【T3 下单】    20并发 → QPS=___  p99=___ms  业务拒绝(15001)=__  系统错误=__%  实际建单=___
               50并发 → QPS=___  p99=___ms  业务拒绝(15001)=__  系统错误=__%  实际建单=___
【T4 防超卖】  200 抢 10 → 成功=___（期望 10）  失败码分布=___  available=__ locked=__
【T5 库存lock】100并发 → QPS=___  p99=___ms
【T6 分片】    2 实例 → 处理=___/100  重复=___ 条
【瓶颈结论】   ______________________
【踩坑/发现】  ______________________
```

---

## 12. 已确认的执行决策（owner 拍板）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | **HikariCP 连接池** | ✅ **两轮对比**：先用默认 10 跑一轮 → 再调到 50 跑第二轮，产出"p99 从 X 降到 Y，瓶颈是连接池"的结论 |
| 2 | **T2 缓存收益量化** | ✅ **读 Redis 命中率**（压测前后取 `keyspace_hits/misses` 差值），不造 500 SPU |
| 3 | **首轮范围** | ✅ **先只跑 T4 防超卖**（已跑通，见 [10](10-load-test-report.md) §3）；其余场景待批 |

### 执行进度
```
[x] JMeter 工具链验证（40 样本 / 0 失败 / HTML 报告生成）
[x] T4 防超卖 —— 通过：200 抢 10 → 成功恰好 10
[x] T1 商品列表 —— 直连：3,105 QPS(50并发) / 2,786(100并发)，0 错误
[x] T1b 经网关 —— 3 QPS 限流（30s 仅 90 次成功，其余 429）★ 见 [10](10-load-test-report.md) §7-①
[x] T2 商品详情 —— 直连：5,023 QPS，L1 命中率 ≈ 100%
[x] T5 库存只读 —— 直连 50 SKU：4,528 QPS(100并发)，0 错误
[x] T3 下单主链路 —— 164.9 QPS / p50 104ms(20并发)，7,766 单全部落库，0 错误
[x] T6 多实例分片 —— 2 实例分片 0/2 + 1/2，100 条全部投递、无重复无遗漏
[x] T4/T5/T3/T6 测试数据已清理，库存恢复基线，一致性自检全绿
[ ] HikariCP 两轮对比（需多 SKU 写场景，已确认 T4 的慢是行锁而非池子）
```

### ⚠️ 执行前必读：本轮踩到的环境/脚本陷阱
1. **Nacos `SENTINEL_GROUP` 有 3 条限流规则**（网关路由 `shop-product`、`order:create`、`product:getSku`）。**压测前必须确认，否则测的是限流器。** 当前已放宽到 200 / 2000 / 2000。
2. **机器上有两台 Redis**，服务原先连的是**宿主机原生 Redis（无密码）**；已把 compose 的 redis 宿主端口改到 **6380** 并在 Nacos 显式配置。测 Redis 指标时注意连对实例。
3. **JS 处理雪花 ID 必须用字符串**：`2^53` 精度截断会让 `...818` 变成 `...800`（见 [10](10-load-test-report.md) §7-⑤）。
4. **清理带计数列的库存要"先算再删再还"**：`locked_stock` 不会随流水删除而变化（见 [10](10-load-test-report.md) §7-⑥）。
