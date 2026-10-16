import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { RosterEntry } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/** Today's date as YYYY-MM-DD in the browser's local timezone. */
function today(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000)
    .toISOString()
    .slice(0, 10);
}

/**
 * FACULTY VIEW: the class roster, and the roll call for one date.
 *
 * <p>Students never reach this page. The server enforces that with 403 on
 * both endpoints; the route is hidden for them as a convenience only.
 */
export function RosterPage({ subjectId, subjectName }: { subjectId: string; subjectName: string }) {
  const { fullName } = useAuth();
  const [roster, setRoster] = useState<RosterEntry[] | null>(null);
  const [date, setDate] = useState(today());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [flash, setFlash] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setRoster(await api.roster(subjectId));
    } catch (err) {
      setRoster(null);
      setError(err instanceof Error ? err.message : 'Could not load the roster.');
    }
  }, [subjectId]);

  useEffect(() => {
    void load();
  }, [load]);

  /**
   * Records one student. Marks are per-student and per-date, so a
   * teacher correcting one mistake does not have to redo the class.
   */
  async function mark(studentId: string, status: 'present' | 'absent') {
    setBusy(true);
    setError(null);
    setFlash(null);
    try {
      await api.markAttendance(subjectId, studentId, { heldOn: date, status });
      setFlash(`Recorded ${status} for ${date}.`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not record attendance.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{subjectName}</h1>
          <p className="subtitle">
            Class roster. Attendance is recorded here by you, not by students.
          </p>
        </div>
        <button className="secondary" onClick={() => void load()}>
          Refresh
        </button>
      </div>

      <div className="card">
        <h2>Take the roll call</h2>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <div className="field" style={{ marginBottom: 0, maxWidth: 240 }}>
            <label htmlFor="rollcall-date">Class date</label>
            <input
              id="rollcall-date"
              type="date"
              max={today()}
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </div>
        </div>
        {flash && (
          <div className="alert alert--info" role="status" style={{ marginTop: '0.9rem' }}>
            {flash}
          </div>
        )}
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="card">
        <h2>Roster</h2>

        {!roster && <p className="subtitle">Loading…</p>}

        {roster && roster.length === 0 && (
          <p className="subtitle">
            No students are enrolled in this offering yet.
          </p>
        )}

        {roster?.map((r) => (
          <div
            key={r.profileId}
            className="roster-row"
            style={{ borderTop: '1px solid var(--line)', paddingTop: '0.85rem', marginTop: '0.85rem' }}
          >
            <div>
              <div className="subject__name">{r.fullName}</div>
              <div className="subject__code">
                {r.email}
                {r.pct === null ? (
                  <> · no classes recorded</>
                ) : (
                  <> · {r.pct.toFixed(1)}% · {r.status.replace('_', ' ').toLowerCase()}</>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '0.4rem' }}>
              <button
                type="button"
                className="small present-btn"
                disabled={busy}
                onClick={() => void mark(r.profileId, 'present')}
              >
                Present
              </button>
              <button
                type="button"
                className="small danger"
                disabled={busy}
                onClick={() => void mark(r.profileId, 'absent')}
              >
                Absent
              </button>
            </div>
          </div>
        ))}
      </div>

      {fullName && (
        <p className="subtitle" style={{ marginTop: '1rem' }}>
          Signed in as {fullName}. Every mark you record is stored with your
          name and the time, so a disputed record is traceable.
        </p>
      )}
    </div>
  );
}