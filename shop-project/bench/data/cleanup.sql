-- 压测数据清理：只删专用测试 SKU 的数据，绝不动业务数据
-- 用法：docker exec -i shop-mysql mysql -uroot -proot123 < bench/data/cleanup.sql

-- T4 / T5 专用 SKU
SET @test_sku = 900001;

DELETE FROM shop_inventory.inventory_log WHERE sku_id = @test_sku;
DELETE FROM shop_inventory.inventory     WHERE sku_id = @test_sku;

-- 校验是否已清空（两条都应返回空）
SELECT 'leftover inventory' AS chk, COUNT(*) AS n FROM shop_inventory.inventory     WHERE sku_id = @test_sku;
SELECT 'leftover log'       AS chk, COUNT(*) AS n FROM shop_inventory.inventory_log WHERE sku_id = @test_sku;
