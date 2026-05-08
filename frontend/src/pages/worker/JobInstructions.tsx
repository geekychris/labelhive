import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { renderers } from '../../sdk/renderers';

/**
 * Worker-facing instructions page. Pulls the job description plus the curated set of
 * exemplar units (admin-flagged), and renders each one with its template's renderer
 * in read-only mode so labelers can see "what good looks like" before they start.
 */
export function JobInstructions() {
  const { id } = useParams();
  const jobId = Number(id);
  const q = useQuery({
    queryKey: ['job-instructions', jobId],
    queryFn: () => api.jobInstructions(jobId),
  });

  if (q.isLoading) return <p className="text-sm text-gray-500">Loading…</p>;
  if (q.error) return <p className="text-sm text-red-600">{(q.error as Error).message}</p>;
  if (!q.data) return null;

  const { name, description, templateType, templateConfig, exemplars } = q.data;
  const Renderer = renderers[templateType];

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">{name}</h1>
          <p className="text-xs text-gray-500">Task instructions</p>
        </div>
        <Link
          to={`/work/${jobId}`}
          className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700"
        >
          Start labeling →
        </Link>
      </header>

      {description ? (
        <section className="rounded border border-gray-200 bg-white p-4">
          <h2 className="mb-2 text-sm font-medium text-gray-900">Description</h2>
          <p className="whitespace-pre-wrap text-sm text-gray-800">{description}</p>
        </section>
      ) : (
        <p className="text-sm text-gray-500">(No description provided.)</p>
      )}

      <section>
        <h2 className="mb-2 text-sm font-medium text-gray-900">
          Examples ({exemplars.length})
        </h2>
        {exemplars.length === 0 && (
          <p className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-500">
            The admin hasn't flagged any examples for this job yet. Once they do, you'll see
            sample tasks here with the correct answer pre-filled.
          </p>
        )}
        <div className="space-y-4">
          {exemplars.map((ex) => (
            <article key={ex.unitId} className="rounded border border-gray-200 bg-white p-4">
              <div className="mb-3 flex items-center gap-2 text-xs">
                <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-emerald-800">
                  example
                </span>
                <span className="text-gray-500">unit #{ex.unitId}</span>
              </div>

              {Renderer ? (
                // Read-only mode: renderer ignores onChange and disables interactive controls.
                <Renderer
                  config={templateConfig}
                  payload={ex.payload}
                  value={ex.goldAnswer}
                  onChange={() => {}}
                  readOnly
                />
              ) : (
                <pre className="overflow-auto rounded bg-slate-100 p-3 text-xs">
                  {JSON.stringify({ payload: ex.payload, goldAnswer: ex.goldAnswer }, null, 2)}
                </pre>
              )}

              {ex.goldExplanation && (
                <div className="mt-3 rounded border border-amber-200 bg-amber-50 p-3 text-sm">
                  <span className="font-medium text-amber-900">Why this answer:</span>{' '}
                  <span className="text-amber-900">{ex.goldExplanation}</span>
                </div>
              )}
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
