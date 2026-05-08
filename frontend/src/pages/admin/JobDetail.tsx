import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { JobSettingsPanel } from '../../components/JobSettingsPanel';
import { InviteWorkerLink } from '../../components/InviteWorkerLink';
import { JobNav } from '../../components/JobNav';
import { CloneJobModal, type CloneMode } from '../../components/CloneJobModal';
import { DatasetFormatHelper } from '../../components/DatasetFormatHelper';

export function JobDetail() {
  const { id } = useParams();
  const jobId = Number(id);
  const qc = useQueryClient();
  const nav = useNavigate();

  const job = useQuery({ queryKey: ['job', jobId], queryFn: () => api.getJob(jobId) });
  const units = useQuery({ queryKey: ['units', jobId], queryFn: () => api.listUnits(jobId) });

  const clone = useMutation({
    mutationFn: ({ includeUnits, includeGold }: { includeUnits: boolean; includeGold: boolean }) =>
      api.cloneJob(jobId, { includeUnits, includeGold }),
    onSuccess: (j) => nav(`/jobs/${j.id}`),
  });

  const [cloneOpen, setCloneOpen] = useState(false);
  const askAndClone = (mode: CloneMode) => {
    setCloneOpen(false);
    clone.mutate({
      includeUnits: mode !== 'settings',
      includeGold:  mode === 'with-gold',
    });
  };

  const [uploadText, setUploadText] = useState('[]');
  const [uploadError, setUploadError] = useState<string>();

  const upload = useMutation({
    mutationFn: async () => api.uploadUnits(jobId, JSON.parse(uploadText)),
    onSuccess: () => {
      setUploadText('[]'); setUploadError(undefined);
      qc.invalidateQueries({ queryKey: ['units', jobId] });
      qc.invalidateQueries({ queryKey: ['job', jobId] });
    },
    onError: (e: any) => setUploadError(e.message),
  });

  const promote = useMutation({
    mutationFn: (unitId: number) => api.promoteToGold(unitId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['units', jobId] }),
  });
  const setExemplar = useMutation({
    mutationFn: ({ unitId, exemplar }: { unitId: number; exemplar: boolean }) =>
      api.setExemplar(unitId, exemplar),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['units', jobId] }),
  });

  if (job.isLoading) return <div className="text-slate-600">Loading…</div>;
  if (!job.data) return <div className="text-slate-600">Not found.</div>;
  const j = job.data.job;
  const samplePayload = units.data?.[0]?.payload;

  return (
    <div className="space-y-6">
      <JobNav jobId={jobId} />
      <div className="text-xs text-slate-500 flex gap-4 flex-wrap items-center">
        <span>units {job.data.totalUnits} ({job.data.goldUnits} gold)</span>
        <span>1 gold per {j.goldCadence} non-gold</span>
        <span>{j.tasksPerPage}/page</span>
        <Link className="text-indigo-700 hover:underline" to={`/work/${jobId}`}>start labeling →</Link>
        <button onClick={() => setCloneOpen(true)} disabled={clone.isPending} className="text-indigo-700 hover:underline">
          {clone.isPending ? 'cloning…' : 'clone job →'}
        </button>
      </div>

      <InviteWorkerLink jobId={jobId} />

      <JobSettingsPanel job={j} samplePayload={samplePayload} />

      <section className="bg-white rounded-lg border border-slate-200 shadow-sm p-5 space-y-3">
        <h2 className="font-semibold text-slate-900">Upload units</h2>
        <DatasetFormatHelper
          templateType={j.templateType}
          templateConfig={j.templateConfig}
          datasetText={uploadText}
        />
        <textarea rows={6} className="w-full rounded border border-slate-300 p-2 font-mono text-xs"
                  value={uploadText} onChange={e => setUploadText(e.target.value)} />
        {uploadError && <div className="text-red-700 text-xs">{uploadError}</div>}
        <button onClick={() => upload.mutate()} disabled={upload.isPending}
                className="px-3 py-1.5 rounded bg-amber-500 text-slate-900 font-medium text-sm hover:bg-amber-400">
          {upload.isPending ? 'Uploading…' : 'Upload'}
        </button>
      </section>

      <section className="bg-white rounded-lg border border-slate-200 shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200">
          <h2 className="font-semibold text-slate-900">Units ({units.data?.length ?? 0})</h2>
        </div>
        <div className="overflow-auto">
          <table className="text-xs w-full">
            <thead className="bg-stone-50">
              <tr className="text-left text-slate-600">
                <th className="p-2">id</th>
                <th className="p-2">externalId</th>
                <th className="p-2">payload</th>
                <th className="p-2">gold</th>
                <th className="p-2">worker agreement</th>
                <th className="p-2">judg</th>
                <th className="p-2">status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {units.data?.map(u => (
                <tr key={u.id} className={`border-t border-slate-100 ${rowTone(u)}`}>
                  <td className="p-2 align-top">{u.id}</td>
                  <td className="p-2 align-top">{u.externalId}</td>
                  <td className="p-2 align-top max-w-md truncate">{JSON.stringify(u.payload)}</td>
                  <td className="p-2 align-top">
                    {u.goldAnswer ? (
                      <div className="space-y-1">
                        <span className="bg-amber-100 px-1 rounded">{JSON.stringify(u.goldAnswer)}</span>
                        {u.promotedByUsername && (
                          <div className="text-[10px] text-slate-500">
                            by {u.promotedByUsername} ({u.source.toLowerCase()})
                          </div>
                        )}
                      </div>
                    ) : <span className="text-slate-400">—</span>}
                  </td>
                  <td className="p-2 align-top"><GoldAgreementBadge unit={u} /></td>
                  <td className="p-2 align-top">{u.judgmentCount}</td>
                  <td className="p-2 align-top">{u.status}</td>
                  <td className="p-2 align-top space-y-1">
                    {!u.goldAnswer && u.judgmentCount > 0 && (
                      <button onClick={() => promote.mutate(u.id)} className="text-indigo-700 hover:underline">promote→gold</button>
                    )}
                    {u.goldAnswer && (
                      <label className="flex items-center gap-1 text-[11px] text-slate-700" title="Show this unit on the worker's instructions page as an example">
                        <input
                          type="checkbox"
                          checked={!!u.exemplar}
                          onChange={(e) => setExemplar.mutate({ unitId: u.id, exemplar: e.target.checked })}
                        />
                        exemplar
                      </label>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-3 py-2 text-[11px] text-slate-500 border-t border-slate-100 bg-stone-50 flex flex-wrap gap-3">
            <span><Dot tone="good" /> all workers matched gold</span>
            <span><Dot tone="mixed" /> some matched, some didn't</span>
            <span><Dot tone="bad" /> no matches yet</span>
            <span><Dot tone="neutral" /> no gold judgments yet</span>
          </div>
        </div>
      </section>
      <CloneJobModal open={cloneOpen}
                     onCancel={() => setCloneOpen(false)}
                     onConfirm={askAndClone}
                     busy={clone.isPending} />
    </div>
  );
}

import type { AdminUnit } from '../../api/types';

function rowTone(u: AdminUnit): string {
  if (!u.goldAnswer) return '';
  const m = u.goldMatches ?? 0;
  const w = u.goldMismatches ?? 0;
  if (m + w === 0) return '';
  if (w === 0)     return 'bg-emerald-50/60';
  if (m === 0)     return 'bg-red-50/60';
  return 'bg-amber-50/60';
}

function GoldAgreementBadge({ unit }: { unit: AdminUnit }) {
  if (!unit.goldAnswer) return <span className="text-slate-400">—</span>;
  const m = unit.goldMatches ?? 0;
  const w = unit.goldMismatches ?? 0;
  const total = m + w;
  if (total === 0) return <span className="text-slate-400 text-[11px] italic">not yet judged</span>;

  const summary =
    w === 0 ? <span className="inline-flex items-center gap-1 bg-emerald-100 text-emerald-800 rounded px-2 py-0.5 text-[11px] font-medium">✓ {m}/{total} match</span>
  : m === 0 ? <span className="inline-flex items-center gap-1 bg-red-100 text-red-800 rounded px-2 py-0.5 text-[11px] font-medium">✗ {w}/{total} disagree</span>
            : <span className="inline-flex items-center gap-1 bg-amber-100 text-amber-900 rounded px-2 py-0.5 text-[11px] font-medium">{m}/{total} match · {w} disagree</span>;

  return (
    <div className="space-y-1.5">
      {summary}
      {(unit.goldJudgmentBreakdown ?? []).length > 0 && (
        <div className="flex flex-wrap gap-1">
          {unit.goldJudgmentBreakdown!.map((b, i) => <AnswerChip key={i} entry={b} />)}
        </div>
      )}
    </div>
  );
}

function AnswerChip({ entry }: { entry: { answer: any; correct: boolean; count: number } }) {
  const cls = entry.correct
    ? 'bg-emerald-50 border-emerald-300 text-emerald-800'
    : 'bg-red-50 border-red-300 text-red-800';
  return (
    <span className={`inline-flex items-center gap-1 border rounded px-1.5 py-0.5 text-[10px] font-mono ${cls}`}
          title={JSON.stringify(entry.answer)}>
      <span className="font-semibold">{entry.correct ? '✓' : '✗'}</span>
      <span>{prettyAnswer(entry.answer)}</span>
      <span className="text-slate-500">×{entry.count}</span>
    </span>
  );
}

/** Best-effort short rendering of a worker answer for the chip. */
function prettyAnswer(a: any): string {
  if (a == null) return 'null';
  if (typeof a !== 'object') return String(a);
  if ('choice' in a)   return String(a.choice);
  if ('grade'  in a)   return `grade ${a.grade}`;
  if ('text'   in a)   return JSON.stringify(a.text).slice(0, 30);
  if ('choices' in a)  return Array.isArray(a.choices) ? a.choices.join(',') : JSON.stringify(a.choices);
  if ('spans'  in a)   return `${(a.spans ?? []).length} span${(a.spans ?? []).length === 1 ? '' : 's'}`;
  if ('boxes'  in a)   return `${(a.boxes ?? []).length} box${(a.boxes ?? []).length === 1 ? '' : 'es'}`;
  return JSON.stringify(a).slice(0, 30);
}

function Dot({ tone }: { tone: 'good' | 'mixed' | 'bad' | 'neutral' }) {
  const c = tone === 'good'    ? 'bg-emerald-400'
          : tone === 'mixed'   ? 'bg-amber-400'
          : tone === 'bad'     ? 'bg-red-400'
          :                       'bg-slate-300';
  return <span className={`inline-block w-2.5 h-2.5 rounded ${c} mr-1 align-middle`} />;
}
