import { useParams } from 'react-router-dom';
import { RosterPage } from './RosterPage';
import { useAuth } from '../auth/AuthContext';

export function RosterRoute() {
  const { subjectId = '' } = useParams();
  const { canMarkAttendance, fullName } = useAuth();

  // A student following the URL directly still gets nothing useful. The
  // server would reject both roster calls with 403; this avoids asking.
  if (!canMarkAttendance) {
    return (
      <div className="page">
        <div className="card empty">
          <h3>Faculty only</h3>
          <p>
            Taking attendance is done by the teacher of a subject. You can
            see your own attendance on the Attendance page.
          </p>
        </div>
      </div>
    );
  }

  return (
    <RosterPage subjectId={subjectId} subjectName={fullName ? 'Class roster' : ''} />
  );
}