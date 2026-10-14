#!/usr/bin/env bash
# T6 · 多实例分片任务验证（并行且不重复）
#
# 前置：需要跑着【两个】shop-order 实例（第二个用不同端口 + 不同 xxl-job executor 端口）：
#   java -jar shop-order/target/shop-order-1.0.0.jar \
#        --server.port=18086 --xxl.job.executor.port=9998 -Dfile.encoding=UTF-8
#   然后 XXL-JOB 的执行器地址列表里应看到两个地址。
#
# 原理：`LocalMessageClaimer` 用 `SELECT ... FOR UPDATE SKIP LOCKED` 抢占，
#       分片键是 `MOD(id, shardTotal)=shardIndex` → 两个实例各领一半、互不重复。
#
# 用法：bash bench/run-t6.sh

set -u
XXL=${XXL:-http://localhost:8888/xxl-job-admin}
MYSQL="docker exec shop-mysql mysql -uroot -proot123"
MYSQL_R="docker exec shop-mysql mysql --default-character-set=utf8mb4 -uroot -proot123"
JOB_ID=2   # localMessageCompensateHandler

say() { printf '\n\033[1m=== %s ===\033[0m\n' "$1"; }

say "0. 前置：XXL-JOB 执行器注册情况（应有 2 个地址）"
curl -s -c /tmp/xc6.txt -X POST "$XXL/login" -d "userName=admin&password=123456" >/dev/null
curl -s -b /tmp/xc6.txt "$XXL/jobgroup/pageList?start=0&length=20" \
  | tr ',' '\n' | grep -E '"appname"|"addressList"|"registryList"' | head -6

say "1. 造 100 条待投递消息（status=0）"
docker exec -i shop-mysql mysql -uroot -proot123 < "$(dirname "$0")/data/seed-local-message-100.sql" 2>&1 | grep -v Warning

say "2. 手动触发 localMessageCompensateHandler（分片广播）"
curl -s -b /tmp/xc6.txt -X POST "$XXL/jobinfo/trigger" -d "id=$JOB_ID&executorParam=&addressList="; echo
sleep 6

say "3. 各分片的执行结果（应为 0/2 与 1/2 两条，抢占数合计 100）"
curl -s -b /tmp/xc6.txt "$XXL/joblog/pageList?start=0&length=6&jobGroup=1&jobId=$JOB_ID&logStatus=-1" \
  | node -e "
let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
  try{
    const d=JSON.parse(s);
    for(const r of (d.data||[]).slice(0,6)){
      const msg=(r.handleMsg||'').replace(/<br>/g,' | ').replace(/&#12290;|&nbsp;/g,'');
      console.log('  ['+r.triggerTime+'] '+r.executorAddress+'  分片='+r.executorShardingParam+'  handleCode='+r.handleCode);
      if(msg) console.log('      '+msg.slice(0,200));
    }
  }catch(e){console.log('  解析失败: '+s.slice(0,200))}
});"

say "4. 校验 local_message 终态（期望：全部 status=1，无 0/2/3 残留）"
$MYSQL_R -e "
SELECT status, COUNT(*) AS n FROM shop_order.local_message WHERE msg_id LIKE 'T6-%' GROUP BY status;
SELECT '未投递(0) / 投递中(2) / 待重试(3)' AS chk,
       SUM(status=0) AS s0, SUM(status=2) AS s2, SUM(status=3) AS s3
FROM shop_order.local_message WHERE msg_id LIKE 'T6-%';" 2>&1 | grep -v Warning

say "5. 校验消费端去重（Redis 里 order-created:T6-* 键数应为 100，说明每条只被消费一次）"
docker exec shop-redis redis-cli -p 6379 -a redis123 --no-auth-warning --scan --pattern 'idempotent:consume:order-created:T6-*' 2>/dev/null | wc -l

say "6. 清理"
docker exec -i shop-mysql mysql -uroot -proot123 < "$(dirname "$0")/data/cleanup-local-message.sql" 2>&1 | grep -v Warning
docker exec shop-redis redis-cli -p 6379 -a redis123 --no-auth-warning --scan --pattern 'idempotent:consume:order-created:T6-*' 2>/dev/null \
  | while read -r k; do docker exec shop-redis redis-cli -p 6379 -a redis123 --no-auth-warning del "$k" >/dev/null; done
echo "  Redis 去重键已清理"

say "完成"
