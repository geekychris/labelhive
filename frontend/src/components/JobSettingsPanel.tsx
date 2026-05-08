import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { EndOfWorkMessageCode, Job, VariantField } from '../api/types';
import { VariantConfigForm } from './VariantConfigForm';
import { DisplayTemplateEditor } from './DisplayTemplateEditor';

/**
 * Inline settings panel for a job. Mirrors the JobBuilder wizard's editable
 * fields so admins can tweak a job after creation — cadence, page size,
 * end-of-work copy, trust thresholds, variant config, display template.
 */
export function JobSettingsPanel({ job, samplePayload }: { job: Job; samplePayload?: any }) {
  const qc = useQueryClient();

  // Local edit buffer so saves are explicit (no jittery saves on every keystroke).
  const [draft, setDraft] = useState<Partial<Job>>({
    description: job.description ?? '',
    displayTemplate: job.displayTemplate ?? '',
    templateConfig: job.templateConfig,
    goldCadence: job.goldCadence,
    tasksPerPage: job.tasksPerPage,
    endOfWorkMessage: job.endOfWorkMessage,
    minJudgments: job.minJudgments,
    maxJudgments: job.maxJudgments,
    trustFloor: job.trustFloor,
    promoteAgreementMin: job.promoteAgreementMin,
    autoPromoteToGold: job.autoPromoteToGold,
  });

  const variant = useQuery({
    queryKey: ['template', job.templateType],
    queryFn: () => api.getTemplate(job.templateType),
  });
  const fields: VariantField[] = variant.data?.variantSchema.fields ?? [];

  const save = useMutation({
    mutationFn: () => api.updateJob(job.id, draft),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['job', job.id] });
      qc.invalidateQueries({ queryKey: ['jobs'] });
    },
  });

  const set = <K extends keyof Job>(k: K, v: Job[K]) => setDraft(d => ({ ...d, [k]: v }));

  const dirty = JSON.stringify(draft) !== JSON.stringify({
    description: job.description ?? '',
    displayTemplate: job.displayTemplate ?? '',
    templateConfig: job.templateConfig,
    goldCadence: job.goldCadence,
    tasksPerPage: job.tasksPerPage,
    endOfWorkMessage: job.endOfWorkMessage,
    minJudgments: job.minJudgments,
    maxJudgments: job.maxJudgments,
    trustFloor: job.trustFloor,
    promoteAgreementMin: job.promoteAgreementMin,
    autoPromoteToGold: job.autoPromoteToGold,
  });

  return (
    <section className="bg-white rounded-lg border border-slate-200 shadow-sm">
      <div className="px-5 py-3 border-b border-slate-200 flex items-center justify-between">
        <h2 className="font-semibold text-slate-900">Settings</h2>
        {save.isError && <span className="text-xs text-red-700">{(save.error as Error).message}</span>}
        {save.isSuccess && !dirty && <span className="text-xs text-emerald-700">saved</span>}
      </div>

      <div className="p-5 space-y-6">
        <Group title="Worker feed">
          <Field label="Gold cadence">
            <input type="number" min={1} className="rounded border border-slate-300 p-2 text-sm w-28"
                   value={draft.goldCadence ?? 3}
                   onChange={e => set('goldCadence', +e.target.value as any)} />
            <p className="text-xs text-slate-500 mt-1">
              1 gold mixed in per N non-gold tasks. <b>3</b> ≈ 25% gold; <b>4</b> ≈ 20% gold.
              The worker can't tell which is which.
            </p>
          </Field>
          <Field label="Tasks per page">
            <input type="number" min={1} className="rounded border border-slate-300 p-2 text-sm w-28"
                   value={draft.tasksPerPage ?? 1}
                   onChange={e => set('tasksPerPage', +e.target.value as any)} />
            <p className="text-xs text-slate-500 mt-1">
              How many tasks the worker sees at once. Use higher values for related-item
              tasks (e.g. 4 search results graded together).
            </p>
          </Field>
          <Field label="End-of-work message">
            <select className="rounded border border-slate-300 p-2 text-sm"
                    value={draft.endOfWorkMessage ?? 'COME_BACK_LATER'}
                    onChange={e => set('endOfWorkMessage', e.target.value as EndOfWorkMessageCode)}>
              <option value="COME_BACK_LATER">Come back later (more work may show up)</option>
              <option value="COMPLETE">Thanks — labeling complete</option>
            </select>
          </Field>
        </Group>

        <Group title="Quality">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Min judgments per unit">
              <input type="number" min={1} className="rounded border border-slate-300 p-2 text-sm w-28"
                     value={draft.minJudgments ?? 3}
                     onChange={e => set('minJudgments', +e.target.value as any)} />
              <p className="text-xs text-slate-500 mt-1">
                Lower bound. Unit is done once it has this many judgments and they all agree.
              </p>
            </Field>
            <Field label="Max judgments per unit">
              <input type="number" min={1} className="rounded border border-slate-300 p-2 text-sm w-28"
                     value={draft.maxJudgments ?? 3}
                     onChange={e => set('maxJudgments', +e.target.value as any)} />
              <p className="text-xs text-slate-500 mt-1">
                Hard cap. When &gt; min, additional reviewers are brought in if workers
                <em> disagree</em>. When equal to min, fixed-N consensus.
              </p>
            </Field>
          </div>
          <Field label="Trust floor (0–1)">
            <input className="rounded border border-slate-300 p-2 text-sm w-28"
                   value={String(draft.trustFloor ?? '0.70')}
                   onChange={e => set('trustFloor', e.target.value as any)} />
            <p className="text-xs text-slate-500 mt-1">Workers below this gold accuracy are quarantined.</p>
          </Field>
          <Field label="Promote agreement min (0–1)">
            <input className="rounded border border-slate-300 p-2 text-sm w-28"
                   value={String(draft.promoteAgreementMin ?? '0.90')}
                   onChange={e => set('promoteAgreementMin', e.target.value as any)} />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={!!draft.autoPromoteToGold}
                   onChange={e => set('autoPromoteToGold', e.target.checked as any)} />
            Auto-promote high-agreement consensus into gold
          </label>
        </Group>

        <Group title="Description">
          <textarea rows={2} className="w-full rounded border border-slate-300 p-2 text-sm"
                    value={draft.description ?? ''}
                    onChange={e => set('description', e.target.value as any)} />
        </Group>

        {fields.length > 0 && (
          <Group title={`Variant config (${variant.data?.type})`}>
            <VariantConfigForm
              fields={fields}
              value={draft.templateConfig}
              onChange={v => set('templateConfig', v)}
            />
          </Group>
        )}

        <Group title="Display template">
          <DisplayTemplateEditor
            value={draft.displayTemplate ?? ''}
            onChange={v => set('displayTemplate', v as any)}
            samplePayload={samplePayload ?? { example: 'upload data first to preview' }}
          />
        </Group>

        <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
          <button onClick={() => save.mutate()} disabled={!dirty || save.isPending}
                  className="px-4 py-2 rounded bg-amber-500 text-slate-900 font-medium text-sm hover:bg-amber-400 disabled:bg-slate-200 disabled:text-slate-500">
            {save.isPending ? 'Saving…' : dirty ? 'Save settings' : 'No changes'}
          </button>
        </div>
      </div>
    </section>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-slate-500 mb-2">{title}</div>
      <div className="space-y-3">{children}</div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block text-sm">
      <div className="text-slate-700 mb-1">{label}</div>
      {children}
    </label>
  );
}
