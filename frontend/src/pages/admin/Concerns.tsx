import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { ConcernRow } from '../../api/types';

/**
 * Admin concerns inbox. Workers raise concerns via the worker UI; this page is the
 * triage view: dismiss, edit-the-payload (which discards prior judgments), or
 * archive (soft-delete the unit, keep judgments for audit).
 */
export function Concerns() {
  const qc = useQueryClient();
  const [showAll, setShowAll] = useState(false);
  const list = useQuery({
    queryKey: ['concerns', showAll ? 'ALL' : 'OPEN'],
    queryFn: () => api.listConcerns({ status: showAll ? 'ALL' : 'OPEN' }),
    refetchInterval: 5_000,
  });
  const refresh = () => qc.invalidateQueries({ queryKey: ['concerns'] });

  return (
    <div className="space-y-4">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Concerns</h1>
          <p className="text-sm text-slate-600">
            Worker-raised flags about specific labeling units. Resolve each by dismissing,
            editing the payload (discards prior judgments on the unit), or archiving the unit.
          </p>
        </div>
        <label className="text-xs text-slate-600 flex items-center gap-1">
          <input type="checkbox" checked={showAll} onChange={(e) => setShowAll(e.target.checked)} />
          show resolved
        </label>
      </div>

      {list.isLoading && <div className="text-slate-600 text-sm">Loading…</div>}
      {list.error && <div className="text-rose-700 text-sm">{(list.error as Error).message}</div>}

      {list.data && list.data.length === 0 && (
        <div className="rounded border border-slate-200 bg-white p-6 text-sm text-slate-600">
          {showAll ? 'No concerns yet.' : 'Inbox is empty — no open concerns.'}
        </div>
      )}

      <div className="space-y-3">
        {(list.data ?? []).map((c) => (
          <ConcernCard key={c.id} c={c} onChanged={refresh} />
        ))}
      </div>
    </div>
  );
}

function ConcernCard({ c, onChanged }: { c: ConcernRow; onChanged: () => void }) {
  const [mode, setMode] = useState<'idle' | 'dismiss' | 'edit' | 'archive'>('idle');
  const [note, setNote] = useState('');
  const [payloadText, setPayloadText] = useState(() => JSON.stringify(c.payload ?? {}, null, 2));
  const [parseError, setParseError] = useState<string | null>(null);

  // Other concerns on the same unit — shown so admins see the full stack of complaints
  // before they decide. Skipped when this concern is already resolved.
  const stacked = useQuery({
    queryKey: ['unit-concerns', c.unitId],
    queryFn: () => api.unitConcerns(c.unitId),
    enabled: c.status === 'OPEN',
  });

  const dismiss = useMutation({
    mutationFn: () => api.dismissConcern(c.id, note || undefined),
    onSuccess: () => { setMode('idle'); setNote(''); onChanged(); },
  });
  const edit = useMutation({
    mutationFn: () => {
      let parsed: unknown;
      try { parsed = JSON.parse(payloadText); }
      catch (e) { setParseError((e as Error).message); throw e; }
      setParseError(null);
      return api.editUnitPayload(c.unitId, parsed, note || undefined);
    },
    onSuccess: () => { setMode('idle'); setNote(''); onChanged(); },
  });
  const archive = useMutation({
    mutationFn: () => api.archiveUnit(c.unitId, note || undefined),
    onSuccess: () => { setMode('idle'); setNote(''); onChanged(); },
  });

  const tone =
    c.status === 'RESOLVED'
      ? 'border-slate-200 bg-slate-50 text-slate-700'
      : 'border-rose-200 bg-white';

  return (
    <article className={`rounded-lg border ${tone} p-4`}>
      <header className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
        <div className="flex items-baseline gap-2">
          <span className="text-xs uppercase tracking-wide text-slate-500">
            {c.status === 'OPEN' ? 'OPEN' : `RESOLVED · ${c.resolution}`}
          </span>
          <span className="text-slate-900 font-medium">unit #{c.unitId}</span>
          {c.jobId && (
            <Link to={`/jobs/${c.jobId}`} className="text-blue-700 hover:underline text-xs">
              job #{c.jobId}
            </Link>
          )}
          {c.unitStatus === 'ARCHIVED' && (
            <span className="rounded bg-slate-200 px-1.5 py-0.5 text-[10px] text-slate-700">archived</span>
          )}
        </div>
        <div className="text-xs text-slate-500">
          raised by <span className="font-mono">{c.raisedByUsername ?? `#${c.raisedByUserId}`}</span>
          {' · '}
          {new Date(c.raisedAt).toLocaleString()}
        </div>
      </header>

      <p className="mt-2 whitespace-pre-wrap text-sm text-slate-900">{c.body}</p>

      {c.status === 'RESOLVED' && c.resolutionNote && (
        <p className="mt-2 text-xs text-slate-600">
          <span className="font-medium">Resolver note:</span> {c.resolutionNote}
        </p>
      )}

      {c.payload !== undefined && (
        <details className="mt-2 text-xs">
          <summary className="cursor-pointer text-slate-600 hover:text-slate-900">unit payload</summary>
          <pre className="mt-1 overflow-auto rounded bg-slate-100 p-2">
            {JSON.stringify(c.payload, null, 2)}
          </pre>
        </details>
      )}

      {c.status === 'OPEN' && stacked.data && stacked.data.length > 1 && (
        <details className="mt-2 text-xs">
          <summary className="cursor-pointer text-slate-600 hover:text-slate-900">
            {stacked.data.length - 1} other concern{stacked.data.length - 1 === 1 ? '' : 's'} on this unit
          </summary>
          <ul className="mt-1 space-y-1">
            {stacked.data.filter((s) => s.id !== c.id).map((s) => (
              <li key={s.id} className="rounded border border-slate-100 bg-white p-2">
                <div className="text-[10px] text-slate-500">
                  {s.raisedByUsername ?? `#${s.raisedByUserId}`} · {new Date(s.raisedAt).toLocaleString()}
                  {s.status === 'RESOLVED' && <> · resolved {s.resolution}</>}
                </div>
                <div className="text-slate-800">{s.body}</div>
              </li>
            ))}
          </ul>
        </details>
      )}

      {c.status === 'OPEN' && (
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          {mode === 'idle' && (
            <>
              <button
                onClick={() => setMode('dismiss')}
                className="rounded border border-slate-300 bg-white px-3 py-1 hover:bg-slate-50"
                title="Worker concern was unfounded; unit stays as-is"
              >
                Dismiss
              </button>
              <button
                onClick={() => setMode('edit')}
                className="rounded border border-amber-400 bg-amber-50 px-3 py-1 text-amber-900 hover:bg-amber-100"
                title="Fix the data — prior judgments on this unit will be discarded"
              >
                Edit payload
              </button>
              <button
                onClick={() => setMode('archive')}
                className="rounded border border-rose-400 bg-rose-50 px-3 py-1 text-rose-900 hover:bg-rose-100"
                title="Soft-delete the unit; existing judgments are kept for audit"
              >
                Archive unit
              </button>
            </>
          )}

          {mode === 'dismiss' && (
            <ResolveForm
              label="Dismiss this concern"
              onCancel={() => setMode('idle')}
              note={note}
              setNote={setNote}
              error={(dismiss.error as Error | null)?.message}
              busy={dismiss.isPending}
              onSubmit={() => dismiss.mutate()}
              cta="Dismiss"
            />
          )}

          {mode === 'edit' && (
            <div className="w-full space-y-2">
              <div className="text-[11px] text-slate-600">
                Edit the unit's payload JSON. <span className="text-rose-700 font-medium">
                  All {c.judgmentCount} existing judgment{c.judgmentCount === 1 ? '' : 's'} on this
                  unit will be deleted</span> — workers were given the old data, so their answers
                no longer apply.
              </div>
              <textarea
                value={payloadText}
                onChange={(e) => setPayloadText(e.target.value)}
                className="w-full h-48 rounded border border-slate-300 p-2 font-mono text-xs"
                spellCheck={false}
              />
              {parseError && <div className="text-xs text-rose-700">JSON parse: {parseError}</div>}
              <ResolveForm
                label=""
                onCancel={() => setMode('idle')}
                note={note}
                setNote={setNote}
                error={(edit.error as Error | null)?.message}
                busy={edit.isPending}
                onSubmit={() => edit.mutate()}
                cta="Save & discard prior judgments"
              />
            </div>
          )}

          {mode === 'archive' && (
            <ResolveForm
              label={`Archive unit (${c.judgmentCount} prior judgments will be kept for audit)`}
              onCancel={() => setMode('idle')}
              note={note}
              setNote={setNote}
              error={(archive.error as Error | null)?.message}
              busy={archive.isPending}
              onSubmit={() => archive.mutate()}
              cta="Archive unit"
            />
          )}
        </div>
      )}
    </article>
  );
}

function ResolveForm({
  label, onCancel, note, setNote, error, busy, onSubmit, cta,
}: {
  label: string;
  onCancel: () => void;
  note: string;
  setNote: (v: string) => void;
  error?: string;
  busy: boolean;
  onSubmit: () => void;
  cta: string;
}) {
  return (
    <div className="w-full space-y-2">
      {label && <div className="text-[11px] text-slate-700">{label}</div>}
      <input
        autoFocus
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Optional note (visible only to admins)"
        className="w-full rounded border border-slate-300 px-2 py-1 text-sm"
      />
      {error && <div className="text-xs text-rose-700">{error}</div>}
      <div className="flex gap-2 justify-end">
        <button onClick={onCancel} className="rounded border border-slate-300 bg-white px-3 py-1 text-xs hover:bg-slate-50">
          Cancel
        </button>
        <button
          onClick={onSubmit}
          disabled={busy}
          className="rounded bg-slate-900 text-white px-3 py-1 text-xs hover:bg-slate-800 disabled:bg-slate-400"
        >
          {busy ? 'Saving…' : cta}
        </button>
      </div>
    </div>
  );
}
