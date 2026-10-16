import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import { ApiRequestError, type Subject } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/**
 * STUDENT VIEW: their subjects, read-only.
 *
 * <p>There is deliberately NO attendance-marking control on this page.
 * A student declaring themselves present would make the record
 * meaningless — attendance is an observation made by the teacher.
 *
 * <p>That is not just a UI decision. The server answers any student
 * marking attempt with 403, so removing the button is a courtesy, not
 * the control.
 */
export function SubjectsPage() {
  const { canMarkAttendance } = useAuth();
  const [subjects, setSubjects] = useState<Subject[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Faculty-only fields
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [semester, setSemester] = useState('5');
  const [target, setTarget] = useState('75');
  const [remaining, setRemaining] = useState('20');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setError(null);
    try {
      setSubjects(await api.subjects());
    } catch (err) {
      setSubjects(null);
      setError(err instanceof Error ? err.message : 'Could not load subjects.');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await api.createSubject({
        name: name.trim(),
        code: code.trim() || null,
        semester: Number(semester),
        targetAttendance: Number(target),
        remainingClassesEstimate: Number(remaining),
      });
      setName('');
      setCode('');
      await load();
    } catch (err) {
      // Field-level messages from the server render under the right
      // input, so the user fixes everything in one pass.
      if (err instanceof ApiRequestError) {
        const fe = err.fieldErrors;
        if (Object.keys(fe).length > 0) setFieldErrors(fe);
        else setError(err.message);
      } else {
        setError('Could not create the subject.');
      }
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(id: string, subjectName: string) {
    if (!confirm(`Delete "${subjectName}"? Its attendance records go too.`)) return;
    try {
      await api.deleteSubject(id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete.');
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Subjects</h1>
          <p className="subtitle">
            {canMarkAttendance
              ? 'Offerings you teach. Each target and estimate affects what the dashboard can tell you.'
              : 'The subjects you are enrolled in. Attendance for these is recorded by your teacher.'}
          </p>
        </div>
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      {canMarkAttendance && (
        <div className="card">
          <h2>Create an offering</h2>
          <form onSubmit={onCreate} noValidate>
            <div className="row">
              <div className="field">
                <label htmlFor="name">Name</label>
                <input
                  id="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.name)}
                  required
                />
                {fieldErrors.name && <p className="field-error">{fieldErrors.name}</p>}
              </div>

              <div className="field">
                <label htmlFor="code">Code</label>
                <input
                  id="code"
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="CS501"
                  aria-invalid={Boolean(fieldErrors.code)}
                />
                {fieldErrors.code && <p className="field-error">{fieldErrors.code}</p>}
              </div>

              <div className="field">
                <label htmlFor="semester">Semester</label>
                <input
                  id="semester"
                  type="number"
                  min={1}
                  max={10}
                  value={semester}
                  onChange={(e) => setSemester(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.semester)}
                />
                {fieldErrors.semester && (
                  <p className="field-error">{fieldErrors.semester}</p>
                )}
              </div>
            </div>

            <div className="row">
              <div className="field">
                <label htmlFor="target">Target attendance %</label>
                <input
                  id="target"
                  type="number"
                  min={0}
                  max={100}
                  value={target}
                  onChange={(e) => setTarget(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="remaining">Classes remaining</label>
                <input
                  id="remaining"
                  type="number"
                  min={0}
                  max={200}
                  value={remaining}
                  onChange={(e) => setRemaining(e.target.value)}
                />
              </div>
            </div>

            <button type="submit" disabled={busy || !name.trim()}>
              {busy ? 'Saving…' : 'Create offering'}
            </button>
          </form>
        </div>
      )}

      <div className="card">
        <h2>{canMarkAttendance ? 'Offerings you teach' : 'Your subjects'}</h2>

        {!subjects && <p className="subtitle">Loading…</p>}

        {subjects && subjects.length === 0 && (
          <p className="subtitle">
            {canMarkAttendance
              ? 'You are not teaching anything yet.'
              : 'You are not enrolled in anything yet.'}
          </p>
        )}

        {subjects?.map((s) => (
          <div
            key={s.id}
            style={{
              borderTop: '1px solid var(--line)',
              paddingTop: '0.85rem',
              marginTop: '0.85rem',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '0.75rem',
              flexWrap: 'wrap',
            }}
          >
            <div>
              <div className="subject__name">{s.name}</div>
              <div className="subject__code">
                {s.code ?? '—'} · Sem {s.semester} · target {s.targetAttendance}% ·{' '}
                {s.remainingClassesEstimate} classes left
              </div>
            </div>
            {canMarkAttendance && (
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <a className="btn-link" href={`/subjects/${s.id}/roster`}>
                  Take attendance
                </a>
                <button
                  type="button"
                  className="danger small"
                  onClick={() => void onDelete(s.id, s.name)}
                >
                  Delete
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}