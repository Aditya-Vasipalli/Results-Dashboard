import { useMemo, useState } from 'react';
import { api, useLoad } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Empty, ErrorNote, Field, Footer, Loading, Modal, PageHead, RoleTag, useUi } from '../components/ui.jsx';

const TABS = [['ALL', 'Everyone'], ['STUDENT', 'Students'], ['TEACHER', 'Teachers'], ['ADMIN', 'Admins']];

export default function AdminPeople() {
  const { user: me } = useAuth();
  const { toast, confirm } = useUi();
  const { data, error, loading, reload } = useLoad(() => api('/admin/users'));
  const [tab, setTab] = useState('ALL');
  const [q, setQ] = useState('');
  const [modal, setModal] = useState(null); // {} = create, { user } = edit

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return (data ?? []).filter((u) =>
      (tab === 'ALL' || u.role === tab) &&
      (!needle || u.name.toLowerCase().includes(needle) || u.email.includes(needle) || (u.prn ?? '').toLowerCase().includes(needle)));
  }, [data, tab, q]);

  const remove = async (u) => {
    const ok = await confirm({
      title: `Delete ${u.name}?`,
      message: u.role === 'STUDENT' ? 'Their marks in every subject will be permanently deleted.'
        : u.role === 'TEACHER' ? 'Their subjects become unassigned. Marks already entered are kept.'
        : 'This admin account will be permanently deleted.',
      danger: true,
      confirmLabel: 'Delete',
    });
    if (!ok) return;
    try {
      await api(`/admin/users/${u.id}`, { method: 'DELETE' });
      toast(`${u.name} deleted`);
      reload();
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  return (
    <>
      <PageHead title="People" sub="Accounts for students, teachers and the exam cell.">
        <button className="btn primary" onClick={() => setModal({})}>Add person</button>
      </PageHead>

      <div className="toolbar">
        <div className="tabs">
          {TABS.map(([v, l]) => <button key={v} className={tab === v ? 'on' : ''} onClick={() => setTab(v)}>{l}</button>)}
        </div>
        <input className="input" placeholder="Search name, email or PRN" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>

      {loading && !data ? <Loading /> : error ? <ErrorNote message={error} /> : (
        <div className="card">
          {rows.length === 0 ? <Empty title="No one found">Try a different filter or search.</Empty> : (
            <div className="table-wrap">
              <table className="rtable">
                <thead><tr><th>Name</th><th>Email</th><th>PRN</th><th>Role</th><th /></tr></thead>
                <tbody>
                  {rows.map((u) => (
                    <tr key={u.id}>
                      <td className="lead"><strong>{u.name}</strong>{u.id === me.id && <span className="muted"> (you)</span>}</td>
                      <td data-label="Email" className="muted">{u.email}</td>
                      <td data-label="PRN" className="mono">{u.prn ?? <span className="muted">—</span>}</td>
                      <td data-label="Role"><RoleTag role={u.role} /></td>
                      <td className="right nowrap">
                        <button className="btn small" onClick={() => setModal({ user: u })}>Edit</button>{' '}
                        <button className="btn small danger" disabled={u.id === me.id} onClick={() => remove(u)}>Delete</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {modal && (
        <UserModal
          user={modal.user}
          isSelf={modal.user?.id === me.id}
          onClose={() => setModal(null)}
          onDone={(msg) => { setModal(null); toast(msg); reload(); }}
        />
      )}
    </>
  );
}

function UserModal({ user, isSelf, onClose, onDone }) {
  const [form, setForm] = useState({
    name: user?.name ?? '',
    email: user?.email ?? '',
    role: user?.role ?? 'STUDENT',
    prn: user?.prn ?? '',
    password: '',
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    const body = { ...form, prn: form.role === 'STUDENT' ? form.prn : null, password: form.password || null };
    try {
      if (user) await api(`/admin/users/${user.id}`, { method: 'PUT', body });
      else await api('/admin/users', { method: 'POST', body });
      onDone(user ? 'Changes saved' : `${form.name} added`);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const roleChange = user && form.role !== user.role;

  return (
    <Modal title={user ? 'Edit person' : 'Add person'} onClose={onClose}>
      <form onSubmit={submit}>
        {error && <div className="alert error">{error}</div>}
        <Field label="Full name"><input className="input" value={form.name} onChange={set('name')} required autoFocus /></Field>
        <Field label="Email"><input className="input" type="email" value={form.email} onChange={set('email')} required /></Field>
        <div className="row">
          <Field label="Role">
            <select className="input" value={form.role} onChange={set('role')} disabled={isSelf}>
              <option value="STUDENT">Student</option>
              <option value="TEACHER">Teacher</option>
              <option value="ADMIN">Admin</option>
            </select>
          </Field>
          {form.role === 'STUDENT' && (
            <Field label="PRN"><input className="input mono" value={form.prn} onChange={set('prn')} placeholder="12310013" required /></Field>
          )}
        </div>
        <Field label={user ? 'New password' : 'Password'} hint={user ? 'Leave blank to keep the current password.' : 'At least 6 characters.'}>
          <input className="input" type="text" autoComplete="off" value={form.password} onChange={set('password')} required={!user} minLength={6} />
        </Field>
        {roleChange && (
          <div className="alert warn">
            {user.role === 'STUDENT' ? 'This student\'s marks will be deleted.' : user.role === 'TEACHER' ? 'They will be unassigned from their subjects.' : 'Their admin access will be removed.'}
          </div>
        )}
        <Footer>
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button className="btn primary" disabled={busy}>{busy ? 'Saving…' : user ? 'Save' : 'Add'}</button>
        </Footer>
      </form>
    </Modal>
  );
}
