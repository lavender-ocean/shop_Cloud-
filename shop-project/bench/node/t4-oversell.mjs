/**
 * T4 · 防超卖并发正确性验证
 *
 * 目的：对同一个 SKU 的高并发锁库存请求，验证「成功数恰好等于库存数」。
 * 用法：node t4-oversell.mjs [port] [skuId] [stock] [concurrency]
 *       默认 8084 / 900001 / 10 / 200
 *
 * 只做 HTTP 并发 + 统计，不查 DB；DB 校验由 run-t4.sh 负责。
 * 用 node:http 而非 fetch —— 为了能显式设 maxSockets，保证 200 个请求是【真并发】
 * （fetch/undici 的连接池会限制并发度，压不出争抢）。
 */

import http from 'node:http';

const port = Number(process.argv[2] ?? 8084);
const skuId = Number(process.argv[3] ?? 900001);
const expectedStock = Number(process.argv[4] ?? 10);
const concurrency = Number(process.argv[5] ?? 200);

const agent = new http.Agent({ keepAlive: true, maxSockets: concurrency + 8 });
const runId = Date.now();

function lockOnce(i) {
  const body = JSON.stringify({
    orderNo: `T4-${runId}-${i}`,
    items: [{ skuId, quantity: 1 }],
  });
  return new Promise((resolve) => {
    const t0 = process.hrtime.bigint();
    const req = http.request(
      {
        host: 'localhost',
        port,
        path: '/inner/inventory/lock',
        method: 'POST',
        agent,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(body),
        },
      },
      (res) => {
        let buf = '';
        res.on('data', (c) => (buf += c));
        res.on('end', () => {
          const ms = Number(process.hrtime.bigint() - t0) / 1e6;
          let code = -1;
          try {
            code = JSON.parse(buf).code;
          } catch {
            /* 非 JSON 响应（如容器级 5xx）留 -1 */
          }
          resolve({ code, ms });
        });
      },
    );
    req.on('error', (e) =>
      resolve({ code: -1, ms: Number(process.hrtime.bigint() - t0) / 1e6, err: e.message }),
    );
    req.write(body);
    req.end();
  });
}

const wall0 = process.hrtime.bigint();
const results = await Promise.all(Array.from({ length: concurrency }, (_, i) => lockOnce(i)));
const wallMs = Number(process.hrtime.bigint() - wall0) / 1e6;

const byCode = {};
for (const r of results) byCode[r.code] = (byCode[r.code] ?? 0) + 1;

const lat = results.map((r) => r.ms).sort((a, b) => a - b);
const q = (p) => Number(lat[Math.min(lat.length - 1, Math.floor(lat.length * p))].toFixed(1));
const success = byCode[0] ?? 0;

const report = {
  skuId,
  expectedSuccess: expectedStock,
  requests: concurrency,
  success,
  pass: success === expectedStock,
  byCode,
  wallMs: Math.round(wallMs),
  qps: Number((concurrency / (wallMs / 1000)).toFixed(1)),
  latencyMs: { p50: q(0.5), p90: q(0.9), p99: q(0.99), max: q(1) },
};

console.log(JSON.stringify(report, null, 2));
process.exit(report.pass ? 0 : 1);
