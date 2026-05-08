import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Gate admin-only pages. Workers (and anonymous users) get redirected to the
 * worker feed. Until /api/auth/me has resolved, render a tiny placeholder so
 * we don't flicker the wrong page.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  if (!me) return <div className="text-slate-500 text-sm">…</div>;
  if (me.role !== 'ADMIN') return <Navigate to="/work" replace />;
  return <>{children}</>;
}
