import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, downloadCsv, fmtPct, fmtSgpa, useLoad } from '../api.js';
import Marksheet from '../components/Marksheet.jsx';
import { Empty, ErrorNote, GradeChip, Loading, PageHead, Status } from '../components/ui.jsx';
import { SEMESTER } from '../grading.js';

const SORTS = {
  prn: (a, b) => a.student.prn.localeCompare(b.student.prn),
  name: (a, b) => a.student.name.localeCompare(b.student.name),
  sgpa: (a, b) => (b.sgpa ?? -1) - (a.sgpa ?? -1),
};

export default function AdminResults() {
  const navigate = useNavigate();
  const { data, error, loading } = useLoad(() => api('/admin/results'));
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [sort, setSort] = useState('prn');

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return (data ?? [])
      .filter((r) => filter === 'ALL' || r.status === filter)
      .filter((r) => !needle || r.student.name.toLowerCase().includes(needle) || r.student.prn.toLowerCase().includes(needle))
      .sort(SORTS[sort]);
  }, [data, q, filter, sort]);

  if (loading) return <Loading />;
  if (error) return <ErrorNote message={error} />;

  const subjects = data[0]?.subjects ?? [];
  const count = (st) => data.filter((r) => r.status === st).length;

  const exportCsv = () => {
    const head = ['PRN', 'Name'];
    subjects.forEach((s) => head.push(`${s.code} MSE`, `${s.code} ESE`, `${s.code} Total`, `${s.code} Grade`));
    head.push('Credits earned', 'SGPA', 'Percentage', 'Result');
    const body = rows.map((r) => [
      r.student.prn,
      r.student.name,
      ...r.subjects.flatMap((s) => [s.mse, s.ese, s.total, s.grade]),
      r.creditsEarned,
      r.sgpa,
      r.percentage,
      r.status,
    ]);
    downloadCsv('semester-results.csv', [head, ...body]);
  };

  return (
    <>
      <PageHead title="Semester results" sub={`${SEMESTER} · ${data.length} students`}>
        <button className="btn" onClick={exportCsv} disabled={!rows.length}>Export CSV</button>
      </PageHead>

      <div className="toolbar">
        <div className="tabs">
          {[['ALL', `All (${data.length})`], ['PASS', `Pass (${count('PASS')})`], ['FAIL', `Fail (${count('FAIL')})`], ['PENDING', `Pending (${count('PENDING')})`]].map(([v, l]) => (
            <button key={v} className={filter === v ? 'on' : ''} onClick={() => setFilter(v)}>{l}</button>
          ))}
        </div>
        <div className="actions">
          <select className="input" style={{ width: 'auto' }} value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sort by">
            <option value="prn">Sort by PRN</option>
            <option value="name">Sort by name</option>
            <option value="sgpa">Sort by SGPA</option>
          </select>
          <input className="input" style={{ width: 220 }} placeholder="Search name or PRN" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      </div>

      <div className="card">
        {rows.length === 0 ? (
          <Empty title="No students found">Try a different filter or search.</Empty>
        ) : (
          <div className="table-wrap">
            <table className="rtable">
              <thead>
                <tr>
                  <th>Student</th>
                  {subjects.map((s) => <th key={s.subjectId} className="num" title={s.name}>{s.code}</th>)}
                  <th className="num">SGPA</th>
                  <th className="num">%</th>
                  <th className="right">Result</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.student.id} className="clickable" onClick={() => navigate(`/results/${r.student.id}`)}>
                    <td className="lead">
                      <Link to={`/results/${r.student.id}`} style={{ color: 'inherit' }} onClick={(e) => e.stopPropagation()}>
                        <strong>{r.student.name}</strong>
                      </Link>
                      <div className="muted small mono">{r.student.prn}</div>
                    </td>
                    {r.subjects.map((s) => (
                      <td key={s.subjectId} className="num" data-label={s.code}><GradeChip grade={s.grade} /></td>
                    ))}
                    <td className="num" data-label="SGPA"><strong>{fmtSgpa(r.sgpa)}</strong></td>
                    <td className="num" data-label="Percentage">{fmtPct(r.percentage)}</td>
                    <td className="right" data-label="Result"><Status value={r.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

export function AdminStudentResult() {
  const { studentId } = useParams();
  const { data, error, loading } = useLoad(() => api(`/admin/results/${studentId}`), [studentId]);

  return (
    <>
      <PageHead crumb={<Link className="crumb" to="/results">← All results</Link>} title="Marksheet">
        {data && <button className="btn" onClick={() => window.print()}>Print</button>}
      </PageHead>
      {loading ? <Loading /> : error ? <ErrorNote message={error} /> : <Marksheet result={data} />}
    </>
  );
}
