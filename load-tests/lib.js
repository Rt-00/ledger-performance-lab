import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

let sequence = 0;

/** Unique per VU and iteration; also unique across runs thanks to the timestamp prefix. */
export function newKey(prefix) {
  sequence += 1;
  return `${prefix}-${Date.now().toString(36)}-${__VU}-${sequence}`;
}

export function openFundedAccounts(count, balance) {
  const ids = [];
  for (let i = 0; i < count; i++) {
    const res = http.post(`${BASE_URL}/accounts`, JSON.stringify({ currency: 'BRL' }), { headers: JSON_HEADERS });
    if (res.status !== 201) fail(`could not open account: ${res.status} ${res.body}`);
    const id = res.json('id');
    const dep = http.post(`${BASE_URL}/accounts/${id}/deposits`, JSON.stringify({ amount: balance }), {
      headers: Object.assign({ 'Idempotency-Key': newKey('seed') }, JSON_HEADERS),
    });
    check(dep, { 'deposit created': (r) => r.status === 201 });
    ids.push(id);
  }
  return ids;
}

export function transfer(source, target, amount, tags) {
  return http.post(
    `${BASE_URL}/transfers`,
    JSON.stringify({ sourceAccountId: source, targetAccountId: target, amount, currency: 'BRL' }),
    { headers: Object.assign({ 'Idempotency-Key': newKey('t') }, JSON_HEADERS), tags },
  );
}

export function summaryWriter(name) {
  return (data) => {
    const out = {};
    out[`/results/${name}.json`] = JSON.stringify(data, null, 2);
    // Prefer the measured-phase sub-metrics (warm-up excluded) when the script defines them.
    const m = {};
    for (const [key, value] of Object.entries(data.metrics)) {
      const base = key.replace('{phase:measure}', '');
      if (key.endsWith('{phase:measure}') || !(base in m)) m[base] = value;
    }
    const seconds = parseFloat((__ENV.DURATION || '45s').replace('s', ''));
    const d = m.http_req_duration.values;
    const requests = m.http_reqs.values.count;
    const lines = [
      `${name}`,
      `  requests   : ${requests} (${(requests / seconds).toFixed(1)}/s over ${seconds.toFixed(0)}s)`,
      `  latency ms : p50=${d['p(50)'].toFixed(1)} p95=${d['p(95)'].toFixed(1)} p99=${d['p(99)'].toFixed(1)} max=${d.max.toFixed(1)}`,
    ];
    for (const key of Object.keys(m).filter((k) => k.startsWith('status_')).sort()) {
      lines.push(`  ${key.padEnd(11)}: ${m[key].values.count}`);
    }
    out.stdout = lines.join('\n') + '\n';
    return out;
  };
}
