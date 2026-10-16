// Verifies the attendance_health() math independently of Postgres.
// If this disagrees with db/04_verify.sql, the SQL expectations are wrong.

const cases = [
  { label: 'no data',           attended: 0,  total: 0,   target: 75, future: 0,   exp: { pct: null,    skip: 0,  must: 0,  status: 'NO_DATA' } },
  { label: 'exactly on target', attended: 60, total: 80,  target: 75, future: 10,  exp: { pct: 75.00,   skip: 0,  must: 8,  status: 'ON_TRACK' } },
  { label: 'unrecoverable',     attended: 50, total: 80,  target: 75, future: 20,  exp: { pct: 62.50,   skip: 0,  must: 25, status: 'IMPOSSIBLE' } },
  { label: 'ahead of target',   attended: 70, total: 80,  target: 75, future: 10,  exp: { pct: 87.50,   skip: 13, must: 0,  status: 'ON_TRACK' } },
  { label: 'slightly over',     attended: 70, total: 90,  target: 75, future: 5,   exp: { pct: 77.78,   skip: 3,  must: 2,  status: 'ON_TRACK' } },
  { label: 'at risk',           attended: 70, total: 95,  target: 75, future: 10,  exp: { pct: 73.68,   skip: 0,  must: 9,  status: 'AT_RISK' } },
  { label: 'critical',          attended: 60, total: 100, target: 75, future: 100, exp: { pct: 60.00,   skip: 0,  must: 90, status: 'CRITICAL' } },
];

const round2 = (n) => Math.round(n * 100) / 100;

function attendanceHealth(attended, total, target, future) {
  if (target <= 0) target = 75;
  if (attended < 0 || total < 0 || future < 0) throw new Error('negative counts');
  if (attended > total) throw new Error('attended > total');

  if (total === 0) return { pct: null, skip: 0, must: 0, recoverable: false, status: 'NO_DATA' };

  const pct = round2(attended * 100 / total);
  const skip = Math.max(0, Math.floor(attended * 100 / target - total));
  const needed = (target * (total + future) - 100 * attended) / 100;
  const must = Math.max(0, Math.ceil(needed));
  const recoverable = needed <= future;

  let status;
  if (!recoverable) status = 'IMPOSSIBLE';
  else if (pct >= target) status = 'ON_TRACK';
  else if (pct >= target * 0.90) status = 'AT_RISK';
  else status = 'CRITICAL';

  return { pct, skip, must, recoverable, status };
}

let failures = 0;

for (const c of cases) {
  const got = attendanceHealth(c.attended, c.total, c.target, c.future);
  const ok =
    got.pct === c.exp.pct &&
    got.skip === c.exp.skip &&
    got.must === c.exp.must &&
    got.status === c.exp.status;

  if (!ok) failures++;
  console.log(
    `${ok ? 'PASS' : 'FAIL'}  ${c.label.padEnd(20)} ` +
    `pct=${String(got.pct).padEnd(6)} skip=${String(got.skip).padEnd(3)} ` +
    `must=${String(got.must).padEnd(3)} ${got.status}`
  );
  if (!ok) console.log(`      expected: ${JSON.stringify(c.exp)}\n      actual:   ${JSON.stringify(got)}`);
}

// Guard: corrupt data must throw, not silently return nonsense.
try {
  attendanceHealth(80, 60, 75, 0);
  console.log('\nFAIL  guard: 80/60 should have thrown');
  failures++;
} catch (e) {
  console.log(`\nPASS  guard: 80/60 correctly rejected -> "${e.message}"`);
}

console.log(`\n${failures === 0 ? 'ALL PASS' : failures + ' FAILURE(S)'}`);
process.exit(failures === 0 ? 0 : 1);