# 02 · 数据库设计（B）

## B-1 全局规范

| 项 | 规范 | 理由 |
|---|---|---|
| 引擎 / 字符集 | InnoDB / `utf8mb4` | 支持 emoji、生鲜商品名 |
| 主键 | `id BIGINT`，雪花 ID | 分布式唯一、趋势递增（配合 Jackson Long→String） |
| 金额 | `BIGINT` 存**分** | 杜绝浮点误差 |
| 状态 | `TINYINT` + 状态机 | 流转用 CAS |
| 时间 | `DATETIME` | 可读性好 |
| 公共字段 | `create_time / update_time / create_by / update_by / deleted / version` | 逻辑删除 + 乐观锁 + 审计 |
| 外键 | **不建物理外键** | 影响性能与运维 |
| 命名 | 下划线；**避开保留字**，订单表用 `order_info` | `order` 是保留字 |

---

## B-2 库（schema）划分

一服务一 schema（演示环境单实例多 schema）：

```
shop_auth   shop_user   shop_product   shop_inventory
shop_order  shop_payment  shop_promotion
```

每个业务 schema **都要建 `undo_log` 表**（Seata AT 回滚依赖）。

---

## B-3 `shop_auth`（认证 + RBAC）

```sql
-- 登录身份
sys_user
  id BIGINT PK, username VARCHAR(64) UK, password VARCHAR(100),   -- BCrypt
  nickname VARCHAR(64), phone VARCHAR(20), email VARCHAR(100),
  status TINYINT DEFAULT 1, last_login_time DATETIME,
  + 公共字段
  UK(username), IDX(phone)

sys_role
  id, role_code VARCHAR(64) UK, role_name, description,
  data_scope TINYINT DEFAULT 1,          -- 1全部 2本人（可后置）
  status, sort, + 公共字段

sys_permission                           -- 菜单 + 按钮 + 接口 统一为权限
  id, parent_id BIGINT DEFAULT 0, perm_code VARCHAR(128) UK,      -- 如 order:list
  perm_name, type TINYINT,               -- 1菜单 2按钮 3接口
  path, component, icon, sort, status, + 公共字段
  IDX(parent_id)

sys_user_role        (id, user_id, role_id, UK(user_id, role_id))
sys_role_permission  (id, role_id, permission_id, UK(role_id, permission_id))
```

- 权限用**一张表**统管菜单/按钮/接口，鉴权统一判 `perm_code`。
- **refresh token 不落库**，放 Redis。

---

## B-4 `shop_user`

> `sys_user`（凭证，auth 服务）与用户资料（user 服务）**分库**。注册时 auth 建凭证 → 发 MQ → user 建 profile（最终一致）。

```sql
user_profile
  id, user_id BIGINT UK, nickname, avatar, gender TINYINT,
  birthday DATE, real_name, status, + 公共字段

user_address
  id, user_id, receiver_name, receiver_phone,
  province, city, district, detail_address, tag VARCHAR(16),
  is_default TINYINT, + 公共字段
  IDX(user_id)

user_member
  id, user_id UK, level TINYINT, points INT, total_points INT, grow_value INT, + 公共字段

user_points_log
  id, user_id, change_points INT, type TINYINT, biz_no VARCHAR(32), remark, create_time
  IDX(user_id, create_time)
```

---

## B-5 `shop_product`（超市商品模型）

```sql
category      (id, parent_id, name, level, sort, icon, status, + 公共字段)  IDX(parent_id)
brand         (id, name UK, logo, description, status, + 公共字段)

spu                                     -- 商品
  id, spu_code VARCHAR(32) UK, name, category_id, brand_id,
  main_image, images JSON, detail MEDIUMTEXT,
  price_min BIGINT, price_max BIGINT,
  unit VARCHAR(16),                     -- 超市特色：500g / 盒 / 袋
  is_cold_chain TINYINT DEFAULT 0,      -- 超市特色：冷链
  status TINYINT, + 公共字段
  IDX(category_id), IDX(brand_id), IDX(status)

sku                                     -- 规格
  id, spu_id, sku_code VARCHAR(32) UK, name,
  spec_json JSON, image, price BIGINT, status, + 公共字段
  IDX(spu_id)
```

- **`sku` 不存库存**：库存属独立服务，展示时批量 Feign 查 inventory。
- `unit / is_cold_chain` 是"超市味"字段。

---

## B-6 `shop_inventory`

```sql
inventory
  id, sku_id BIGINT UK,
  total_stock INT, available_stock INT, locked_stock INT, warn_stock INT,
  version INT, + create_time/update_time
  UK(sku_id)

inventory_log
  id, sku_id, order_no VARCHAR(32),
  type TINYINT,                          -- 1入库 2锁定 3扣减 4释放 5归还
  quantity INT, before_stock INT, after_stock INT,
  biz_no VARCHAR(32), remark, create_time
  IDX(sku_id, order_no), IDX(create_time)
```

- 库存是热点行：`version` 乐观锁 + `update ... where available_stock >= n` 防超卖。
- `locked_stock` 支撑"下单锁定 → 支付扣减 → 超时释放"。

---

## B-7 `shop_order`

```sql
order_info
  id BIGINT PK,
  order_no VARCHAR(32) UK,               -- 幂等兜底
  user_id BIGINT,
  total_amount BIGINT, discount_amount BIGINT, freight_amount BIGINT, pay_amount BIGINT,
  coupon_id BIGINT NULL, points_deduct BIGINT DEFAULT 0,
  status TINYINT, pay_type TINYINT, pay_time DATETIME,
  receiver_name, receiver_phone,
  receiver_province, receiver_city, receiver_district, receiver_detail,
  delivery_time_slot VARCHAR(32),        -- 超市特色：配送时段（预留）
  remark, expire_time DATETIME,
  cancel_type TINYINT, cancel_reason, finish_time DATETIME,
  version INT, + create_time/update_time/deleted
  UK(order_no),
  IDX(user_id, status),
  IDX(status, expire_time),              -- 扫描超时未支付单
  IDX(create_time)

order_item
  id, order_id BIGINT, order_no VARCHAR(32),
  spu_id, sku_id,
  spu_name, sku_name, sku_image, spec_json,   -- ★ 商品快照
  price BIGINT, quantity INT, total_amount BIGINT, create_time
  IDX(order_id), IDX(sku_id)

order_status_log
  id, order_no, from_status TINYINT, to_status TINYINT,
  operator_type TINYINT,                 -- 1用户 2系统 3管理员
  operator_id, remark, create_time
  IDX(order_no)
```

**状态机：**

| 值 | 状态 | 允许流转到 |
|---|---|---|
| 0 | 待支付 | 1 已支付 / 5 已关闭 |
| 1 | 待发货 | 2 配送中 / 6 退款中 |
| 2 | 配送中 | 3 已完成 |
| 3 | 已完成 | — |
| 4 | 已取消（用户） | — |
| 5 | 已关闭（超时） | — |
| 6/7 | 退款中 / 已退款 | 后置 |

**设计理由：** 商品快照 + 地址快照（订单是历史事实）；`order_no` 唯一索引（幂等兜底）；`IDX(status, expire_time)`（超时扫描命脉）。

---

## B-8 `shop_payment`

```sql
payment_record
  id, payment_no VARCHAR(32) UK, order_no VARCHAR(32), user_id,
  channel TINYINT, amount BIGINT, status TINYINT,          -- 0待 1成功 2失败 3退款
  third_trade_no VARCHAR(64), callback_content TEXT, pay_time DATETIME,
  + 公共字段
  UK(payment_no), UK(third_trade_no), IDX(order_no), IDX(user_id)

refund_record
  id, refund_no UK, order_no, payment_no, amount, reason, status, refund_time, + 公共字段
```

- `payment_no` + `third_trade_no` **双唯一索引** → 回调幂等双保险。

---

## B-9 `shop_promotion`（底座留表）

```sql
coupon_template
  id, name, type TINYINT,                -- 1满减 2折扣
  threshold_amount BIGINT, discount_amount BIGINT, discount_rate INT,
  total_count INT, received_count INT, used_count INT, per_user_limit INT,
  valid_type TINYINT, start_time, end_time, valid_days INT,
  scope TINYINT, scope_ids JSON, status, + 公共字段

user_coupon
  id, coupon_no VARCHAR(32) UK, template_id, user_id, status TINYINT,
  order_no VARCHAR(32) NULL, receive_time, use_time, expire_time, + 公共字段
  IDX(user_id, status)

seckill_activity (id, name, start_time, end_time, status, + 公共字段)

seckill_sku
  id, activity_id, sku_id, seckill_price BIGINT,
  total_stock INT, seckill_stock INT, limit_per_user INT, version INT, + 公共字段
  IDX(activity_id), IDX(sku_id)
```

---

## B-10 `shop_cart`（不落 MySQL）

```
Key:   cart:{userId}      Hash
Field: {skuId}
Value: {skuId, spuId, quantity, selected, addTime, price(快照)}
```

---

## B-11 数据归属（单一数据源）

| 数据 | 源服务 | 引用方 |
|---|---|---|
| 用户身份 / 角色 | auth | 网关、所有服务 |
| 用户资料 | user | order（快照） |
| 商品 / 规格 | product | order、cart、search |
| 库存 | inventory | order |
| 订单 | order | payment、promotion |
| 优惠券 | promotion | order |

**铁律**：跨服务数据靠 **Feign 实时查** 或 **MQ 异步同步/本地冗余**，**绝不跨库 join**。
