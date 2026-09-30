import { useState } from 'react';
import { useAuth } from '../auth.jsx';
import { Field, Logo } from '../components/ui.jsx';
import { SEMESTER } from '../grading.js';

const DEMO = [
  ['Admin', 'admin@vit.edu', 'admin123'],
  ['Teacher', 'iyer@vit.edu', 'teacher123'],
  ['Student', 'aarav.mehta@vit.edu', 'student123'],
];

export default function Login() {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await signIn(email, password);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <aside className="login-side">
        <div className="brand"><Logo /> Result Desk</div>
        <div>
          <h1>One semester, four subjects, one clear result.</h1>
          <p style={{ marginTop: 14 }}>
            Faculty enter mid- and end-semester marks. The exam cell reviews and publishes. Students see their grades and SGPA the moment results go live.
          </p>
        </div>
        <div className="formula">
          <div>{SEMESTER}</div>
          <div>total &nbsp;= 0.30 × MSE + 0.70 × ESE</div>
          <div>SGPA &nbsp;&nbsp;= Σ(credits × points) ÷ Σ credits</div>
        </div>
      </aside>
      <main className="login-main">
        <form className="login-form" onSubmit={submit}>
          <div className="brand login-brand"><Logo /> Result Desk</div>
          <h2>Sign in</h2>
          <p>Use the account issued by the exam cell.</p>
          {error && <div className="alert error" role="alert">{error}</div>}
          <Field label="Email">
            <input className="input" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus required />
          </Field>
          <Field label="Password">
            <input className="input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </Field>
          <button className="btn primary" style={{ width: '100%', justifyContent: 'center', padding: '9px' }} disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
          <div className="demo">
            Demo accounts (click to fill):
            {DEMO.map(([label, e, p]) => (
              <div key={label}>
                <button type="button" onClick={() => { setEmail(e); setPassword(p); setError(''); }}>{label}</button>{' '}
                <span>{e}</span>
              </div>
            ))}
          </div>
        </form>
      </main>
    </div>
  );
}
