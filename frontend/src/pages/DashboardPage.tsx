import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { DashboardRow } from '../api/types';
import { SubjectCard } from '../components/SubjectCard';
import { ApiRequestError } from '../api/types';

export function DashboardPage() {
  const [rows, setRows] = useState<DashboardRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await api.dashboard());
    } catch (err) {
      // A thrown error must not leave `rows` as a stale list from a
      // previous successful load, or the user sees old data next to an
      // error banner and cannot tell which is true.
      setRows(null);
      setError(
        err instanceof ApiRequestError
          ? err.message
          : 'Could not load the dashboard.',
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <div className="page">
        <div className="page-head">
          <div>
            <h1>Attendance</h1>
            <p className="subtitle">Loading your subjects…</p>
          </div>
        </div>
        <div className="grid">
          {[0, 1, 2].map((i) => (
            <div key={i} className="skeleton" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <h1>Attendance</h1>
        <div className="alert alert--error" role="alert">
          {error}
        </div>
        <button className="secondary" onClick={() => void load()}>
          Try again
        </button>
      </div>
    );
  }

  // An empty list is a valid, meaningful state — a new account with no
  // subjects yet. It deserves its own message, not a blank screen.
  if (!rows || rows.length === 0) {
    return (
      <div className="page">
        <h1>Attendance</h1>
        <div className="card empty">
          <h3>No subjects yet</h3>
          <p>
            Add a subject on the Subjects page and start marking attendance.
            Your standing will appear here.
          </p>
        </div>
      </div>
    );
  }

  const needsAttention = rows.filter(
    (r) => r.status === 'IMPOSSIBLE' || r.status === 'CRITICAL' || r.status === 'AT_RISK',
  ).length;
  const totalSkips = rows.reduce((sum, r) => sum + r.canSkip, 0);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Attendance</h1>
          <p className="subtitle">
            {needsAttention === 0
              ? 'All subjects are on track.'
              : `${needsAttention} subject${needsAttention === 1 ? '' : 's'} need attention.`}
          </p>
        </div>
        <button className="secondary" onClick={() => void load()}>
          Refresh
        </button>
      </div>

      {totalSkips > 0 && (
        <div className="summary-strip">
          <span className="chip">
            {totalSkips} class{totalSkips === 1 ? '' : 'es'} you can safely miss
          </span>
          <span className="chip">{rows.length} subjects</span>
        </div>
      )}

      <div className="grid">
        {rows.map((row) => (
          <SubjectCard key={row.subjectId} row={row} />
        ))}
      </div>
    </div>
  );
}