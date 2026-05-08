import type { AdminUnit, AgreementReport, ConcernRow, DatasetValidation, DebugUser, Job, JobInstructions, Me, MonitorReport, TaskPage, VariantSchema, WorkerUnit } from './types';

let debugUserId: string | null = localStorage.getItem('debugUserId');

export function setDebugUserId(id: string | null) {
  debugUserId = id;
  if (id) localStorage.setItem('debugUserId', id);
  else localStorage.removeItem('debugUserId');
}

async function http<T>(method: string, url: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (debugUserId) headers['X-User-Id'] = debugUserId;
  const res = await fetch(url, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return undefined as T;
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status}: ${text}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  me: () => http<Me>('GET', '/api/auth/me'),
  debugUsers: () => http<DebugUser[]>('GET', '/api/auth/debug-users'),

  listTemplates:   () => http<{ type: string }[]>('GET', '/api/templates'),
  getTemplate:     (type: string) => http<VariantSchema>('GET', `/api/templates/${type}`),
  validateDataset: (type: string, body: { config: any; units: any[] }) =>
                     http<DatasetValidation>('POST', `/api/templates/${type}/validate-dataset`, body),
  exportJob:       (jobId: number) => http<any>('GET', `/api/jobs/${jobId}/export`),

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
