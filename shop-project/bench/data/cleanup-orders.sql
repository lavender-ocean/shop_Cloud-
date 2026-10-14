-- T3 下单压测数据清理
-- 只删 remark='LOADTEST' 的订单及其全部痕迹，绝不动业务数据。
--
-- ⚠️ 关键：`inventory.locked_stock` 是【计数列】，删流水不会让它变小！
--    所以必须先把这些订单占用的锁量算出来，再从 locked_stock 里减掉，归还到 available_stock。
--    （这是本项目第一次写清理脚本时踩的坑：只删流水 → locked_stock 残留 → 假性缺货）
--
-- 用法：docker exec -i shop-mysql mysql -uroot -proot123 < bench/data/cleanup-orders.sql

SET @sku = 2107390427584630786;

-- ① 先算出这些订单占用的锁量（必须在删流水之前算）
SET @rel = (
  SELECT COALESCE(SUM(quantity), 0) FROM shop_inventory.inventory_log
  WHERE sku_id = @sku AND type = 2
    AND order_no IN (SELECT order_no FROM shop_order.order_info WHERE remark = 'LOADTEST')
);

-- ② 删流水
DELETE FROM shop_inventory.inventory_log
WHERE order_no IN (SELECT order_no FROM shop_order.order_info WHERE remark = 'LOADTEST');

-- ③ 删订单痕迹
DELETE FROM shop_order.order_status_log
WHERE order_no IN (SELECT order_no FROM shop_order.order_info WHERE remark = 'LOADTEST');

DELETE FROM shop_order.order_item
WHERE order_no IN (SELECT order_no FROM shop_order.order_info WHERE remark = 'LOADTEST');

DELETE FROM shop_order.local_message
WHERE biz_no IN (SELECT order_no FROM shop_order.order_info WHERE remark = 'LOADTEST');

DELETE FROM shop_order.order_info WHERE remark = 'LOADTEST';

-- ④ 释放锁：locked -= @rel，available += @rel
UPDATE shop_inventory.inventory
SET locked_stock    = GREATEST(locked_stock - @rel, 0),
    available_stock = available_stock + @rel,
    update_time     = NOW()
WHERE sku_id = @sku;

-- ⑤ 校验：三项都应为 0 或自洽
SELECT 'leftover LOADTEST orders' AS chk, COUNT(*) AS n
FROM shop_order.order_info WHERE remark = 'LOADTEST';

SELECT 'in-flight orders' AS chk, COUNT(*) AS n
FROM shop_order.order_info WHERE status = 0;

SELECT 'inventory' AS chk, total_stock, available_stock, locked_stock,
       (total_stock - available_stock - locked_stock) AS gap
FROM shop_inventory.inventory WHERE sku_id = @sku;

SELECT 'released lock qty' AS chk, @rel AS n;
