import { Link, Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import { Loading, Logo } from './components/ui.jsx';
import Login from './pages/Login.jsx';
import StudentResult from './pages/StudentResult.jsx';
import TeacherHome from './pages/TeacherHome.jsx';
import SubjectMarks from './pages/SubjectMarks.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import AdminResults, { AdminStudentResult } from './pages/AdminResults.jsx';
import AdminPeople from './pages/AdminPeople.jsx';
import AdminSubjects from './pages/AdminSubjects.jsx';
import AdminAudit from './pages/AdminAudit.jsx';
import Account from './pages/Account.jsx';

const NAV = {
  ADMIN: [['/', 'Overview', true], ['/results', 'Results'], ['/subjects', 'Subjects'], ['/people', 'People'], ['/activity', 'Activity']],
  TEACHER: [['/', 'My subjects', true]],
  STUDENT: [['/', 'My result', true]],
};

function Shell({ children }) {
  const { user, signOut } = useAuth();
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand"><Logo /> Result Desk</Link>
          <nav className="nav">
            {NAV[user.role].map(([to, label, end]) => (
              <NavLink key={to} to={to} end={end}>{label}</NavLink>
            ))}
          </nav>
          <div className="who">
            <Link to="/account" className="who-name">
              {user.name}
              <small>{user.role.toLowerCase()}{user.prn ? ` · ${user.prn}` : ''}</small>
            </Link>
            <Link to="/account" className="btn small mobile-only">Account</Link>
            <button className="btn small" onClick={signOut}>Sign out</button>
          </div>
        </div>
      </header>
      <main className="page">{children}</main>
    </>
  );
}

export default function App() {
  const { user, ready } = useAuth();
  if (!ready) return <Loading />;
  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  const home = { ADMIN: <AdminDashboard />, TEACHER: <TeacherHome />, STUDENT: <StudentResult /> }[user.role];
  return (
    <Shell>
      <Routes>
        <Route path="/" element={home} />
        <Route path="/account" element={<Account />} />
        {user.role !== 'STUDENT' && <Route path="/subjects/:id/marks" element={<SubjectMarks />} />}
        {user.role === 'ADMIN' && (
          <>
            <Route path="/results" element={<AdminResults />} />
            <Route path="/results/:studentId" element={<AdminStudentResult />} />
            <Route path="/subjects" element={<AdminSubjects />} />
            <Route path="/people" element={<AdminPeople />} />
            <Route path="/activity" element={<AdminAudit />} />
          </>
        )}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
}
