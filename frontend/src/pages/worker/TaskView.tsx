import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { TaskPage, WorkerUnit } from '../../api/types';
import { renderers } from '../../sdk/renderers';
import { useShortcuts } from '../../sdk/renderers/useShortcuts';
import { renderTemplate } from '../../components/DisplayTemplateEditor';
import { useAuth } from '../../auth/AuthContext';

/**
 * Worker page view. Fetches a page of tasks (size set by the job's
 * tasksPerPage) and submits all answers in one batch.
 *
 * Keyboard:
 * - Number keys interact with the focused task (renderer-specific).
 * - <kbd>Tab</kbd> / <kbd>Shift+Tab</kbd> move focus between tasks.
 * - <kbd>Enter</kbd> submits the page when every task is answered.
 *
 * Magic-link support: when the URL has <code>?as=&lt;username&gt;</code> and we're
 * in debug mode, switch identity to that user and strip the param so a
 * subsequent refresh doesn't override a manual switcher action.
 */
export function TaskView() {
  const { id } = useParams();
  const jobId = Number(id);
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const { me, switchDebugUser } = useAuth();

  const asParam = params.get('as');
  useEffect(() => {
    if (asParam && me?.debug && me.username !== asParam) {
      switchDebugUser(asParam);
      params.delete('as');
      setParams(params, { replace: true });
    } else if (asParam && me?.username === asParam) {
      params.delete('as');
      setParams(params, { replace: true });
    }
  }, [asParam, me?.debug, me?.username, switchDebugUser, setParams, params]);

  const page = useQuery<TaskPage>({
    queryKey: ['next-page', jobId, me?.id],
    queryFn: () => api.nextPage(jobId),
    refetchOnWindowFocus: false,
    enabled: !asParam,
  });

  const [answers, setAnswers] = useState<Record<number, any>>({});
  const [flagged, setFlagged] = useState<Record<number, true>>({});
  const [startedAt, setStartedAt] = useState<number>(Date.now());
  const [focusedIndex, setFocusedIndex] = useState(0);

  useEffect(() => {
    setAnswers({});
    setFlagged({});
    setStartedAt(Date.now());
    setFocusedIndex(0);
  }, [page.data?.tasks?.map(t => t.id).join(',')]);

  const submit = useMutation({
    mutationFn: async () => {
      if (!page.data) return;
      const ms = Date.now() - startedAt;
      await Promise.all(page.data.tasks.map(t =>
        answers[t.id] !== undefined
          ? api.submit(jobId, t.id, answers[t.id], ms)
          : Promise.resolve()));
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['next-page', jobId] });
    },
  });

  const tasks = page.data?.tasks ?? [];
  // Flagged tasks count as resolved for the page's "everything done?" check — we won't submit
  // a judgment for them, but the worker shouldn't have to label data they just flagged.
  const allAnswered = tasks.length > 0 && tasks.every(t => answers[t.id] !== undefined || flagged[t.id]);

  // Page-level keys: Tab/Shift+Tab to navigate, Enter to submit.
  useShortcuts({
    Tab: () => setFocusedIndex(i => Math.min(tasks.length - 1, i + 1)),
    Enter: () => { if (allAnswered && !submit.isPending) submit.mutate(); },
  }, tasks.length > 0);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Tab' && e.shiftKey) {
        const t = e.target as HTMLElement | null;
        if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || (t as HTMLElement).isContentEditable)) return;
        e.preventDefault();
        setFocusedIndex(i => Math.max(0, i - 1));
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  if (page.isLoading) return <div className="text-slate-600">Loading…</div>;
  if (!page.data) return <div className="text-slate-600">No data.</div>;

  if (tasks.length === 0) {
    return (
      <div className="max-w-2xl bg-white border border-slate-200 rounded-lg p-8 text-center shadow-sm">
        <img src="/label_hive.png" alt="" className="h-16 w-16 mx-auto mb-3 opacity-90" />
        <h2 className="text-xl font-semibold text-slate-800 mb-2">
          {page.data.code === 'COMPLETE' ? 'All done' : "You're caught up"}
        </h2>
        <p className="text-slate-600">{page.data.message}</p>
        {page.data.code === 'COME_BACK_LATER' && (
          <button onClick={() => page.refetch()} className="mt-4 px-3 py-1.5 rounded border border-slate-300 text-sm text-slate-700 hover:bg-slate-50">
            Check again
          </button>
        )}
      </div>
    );
  }

  const advanceFocus = () => {
    // Move to the next task that doesn't yet have an answer; otherwise just step forward.
    const start = focusedIndex + 1;
    for (let off = 0; off < tasks.length; off++) {
      const idx = (start + off) % tasks.length;
      if (answers[tasks[idx].id] === undefined) { setFocusedIndex(idx); return; }
    }
    setFocusedIndex(Math.min(tasks.length - 1, focusedIndex + 1));
  };

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-3 text-sm text-slate-600">
          <span>
            {tasks.length} task{tasks.length === 1 ? '' : 's'} on this page.
          </span>
          <a
            href={`/work/${jobId}/instructions`}
            className="text-blue-700 hover:underline text-xs"
            title="View task description and example answers"
          >
            View instructions →
          </a>
        </div>
        {tasks.length > 1 && (
          <div className="text-[11px] text-slate-500">
            <kbd className="kbd">Tab</kbd> / <kbd className="kbd">Shift+Tab</kbd> to switch tasks ·
            <kbd className="kbd ml-1">Enter</kbd> to submit
          </div>
        )}
      </div>

      <div className="space-y-4">
        {tasks.map((task, i) => (
          <TaskCard
            key={task.id}
            index={i + 1}
            jobId={jobId}
            task={task}
            value={answers[task.id]}
            focused={i === focusedIndex}
            flagged={!!flagged[task.id]}
            onFocus={() => setFocusedIndex(i)}
            onChange={v => setAnswers(a => ({ ...a, [task.id]: v }))}
            onAnswered={advanceFocus}
            onFlagged={() => {
              setFlagged(f => ({ ...f, [task.id]: true }));
              // Drop any in-progress answer so we don't accidentally submit it.
              setAnswers(a => { const { [task.id]: _, ...rest } = a; return rest; });
              advanceFocus();
            }}
          />
        ))}
      </div>

      <div className="sticky bottom-0 bg-stone-50 -mx-6 px-6 py-3 border-t border-slate-200 flex items-center justify-between">
        <div className="text-sm text-slate-600">
          {Object.keys(answers).length} / {tasks.length} answered
          {allAnswered && <span className="ml-2 text-emerald-700">— press <kbd className="kbd">Enter</kbd> to submit</span>}
        </div>
        <div className="flex gap-2">
          <button onClick={() => page.refetch()}
                  className="px-3 py-1.5 rounded border border-slate-300 text-sm text-slate-700 hover:bg-slate-50">
            Skip page
          </button>
          <button onClick={() => submit.mutate()}
                  disabled={submit.isPending || !allAnswered}
                  className="px-4 py-1.5 rounded bg-amber-500 text-slate-900 font-medium text-sm hover:bg-amber-400 disabled:bg-slate-200 disabled:text-slate-500">
            {submit.isPending ? 'Submitting…' : 'Submit & next page'}
          </button>
        </div>
      </div>
      {submit.isError && <div className="text-red-700 text-sm">{(submit.error as Error).message}</div>}
    </div>
  );
}

function TaskCard({
  index, jobId, task, value, focused, flagged, onFocus, onChange, onAnswered, onFlagged,
}: {
  index: number; jobId: number; task: WorkerUnit; value: any;
  focused: boolean; flagged: boolean; onFocus: () => void;
  onChange: (v: any) => void; onAnswered: () => void; onFlagged: () => void;
}) {
  const Renderer = renderers[task.templateType];
  const [showFlag, setShowFlag] = useState(false);
  const [flagBody, setFlagBody] = useState('');
  const raise = useMutation({
    mutationFn: () => api.raiseConcern(jobId, task.id, flagBody.trim()),
    onSuccess: () => { setShowFlag(false); setFlagBody(''); onFlagged(); },
  });

  return (
    <div onClick={onFocus}
         className={
           'bg-white border rounded-lg p-4 shadow-sm transition ' +
           (flagged ? 'border-rose-300 ring-1 ring-rose-200 opacity-80' :
            focused ? 'border-amber-400 ring-2 ring-amber-200' : 'border-slate-200 hover:border-slate-300')
         }>
      <div className="text-[10px] uppercase tracking-wider text-slate-500 mb-2 flex items-center justify-between gap-2">
        <span>Task {index} · {task.templateType}</span>
        <div className="flex items-center gap-3 normal-case">
          {focused && !flagged && <span className="text-amber-700">⌨ shortcuts active</span>}
          {!flagged && (
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); setShowFlag(true); }}
              className="text-[11px] text-rose-700 hover:underline"
              title="Tell admins this data looks wrong or is unlabelable"
            >
              ⚑ Flag this task
            </button>
          )}
        </div>
      </div>

      {flagged ? (
        <div className="rounded border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900">
          <div className="font-medium mb-1">Flagged for admin review.</div>
          <div className="text-xs text-rose-800">
            Submit the page to skip this task — it won't count as a judgment.
          </div>
        </div>
      ) : (
        <>
          {task.displayTemplate && (
            <div className="rounded border border-slate-100 bg-stone-50 p-3 mb-3"
                 dangerouslySetInnerHTML={{ __html: renderTemplate(task.displayTemplate, task.payload) }} />
          )}
          {Renderer
            ? <Renderer config={task.templateConfig} payload={task.payload}
                        value={value} onChange={onChange}
                        focused={focused} onAnswered={onAnswered} />
            : <div className="text-sm text-amber-700 bg-amber-50 rounded p-3">
                No renderer registered for <code>{task.templateType}</code>.
              </div>}
        </>
      )}

      {showFlag && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40"
             onClick={(e) => { e.stopPropagation(); setShowFlag(false); }}>
          <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl"
               onClick={(e) => e.stopPropagation()}>
            <h3 className="text-base font-semibold text-slate-900">Flag this task</h3>
            <p className="mt-1 text-xs text-slate-600">
              Tell the admin what's wrong with this data. They'll review, then either fix the
              data, archive it, or dismiss your concern. Your note isn't visible to other workers.
            </p>
            <textarea
              autoFocus
              value={flagBody}
              onChange={(e) => setFlagBody(e.target.value)}
              placeholder="What looks wrong here?"
              className="mt-3 w-full h-32 rounded border border-slate-300 p-2 text-sm"
            />
            {raise.error && (
              <div className="mt-2 text-xs text-rose-700">{(raise.error as Error).message}</div>
            )}
            <div className="mt-3 flex justify-end gap-2">
              <button onClick={() => setShowFlag(false)} className="px-3 py-1.5 rounded border border-slate-300 text-sm hover:bg-slate-50">
                Cancel
              </button>
              <button
                onClick={() => raise.mutate()}
                disabled={!flagBody.trim() || raise.isPending}
                className="px-3 py-1.5 rounded bg-rose-600 text-white text-sm hover:bg-rose-500 disabled:bg-rose-300"
              >
                {raise.isPending ? 'Sending…' : 'Submit concern'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
