import { api, fmtDate, useLoad } from '../api.js';
import { Empty, ErrorNote, Loading, PageHead } from '../components/ui.jsx';

export default function AdminAudit() {
  const { data, error, loading, reload } = useLoad(() => api('/admin/audit?limit=200'));

  return (
    <>
      <PageHead title="Activity" sub="The 200 most recent changes: who did what, and when.">
        <button className="btn" onClick={reload}>Refresh</button>
      </PageHead>
      {loading && !data ? <Loading /> : error ? <ErrorNote message={error} /> : (
        <div className="card">
          {data.length === 0 ? (
            <Empty title="Nothing logged yet">Saving marks, publishing results and editing accounts all show up here.</Empty>
          ) : (
            <div className="table-wrap">
              <table className="rtable">
                <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Details</th></tr></thead>
                <tbody>
                  {data.map((e) => (
                    <tr key={e.id}>
                      <td className="lead muted nowrap">{fmtDate(e.createdAt)}</td>
                      <td data-label="Who" className="nowrap">{e.actor}</td>
                      <td data-label="Action" className="mono small nowrap">{e.action}</td>
                      <td data-label="Details">{e.detail}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  );
}
