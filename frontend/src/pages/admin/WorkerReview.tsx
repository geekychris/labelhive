import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { JobNav } from '../../components/JobNav';

interface JudgmentRow {
  judgmentId: number;
  unitId: number;
  payload: any;
  answer: any;
  wasGold: boolean;
  /** "n/a" | true | false (server returns string for non-gold). */
  goldCorrect: boolean | 'n/a';
  alreadyGold: boolean;
}

export function WorkerReview() {
  const { id, userId } = useParams();
  const jobId = Number(id), uid = Number(userId);
  const qc = useQueryClient();
  const nav = useNavigate();

  const mistakes = useQuery({
    queryKey: ['mistakes', jobId, uid],
    queryFn: () => api.goldMistakes(jobId, uid),
  });
  const judgments = useQuery<JudgmentRow[]>({
    queryKey: ['workerJudg', jobId, uid],
    queryFn: () => api.workerJudgments(jobId, uid),
  });

  const invalidateAll = () => {
    qc.invalidateQueries({ queryKey: ['workerJudg', jobId, uid] });
    qc.invalidateQueries({ queryKey: ['mistakes', jobId, uid] });
    qc.invalidateQueries({ queryKey: ['monitor', jobId] });
    qc.invalidateQueries({ queryKey: ['units', jobId] });
    qc.invalidateQueries({ queryKey: ['report', jobId] });
  };

  const clear = useMutation({
    mutationFn: () => api.clearQuarantine(jobId, uid),
    onSuccess: invalidateAll,
  });

  const promote = useMutation({
    mutationFn: ({ judgmentId, explanation }: { judgmentId: number; explanation?: string }) =>
      api.promoteJudgmentToGold(judgmentId, explanation),
    onSuccess: invalidateAll,
  });

  const revertOne = useMutation({
    mutationFn: (judgmentId: number) => api.revertJudgment(judgmentId),
    onSuccess: invalidateAll,
  });

  const revertAll = useMutation({
    mutationFn: () => api.revertWorker(jobId, uid),
    onSuccess: (r) => {
      invalidateAll();
      alert(`Reverted ${r.removedJudgments} judgments. Reopened ${r.reopenedUnits} units.`);
      nav(`/jobs/${jobId}/monitor`);
    },
  });

  const onPromote = (judgmentId: number) => {
    const explanation = prompt('Optional explanation (why is this answer being promoted to gold?)') ?? undefined;
    promote.mutate({ judgmentId, explanation });
  };

  const onRevertOne = (j: JudgmentRow) => {
    if (confirm(`Revert this single judgment for unit #${j.unitId}? (Trust counters update accordingly.)`)) {
      revertOne.mutate(j.judgmentId);
    }
  };

  const onRevertAll = () => {
    if (confirm(`Permanently delete ALL of worker ${uid}'s judgments on this job? This cannot be undone.`)) {
      revertAll.mutate();
    }
  };

  // Roll-up stats so the admin gets context up top.
  const total = judgments.data?.length ?? 0;
  const goldRows = judgments.data?.filter(j => j.wasGold) ?? [];
  const goldRight = goldRows.filter(j => j.goldCorrect === true).length;
  const goldWrong = goldRows.filter(j => j.goldCorrect === false).length;

  return (
    <div className="space-y-6">
      <JobNav jobId={jobId} />

      <div className="flex items-center justify-between gap-2 flex-wrap">
        <h1 className="text-xl font-semibold tracking-tight text-slate-900">Worker {uid}</h1>
        <div className="flex gap-2">
          <button onClick={() => clear.mutate()} className="px-3 py-1.5 rounded border border-slate-300 text-sm text-slate-700 hover:bg-slate-50">
            Clear quarantine
          </button>
          <button onClick={onRevertAll} disabled={revertAll.isPending}
                  className="px-3 py-1.5 rounded border border-red-300 text-red-700 text-sm hover:bg-red-50">
            {revertAll.isPending ? 'Reverting…' : 'Revert all of this worker’s labels'}
          </button>
        </div>
      </div>

      <section className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
        <Stat label="Judgments" value={total} />
        <Stat label="Gold attempts" value={goldRows.length} />
        <Stat label="Gold correct" value={goldRight} tone={goldRight > 0 ? 'good' : undefined} />
        <Stat label="Gold wrong" value={goldWrong} tone={goldWrong > 0 ? 'bad' : undefined} />
      </section>

      <section>
        <h2 className="font-semibold text-slate-900 mb-2">Disagreements with gold</h2>
        <div className="space-y-2">
          {mistakes.data?.length === 0 && (
            <div className="bg-emerald-50 border border-emerald-200 rounded p-3 text-sm text-emerald-800">
              No gold mistakes — nice.
            </div>
          )}
          {mistakes.data?.map((m: any) => (
            <div key={m.unitId} className="bg-white rounded-lg border-l-4 border-red-400 border border-slate-200 p-3 text-sm shadow-sm">
              <div className="text-xs text-slate-500">unit #{m.unitId}</div>
              <pre className="text-xs bg-stone-50 p-2 rounded mt-1 overflow-auto">{JSON.stringify(m.payload, null, 2)}</pre>
              <div className="grid grid-cols-2 gap-2 mt-2">
                <Box title="Their answer" v={m.yourAnswer} tone="bad" />
                <Box title="Gold answer" v={m.goldAnswer} tone="good" />
              </div>
              {m.explanation && <div className="text-xs italic text-slate-600 mt-2">{m.explanation}</div>}
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2 className="font-semibold text-slate-900 mb-2">All their judgments on this job</h2>
        <div className="overflow-auto rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="text-xs w-full">
            <thead className="bg-stone-50 text-slate-600">
              <tr className="text-left">
                <th className="p-2"></th>
                <th className="p-2">unit</th>
                <th className="p-2">payload</th>
                <th className="p-2">answer</th>
                <th className="p-2">vs gold</th>
                <th className="p-2 text-right" />
              </tr>
            </thead>
            <tbody>
              {judgments.data?.map(j => (
                <JudgmentTableRow key={j.judgmentId}
                                  row={j}
                                  onPromote={() => onPromote(j.judgmentId)}
                                  onRevert={() => onRevertOne(j)} />
              ))}
              {judgments.data?.length === 0 && (
                <tr><td colSpan={6} className="p-4 text-center text-slate-500">No judgments yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="mt-2 text-xs text-slate-500 flex flex-wrap gap-3">
          <span><LegendDot tone="good" /> gold + correct</span>
          <span><LegendDot tone="bad" /> gold + wrong</span>
          <span><LegendDot tone="neutral" /> non-gold</span>
          <span><LegendDot tone="promoted" /> already promoted to gold</span>
        </div>
      </section>
    </div>
  );
}

function JudgmentTableRow({
  row, onPromote, onRevert,
}: { row: JudgmentRow; onPromote: () => void; onRevert: () => void }) {
  const tone = row.alreadyGold
    ? { row: 'bg-amber-50 hover:bg-amber-100', accent: 'border-l-4 border-amber-400' }
    : !row.wasGold
      ? { row: 'bg-white hover:bg-slate-50',     accent: 'border-l-4 border-slate-200' }
      : row.goldCorrect === true
        ? { row: 'bg-emerald-50 hover:bg-emerald-100', accent: 'border-l-4 border-emerald-400' }
        : { row: 'bg-red-50 hover:bg-red-100',         accent: 'border-l-4 border-red-400' };

  const badge = row.alreadyGold
    ? <span className="text-[10px] uppercase bg-amber-200 text-amber-900 px-1.5 py-0.5 rounded">promoted</span>
    : !row.wasGold
      ? <span className="text-[10px] uppercase bg-slate-200 text-slate-700 px-1.5 py-0.5 rounded">non-gold</span>
      : row.goldCorrect === true
        ? <span className="text-[10px] uppercase bg-emerald-200 text-emerald-900 px-1.5 py-0.5 rounded">✓ gold-correct</span>
        : <span className="text-[10px] uppercase bg-red-200 text-red-900 px-1.5 py-0.5 rounded">✗ gold-wrong</span>;

  return (
    <tr className={`border-t border-slate-100 ${tone.row} ${tone.accent}`}>
      <td className="p-2 align-top">{badge}</td>
      <td className="p-2 align-top text-slate-700">#{row.unitId}</td>
      <td className="p-2 align-top max-w-xs truncate text-slate-700">{JSON.stringify(row.payload)}</td>
      <td className="p-2 align-top text-slate-800">{JSON.stringify(row.answer)}</td>
      <td className="p-2 align-top">
        {!row.wasGold && <span className="text-slate-400">—</span>}
        {row.wasGold && row.goldCorrect === true  && <span className="text-emerald-700 font-medium">match</span>}
        {row.wasGold && row.goldCorrect === false && <span className="text-red-700 font-medium">disagree</span>}
      </td>
      <td className="p-2 align-top text-right whitespace-nowrap">
        {!row.alreadyGold && !row.wasGold && (
          <button onClick={onPromote} className="text-indigo-700 hover:underline mr-3">promote → gold</button>
        )}
        <button onClick={onRevert} className="text-red-700 hover:underline">revert</button>
      </td>
    </tr>
  );
}

function Stat({ label, value, tone }: { label: string; value: any; tone?: 'good' | 'bad' }) {
  const ring = tone === 'good' ? 'border-emerald-300 bg-emerald-50'
             : tone === 'bad'  ? 'border-red-300 bg-red-50'
             : 'border-slate-200 bg-white';
  return (
    <div className={`rounded-lg border ${ring} p-3 shadow-sm`}>
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="text-2xl">{String(value)}</div>
    </div>
  );
}

function Box({ title, v, tone }: { title: string; v: any; tone?: 'good' | 'bad' }) {
  const ring = tone === 'good' ? 'bg-emerald-50 border-emerald-200'
             : tone === 'bad'  ? 'bg-red-50 border-red-200'
             : 'bg-stone-50 border-slate-200';
  return (
    <div className={`rounded border ${ring} p-2`}>
      <div className="text-xs text-slate-500">{title}</div>
      <pre className="text-xs">{JSON.stringify(v, null, 2)}</pre>
    </div>
  );
}

function LegendDot({ tone }: { tone: 'good' | 'bad' | 'neutral' | 'promoted' }) {
  const c = tone === 'good'      ? 'bg-emerald-400'
          : tone === 'bad'       ? 'bg-red-400'
          : tone === 'promoted'  ? 'bg-amber-400'
          :                        'bg-slate-300';
  return <span className={`inline-block w-2.5 h-2.5 rounded ${c} mr-1 align-middle`} />;
}
