import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { JobNav } from '../../components/JobNav';

/**
 * Live job monitor — completeness + per-worker leaderboard. Auto-refreshes every
 * 5 seconds so an admin can leave it open during a labeling session.
 */
export function Monitor() {
  const { id } = useParams();
  const jobId = Number(id);
  const { data, isLoading } = useQuery({
    queryKey: ['monitor', jobId],
    queryFn: () => api.monitor(jobId),
    refetchInterval: 5000,
  });

  if (isLoading || !data) return <div>Loading…</div>;

  const pct = Math.round(data.percentComplete * 100);

  return (
    <div className="space-y-6">
      <JobNav jobId={jobId} />

      <section className="bg-white border border-slate-200 rounded-lg p-5 shadow-sm">
        <div className="flex items-center justify-between text-sm">
          <span className="font-semibold">Completeness</span>
          <span className="text-slate-500">
            {data.judgmentsCollected} / {data.judgmentsNeeded} judgments collected — {pct}%
          </span>
        </div>
        <div className="h-2 bg-slate-100 rounded mt-2">
          <div className="h-2 bg-emerald-500 rounded" style={{ width: `${pct}%` }} />
        </div>
        <div className="mt-3 grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
          <Stat label="Total units" value={data.totalUnits} />
          <Stat label="Gold units" value={data.goldUnits} />
          <Stat label="Completed" value={data.completedUnits} />
          <Stat label="Open non-gold" value={data.openNonGoldUnits} />
        </div>
      </section>

      <section className="bg-white border border-slate-200 rounded-lg p-5 shadow-sm">
        <h2 className="font-semibold text-slate-900 mb-3">Per-worker leaderboard</h2>
        <table className="text-xs w-full">
          <thead className="bg-slate-50 text-left">
            <tr>
              <th className="p-2">user</th>
              <th className="p-2 text-right">judgments</th>
              <th className="p-2 text-right">gold acc.</th>
              <th className="p-2 text-right">mean time</th>
              <th className="p-2 text-right">throughput / h</th>
              <th className="p-2">status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.workers.map(w => (
              <tr key={w.userId} className="border-t">
                <td className="p-2">{w.userId}</td>
                <td className="p-2 text-right">{w.judgmentsTotal}</td>
                <td className="p-2 text-right">
                  {w.goldAttempts === 0 ? '—' : `${w.goldCorrect}/${w.goldAttempts} (${Number(w.accuracy).toFixed(2)})`}
                </td>
                <td className="p-2 text-right">{w.meanTimeMs ? `${(w.meanTimeMs / 1000).toFixed(1)}s` : '—'}</td>
                <td className="p-2 text-right">{w.throughputPerHour.toFixed(1)}</td>
                <td className="p-2">
                  <span className={
                    w.status === 'QUARANTINED'
                      ? 'text-red-700 bg-red-50 rounded px-1.5 py-0.5'
                      : 'text-emerald-700'
                  }>{w.status}</span>
                </td>
                <td className="p-2"><Link className="text-indigo-700 hover:underline" to={`/jobs/${jobId}/workers/${w.userId}`}>review →</Link></td>
              </tr>
            ))}
            {data.workers.length === 0 && (
              <tr><td colSpan={7} className="p-3 text-slate-500 text-center">No worker activity yet.</td></tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: any }) {
  return (
    <div className="border rounded p-2">
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="text-2xl">{String(value)}</div>
    </div>
  );
}
