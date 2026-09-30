import { fmtNum, fmtPct, fmtSgpa } from '../api.js';
import { ESE_WEIGHT, MSE_WEIGHT, PASS_MARK, SEMESTER } from '../grading.js';
import { GradeChip, Status } from './ui.jsx';

/** A printable semester result for one student. */
export default function Marksheet({ result }) {
  const { student, subjects, totalCredits, creditsEarned, sgpa, percentage, status } = result;
  return (
    <article className="card">
      <header className="sheet-head">
        <div>
          <div className="inst">VIT · Statement of Semester Result</div>
          <h2>{student.name}</h2>
        </div>
        <dl className="sheet-meta">
          <dt>PRN</dt><dd>{student.prn}</dd>
          <dt>Semester</dt><dd>{SEMESTER}</dd>
          <dt>Email</dt><dd>{student.email}</dd>
        </dl>
      </header>

      <div className="table-wrap">
        <table className="rtable">
          <thead>
            <tr>
              <th>Subject</th>
              <th className="num">Credits</th>
              <th className="num">MSE<span className="sub">/100 · {MSE_WEIGHT}%</span></th>
              <th className="num">ESE<span className="sub">/100 · {ESE_WEIGHT}%</span></th>
              <th className="num">Total<span className="sub">/100</span></th>
              <th className="num">Grade</th>
              <th className="num">Points</th>
              <th className="right">Status</th>
            </tr>
          </thead>
          <tbody>
            {subjects.map((s) => (
              <tr key={s.subjectId}>
                <td className="lead">
                  <strong>{s.name}</strong>
                  <div className="muted small mono">{s.code}</div>
                </td>
                <td className="num" data-label="Credits">{s.credits}</td>
                <td className="num" data-label={`MSE (${MSE_WEIGHT}%)`}>{s.mse == null ? <span className="pending">—</span> : fmtNum(s.mse)}</td>
                <td className="num" data-label={`ESE (${ESE_WEIGHT}%)`}>{s.ese == null ? <span className="pending">—</span> : fmtNum(s.ese)}</td>
                <td className="num" data-label="Total"><strong>{fmtNum(s.total)}</strong></td>
                <td className="num" data-label="Grade"><GradeChip grade={s.grade} /></td>
                <td className="num" data-label="Grade points">{s.gradePoints ?? '—'}</td>
                <td className="right" data-label="Status"><Status value={s.status} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="sheet-summary">
        <div><span>SGPA</span><b>{fmtSgpa(sgpa)}</b></div>
        <div><span>Percentage</span><b>{fmtPct(percentage)}</b></div>
        <div><span>Credits earned</span><b>{creditsEarned ?? '—'}<span style={{ display: 'inline', fontSize: 16 }} className="muted"> / {totalCredits}</span></b></div>
        <div><span>Result</span><b className={status}>{status === 'PENDING' ? 'Pending' : status === 'PASS' ? 'Pass' : 'Fail'}</b></div>
      </div>
      <p className="sheet-note" style={{ margin: 0 }}>
        Subject total = {MSE_WEIGHT}% of MSE + {ESE_WEIGHT}% of ESE. A subject is cleared with at least {PASS_MARK} overall and {PASS_MARK} in the ESE.
        SGPA = Σ(credits × grade points) ÷ Σ credits. {status === 'PENDING' && 'SGPA is shown once every subject has both marks.'}
      </p>
    </article>
  );
}
