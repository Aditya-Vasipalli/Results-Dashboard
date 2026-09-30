import { Link } from 'react-router-dom';
import { api, useLoad } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Empty, ErrorNote, Loading, PageHead } from '../components/ui.jsx';
import { SEMESTER } from '../grading.js';

export default function TeacherHome() {
  const { user } = useAuth();
  const { data, error, loading } = useLoad(() => api('/subjects'));

  if (loading) return <Loading />;
  if (error) return <ErrorNote message={error} />;

  return (
    <>
      <PageHead title="My subjects" sub={`${user.name} · ${SEMESTER}`} />
      {data.length === 0 ? (
        <div className="card"><Empty title="No subjects assigned">The exam cell has not assigned a subject to you yet.</Empty></div>
      ) : (
        <div className="subject-cards">
          {data.map((s) => {
            const pct = s.studentCount ? (s.completeCount * 100) / s.studentCount : 0;
            const done = s.studentCount > 0 && s.completeCount === s.studentCount;
            return (
              <Link key={s.id} to={`/subjects/${s.id}/marks`} className="card subject-card">
                <div>
                  <div className="code">{s.code} · {s.credits} credits</div>
                  <h2>{s.name}</h2>
                </div>
                <div>
                  <div className="small" style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span className="muted">Marks entered</span>
                    <span className="mono">{s.completeCount} / {s.studentCount}</span>
                  </div>
                  <div className={`bar ${done ? '' : 'mid'}`}><i style={{ width: `${pct}%` }} /></div>
                </div>
                <span className="btn primary small" style={{ alignSelf: 'flex-start' }}>{done ? 'Review marks' : 'Enter marks'}</span>
              </Link>
            );
          })}
        </div>
      )}
    </>
  );
}
