#!/usr/bin/env bash
# T3 · 下单主链路压测（一键跑）
#
# 流程：前置检查 → 记录库存基线 → 加库存 → 压测 → 校验建单数 → 清理 → 恢复库存基线
#
# ⚠️ 两个关键点：
#   1) 每个订单都会真建库，`remark='LOADTEST'` 用于事后精确清理
#   2) `inventory.locked_stock` 是计数列，删流水不会让它变小 —— 清理脚本会显式释放
#
# 用法：bash bench/run-t3.sh

set -u
GW=${GW:-http://localhost:8080}
SKU=${SKU:-2107390427584630786}
MYSQL="docker exec shop-mysql mysql -uroot -proot123"
MYSQL_R="docker exec shop-mysql mysql --default-character-set=utf8mb4 -uroot -proot123"

say() { printf '\n\033[1m=== %s ===\033[0m\n' "$1"; }
# 用 read 从一行三列取值，避免被 shell 分词
read_inv() { $MYSQL -N -e "SELECT total_stock FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>/dev/null | tr -d '\r'; }

say "0. 前置检查"
for p in 8080 8082 8083 8084 8086; do
  netstat -ano 2>/dev/null | grep ":$p " | grep -q LISTENING && echo "  $p UP" || { echo "  $p DOWN → 中止"; exit 1; }
done
TOKEN=$(curl -s -m 10 -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -z "$TOKEN" ] && { echo "  登录失败 → 中止"; exit 1; }
echo "  登录 OK"

say "1. 记录基线（压测后要恢复到这个状态）"
BASE=$($MYSQL -N -e "SELECT CONCAT(total_stock,' ',available_stock,' ',locked_stock) FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>/dev/null | tr -d '\r')
BASE_T=$(echo "$BASE" | awk '{print $1}')
BASE_A=$(echo "$BASE" | awk '{print $2}')
BASE_L=$(echo "$BASE" | awk '{print $3}')
MARK_ID=$($MYSQL -N -e "SELECT COALESCE(MAX(id),0) FROM shop_inventory.inventory_log WHERE sku_id=$SKU;" 2>/dev/null | tr -d '\r')
echo "  库存基线: total=$BASE_T available=$BASE_A locked=$BASE_L   log max_id=$MARK_ID"
$MYSQL_R -e "SELECT COUNT(*) AS loadtest_before FROM shop_order.order_info WHERE remark='LOADTEST';" 2>&1 | grep -v Warning

say "2. 给下单 SKU 加 100 万库存（避免被压光）"
curl -s -m 20 -X POST "$GW/admin/inventory/increase" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"skuId\":$SKU,\"quantity\":1000000}"; echo

say "3. 压测（20 / 50 并发，各 20s + 5s 预热）"
node "$(dirname "$0")/node/load.mjs" "$(dirname "$0")/node/scenarios/t3-create-order.json" \
  | tee "$(dirname "$0")/results/t3-create-order.log"

say "4. 校验：建单数 == 成功样本数"
$MYSQL_R -e "
SELECT COUNT(*) AS loadtest_orders FROM shop_order.order_info WHERE remark='LOADTEST';
SELECT COUNT(*) AS order_items FROM shop_order.order_item
  WHERE order_no IN (SELECT order_no FROM shop_order.order_info WHERE remark='LOADTEST');
SELECT 'inventory' AS t, total_stock, available_stock, locked_stock FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>&1 | grep -v Warning

say "5. 清理压测数据（释放锁 + 删订单痕迹）"
docker exec -i shop-mysql mysql -uroot -proot123 < "$(dirname "$0")/data/cleanup-orders.sql" 2>&1 | grep -v Warning

say "6. 恢复库存基线（撤销本次的 +100万 与残留计数）"
$MYSQL -e "
DELETE FROM shop_inventory.inventory_log WHERE sku_id=$SKU AND id > $MARK_ID AND type = 1;
UPDATE shop_inventory.inventory
SET total_stock=$BASE_T, available_stock=$BASE_A, locked_stock=$BASE_L, update_time=NOW()
WHERE sku_id=$SKU;" 2>&1 | grep -v Warning
$MYSQL_R -e "SELECT 'final inventory' AS t, total_stock, available_stock, locked_stock,
  (total_stock-available_stock-locked_stock) AS gap FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>&1 | grep -v Warning

say "完成"
echo "  原始日志: bench/results/t3-create-order.log"
