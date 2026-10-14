-- T6 数据清理
-- 用法：docker exec -i shop-mysql mysql -uroot -proot123 < bench/data/cleanup-local-message.sql
-- 注意：Redis 里的消费去重键（order-created:T6-*）由 run-t6.sh 一并清理

DELETE FROM shop_order.local_message WHERE msg_id LIKE 'T6-%';

SELECT 'leftover T6 messages' AS chk, COUNT(*) AS n
FROM shop_order.local_message WHERE msg_id LIKE 'T6-%';
