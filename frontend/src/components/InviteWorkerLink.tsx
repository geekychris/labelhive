import { useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';

/**
 * Small admin tool: generate a shareable link that drops a specific person
 * into a job as a worker. In debug mode the link includes <code>?as=&lt;username&gt;</code>
 * which the worker view consumes to auto-switch identity. Outside debug mode
 * the link is just <code>/work/:jobId</code> and assumes real auth.
 */
export function InviteWorkerLink({ jobId }: { jobId: number }) {
  const { me, debugUsers } = useAuth();
  const [username, setUsername] = useState('');
  const [copied, setCopied] = useState(false);

  const link = useMemo(() => {
    const base = window.location.origin;
    const path = `/work/${jobId}`;
    if (me?.debug && username.trim()) {
      return `${base}${path}?as=${encodeURIComponent(username.trim())}`;
    }
    return `${base}${path}`;
  }, [jobId, username, me?.debug]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard blocked — fall back to text-select prompt.
      window.prompt('Copy this link:', link);
    }
  };

  const workerOptions = (debugUsers ?? []).filter(u => u.role === 'WORKER');

  return (
    <section className="bg-white rounded-lg border border-slate-200 shadow-sm p-5 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-slate-900">Invite a worker</h2>
        {me?.debug && <span className="text-[10px] uppercase bg-amber-100 text-amber-800 px-1.5 py-0.5 rounded">debug</span>}
      </div>
      <p className="text-xs text-slate-600">
        Share this link with someone — they'll land directly on this job's worker
        view. {me?.debug && <>In debug mode, including a username auto-switches the recipient
        to that worker so you can demo per-person experience.</>}
      </p>

      {me?.debug && (
        <div className="grid md:grid-cols-2 gap-3">
          <label className="text-sm">
            <div className="text-slate-700 mb-1">Worker username</div>
            <input className="w-full rounded border border-slate-300 p-2 text-sm" list="worker-usernames"
                   value={username} onChange={e => setUsername(e.target.value)}
                   placeholder="alice" />
            <datalist id="worker-usernames">
              {workerOptions.map(u => <option key={u.id} value={u.username} />)}
            </datalist>
            {workerOptions.length > 0 && (
              <p className="text-xs text-slate-500 mt-1">
                Quick pick: {workerOptions.map((u, i) => (
                  <span key={u.id}>
                    {i > 0 && ' · '}
                    <button type="button" className="text-indigo-700 hover:underline"
                            onClick={() => setUsername(u.username)}>{u.username}</button>
                  </span>
                ))}
              </p>
            )}
          </label>
          <div />
        </div>
      )}

      <div className="flex items-center gap-2">
        <input readOnly value={link}
               className="flex-1 rounded border border-slate-300 bg-stone-50 p-2 text-sm font-mono text-slate-700"
               onFocus={e => e.target.select()} />
        <button onClick={copy}
                className="px-3 py-2 rounded bg-amber-500 text-slate-900 font-medium text-sm hover:bg-amber-400">
          {copied ? 'Copied!' : 'Copy'}
        </button>
      </div>
    </section>
  );
}
