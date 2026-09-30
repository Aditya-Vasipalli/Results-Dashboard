import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, useLoad } from '../api.js';
import { ErrorNote, Field, Footer, Loading, Modal, PageHead, useUi } from '../components/ui.jsx';
import { SEMESTER } from '../grading.js';

export default function AdminSubjects() {
  const { toast } = useUi();
  const subjects = useLoad(() => api('/admin/subjects'));
  const teachers = useLoad(() => api('/admin/users?role=TEACHER'));
  const [editing, setEditing] = useState(null);

  return (
    <>
      <PageHead title="Subjects" sub={`The four subjects of ${SEMESTER}. Assign a teacher to let them enter marks.`} />
      {subjects.loading && !subjects.data ? <Loading /> : subjects.error ? <ErrorNote message={subjects.error} /> : (
        <div className="card table-wrap">
          <table className="rtable">
            <thead>
              <tr><th>Code</th><th>Subject</th><th className="num">Credits</th><th>Teacher</th><th className="num">Marks entered</th><th /></tr>
            </thead>
            <tbody>
              {subjects.data.map((s) => (
                <tr key={s.id}>
                  <td className="lead mono">{s.code}</td>
                  <td data-label="Subject"><strong>{s.name}</strong></td>
                  <td className="num" data-label="Credits">{s.credits}</td>
                  <td data-label="Teacher" className={s.teacherName ? '' : 'muted'}>{s.teacherName ?? 'Unassigned'}</td>
                  <td className="num" data-label="Marks entered">{s.completeCount} / {s.studentCount}</td>
                  <td className="right nowrap">
                    <Link className="btn small" to={`/subjects/${s.id}/marks`}>View marks</Link>{' '}
                    <button className="btn small" onClick={() => setEditing(s)}>Edit</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && (
        <SubjectModal
          subject={editing}
          teachers={teachers.data ?? []}
          onClose={() => setEditing(null)}
          onDone={() => { setEditing(null); toast('Subject updated'); subjects.reload(); }}
        />
      )}
    </>
  );
}

function SubjectModal({ subject, teachers, onClose, onDone }) {
  const [form, setForm] = useState({
    code: subject.code,
    name: subject.name,
    credits: String(subject.credits),
    teacherId: subject.teacherId ? String(subject.teacherId) : '',
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    const credits = Number(form.credits);
    if (!Number.isInteger(credits) || credits < 1 || credits > 10) return setError('Credits must be a whole number from 1 to 10');
    setBusy(true);
    try {
      await api(`/admin/subjects/${subject.id}`, {
        method: 'PUT',
        body: { code: form.code, name: form.name, credits, teacherId: form.teacherId ? Number(form.teacherId) : null },
      });
      onDone();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal title={`Edit ${subject.code}`} onClose={onClose}>
      <form onSubmit={submit}>
        {error && <div className="alert error">{error}</div>}
        <div className="row">
          <Field label="Code"><input className="input" value={form.code} onChange={set('code')} required /></Field>
          <Field label="Credits"><input className="input" type="number" min="1" max="10" step="1" value={form.credits} onChange={set('credits')} required /></Field>
        </div>
        <Field label="Name"><input className="input" value={form.name} onChange={set('name')} required /></Field>
        <Field label="Teacher" hint="Only the assigned teacher can enter marks for this subject.">
          <select className="input" value={form.teacherId} onChange={set('teacherId')}>
            <option value="">Unassigned</option>
            {teachers.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select>
        </Field>
        <Footer>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </Footer>
      </form>
    </Modal>
  );
}
