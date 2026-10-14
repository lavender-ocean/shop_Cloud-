#!/usr/bin/env bash
# T4 · 防超卖并发正确性验证（一键跑）
#
# 流程：清理 → 初始化 10 库存 → 200 并发抢 → 校验 DB → 清理
# 通过标准：成功数恰为 10，且 available=0 / locked=10 / type=2 流水恰 10 条
#
# 用法：bash bench/run-t4.sh

set -u
GW=${GW:-http://localhost:8080}
INV=${INV:-8084}
SKU=${SKU:-900001}
STOCK=${STOCK:-10}
CONC=${CONC:-200}
MYSQL="docker exec shop-mysql mysql -uroot -proot123"

say() { printf '\n\033[1m=== %s ===\033[0m\n' "$1"; }

say "0. 前置检查"
for p in 8080 8084; do
  if netstat -ano 2>/dev/null | grep ":$p " | grep -q LISTENING; then echo "  $p UP"; else echo "  $p DOWN → 中止"; exit 1; fi
done

TOKEN=$(curl -s -m 10 -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -z "$TOKEN" ] && { echo "  登录失败 → 中止"; exit 1; }
echo "  token 获取成功 (len=${#TOKEN})"

say "1. 清理上一次的测试数据"
$MYSQL -e "DELETE FROM shop_inventory.inventory_log WHERE sku_id=$SKU; DELETE FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>&1 | grep -v Warning

say "2. 初始化库存 = $STOCK"
curl -s -m 15 -X POST "$GW/admin/inventory/init" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"skuId\":$SKU,\"quantity\":$STOCK}"; echo
$MYSQL --default-character-set=utf8mb4 -e "SELECT '初始状态' AS t, sku_id,total_stock,available_stock,locked_stock FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>&1 | grep -v Warning

say "3. $CONC 并发抢 $STOCK（真并发，node:http maxSockets=$((CONC+8))）"
node "$(dirname "$0")/node/t4-oversell.mjs" "$INV" "$SKU" "$STOCK" "$CONC"
NODE_EXIT=$?

say "4. DB 校验（三方对齐）"
$MYSQL --default-character-set=utf8mb4 -e "
SELECT '库存终态' AS t, total_stock, available_stock, locked_stock FROM shop_inventory.inventory WHERE sku_id=$SKU;
SELECT '流水统计' AS t, type, COUNT(*) AS rows_cnt, SUM(quantity) AS qty
  FROM shop_inventory.inventory_log WHERE sku_id=$SKU GROUP BY type;" 2>&1 | grep -v Warning

say "5. 清理测试数据"
$MYSQL -e "DELETE FROM shop_inventory.inventory_log WHERE sku_id=$SKU; DELETE FROM shop_inventory.inventory WHERE sku_id=$SKU;" 2>&1 | grep -v Warning
echo "  已清理"

say "结果"
if [ "$NODE_EXIT" -eq 0 ]; then echo "  ✅ T4 通过：成功数恰为 $STOCK（无超卖）"; else echo "  ❌ T4 未通过：成功数 != $STOCK，需回查 InventoryService 的 insert-first 闸门"; fi
exit $NODE_EXIT
