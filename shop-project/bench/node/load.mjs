/**
 * 通用 HTTP 压测脚本（Node 版）
 *
 * 为什么不用 JMeter 跑这些：本机没装 JMeter 时它最快；且要"多轮并发梯度 + 业务码分类统计"
 * 这种自定义口径，脚本比 GUI 配置更直接。
 *
 * 用法：node load.mjs <scenario.json>
 *
 * 要点：
 *  - 用 node:http + maxSockets，不用 fetch/undici（其连接池会限制并发度，压不出真争抢）
 *  - 先跑 warmupSec 预热并丢弃（JIT / 类加载 / 连接池填充）
 *  - 支持多轮并发梯度（runs 数组）
 *  - 错误率按业务码分类：okCodes 成功 / bizRejectCodes 业务正确拒绝 / 其余算系统错误
 */

import http from 'node:http';
import fs from 'node:fs';

const cfgPath = process.argv[2];
if (!cfgPath) {
  console.error('usage: node load.mjs <scenario.json>');
  process.exit(2);
}
const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));

const base = new URL(cfg.baseUrl);
const HOST = base.hostname;
const PORT = Number(base.port || 80);
const OK = new Set(cfg.okCodes ?? [0]);
const BIZ = new Set(cfg.bizRejectCodes ?? []);

const agent = new http.Agent({ keepAlive: true, maxSockets: 512 });

function call({ method, path, headers = {}, body }) {
  const data = body === undefined ? null : JSON.stringify(body);
  return new Promise((resolve) => {
    const t0 = process.hrtime.bigint();
    const req = http.request(
      {
        host: HOST,
        port: PORT,
        path,
        method,
        agent,
        headers: {
          ...(data
            ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
            : {}),
          ...headers,
        },
      },
      (res) => {
        let text = '';
        res.on('data', (c) => (text += c));
        res.on('end', () => {
          const ms = Number(process.hrtime.bigint() - t0) / 1e6;
          let code = null;
          try {
            code = JSON.parse(text).code;
          } catch {
            /* 非 JSON */
          }
          resolve({ ms, http: res.statusCode, code, text });
        });
      },
    );
    req.on('error', (e) => {
      resolve({
        ms: Number(process.hrtime.bigint() - t0) / 1e6,
        http: 0,
        code: null,
        err: e.message,
      });
    });
    if (data) req.write(data);
    req.end();
  });
}

/* ---------- 1) 可选登录 ---------- */
let authHeaders = {};
if (cfg.login) {
  const r = await call({ method: 'POST', path: cfg.login.path, body: cfg.login.body });
  let token = null;
  try {
    token = JSON.parse(r.text).data?.[cfg.login.tokenField];
  } catch {
    /* ignore */
  }
  if (!token) {
    console.error(`# 登录失败 http=${r.http} body=${(r.text || '').slice(0, 200)}`);
    process.exit(2);
  }
  authHeaders = { Authorization: `Bearer ${token}` };
  console.log(`# 登录成功 token len=${token.length}`);
}

/* ---------- 1.5) 可选：每次请求前先取一个一次性 token（如幂等 token）---------- */
let preFail = 0;
let preOk = 0;
async function preHeaders() {
  if (!cfg.preRequest) return {};
  const r = await call({
    method: cfg.preRequest.method ?? 'GET',
    path: cfg.preRequest.path,
    headers: authHeaders,
    body: cfg.preRequest.body,
  });
  let v;
  try {
    v = JSON.parse(r.text);
  } catch {
    /* ignore */
  }
  if (cfg.preRequest.jsonPath) {
    v = cfg.preRequest.jsonPath.split('.').reduce((o, k) => (o == null ? o : o[k]), v);
  }
  if (!v) {
    preFail++;
    return {};
  }
  preOk++;
  return { [cfg.preRequest.headerName]: String(v) };
}

/* ---------- 2) 请求构造 ---------- */
let seq = 0;
function buildReq(extraHeaders = {}) {
  const i = seq++;
  let path = cfg.request.path;
  if (cfg.request.skuRange) {
    const { from, to } = cfg.request.skuRange;
    path = path.replace('{skuId}', String(from + (i % (to - from + 1))));
  }
  let body;
  if (cfg.request.bodyTemplate) {
    body = JSON.parse(
      JSON.stringify(cfg.request.bodyTemplate).replaceAll('{seq}', `${process.pid}-${i}`),
    );
  }
  return {
    method: cfg.request.method,
    path,
    headers: { ...authHeaders, ...(cfg.request.headers ?? {}), ...extraHeaders },
    body,
  };
}

/* ---------- 3) 单轮执行 ---------- */
function pct(arr, p) {
  if (!arr.length) return 0;
  return Number(arr[Math.min(arr.length - 1, Math.floor(arr.length * p))].toFixed(1));
}

async function runOnce({ concurrency, durationSec, warmupSec = 0, phase }) {
  const samples = [];
  const endAt = Date.now() + durationSec * 1000;
  const workers = Array.from({ length: concurrency }, async () => {
    while (Date.now() < endAt) {
      const extra = await preHeaders();
      const s = await call(buildReq(extra));
      if (phase === 'main') samples.push(s);
    }
  });
  const t0 = Date.now();
  await Promise.all(workers);
  const elapsed = (Date.now() - t0) / 1000;

  const byCode = {};
  for (const s of samples) {
    const k = s.http === 200 ? String(s.code) : `http_${s.http}`;
    byCode[k] = (byCode[k] ?? 0) + 1;
  }
  let ok = 0;
  let biz = 0;
  let err = 0;
  for (const s of samples) {
    if (s.http === 200 && OK.has(s.code)) ok++;
    else if (s.http === 200 && BIZ.has(s.code)) biz++;
    else err++;
  }
  const lat = samples.map((s) => s.ms).sort((a, b) => a - b);

  return {
    concurrency,
    durationSec: Number(elapsed.toFixed(1)),
    samples: samples.length,
    qps: Number((samples.length / elapsed).toFixed(1)),
    ok,
    bizReject: biz,
    error: err,
    errorRate: samples.length ? Number(((err / samples.length) * 100).toFixed(3)) : 0,
    latencyMs: {
      avg: lat.length ? Number((lat.reduce((a, b) => a + b, 0) / lat.length).toFixed(1)) : 0,
      p50: pct(lat, 0.5),
      p90: pct(lat, 0.9),
      p95: pct(lat, 0.95),
      p99: pct(lat, 0.99),
      max: lat.length ? Number(lat[lat.length - 1].toFixed(1)) : 0,
    },
    byCode,
  };
}

/* ---------- 4) 主流程 ---------- */
const runs = cfg.runs ?? [{ concurrency: cfg.concurrency, durationSec: cfg.durationSec }];
const out = { scenario: cfg.name ?? cfgPath, baseUrl: cfg.baseUrl, results: [] };

for (const r of runs) {
  if (cfg.warmupSec) {
    process.stdout.write(`# 预热 ${cfg.warmupSec}s @ 并发 ${r.concurrency} ... `);
    await runOnce({ ...r, durationSec: cfg.warmupSec, phase: 'warmup' });
    console.log('done');
  }
  console.log(`# 压测 ${r.durationSec}s @ 并发 ${r.concurrency} ...`);
  out.results.push(await runOnce({ ...r, phase: 'main' }));
}

if (cfg.preRequest) out.preRequestTotal = { ok: preOk, fail: preFail };
console.log(JSON.stringify(out, null, 2));
