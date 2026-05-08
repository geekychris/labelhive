import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { JobNav } from '../../components/JobNav';

export function Reports() {
  const { id } = useParams();
  const jobId = Number(id);
  const { data, isLoading } = useQuery({ queryKey: ['report', jobId], queryFn: () => api.agreement(jobId) });

  if (isLoading) return <div className="text-slate-600">Loading…</div>;
  if (!data) return <div className="text-slate-600">No report.</div>;

  return (
    <div className="space-y-6">
      <JobNav jobId={jobId} />
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Stat label="Total units" value={data.totalUnits} />
        <Stat label="Gold units" value={data.goldUnits} />
        <Stat label="Completed" value={data.completedUnits} />
        <Stat label="Mean pairwise agreement" value={Number(data.meanPairwiseAgreement).toFixed(3)} />
      </div>
      <div className="text-xs text-slate-500">{data.agreementMethod}</div>

      {data.ndcg && (
        <section className="bg-white rounded-lg border border-slate-200 p-5 shadow-sm">
          <h2 className="font-semibold mb-2 text-slate-900">NDCG (search-relevance)</h2>
          <div className="text-sm">Mean NDCG across {data.ndcg.queries} queries: <b>{data.ndcg.meanNdcg}</b></div>
          <table className="text-xs mt-2 w-full">
            <thead><tr className="text-left bg-stone-50"><th className="p-2">query</th><th className="p-2">results</th><th className="p-2">NDCG</th></tr></thead>
            <tbody>
              {data.ndcg.perQuery.map(q => (
                <tr key={q.query} className="border-t border-slate-100">
                  <td className="p-2">{q.query}</td>
                  <td className="p-2">{q.results}</td>
                  <td className="p-2">{q.ndcg}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <section className="bg-white rounded-lg border border-slate-200 p-5 shadow-sm">
        <h2 className="font-semibold mb-2 text-slate-900">Worker trust</h2>
        <table className="text-xs w-full">
          <thead><tr className="text-left bg-stone-50 text-slate-600">
            <th className="p-2">user</th><th className="p-2">gold attempts</th><th className="p-2">correct</th><th className="p-2">accuracy</th><th className="p-2">status</th><th/>
          </tr></thead>
          <tbody>
            {data.workerTrust.map(t => (
              <tr key={t.userId} className="border-t border-slate-100">
                <td className="p-2">{t.userId}</td>
                <td className="p-2">{t.goldAttempts}</td>
                <td className="p-2">{t.goldCorrect}</td>
                <td className="p-2">{Number(t.accuracy).toFixed(3)}</td>
                <td className="p-2">
                  <span className={
                    t.status === 'QUARANTINED'
                      ? 'text-red-700 bg-red-50 rounded px-1.5 py-0.5'
                      : 'text-emerald-700'
                  }>{t.status}</span>
                </td>
                <td className="p-2"><Link className="text-indigo-700 hover:underline" to={`/jobs/${jobId}/workers/${t.userId}`}>review →</Link></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: any }) {
  return (
    <div className="bg-white rounded-lg border border-slate-200 p-3 shadow-sm">
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div className="text-2xl">{String(value)}</div>
    </div>
  );
}
