import type { AttendanceStatus, DashboardRow } from '../api/types';

const LABELS: Record<AttendanceStatus, string> = {
  IMPOSSIBLE: 'Impossible',
  CRITICAL: 'Critical',
  AT_RISK: 'At risk',
  ON_TRACK: 'On track',
  NO_DATA: 'No data',
};

/**
 * Turns a raw status into the sentence a student actually needs.
 *
 * Every number shown here came from attendance_health() in PostgreSQL.
 * This component only words them — no arithmetic, no thresholds of its
 * own. That separation is the reason the UI and the API can never
 * disagree about what "critical" means.
 */
function explain(row: DashboardRow): string {
  switch (row.status) {
    case 'IMPOSSIBLE':
      return `You need ${row.mustAttend} of the next ${row.remainingClassesEstimate} classes to reach ${row.targetAttendance}%, but only ${row.remainingClassesEstimate} remain. No attendance pattern can recover this — consider retaking the subject or speaking to your department.`;

    case 'CRITICAL':
      return `Attend at least ${row.mustAttend} of the next ${row.remainingClassesEstimate} classes. That is enough to recover, but there is little room for error.`;

    case 'AT_RISK':
      return `Attend at least ${row.mustAttend} of the next ${row.remainingClassesEstimate} classes to stay on track.`;

    case 'ON_TRACK':
      return row.canSkip > 0
        ? `You can safely miss ${row.canSkip} more class${row.canSkip === 1 ? '' : 'es'} and still hold ${row.targetAttendance}%.`
        : `You are exactly at the ${row.targetAttendance}% threshold. Missing even one more class puts you under target.`;

    case 'NO_DATA':
      return 'No classes recorded yet. Mark attendance to see your standing.';
  }
}

export function StatusBadge({ status }: { status: AttendanceStatus }) {
  return (
    <span className={`badge badge--${status}`}>
      {status === 'NO_DATA' ? 'No data' : LABELS[status]}
    </span>
  );
}

export function SubjectCard({ row }: { row: DashboardRow }) {
  // pct is null when nothing is recorded. Rendering 0% there would be
  // wrong — "no data" and "attended nothing" are different facts.
  const pct = row.pct;

  return (
    <article className={`subject subject--${row.status}`}>
      <div className="subject__head">
        <div>
          <div className="subject__name">{row.subjectName}</div>
          <div className="subject__code">
            {row.subjectCode ?? '—'} · Semester {row.semester}
          </div>
        </div>
        <StatusBadge status={row.status} />
      </div>

      <div className="subject__pct">
        {pct === null ? (
          <span style={{ color: 'var(--ink-faint)', fontSize: '1.2rem' }}>
            —
          </span>
        ) : (
          <>
            {pct.toFixed(1)}%<small> of {row.targetAttendance}% target</small>
          </>
        )}
      </div>

      <div
        className="meter"
        role="progressbar"
        aria-valuenow={pct ?? 0}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`${row.subjectName} attendance`}
      >
        <div
          className={`meter__fill meter__fill--${row.status}`}
          style={{ width: `${Math.min(100, pct ?? 0)}%` }}
        />
      </div>

      <dl className="facts">
        <div>
          <dt>Attended</dt>
          <dd>
            {row.attended} / {row.totalHeld}
          </dd>
        </div>
        <div>
          <dt>Missed</dt>
          <dd>{row.missed}</dd>
        </div>
        <div>
          <dt>May skip</dt>
          <dd>{row.canSkip}</dd>
        </div>
        <div>
          <dt>Must attend</dt>
          <dd>
            {row.status === 'IMPOSSIBLE' || row.status === 'NO_DATA'
              ? '—'
              : `${row.mustAttend} of ${row.remainingClassesEstimate}`}
          </dd>
        </div>
      </dl>

      <p className={`why why--${row.status}`}>{explain(row)}</p>
    </article>
  );
}