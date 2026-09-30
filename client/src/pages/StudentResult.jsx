import { api, useLoad } from '../api.js';
import { useAuth } from '../auth.jsx';
import Marksheet from '../components/Marksheet.jsx';
import { Empty, ErrorNote, Loading, PageHead } from '../components/ui.jsx';
import { SEMESTER } from '../grading.js';

export default function StudentResult() {
  const { user } = useAuth();
  const { data, error, loading } = useLoad(() => api('/student/result'));

  if (loading) return <Loading />;
  if (error) return <ErrorNote message={error} />;

  return (
    <>
      <PageHead title={`Hello, ${user.name.split(' ')[0]}`} sub={SEMESTER}>
        {data.published && <button className="btn" onClick={() => window.print()}>Print marksheet</button>}
      </PageHead>
      {data.published ? (
        <Marksheet result={data.result} />
      ) : (
        <div className="card">
          <Empty title="Results have not been declared yet">
            Your marks are being finalised. This page will show your grades and SGPA as soon as the exam cell publishes them.
          </Empty>
        </div>
      )}
    </>
  );
}
