# 02 · 数据库设计

## 1. 全局规范

| 项 | 规范 | 理由 |
|---|---|---|
| 引擎/字符集 | InnoDB / `utf8mb4` | 支持 emoji、生鲜商品名 |
| 主键 | `BIGINT`，雪花 ID（`IdWorker.getId()`） | 分布式唯一、趋势递增 |
| 金额 | `BIGINT` 存**分** | 杜绝浮点误差 |
| 状态 | `TINYINT` + 状态机，流转用 CAS | 并发安全 |
| 公共字段 | `create_time / update_time / create_by / update_by / deleted` | 逻辑删除 + 审计 |
| 外键 | **不建物理外键** | 性能与运维 |
| 命名 | 下划线；避开保留字（订单表用 `order_info`） | |

- **一服务一 schema**（单实例多 schema），不跨库 join，跨服务靠 Feign / MQ。
- 参与的写库都要有 `undo_log`（Seata AT 依赖）：**`shop_order`、`shop_inventory`**。

## 2. schema 一览（实际）

```
shop_auth      认证与 RBAC
shop_user      用户资料（⚠️ 业务表未实现，仅 service 骨架）
shop_product   商品
shop_inventory 库存
shop_order     订单（+ 本地消息表）
shop_payment   支付
shop_promotion 促销（未实现）
nacos_config   Nacos 配置持久化
xxl_job        XXL-JOB（已起未接入）
```

## 3. `shop_auth`

```
sys_user            id, username(UK), password(BCrypt), nickname, phone, email,
                    user_type(1普通 2管理员), status, last_login_time, + 公共字段
sys_role            id, role_code(UK), role_name, description, data_scope, status, sort, + 公共字段
sys_permission      id, parent_id, perm_code(UK), perm_name, type(1菜单 2按钮 3接口),
                    path, component, icon, sort, status, + 公共字段
sys_user_role       id, user_id, role_id, UK(user_id, role_id)
sys_role_permission id, role_id, permission_id, UK(role_id, permission_id)
```

**种子数据**：角色 `ROLE_ADMIN(1)` / `ROLE_OPERATOR(2)`；权限树（系统管理 / 商品管理 / 订单管理 三级，菜单 `type=1` + 按钮 `type=2`，权限码如 `product:spu:add`）。admin 绑 `ROLE_ADMIN`（由 `AdminSeeder` 在启动时按真实 `user_id` 绑定）。

> 权限码既用于**菜单渲染**（`component` 字段就是前端组件路径），也用于**服务端 `@RequirePermission` 校验**——前后端同一套模型。

## 4. `shop_product`（超市模型）

```
category  id, parent_id, name, level, sort, icon, status, + 公共字段      idx(parent_id)
brand     id, name(UK), logo, description, status, + 公共字段
spu       id, spu_code(UK), name, category_id, brand_id, main_image, images(TEXT,JSON数组),
          detail, price_min(分), price_max(分), unit(如500g/盒), is_cold_chain, status(1上架 0下架),
          + 公共字段                                                      idx(category_id) idx(brand_id) idx(status)
sku       id, spu_id, sku_code(UK), name, spec_json, image, price(分), status, + 公共字段   idx(spu_id)
```

**关键设计**：
- **`sku` 不存库存**——库存属独立服务，展示时 Feign 批量查。
- `unit` / `is_cold_chain` 是"超市味"字段（区别于普通商城）。
- `price_min/price_max` 由 SKU 价格区间算出，列表页展示用。

## 5. `shop_inventory`

```
inventory      id, sku_id(UK), total_stock, available_stock, locked_stock, warn_stock,
               version, + create_time/update_time/deleted
inventory_log  id, sku_id, order_no, type(1入库 2锁定 3扣减 4释放), quantity,
               before_stock, after_stock, biz_no, remark, create_time
               UK(order_no, type, sku_id)   ← 幂等关键
               idx(sku_id)
               ⚠️ before_stock / after_stock 两列存在但当前代码未写入，查询恒为 NULL（要么补写，要么删列）
tcc_fence      id, xid, branch_id, action_name(try/confirm/cancel),
               status(0 TRYING / 1 COMMITTED / 2 ROLLBACKED / 3 CONFIRMED),
               business_key(=orderNo), items_json(TEXT, Try 的 items 快照)
               UK(xid, branch_id)           ← TCC 防坑关键
```

**三段式库存**：`available`（可用）/ `locked`（锁定）/ `total`（总）。
- 下单 → `lock`：`available -n, locked +n`
- 支付 → `confirm`：`locked -n, total -n`
- 取消/超时 → `release`：`locked -n, available +n`

**三条硬约束**：
- **防超卖**：`update ... where available_stock >= n`，影响行数 0 即库存不足。
- **幂等（顺序是全部）**：`inventory_log` 唯一键 `(order_no, type, sku_id)` **必须当闸门放在库存变更之前**——先写流水，写入成功才动库存；撞唯一键即"该动作已完成"，直接跳过。
  > 反模式：先 `update` 再 `insert` 并 `catch(DuplicateKeyException)` 放行。InnoDB 的唯一键冲突只回滚**那一条 insert**，事务里已成功的 `update` 会照常提交 → **重复扣减/虚增**。`countByBiz` 前置查询也挡不住并发（查与写不是原子），只能当"顺序检测"用。
- **防死锁**：多 item 按 `skuId` **升序**加锁，避免两笔订单 item 顺序相反时形成 AB-BA 死锁。

**TCC 三条防护（`tcc_fence`）**：
| 坑 | 触发场景 | 判定 |
|---|---|---|
| **空回滚** | Try 未到就收到 Cancel | 查不到记录 → 写 `status=2` 占位、跳过业务（否则迟到的 Try 会锁住永远无人释放的库存） |
| **悬挂** | 已回滚的分支又收到 Try | 查到 `status=2` → 拒绝执行 |
| **幂等** | 同一分支重复 Try/Confirm/Cancel | 状态机 + `UK(xid, branch_id)`；`tryCommit` 的 `expectedStatuses` 必须**含 `COMMITTED`** |
| **互斥** | Confirm 后又 Cancel / 反之 | `status=3` 时 `cancelBegin` 拒绝；`status=2` 时 `confirmBegin` 跳过 |

## 6. `shop_order`

```
order_info       id, order_no(UK), user_id, total_amount, discount_amount, freight_amount, pay_amount,
                 status, pay_type, pay_time,
                 receiver_name/phone/province/city/district/detail,   ← 地址快照
                 remark, expire_time, cancel_type, cancel_reason, finish_time, version, + 公共字段
                 idx(user_id, status)  idx(status, expire_time)  idx(create_time)
order_item       id, order_id, order_no, spu_id, sku_id,
                 spu_name, sku_name, sku_image, spec_json,            ← 商品快照
                 price, quantity, total_amount, create_time          idx(order_id)
order_status_log id, order_no, from_status, to_status, operator_type(1用户 2系统 3管理员),
                 operator_id, remark, create_time                    idx(order_no)
local_message    id, msg_id(UK), biz_type, biz_no, payload,
                 status(0待投递 1投递成功 2投递中 3投递失败),
                 retry_count, next_retry_time, claim_time, create_time   idx(status, next_retry_time)
```

> `local_message.claim_time` 是「投递中」超时回收的判据：`status=2 AND claim_time < NOW()-60s` → 置回待重试（`LocalMessageMapper.xml` 的 `releaseStaleClaims`）。

**订单状态机**：

| 值 | 状态 | 允许流转 |
|---|---|---|
| 0 | 待支付 | 1 已支付 / 5 已关闭 |
| 1 | 待发货 | 2 配送中 / 6 退款中 |
| 2 | 配送中 | 3 已完成 |
| 3 | 已完成 | — |
| 4 | 已取消（用户） | — |
| 5 | 已关闭（超时） | — |
| 6/7 | 退款中/已退款 | 后置 |

**设计理由**：商品快照 + 地址快照（订单是历史事实）；`order_no` 唯一索引（幂等兜底）；`idx(status, expire_time)`（超时扫描命脉）。

## 7. `shop_payment`

```
payment_record id, payment_no(UK), order_no, user_id, channel(1 Mock), amount,
               status(0待 1成功 2失败 3退款), third_trade_no(UK), callback_content,
               pay_time, + 公共字段                                    idx(order_no) idx(user_id)
refund_record  id, refund_no(UK), order_no, payment_no, amount, reason, status, refund_time, + 公共字段
```

> `payment_no` + `third_trade_no` 双唯一索引 → 回调幂等的双保险。

## 8. `shop_cart`（不落库）

```
Key:   cart:{userId}     Hash
Field: {skuId}
Value: CartItem JSON { skuId, spuId, spuName, skuName, image, specJson,
                       price(加入时快照), quantity, selected, addTime }
```

## 9. 索引与幂等小结

| 场景 | 幂等键 |
|---|---|
| 下单 | Redis 幂等 token（原子 DEL）+ `order_info.order_no` 唯一索引 |
| 支付回调 | `payment_record.payment_no` / `third_trade_no` 唯一 + `status=0` 的 CAS |
| 库存锁定/确认/释放 | `inventory_log` 唯一键 `(order_no, type, sku_id)` |
| 订单状态流转 | `update ... where status = 旧值`（CAS） |
| 本地消息 | `local_message.msg_id` 唯一 |
