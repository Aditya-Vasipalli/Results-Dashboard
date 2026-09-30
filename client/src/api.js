import { useCallback, useEffect, useRef, useState } from 'react';

const KEY = 'results.token';

function readToken() {
  try { return localStorage.getItem(KEY); } catch { return null; }
}
export const getToken = readToken;
export function setToken(t) {
  try { t ? localStorage.setItem(KEY, t) : localStorage.removeItem(KEY); } catch { /* storage blocked */ }
}

let onUnauthorized = () => {};
export const setUnauthorizedHandler = (fn) => {
  onUnauthorized = fn;
};

export async function api(path, { method = 'GET', body } = {}) {
  const token = getToken();
  let res;
  try {
    res = await fetch('/api' + path, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error('Cannot reach the server. Is the Spring Boot backend running?');
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && token && path !== '/auth/login') onUnauthorized();
    throw new Error(data?.error || `Request failed (${res.status})`);
  }
  return data;
}

/** Load data on mount / when deps change. Ignores responses that arrive after a newer request. */
export function useLoad(fn, deps = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const ticket = useRef(0);
  const run = useCallback(() => {
    const mine = ++ticket.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    fn().then(
      (data) => mine === ticket.current && setState({ data, error: null, loading: false }),
      (error) => mine === ticket.current && setState({ data: null, error: error.message, loading: false }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(run, [run]);
  return { ...state, reload: run };
}

export function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

export const fmtNum = (n) => (n == null ? '—' : String(+Number(n).toFixed(2)));
export const fmtPct = (n) => (n == null ? '—' : `${Number(n).toFixed(1)}%`);
export const fmtSgpa = (n) => (n == null ? '—' : Number(n).toFixed(2));
export const pctClass = (p) => (p == null ? '' : p >= 60 ? '' : p >= 40 ? 'mid' : 'low');

/** Offer a CSV string to the viewer as a file download. */
export function downloadCsv(filename, rows) {
  const esc = (v) => {
    const s = v == null ? '' : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const text = rows.map((r) => r.map(esc).join(',')).join('\r\n');
  const blob = new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
