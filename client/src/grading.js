// Mirrors backend GradingPolicy.java so teachers see totals and grades as they type.
// The server recomputes everything on save and is the source of truth.

export const SEMESTER = 'Semester V · AY 2026–27';
export const MSE_WEIGHT = 30;
export const ESE_WEIGHT = 70;
export const PASS_MARK = 40;

const GRADES = [
  ['O', 10, 90],
  ['A+', 9, 80],
  ['A', 8, 70],
  ['B+', 7, 60],
  ['B', 6, 50],
  ['C', 5, 45],
  ['P', 4, 40],
];

export const round2 = (v) => Math.round(v * 100) / 100;

export const total = (mse, ese) => round2((mse * MSE_WEIGHT + ese * ESE_WEIGHT) / 100);

export function grade(t, ese) {
  if (ese < PASS_MARK || t < PASS_MARK) return { label: 'F', points: 0 };
  for (const [label, points, min] of GRADES) if (t >= min) return { label, points };
  return { label: 'F', points: 0 };
}

/** '' → null, a valid mark (0–100, up to 2 decimals) → number, anything else → NaN. */
export function parseMark(text) {
  const t = String(text ?? '').trim();
  if (t === '') return null;
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(t)) return NaN;
  const n = Number(t);
  return n <= 100 ? n : NaN;
}

export function gradeTone(label) {
  if (label === 'O' || label === 'A+') return 'top';
  if (label === 'A' || label === 'B+') return 'good';
  if (label === 'F') return 'fail';
  return 'mid';
}
