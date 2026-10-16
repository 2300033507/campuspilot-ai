import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { ApiRequestError, type AttendanceRecord, type Subject } from '../api/types';

/** Today's date as YYYY-MM-DD in the browser's local timezone. */
function today(): string {
  const d = new Date();
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

export function SubjectsPage() {
  const [subjects, setSubjects] = useState<Subject[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [semester, setSemester] = useState('5');
  const [target, setTarget] = useState('75');
  const [remaining, setRemaining] = useState('20');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [expanded, setExpanded] = useState<string | null>(null);
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [markDate, setMarkDate] = useState(today());
  const [markStatus, setMarkStatus] = useState<'present' | 'absent'>('present');

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

  async function onCreate(e: FormEvent) {
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
      // Field-level messages from the server are shown under the right
      // input, so the user fixes everything in one pass rather than
      // discovering problems one resubmit at a time.
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
    if (!confirm(`Delete "${subjectName}"? Its attendance records go too.`)) {
      return;
    }
    try {
      await api.deleteSubject(id);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete.');
    }
  }

  async function toggleRecords(subject: Subject) {
    if (expanded === subject.id) {
      setExpanded(null);
      return;
    }
    setExpanded(subject.id);
    try {
      setRecords(await api.attendance(subject.id));
    } catch {
      setRecords([]);
    }
  }

  async function onMark(e: FormEvent) {
    e.preventDefault();
    if (!expanded) return;
    setBusy(true);
    setError(null);
    try {
      await api.markAttendance(expanded, { heldOn: markDate, status: markStatus });
      setRecords(await api.attendance(expanded));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not record attendance.');
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteRecord(recordId: string) {
    if (!expanded) return;
    try {
      await api.deleteAttendance(expanded, recordId);
      setRecords(await api.attendance(expanded));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove the record.');
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>Subjects</h1>
          <p className="subtitle">
            Every subject needs a target and an estimate of how many classes
            remain. Both affect what the dashboard can tell you.
          </p>
        </div>
      </div>

      {error && (
        <div className="alert alert--error" role="alert">
          {error}
        </div>
      )}

      <div className="card">
        <h2>Add a subject</h2>
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
              {fieldErrors.name && (
                <p className="field-error">{fieldErrors.name}</p>
              )}
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
              {fieldErrors.code && (
                <p className="field-error">{fieldErrors.code}</p>
              )}
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
                aria-invalid={Boolean(fieldErrors.targetAttendance)}
              />
              {fieldErrors.targetAttendance && (
                <p className="field-error">{fieldErrors.targetAttendance}</p>
              )}
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
                aria-invalid={Boolean(fieldErrors.remainingClassesEstimate)}
              />
              {fieldErrors.remainingClassesEstimate && (
                <p className="field-error">
                  {fieldErrors.remainingClassesEstimate}
                </p>
              )}
            </div>
          </div>

          <button type="submit" disabled={busy || !name.trim()}>
            {busy ? 'Saving…' : 'Add subject'}
          </button>
        </form>
      </div>

      <div className="card">
        <h2>Your subjects</h2>

        {!subjects && <p className="subtitle">Loading…</p>}

        {subjects && subjects.length === 0 && (
          <p className="subtitle">
            No subjects yet. Add one above to get started.
          </p>
        )}

        {subjects?.map((s) => (
          <div key={s.id} style={{ borderTop: '1px solid var(--line)', paddingTop: '0.85rem', marginTop: '0.85rem' }}>
            <div className="subject__head" style={{ marginBottom: 0 }}>
              <div>
                <div className="subject__name">{s.name}</div>
                <div className="subject__code">
                  {s.code ?? '—'} · Sem {s.semester} · target {s.targetAttendance}% ·{' '}
                  {s.remainingClassesEstimate} classes left
                </div>
              </div>
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <button
                  type="button"
                  className="secondary small"
                  onClick={() => void toggleRecords(s)}
                >
                  {expanded === s.id ? 'Hide' : 'Attendance'}
                </button>
                <button
                  type="button"
                  className="danger small"
                  onClick={() => void onDelete(s.id, s.name)}
                >
                  Delete
                </button>
              </div>
            </div>

            {expanded === s.id && (
              <div style={{ marginTop: '0.9rem' }}>
                <form onSubmit={onMark} className="row" style={{ alignItems: 'flex-end' }}>
                  <div className="field" style={{ marginBottom: 0 }}>
                    <label htmlFor={`date-${s.id}`}>Date</label>
                    <input
                      id={`date-${s.id}`}
                      type="date"
                      max={today()}
                      value={markDate}
                      onChange={(e) => setMarkDate(e.target.value)}
                      required
                    />
                  </div>
                  <div className="field" style={{ marginBottom: 0 }}>
                    <label htmlFor={`status-${s.id}`}>Status</label>
                    <select
                      id={`status-${s.id}`}
                      value={markStatus}
                      onChange={(e) =>
                        setMarkStatus(e.target.value as 'present' | 'absent')
                      }
                    >
                      <option value="present">Present</option>
                      <option value="absent">Absent</option>
                    </select>
                  </div>
                  <div style={{ flex: '0 0 auto' }}>
                    <button type="submit" disabled={busy}>
                      Mark
                    </button>
                  </div>
                </form>

                {records.length === 0 ? (
                  <p className="subtitle" style={{ marginTop: '0.8rem' }}>
                    No classes recorded yet.
                  </p>
                ) : (
                  <ul style={{ listStyle: 'none', padding: 0, margin: '0.9rem 0 0' }}>
                    {records.map((r) => (
                      <li
                        key={r.id}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          padding: '0.4rem 0',
                          borderTop: '1px solid var(--line)',
                          fontSize: '0.9rem',
                        }}
                      >
                        <span>
                          {r.heldOn} —{' '}
                          <strong
                            style={{
                              color:
                                r.status === 'present'
                                  ? 'var(--on-track)'
                                  : 'var(--impossible)',
                            }}
                          >
                            {r.status}
                          </strong>
                        </span>
                        <button
                          type="button"
                          className="danger small"
                          onClick={() => void onDeleteRecord(r.id)}
                        >
                          Remove
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
