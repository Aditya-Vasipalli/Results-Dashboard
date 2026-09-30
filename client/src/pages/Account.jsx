import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Field, PageHead, RoleTag, useUi } from '../components/ui.jsx';

export default function Account() {
  const { user } = useAuth();
  const { toast } = useUi();
  const [form, setForm] = useState({ current: '', next: '', again: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (form.next !== form.again) return setError('The new passwords do not match');
    setBusy(true);
    try {
      await api('/auth/change-password', { method: 'POST', body: { current: form.current, next: form.next } });
      toast('Password changed');
      setForm({ current: '', next: '', again: '' });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead title="Your account" />
      <div className="card" style={{ maxWidth: 460 }}>
        <div className="card-body">
          <p style={{ margin: '0 0 20px' }}>
            <strong>{user.name}</strong> <RoleTag role={user.role} />
            <br /><span className="muted">{user.email}</span>
          </p>
          <h3 style={{ marginBottom: 12 }}>Change password</h3>
          <form onSubmit={submit}>
            {error && <div className="alert error">{error}</div>}
            <Field label="Current password"><input className="input" type="password" autoComplete="current-password" value={form.current} onChange={set('current')} required /></Field>
            <Field label="New password"><input className="input" type="password" autoComplete="new-password" minLength={6} value={form.next} onChange={set('next')} required /></Field>
            <Field label="New password again"><input className="input" type="password" autoComplete="new-password" value={form.again} onChange={set('again')} required /></Field>
            <button className="btn primary" disabled={busy}>Update password</button>
          </form>
        </div>
      </div>
    </>
  );
}
