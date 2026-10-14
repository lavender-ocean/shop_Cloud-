-- T6 专用：造 100 条待投递的本地消息（status=0），用于验证多实例分片「并行且不重复」
-- 用法：docker exec -i shop-mysql mysql -uroot -proot123 < bench/data/seed-local-message-100.sql
--
-- 说明：
--   * id 连续 → claim 的 `MOD(id, shardTotal) = shardIndex` 能均分到两个分片（各 50 条）
--   * biz_type 用 ORDER_CREATED：dispatch 只把它转发到 order.exchange，消费端仅打日志，
--     不会真的建订单（安全）
--   * payload 带 orderNo，消费端 IdempotentConsumeGuard 会用它做去重键

DELETE FROM shop_order.local_message WHERE msg_id LIKE 'T6-%';

-- i = 1..100（用两位数笛卡尔积生成，避免依赖 CTE 语法）
INSERT INTO shop_order.local_message
  (id, msg_id, biz_type, biz_no, payload, status, retry_count, next_retry_time, create_time)
SELECT 9000000000000000000 + x.i,
       CONCAT('T6-', LPAD(x.i, 3, '0')),
       'ORDER_CREATED',
       CONCAT('T6-', LPAD(x.i, 3, '0')),
       CONCAT('{"orderNo":"T6-', LPAD(x.i, 3, '0'), '"}'),
       0, 0, NOW(), NOW()
FROM (
  SELECT (a.n + b.n * 10 + 1) AS i
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
       (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
) x;

SELECT status, COUNT(*) AS n FROM shop_order.local_message WHERE msg_id LIKE 'T6-%' GROUP BY status;
SELECT '按分片预分布' AS chk, MOD(id, 2) AS shard, COUNT(*) AS n
FROM shop_order.local_message WHERE msg_id LIKE 'T6-%' GROUP BY MOD(id, 2);
