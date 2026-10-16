import { Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { SubjectsPage } from './pages/SubjectsPage';
import { RosterRoute } from './pages/RosterRoute';
import { CampusMapPage } from './pages/CampusMapPage';

function Header() {
  const { fullName, role, canMarkAttendance, logout } = useAuth();

  return (
    <header className="app-header">
      <div className="app-header__inner">
        <div className="brand">
          Campus<span>Pilot</span>
        </div>
        <nav className="nav">
          {/*
            Teachers get no attendance dashboard of their own — the view
            is built from enrollments, and a teacher is not enrolled in
            the classes they teach. Showing them an empty page would look
            broken, so the nav only offers what applies to them.
          */}
          {!canMarkAttendance && (
            <NavLink to="/" end>
              Attendance
            </NavLink>
          )}
          <NavLink to="/subjects">
            {canMarkAttendance ? 'My classes' : 'My subjects'}
          </NavLink>
          <NavLink to="/map">Campus map</NavLink>
        </nav>
        <div className="user-chip">
          <span>
            {fullName}
            {role && role !== 'student' && ` (${role})`}
          </span>
          <button type="button" className="secondary small" onClick={logout}>
            Sign out
          </button>
        </div>
      </div>
    </header>
  );
}

export default function App() {
  const { isAuthenticated, canMarkAttendance } = useAuth();

  // Routing is the only gate. The real enforcement is server-side on
  // every query — this just avoids rendering a shell the user cannot
  // load data into.
  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }

  return (
    <>
      <Header />
      <Routes>
        <Route path="/" element={
          canMarkAttendance ? <Navigate to="/subjects" replace /> : <DashboardPage />
        } />
        <Route path="/subjects" element={<SubjectsPage />} />
        <Route path="/subjects/:subjectId/roster" element={<RosterRoute />} />
        <Route path="/map" element={<CampusMapPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}