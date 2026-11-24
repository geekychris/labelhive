import type { AdminUnit, AgreementReport, ConcernRow, DatasetValidation, DebugUser, Job, JobInstructions, Me, MonitorReport, TaskPage, VariantSchema, WorkerUnit } from './types';

// Storage can be missing or throw (private windows, blocked site data, some test runtimes).
function store(kind: 'localStorage' | 'sessionStorage'): Storage | null {
  try { return (globalThis as any)[kind] ?? null; } catch { return null; }
}
function storeGet(kind: 'localStorage' | 'sessionStorage', key: string): string | null {
  try { return store(kind)?.getItem(key) ?? null; } catch { return null; }
}
function storeSet(kind: 'localStorage' | 'sessionStorage', key: string, value: string | null) {
  try {
    const s = store(kind);
    if (value === null) s?.removeItem(key); else s?.setItem(key, value);
  } catch { /* storage blocked */ }
}

let debugUserId: string | null = storeGet('localStorage', 'debugUserId');

/** Where the backend starts an OpenID Connect sign-in (registrationId `cloudstack`). */
export const LOGIN_URL = '/oauth2/authorization/cloudstack';
const LOGIN_GUARD_KEY = 'labelhive.loginRedirectAt';

/** Indirection so tests can observe navigation (jsdom can't navigate). */
export const nav = {
  assign: (url: string) => { window.location.assign(url); },
};

/** Only the debug profile (local dev) reads X-User-Id; deployed backends ignore it. */
export function setDebugUserId(id: string | null) {
  debugUserId = id;
  storeSet('localStorage', 'debugUserId', id);
}

/** The CSRF token Spring Security keeps in the XSRF-TOKEN cookie (absent in the debug profile). */
export function csrfToken(): string | null {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1]) : null;
}

/**
 * Not signed in (or the session expired): go to the sign-in. A second 401
 * within 15 s doesn't redirect again, so a broken session can't loop.
 */
let lastLoginRedirect = 0;
/** For tests: forget the last sign-in redirect. */
export function resetLoginGuard() { lastLoginRedirect = 0; storeSet('sessionStorage', LOGIN_GUARD_KEY, null); }
export function redirectToLogin(): boolean {
  const last = Math.max(lastLoginRedirect, Number(storeGet('sessionStorage', LOGIN_GUARD_KEY) ?? 0));
  if (Date.now() - last < 15_000) return false;
  lastLoginRedirect = Date.now();
  storeSet('sessionStorage', LOGIN_GUARD_KEY, String(lastLoginRedirect));
  nav.assign(LOGIN_URL);
  return true;
}

export class UnauthorizedError extends Error {
  constructor() { super('401: not signed in'); }
}

async function http<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (debugUserId) headers['X-User-Id'] = debugUserId;
  if (method !== 'GET' && method !== 'HEAD') {
    const token = csrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  const res = await fetch(url, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 401) {
    redirectToLogin();
    throw new UnauthorizedError();
  }
  if (res.status === 204) return undefined as T;
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status}: ${text}`);
  }
  return res.json() as Promise<T>;
}

/** Sign out: a real form POST so the browser follows the redirect to the provider's end_session. */
export function signOut() {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/logout';
  const token = csrfToken();
  if (token) {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = '_csrf';
    input.value = token;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
}

export const api = {
  me: () => http<Me>('GET', '/api/auth/me'),
  debugUsers: () => http<DebugUser[]>('GET', '/api/auth/debug-users'),

  listTemplates:   () => http<{ type: string }[]>('GET', '/api/templates'),
  getTemplate:     (type: string) => http<VariantSchema>('GET', `/api/templates/${type}`),
  validateDataset: (type: string, body: { config: any; units: any[] }) =>
                     http<DatasetValidation>('POST', `/api/templates/${type}/validate-dataset`, body),
  exportJob:       (jobId: number) => http<any>('GET', `/api/jobs/${jobId}/export`),
  mailStatus:      () => http<{ enabled: boolean; reason?: string }>('GET', '/api/mail/status'),
  inviteByEmail:   (jobId: number, to: string, note?: string) =>
                     http<{ sent: boolean; to: string }>('POST', `/api/jobs/${jobId}/invite`, { to, note }),

  listJobs:   () => http<Job[]>('GET', '/api/jobs'),
  cloneJob:   (id: number, opts?: { includeUnits?: boolean; includeGold?: boolean }) =>
                http<Job>('POST', `/api/jobs/${id}/clone`, opts ?? {}),
  getJob:     (id: number) => http<{ job: Job; totalUnits: number; goldUnits: number }>('GET', `/api/jobs/${id}`),
  createJob:  (job: Partial<Job>) => http<Job>('POST', '/api/jobs', job),
  updateJob:  (id: number, patch: Partial<Job>) => http<Job>('PUT', `/api/jobs/${id}`, patch),
  // (createJob/updateJob accept partial Job — including the new minJudgments/maxJudgments fields)
  deleteJob:  (id: number) => http<void>('DELETE', `/api/jobs/${id}`),

  listUnits:  (jobId: number) => http<AdminUnit[]>('GET', `/api/jobs/${jobId}/units`),
  uploadUnits:(jobId: number, body: unknown) => http<AdminUnit[]>('POST', `/api/jobs/${jobId}/units`, body),

  nextTask:   (jobId: number) => http<WorkerUnit | undefined>('GET', `/api/jobs/${jobId}/tasks/next`),
  nextPage:   (jobId: number, n?: number) =>
                http<TaskPage>('GET', `/api/jobs/${jobId}/tasks/next${n ? `?n=${n}` : ''}`),
  jobInstructions: (jobId: number) =>
                http<JobInstructions>('GET', `/api/jobs/${jobId}/tasks/instructions`),
  setExemplar: (unitId: number, exemplar: boolean) =>
                http<AdminUnit>('POST', `/api/admin/units/${unitId}/exemplar`, { exemplar }),
  submit:     (jobId: number, unitId: number, answer: unknown, timeMs: number) =>
                http<{ ok: boolean; judgmentId: number }>('POST', `/api/jobs/${jobId}/tasks/${unitId}/judgment`, { answer, timeMs }),

  // Worker raises a concern about a unit ("this data looks wrong").
  raiseConcern: (jobId: number, unitId: number, body: string) =>
                http<{ ok: boolean; concernId: number }>('POST', `/api/jobs/${jobId}/tasks/${unitId}/concern`, { body }),

  // Admin concerns inbox + resolution actions.
  listConcerns: (params?: { status?: 'OPEN' | 'ALL'; jobId?: number }) => {
    const q = new URLSearchParams();
    if (params?.status) q.set('status', params.status);
    if (params?.jobId)  q.set('jobId',  String(params.jobId));
    const qs = q.toString();
    return http<ConcernRow[]>('GET', `/api/admin/concerns${qs ? `?${qs}` : ''}`);
  },
  unitConcerns:  (unitId: number) => http<ConcernRow[]>('GET', `/api/admin/units/${unitId}/concerns`),
  dismissConcern:(id: number, note?: string) => http<ConcernRow>('POST', `/api/admin/concerns/${id}/dismiss`, { note }),
  editUnitPayload: (unitId: number, payload: unknown, note?: string) =>
                http<{ unitId: number; judgmentsDiscarded: number; concernsResolved: number }>(
                  'PUT', `/api/admin/units/${unitId}/payload`, { payload, note }),
  archiveUnit:   (unitId: number, note?: string) =>
                http<{ unitId: number; concernsResolved: number }>(
                  'POST', `/api/admin/units/${unitId}/archive`, { note }),

  agreement:  (jobId: number) => http<AgreementReport>('GET', `/api/jobs/${jobId}/reports/agreement`),
  monitor:    (jobId: number) => http<MonitorReport>('GET', `/api/jobs/${jobId}/reports/monitor`),
  goldMistakes:(jobId: number, userId: number) => http<any[]>('GET', `/api/jobs/${jobId}/reports/worker/${userId}/gold-mistakes`),

  promoteToGold: (unitId: number, body?: { answer?: unknown; explanation?: string }) =>
                   http<AdminUnit>('POST', `/api/admin/units/${unitId}/promote-to-gold`, body ?? {}),
  promoteJudgmentToGold: (judgmentId: number, explanation?: string) =>
                   http<AdminUnit>('POST', `/api/admin/judgments/${judgmentId}/promote-to-gold`, { explanation }),
  revertWorker: (jobId: number, userId: number) =>
                   http<{ ok: boolean; removedJudgments: number; affectedUnits: number; reopenedUnits: number }>(
                     'POST', `/api/admin/jobs/${jobId}/workers/${userId}/revert`),
  revertJudgment: (judgmentId: number) =>
                   http<{ ok: boolean; judgmentId: number; unitId: number; unitReopened: boolean }>(
                     'POST', `/api/admin/judgments/${judgmentId}/revert`),
  clearQuarantine: (jobId: number, userId: number) =>
                     http<{ ok: boolean }>('POST', `/api/admin/jobs/${jobId}/workers/${userId}/clear-quarantine`),
  workerJudgments: (jobId: number, userId: number) =>
                     http<any[]>('GET', `/api/admin/jobs/${jobId}/workers/${userId}/judgments`),
};
