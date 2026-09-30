import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, fmtDate, fmtNum, fmtPct, useLoad } from '../api.js';
import { Empty, ErrorNote, Field, Footer, GradeChip, Loading, Modal, PageHead, Status, useUi } from '../components/ui.jsx';
import { ESE_WEIGHT, MSE_WEIGHT, grade, parseMark, round2, total } from '../grading.js';

const FIELDS = ['mse', 'ese'];
const orig = (row, field) => (row[field] == null ? '' : String(row[field]));

/** Returns the edits map with one cell changed; rows that match the saved values drop out. */
function withCell(prev, row, field, text) {
  const cur = prev[row.studentId] ?? { mse: orig(row, 'mse'), ese: orig(row, 'ese') };
  const next = { ...cur, [field]: text };
  const out = { ...prev };
  if (next.mse === orig(row, 'mse') && next.ese === orig(row, 'ese')) delete out[row.studentId];
  else out[row.studentId] = next;
  return out;
}

/** Live preview of a row from what is currently typed. */
function preview(mseText, eseText) {
  const m = parseMark(mseText);
  const e = parseMark(eseText);
  if (Number.isNaN(m) || Number.isNaN(e)) return { invalid: true };
  if (m === null || e === null) return { status: 'PENDING' };
  const t = total(m, e);
  const g = grade(t, e);
  return { total: t, grade: g.label, status: g.label === 'F' ? 'FAIL' : 'PASS' };
}

export default function SubjectMarks() {
  const { id } = useParams();
  const { toast, confirm } = useUi();
  const { data, error, loading, reload } = useLoad(() => api(`/subjects/${id}/marks`), [id]);
  const [edits, setEdits] = useState({});
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('all');
  const [importing, setImporting] = useState(false);

  useEffect(() => { setEdits({}); setFormError(''); }, [id]);

  const dirty = Object.keys(edits).length;
  useEffect(() => {
    if (!dirty) return;
    const warn = (e) => e.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const rows = useMemo(() => {
    if (!data) return [];
    return data.rows.map((r) => {
      const e = edits[r.studentId];
      const mse = e ? e.mse : orig(r, 'mse');
      const ese = e ? e.ese : orig(r, 'ese');
      return { row: r, mse, ese, dirty: !!e, ...preview(mse, ese) };
    });
  }, [data, edits]);

  const visible = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return rows.filter((x) => {
      if (needle && !x.row.name.toLowerCase().includes(needle) && !x.row.prn.toLowerCase().includes(needle)) return false;
      if (filter === 'missing') return x.status === 'PENDING' || x.invalid;
      if (filter === 'fail') return x.status === 'FAIL';
      return true;
    });
  }, [rows, q, filter]);

  if (loading && !data) return <Loading />;
  if (error) return <ErrorNote message={error} />;

  const { subject, canEdit } = data;
  const graded = rows.filter((x) => x.total != null);
  const avg = graded.length ? round2(graded.reduce((n, x) => n + x.total, 0) / graded.length) : null;
  const passRate = graded.length ? (graded.filter((x) => x.status === 'PASS').length * 100) / graded.length : null;

  const setCell = (row, field, text) => setEdits((prev) => withCell(prev, row, field, text));

  const moveFocus = (e, index, field) => {
    const step = e.key === 'Enter' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowUp' ? -1 : 0;
    if (!step) return;
    e.preventDefault();
    document.querySelector(`[data-cell="${index + step}-${field}"]`)?.focus();
  };

  const save = async () => {
    setFormError('');
    const entries = [];
    for (const x of rows) {
      if (!x.dirty) continue;
      if (x.invalid) {
        setFormError(`${x.row.name} (${x.row.prn}): marks must be numbers from 0 to 100, up to 2 decimals. Leave blank if not yet available.`);
        return;
      }
      entries.push({ studentId: x.row.studentId, mse: parseMark(x.mse), ese: parseMark(x.ese) });
    }
    setSaving(true);
    try {
      const r = await api(`/subjects/${subject.id}/marks`, { method: 'PUT', body: { entries } });
      toast(`Saved marks for ${r.saved + r.cleared} student${r.saved + r.cleared === 1 ? '' : 's'}`);
      setEdits({});
      reload();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const discard = async () => {
    const ok = await confirm({
      title: 'Discard changes?',
      message: `Unsaved marks for ${dirty} student${dirty === 1 ? '' : 's'} will be lost.`,
      danger: true,
      confirmLabel: 'Discard',
    });
    if (ok) { setEdits({}); setFormError(''); }
  };

  const applyImport = (updates) => {
    setEdits((prev) => {
      let next = prev;
      for (const { row, mse, ese } of updates) {
        if (mse !== undefined) next = withCell(next, row, 'mse', mse);
        if (ese !== undefined) next = withCell(next, row, 'ese', ese);
      }
      return next;
    });
    setImporting(false);
    toast(`Filled marks for ${updates.length} student${updates.length === 1 ? '' : 's'}. Review, then save.`);
  };

  return (
    <>
      <PageHead
        crumb={<Link className="crumb" to={canEdit ? '/' : '/subjects'}>← {canEdit ? 'My subjects' : 'All subjects'}</Link>}
        title={subject.name}
        sub={`${subject.code} · ${subject.credits} credits · ${subject.teacherName ?? 'no teacher assigned'}${canEdit ? '' : ' · view only'}`}
      >
        {canEdit && <button className="btn" onClick={() => setImporting(true)}>Import CSV</button>}
      </PageHead>

      <div className="card card-body" style={{ marginBottom: 16 }}>
        <div className="chips">
          <span>Entered <b>{graded.length} / {rows.length}</b></span>
          <span>Class average <b>{fmtNum(avg)}</b></span>
          <span>Pass rate <b>{fmtPct(passRate)}</b></span>
          <span className="muted">Total = {MSE_WEIGHT}% MSE + {ESE_WEIGHT}% ESE · pass needs 40 total and 40 in ESE</span>
        </div>
      </div>

      <div className="toolbar">
        <div className="tabs">
          {[['all', 'All students'], ['missing', 'Missing marks'], ['fail', 'Failing']].map(([v, l]) => (
            <button key={v} className={filter === v ? 'on' : ''} onClick={() => setFilter(v)}>{l}</button>
          ))}
        </div>
        <input className="input" placeholder="Search name or PRN" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>

      {formError && <div className="alert error" role="alert">{formError}</div>}

      <div className="card">
        {rows.length === 0 ? (
          <Empty title="No students yet">Ask the exam cell to add students.</Empty>
        ) : visible.length === 0 ? (
          <Empty title="Nothing here">No students match this filter.</Empty>
        ) : (
          <div className="table-wrap">
            <table className="rtable">
              <thead>
                <tr>
                  <th>Student</th>
                  <th className="num">MSE<span className="sub">/100 · {MSE_WEIGHT}%</span></th>
                  <th className="num">ESE<span className="sub">/100 · {ESE_WEIGHT}%</span></th>
                  <th className="num">Total</th>
                  <th className="num">Grade</th>
                  <th className="right">Status</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((x, i) => (
                  <tr key={x.row.studentId}>
                    <td className="lead" title={x.row.updatedAt ? `Last saved by ${x.row.updatedBy} · ${fmtDate(x.row.updatedAt)}` : 'No marks saved yet'}>
                      <strong>{x.row.name}</strong>
                      <div className="muted small mono">{x.row.prn}</div>
                    </td>
                    {FIELDS.map((f) => {
                      const bad = x.dirty && Number.isNaN(parseMark(x[f]));
                      const changed = x.dirty && x[f] !== orig(x.row, f);
                      return (
                        <td className="num" key={f} data-label={f.toUpperCase()}>
                          <input
                            className={`cell ${changed ? 'dirty' : ''} ${bad ? 'bad' : ''}`}
                            inputMode="decimal"
                            aria-label={`${x.row.name} ${f.toUpperCase()}`}
                            data-cell={`${i}-${f}`}
                            value={x[f]}
                            disabled={!canEdit || saving}
                            placeholder={canEdit ? '—' : ''}
                            onChange={(e) => setCell(x.row, f, e.target.value)}
                            onKeyDown={(e) => moveFocus(e, i, f)}
                          />
                        </td>
                      );
                    })}
                    <td className="num" data-label="Total">{x.invalid ? <span className="muted">?</span> : fmtNum(x.total)}</td>
                    <td className="num" data-label="Grade">{x.invalid ? '' : <GradeChip grade={x.grade} />}</td>
                    <td className="right" data-label="Status">{x.invalid ? <span className="status FAIL">Invalid</span> : <Status value={x.status} />}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {canEdit && dirty > 0 && (
        <div className="savebar">
          <span>Unsaved marks for {dirty} student{dirty === 1 ? '' : 's'}</span>
          <span className="actions">
            <button className="btn" onClick={discard} disabled={saving}>Discard</button>
            <button className="btn save" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save marks'}</button>
          </span>
        </div>
      )}

      {importing && <ImportModal rows={data.rows} onClose={() => setImporting(false)} onApply={applyImport} />}
    </>
  );
}

/** Parses "prn,mse,ese" lines. Empty or missing columns keep the current value. */
function parseCsv(text, rows) {
  const byPrn = new Map(rows.map((r) => [r.prn.toUpperCase(), r]));
  const updates = [];
  const problems = [];
  const seen = new Set();
  text.replace(/^﻿/, '').split(/\r?\n/).forEach((line, i) => {
    if (!line.trim()) return;
    const cells = line.split(',').map((c) => c.trim().replace(/^"|"$/g, ''));
    const prn = (cells[0] || '').toUpperCase();
    const row = byPrn.get(prn);
    if (!row) {
      // Quietly skip a header row such as "prn,mse,ese".
      if (!(i === 0 && /[a-z]/i.test(cells[1] || ''))) problems.push(`Line ${i + 1}: no student with PRN "${cells[0]}"`);
      return;
    }
    if (seen.has(prn)) return problems.push(`Line ${i + 1}: PRN ${prn} appears more than once`);
    const u = { row };
    for (const [idx, f] of [[1, 'mse'], [2, 'ese']]) {
      const v = cells[idx];
      if (v === undefined || v === '') continue;
      if (Number.isNaN(parseMark(v))) {
        problems.push(`Line ${i + 1}: ${f.toUpperCase()} "${v}" is not a mark from 0 to 100`);
        return;
      }
      u[f] = v;
    }
    seen.add(prn);
    updates.push(u);
  });
  return { updates, problems };
}

function ImportModal({ rows, onClose, onApply }) {
  const [text, setText] = useState('');
  const { updates, problems } = useMemo(() => parseCsv(text, rows), [text, rows]);

  const onFile = async (e) => {
    const f = e.target.files?.[0];
    if (f) setText(await f.text());
  };

  return (
    <Modal title="Import marks from CSV" onClose={onClose} wide>
      <p className="muted" style={{ marginTop: 0 }}>
        One student per line as <code>prn,mse,ese</code>. Leave a column empty to keep the current value.
        Marks are filled into the grid for you to check before saving.
      </p>
      <Field label="CSV file"><input type="file" accept=".csv,text/csv,text/plain" onChange={onFile} /></Field>
      <Field label="Or paste here">
        <textarea className="input" rows={7} style={{ fontFamily: 'var(--mono)', fontSize: 13 }} value={text}
          onChange={(e) => setText(e.target.value)} placeholder={'prn,mse,ese\n12310001,72,81.5\n12310002,65,'} />
      </Field>
      {text.trim() && (
        <div className={`alert ${problems.length ? 'warn' : 'info'}`}>
          {updates.length} student{updates.length === 1 ? '' : 's'} ready to fill in.
          {problems.slice(0, 5).map((p) => <div key={p}>{p}</div>)}
          {problems.length > 5 && <div>…and {problems.length - 5} more.</div>}
        </div>
      )}
      <Footer>
        <button className="btn" onClick={onClose}>Cancel</button>
        <button className="btn primary" disabled={!updates.length} onClick={() => onApply(updates)}>Fill into grid</button>
      </Footer>
    </Modal>
  );
}
