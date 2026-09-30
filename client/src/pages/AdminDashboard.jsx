import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, fmtDate, fmtNum, fmtPct, fmtSgpa, useLoad } from '../api.js';
import { Empty, ErrorNote, Loading, PageHead, useUi } from '../components/ui.jsx';
import { SEMESTER } from '../grading.js';

function GradeDistribution({ grades }) {
  const entries = Object.entries(grades);
  const max = Math.max(1, ...entries.map(([, n]) => n));
  return (
    <div className="dist" aria-label={entries.map(([g, n]) => `${g}: ${n}`).join(', ')}>
      {entries.map(([g, n]) => (
        <div key={g} title={`${g}: ${n} student${n === 1 ? '' : 's'}`}>
          <i className={g === 'F' ? 'f' : ''} style={{ height: `${(n / max) * 22}px` }} />
          <span>{g}</span>
        </div>
      ))}
    </div>
  );
}

export default function AdminDashboard() {
  const { toast, confirm } = useUi();
  const stats = useLoad(() => api('/admin/stats'));
  const audit = useLoad(() => api('/admin/audit?limit=6'));
  const [busy, setBusy] = useState(false);

  if (stats.loading && !stats.data) return <Loading />;
  if (stats.error) return <ErrorNote message={stats.error} />;
  const s = stats.data;

  const togglePublish = async () => {
    const next = !s.published;
    const ok = await confirm(next
      ? {
          title: 'Publish results?',
          message: s.pending > 0
            ? `${s.pending} student${s.pending === 1 ? ' has' : 's have'} missing marks and will see "Pending". Students will be able to see their results immediately.`
            : 'Every student will be able to see their marksheet and SGPA immediately.',
          confirmLabel: 'Publish',
        }
      : { title: 'Withdraw results?', message: 'Students will no longer be able to see their results until you publish again.', danger: true, confirmLabel: 'Withdraw' });
    if (!ok) return;
    setBusy(true);
    try {
      await api('/admin/publication', { method: 'PUT', body: { published: next } });
      toast(next ? 'Results published to students' : 'Results withdrawn');
      stats.reload();
      audit.reload();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const entered = s.marksExpected ? (s.marksComplete * 100) / s.marksExpected : 0;

  return (
    <>
      <PageHead title="Overview" sub={SEMESTER} />

      <div className={`card publish ${s.published ? 'live' : ''}`}>
        <div>
          <strong>{s.published ? 'Results are published' : 'Results are not published'}</strong>
          <p>{s.published ? 'Students can see their marksheets now.' : 'Students see a "not declared yet" message until you publish.'}</p>
        </div>
        <label className="switch">
          <input type="checkbox" aria-label="Publish results to students" checked={s.published} onChange={togglePublish} disabled={busy} />
          <span className="track" />
          {s.published ? 'Live' : 'Hidden'}
        </label>
      </div>

      <div className="stats" style={{ marginBottom: 24 }}>
        <div className="card stat"><b>{s.students}</b><span>Students</span><small>{s.teachers} teachers · {s.subjects} subjects</small></div>
        <div className="card stat"><b>{Math.round(entered)}%</b><span>Marks entered</span><small>{s.marksComplete} of {s.marksExpected} subject results</small></div>
        <div className="card stat"><b>{s.passed}</b><span>Passed all subjects</span><small>{s.failed} with a backlog · {s.pending} pending</small></div>
        <div className="card stat"><b>{fmtSgpa(s.averageSgpa)}</b><span>Average SGPA</span><small>across complete results</small></div>
      </div>

      <div className="grid-2">
        <section className="card">
          <div className="card-head"><h2>Subjects</h2><Link className="btn small" to="/subjects">Manage</Link></div>
          <div className="table-wrap">
            <table className="rtable">
              <thead>
                <tr><th>Subject</th><th className="num">Entered</th><th className="num">Avg</th><th className="num">Pass</th><th>Grades</th></tr>
              </thead>
              <tbody>
                {s.bySubject.map((x) => (
                  <tr key={x.id}>
                    <td className="lead">
                      <Link to={`/subjects/${x.id}/marks`} style={{ color: 'inherit' }}><strong>{x.name}</strong></Link>
                      <div className="muted small">{x.code} · {x.teacher ?? <em>no teacher</em>}</div>
                    </td>
                    <td className="num" data-label="Entered">{x.complete}/{x.students}</td>
                    <td className="num" data-label="Average">{fmtNum(x.average)}</td>
                    <td className="num" data-label="Pass rate">{fmtPct(x.passRate)}</td>
                    <td data-label="Grades" style={{ minWidth: 170 }}><GradeDistribution grades={x.grades} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="card">
          <div className="card-head"><h2>Recent activity</h2><Link className="btn small" to="/activity">See all</Link></div>
          {audit.data?.length ? (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {audit.data.map((e) => (
                <li key={e.id} style={{ padding: '10px 20px', borderBottom: '1px solid var(--line)' }}>
                  <div><strong>{e.actor}</strong> <span className="muted">· {fmtDate(e.createdAt)}</span></div>
                  <div className="small">{e.detail}</div>
                </li>
              ))}
            </ul>
          ) : audit.error ? <ErrorNote message={audit.error} /> : <Empty title="Quiet so far">Changes will be listed here.</Empty>}
        </section>
      </div>
    </>
  );
}
