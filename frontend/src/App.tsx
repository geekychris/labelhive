import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { api } from './api/client';
import { useAuth } from './auth/AuthContext';
import { RequireAdmin } from './auth/RequireAdmin';
import { Concerns } from './pages/admin/Concerns';
import { Dashboard } from './pages/admin/Dashboard';
import { JobBuilder } from './pages/admin/JobBuilder';
import { JobDetail } from './pages/admin/JobDetail';
import { Monitor } from './pages/admin/Monitor';
import { Reports } from './pages/admin/Reports';
import { WorkerReview } from './pages/admin/WorkerReview';
import { Feed } from './pages/worker/Feed';
import { JobInstructions } from './pages/worker/JobInstructions';
import { TaskView } from './pages/worker/TaskView';

export default function App() {
  const { me, debugUsers, switchDebugUser } = useAuth();
  const isAdmin = me?.role === 'ADMIN';
  // Polled badge for the admin nav. Only enabled for admins so workers don't fire it.
  const openConcerns = useQuery({
    queryKey: ['concerns', 'OPEN'],
    queryFn: () => api.listConcerns({ status: 'OPEN' }),
    refetchInterval: 10_000,
    enabled: !!isAdmin,
  });
  const concernCount = openConcerns.data?.length ?? 0;

  return (
    <div className="min-h-screen bg-stone-50">
      <header className="bg-slate-900 text-white border-b border-slate-800">
        <div className="max-w-6xl mx-auto px-5 py-4 flex items-center justify-between gap-6">
          <Link to={isAdmin ? '/' : '/work'} className="flex items-center gap-3 font-semibold tracking-tight">
            <img src="/label_hive.png" alt="LabelHive" className="h-24 w-24 rounded bg-white p-1" />
            <div className="flex flex-col leading-tight">
              <span className="text-2xl">LabelHive</span>
              <span className="hidden md:inline text-xs text-slate-400 font-normal">Crowd labeling. Golden truth.</span>
            </div>
          </Link>
          <nav className="flex gap-5 text-sm items-center">
            {isAdmin && <Link to="/" className="text-slate-200 hover:text-white">Jobs</Link>}
            {isAdmin && (
              <Link to="/concerns" className="text-slate-200 hover:text-white inline-flex items-center gap-1">
                Concerns
                {concernCount > 0 && (
                  <span className="rounded-full bg-rose-500 text-white text-[10px] px-1.5 py-0.5 leading-none">
                    {concernCount}
                  </span>
                )}
              </Link>
            )}
            <Link to="/work" className="text-slate-200 hover:text-white">Worker feed</Link>
          </nav>
          <div className="flex items-center gap-2 text-sm">
            {me?.debug && (
              <select
                value={String(me?.id ?? '')}
                onChange={e => switchDebugUser(e.target.value)}
                className="bg-slate-800 border border-slate-700 rounded px-2 py-1 text-xs text-slate-100">
                <option value="">(none)</option>
                {debugUsers.map(u => (
                  <option key={u.id} value={u.id}>{u.username} ({u.role})</option>
                ))}
              </select>
            )}
            {me?.authenticated ? (
              <span className="text-slate-200">{me.username} <span className="text-xs text-slate-400">{me.role}</span></span>
            ) : <span className="text-slate-500">not signed in</span>}
            {me?.debug && <span className="text-[10px] uppercase bg-amber-400/90 text-slate-900 px-1.5 py-0.5 rounded font-medium">debug</span>}
          </div>
        </div>
      </header>
      <main className="max-w-6xl mx-auto p-6">
        <Routes>
          {/* Workers see the worker feed at the root; admins see the Jobs dashboard. */}
          <Route path="/" element={isAdmin ? <Dashboard /> : <Navigate to="/work" replace />} />

          {/* Admin pages — gated. Non-admins are bounced to /work. */}
          <Route path="/jobs/new" element={<RequireAdmin><JobBuilder /></RequireAdmin>} />
          <Route path="/jobs/:id" element={<RequireAdmin><JobDetail /></RequireAdmin>} />
          <Route path="/jobs/:id/monitor" element={<RequireAdmin><Monitor /></RequireAdmin>} />
          <Route path="/jobs/:id/reports" element={<RequireAdmin><Reports /></RequireAdmin>} />
          <Route path="/jobs/:id/workers/:userId" element={<RequireAdmin><WorkerReview /></RequireAdmin>} />
          <Route path="/concerns" element={<RequireAdmin><Concerns /></RequireAdmin>} />

          {/* Worker pages. */}
          <Route path="/work" element={<Feed />} />
          <Route path="/work/:id" element={<TaskView />} />
          <Route path="/work/:id/instructions" element={<JobInstructions />} />

          <Route path="*" element={<Navigate to={isAdmin ? '/' : '/work'} replace />} />
        </Routes>
      </main>
    </div>
  );
}
