import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

/**
 * Sub-nav strip shown on every job-scoped admin page so the admin can hop
 * between Detail / Monitor / Reports / Worker reviews without backtracking.
 */
export function JobNav({ jobId }: { jobId: number }) {
  const { pathname } = useLocation();
  const job = useQuery({ queryKey: ['job', jobId], queryFn: () => api.getJob(jobId) });

  const tabs: { label: string; to: string; match: (p: string) => boolean }[] = [
    { label: 'Detail',  to: `/jobs/${jobId}`,
      match: p => p === `/jobs/${jobId}` },
    { label: 'Monitor', to: `/jobs/${jobId}/monitor`,
      match: p => p === `/jobs/${jobId}/monitor` },
    { label: 'Reports', to: `/jobs/${jobId}/reports`,
      match: p => p === `/jobs/${jobId}/reports` },
  ];
  const onWorkerPage = pathname.includes(`/jobs/${jobId}/workers/`);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3 text-sm">
        <Link to="/" className="text-indigo-700 hover:underline">← All jobs</Link>
        {job.data && (
          <>
            <span className="text-slate-300">|</span>
            <span className="text-slate-900 font-medium">{job.data.job.name}</span>
            <span className="text-[10px] uppercase tracking-wider text-slate-500 bg-stone-100 rounded px-2 py-0.5">
              {job.data.job.templateType}
            </span>
          </>
        )}
      </div>
      <nav className="flex gap-1 border-b border-slate-200 -mt-1">
        {tabs.map(t => (
          <Link key={t.to} to={t.to}
                className={
                  'px-3 py-2 text-sm border-b-2 -mb-px ' +
                  (t.match(pathname)
                    ? 'border-amber-500 text-slate-900 font-semibold'
                    : 'border-transparent text-slate-600 hover:text-slate-900')
                }>
            {t.label}
          </Link>
        ))}
        {onWorkerPage && (
          <span className="px-3 py-2 text-sm border-b-2 -mb-px border-amber-500 text-slate-900 font-semibold">
            Worker review
          </span>
        )}
      </nav>
    </div>
  );
}
