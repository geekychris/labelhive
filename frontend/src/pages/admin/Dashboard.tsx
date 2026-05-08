import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';

export function Dashboard() {
  const { data: jobs, isLoading } = useQuery({ queryKey: ['jobs'], queryFn: api.listJobs });

  if (isLoading) return <div className="text-slate-600">Loading…</div>;
  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Jobs</h1>
        <Link to="/jobs/new"
              className="px-4 py-2 rounded bg-amber-500 text-slate-900 font-medium text-sm hover:bg-amber-400 shadow-sm">
          + New job
        </Link>
      </div>
      <div className="grid gap-3">
        {jobs?.length === 0 && (
          <div className="bg-white border border-slate-200 rounded-lg p-8 text-center text-slate-600">
            No jobs yet — start one.
          </div>
        )}
        {jobs?.map(j => (
          <Link key={j.id} to={`/jobs/${j.id}`}
                className="block bg-white rounded-lg border border-slate-200 p-4 hover:border-slate-300 hover:shadow-sm transition">
            <div className="flex items-center justify-between gap-4">
              <div className="font-medium text-slate-900">{j.name}</div>
              <span className="text-[10px] uppercase tracking-wider text-slate-500 bg-stone-100 rounded px-2 py-0.5">{j.templateType}</span>
            </div>
            {j.description && <div className="text-sm text-slate-600 mt-1">{j.description}</div>}
            <div className="mt-2 text-xs text-slate-500 flex gap-4 flex-wrap">
              <span>1 gold per {j.goldCadence} non-gold</span>
              <span>{j.tasksPerPage}/page</span>
              <span>{j.minJudgments === j.maxJudgments
                ? `${j.minJudgments} judgments/unit`
                : `${j.minJudgments}–${j.maxJudgments} judgments/unit (escalates)`}</span>
              <span>trust ≥ {Number(j.trustFloor).toFixed(2)}</span>
              <span className="text-slate-400">{j.status}</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
