/**
 * Typed API client.
 *
 * Every shape here mirrors what Spring Boot actually returns. The point
 * of declaring them is that a mismatch between frontend and backend
 * becomes a COMPILE error rather than a runtime surprise — `undefined`
 * showing up in the UI three weeks later.
 *
 * Status codes are string unions, not `string`. Typing them means a typo
 * in a status comparison fails at build time instead of silently never
 * matching.
 */

/** Mirrors the status column produced by attendance_health(). */
export type AttendanceStatus =
  | 'IMPOSSIBLE'
  | 'CRITICAL'
  | 'AT_RISK'
  | 'ON_TRACK'
  | 'NO_DATA';

/** One row of /api/dashboard. Every number was computed in PostgreSQL. */
export interface DashboardRow {
  subjectId: string;
  subjectName: string;
  subjectCode: string | null;
  semester: number;
  targetAttendance: number;
  remainingClassesEstimate: number;
  /** bigint in Postgres, so a number here can exceed 32 bits. */
  totalHeld: number;
  attended: number;
  missed: number;
  /** null means no classes recorded — NOT zero. The distinction matters. */
  pct: number | null;
  canSkip: number;
  mustAttend: number;
  recoverable: boolean;
  status: AttendanceStatus;
  lastClass: string | null;
}

export interface Subject {
  id: string;
  name: string;
  code: string | null;
  semester: number;
  targetAttendance: number;
  remainingClassesEstimate: number;
  /** Null when the offering is not yet assigned to a teacher. */
  facultyId: string | null;
}

export interface AttendanceRecord {
  id: string;
  subjectId: string;
  /** Whose attendance this is. */
  profileId: string;
  heldOn: string;
  status: 'present' | 'absent';
  note: string | null;
  /** Who recorded it. Null on rows migrated before faculty marking. */
  markedBy: string | null;
  markedAt: string | null;
}

/**
 * One student in a faculty member's class roster.
 *
 * <p>The figures come from the same attendance_health() call that fills
 * the student's own dashboard, so a teacher and a student always see the
 * same numbers for the same person.
 */
export interface RosterEntry {
  profileId: string;
  fullName: string;
  email: string | null;
  pct: number | null;
  canSkip: number;
  mustAttend: number;
  recoverable: boolean;
  status: AttendanceStatus;
}

/**
 * A campus landmark. Coordinates are WGS84 decimal degrees, which is what
 * OpenStreetMap and OSRM both use.
 */
export interface CampusLocation {
  id: string;
  name: string;
  description: string | null;
  category: string;
  building: string | null;
  latitude: number;
  longitude: number;
}

/** OSRM's response, passed through the backend mostly unchanged. */
export interface OsrmRouteResponse {
  code?: string;
  routes?: {
    distance: number;
    duration: number;
    geometry: { coordinates: number[][] };
  }[];
}

export interface AuthResponse {
  profileId: string;
  fullName: string;
  role: string;
  token: string;
  expiresInSeconds: number;
}

/** Mirrors the ApiError record on the server. One shape for every failure. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  fieldErrors?: { field: string; message: string }[];
}

/**
 * An error we raised ourselves, carrying the server's structured detail.
 *
 * Without this, callers end up parsing `err.message` as a string, which
 * breaks the moment the server wording changes.
 */
export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiError,
  ) {
    super(body.message);
    this.name = 'ApiRequestError';
  }

  /** Field-level validation messages, keyed by field name. */
  get fieldErrors(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const fe of this.body.fieldErrors ?? []) {
      out[fe.field] = fe.message;
    }
    return out;
  }
}