import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { fmtPct, pctClass } from '../api.js';
import { gradeTone } from '../grading.js';

/* ---------- toasts + confirm ---------- */
const UiContext = createContext(null);
export const useUi = () => useContext(UiContext);

export function UiProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const [dialog, setDialog] = useState(null);
  const nextId = useRef(0);

  const toast = useCallback((message, kind = 'ok') => {
    const id = ++nextId.current;
    setToasts((t) => [...t, { id, message, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3800);
  }, []);

  const confirm = useCallback((opts) => new Promise((resolve) => setDialog({ ...opts, resolve })), []);
  const close = useCallback((result) => {
    setDialog((d) => {
      d?.resolve(result);
      return null;
    });
  }, []);
  const cancel = useCallback(() => close(false), [close]);

  return (
    <UiContext.Provider value={{ toast, confirm }}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`} role="status">{t.message}</div>
        ))}
      </div>
      {dialog && (
        <Modal title={dialog.title} onClose={cancel}>
          <p style={{ margin: '0 0 8px' }}>{dialog.message}</p>
          <Footer>
            <button className="btn" onClick={cancel}>Cancel</button>
            <button className={`btn ${dialog.danger ? 'danger solid' : 'primary'}`} onClick={() => close(true)} autoFocus>
              {dialog.confirmLabel || 'Confirm'}
            </button>
          </Footer>
        </Modal>
      )}
    </UiContext.Provider>
  );
}

/* ---------- modal ---------- */
export function Modal({ title, onClose, wide, children }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head"><h2>{title}</h2></div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}
export const Footer = ({ children }) => <div className="modal-foot">{children}</div>;

export function Field({ label, hint, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}

/* ---------- misc ---------- */
export const Loading = () => <div className="loading">Loading…</div>;
export const ErrorNote = ({ message }) => <div className="alert error" role="alert">{message}</div>;
export const Empty = ({ title, children }) => (
  <div className="empty"><strong>{title}</strong>{children}</div>
);
export const RoleTag = ({ role }) => <span className={`tag ${role.toLowerCase()}`}>{role.toLowerCase()}</span>;
export const GradeChip = ({ grade }) => (grade ? <span className={`grade ${gradeTone(grade)}`}>{grade}</span> : <span className="muted">—</span>);
export const Status = ({ value }) => <span className={`status ${value}`}>{value === 'PENDING' ? 'Pending' : value === 'PASS' ? 'Pass' : 'Fail'}</span>;

export function Percent({ value }) {
  return (
    <div className="pct">
      <div className={`bar ${pctClass(value)}`}><i style={{ width: `${Math.max(0, Math.min(100, value ?? 0))}%` }} /></div>
      <span>{fmtPct(value)}</span>
    </div>
  );
}

export function PageHead({ title, sub, crumb, children }) {
  return (
    <div className="page-head">
      <div>
        {crumb}
        <h1>{title}</h1>
        {sub && <p>{sub}</p>}
      </div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}

export const Logo = () => (
  <svg width="26" height="26" viewBox="0 0 32 32" aria-hidden="true">
    <rect width="32" height="32" rx="4" fill="currentColor" />
    <path d="M10 8h12M10 13h12M10 18h7" style={{ stroke: 'var(--logo-lines, #f6f3ec)' }} strokeWidth="2.2" strokeLinecap="round" />
    <path d="M18.5 22.5l2.5 2.5 4.5-5" fill="none" stroke="#e9b949" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
