// Closed-model load test for POST /transfers.
//   HOT_RATIO=0    -> uniform random pairs among ACCOUNTS accounts (low contention)
//   HOT_RATIO=0.5  -> half of the transfers touch the same "hot" account (high contention)
import { Counter } from 'k6/metrics';
import { openFundedAccounts, transfer, summaryWriter } from './lib.js';

const ACCOUNTS = parseInt(__ENV.ACCOUNTS || '200', 10);
const HOT_RATIO = parseFloat(__ENV.HOT_RATIO || '0');
const NAME = __ENV.NAME || 'transfers';

const counters = {};
for (const s of ['201', '409', '422', '5xx', 'other']) counters[s] = new Counter(`status_${s}`);

export const options = {
  setupTimeout: '5m',
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: { executor: 'constant-vus', vus: 16, duration: __ENV.WARMUP || '15s', gracefulStop: '5s', tags: { phase: 'warmup' } },
    measure: {
      executor: 'constant-vus',
      vus: parseInt(__ENV.VUS || '64', 10),
      duration: __ENV.DURATION || '45s',
      startTime: __ENV.WARMUP || '15s',
      tags: { phase: 'measure' },
    },
  },
  // Only the measure phase feeds the reported numbers.
  // Thresholds on {phase:measure} make k6 keep separate sub-metrics for the measured phase.
  thresholds: Object.fromEntries(
    ['http_req_duration', 'http_reqs', 'status_201', 'status_409', 'status_422', 'status_5xx', 'status_other'].map(
      (m) => [`${m}{phase:measure}`, [m === 'http_req_duration' ? 'max>=0' : 'count>=0']],
    ),
  ),
};

export function setup() {
  return { ids: openFundedAccounts(ACCOUNTS, '10000000.00') };
}

function pick(ids) {
  return ids[Math.floor(Math.random() * ids.length)];
}

export default function (data) {
  const ids = data.ids;
  let source = pick(ids);
  let target = pick(ids);
  if (Math.random() < HOT_RATIO) {
    if (Math.random() < 0.5) source = ids[0]; else target = ids[0];
  }
  if (source === target) return;

  const res = transfer(source, target, '1.00');
  const s = res.status;
  const key = s === 201 || s === 409 || s === 422 ? String(s) : s >= 500 ? '5xx' : 'other';
  counters[key].add(1);
}

export const handleSummary = summaryWriter(NAME);
