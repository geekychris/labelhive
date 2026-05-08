import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Job, VariantField } from '../../api/types';
import { VariantConfigForm } from '../../components/VariantConfigForm';
import { DisplayTemplateEditor } from '../../components/DisplayTemplateEditor';
import { DatasetFormatHelper } from '../../components/DatasetFormatHelper';
import { renderers } from '../../sdk/renderers';

interface StagedUnit {
  payload: any;
  goldAnswer?: any;
  goldExplanation?: string;
  externalId?: string;
}

const STEPS = [
  '1. Basics',
  '2. Variant config',
  '3. Display template',
  '4. Quality settings',
  '5. Dataset',
  '6. Gold labeling',
  '7. Review',
] as const;

export function JobBuilder() {
  const nav = useNavigate();
  const [step, setStep] = useState(0);

  // Step 1
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [templateType, setTemplateType] = useState('MULTIPLE_CHOICE');

  // Step 2 — config seeded from variant schema defaults when type changes
  const [templateConfig, setTemplateConfig] = useState<any>({});

  // Step 3
  const [displayTemplate, setDisplayTemplate] = useState('');

  // Step 4
  const [goldCadence, setGoldCadence] = useState(3);
  const [tasksPerPage, setTasksPerPage] = useState(1);
  const [endOfWorkMessage, setEndOfWorkMessage] = useState<'COME_BACK_LATER' | 'COMPLETE'>('COME_BACK_LATER');
  const [minJudgments, setMinJudgments] = useState(3);
  const [maxJudgments, setMaxJudgments] = useState(3);
  const [trustFloor, setTrustFloor] = useState('0.70');
  const [autoPromoteToGold, setAutoPromoteToGold] = useState(false);
  const [promoteAgreementMin, setPromoteAgreementMin] = useState('0.90');

  // Step 5
  const [units, setUnits] = useState<StagedUnit[]>([]);
  const [datasetText, setDatasetText] = useState('[]');
  const [datasetError, setDatasetError] = useState<string>();

  // Step 6
  const [labelingIdx, setLabelingIdx] = useState<number | null>(null);

  // Async: catalog of registered types
  const types = useQuery({ queryKey: ['templates'], queryFn: api.listTemplates });
  const variant = useQuery({
    queryKey: ['template', templateType],
    queryFn: () => api.getTemplate(templateType),
  });

  // Seed defaults whenever the variant schema arrives.
  useEffect(() => {
    if (!variant.data) return;
    const next: any = {};
    for (const f of variant.data.variantSchema.fields) {
      if (f.default !== undefined && next[f.name] === undefined) next[f.name] = f.default;
    }
    setTemplateConfig((prev: any) => ({ ...next, ...prev }));
  }, [variant.data?.type]); // re-seed only on type change

  const fields: VariantField[] = variant.data?.variantSchema.fields ?? [];
  const samplePayload = units[0]?.payload ?? null;
  const RendererComp = renderers[templateType];

  const goldCount = useMemo(() => units.filter(u => u.goldAnswer !== undefined).length, [units]);

  const tryParseDataset = () => {
    try {
      const parsed = JSON.parse(datasetText);
      const arr = Array.isArray(parsed) ? parsed : (parsed.units ?? []);
      const staged: StagedUnit[] = arr.map((it: any) => ({
        payload: it.payload ?? it,
        goldAnswer: it.goldAnswer,
        goldExplanation: it.goldExplanation,
        externalId: it.externalId,
      }));
      setUnits(staged); setDatasetError(undefined);
    } catch (e: any) { setDatasetError(e.message); }
  };

  const create = async () => {
    const job = await api.createJob({
      name, description, templateType,
      templateConfig, displayTemplate: displayTemplate || undefined,
      goldCadence, tasksPerPage, endOfWorkMessage,
      minJudgments, maxJudgments,
      trustFloor, autoPromoteToGold, promoteAgreementMin,
    } as Partial<Job>);
    if (units.length > 0) {
      await api.uploadUnits(job.id, units);
    }
    nav(`/jobs/${job.id}`);
  };

  return (
    <div className="space-y-5 max-w-4xl">
      <h1 className="text-xl font-semibold">Build a job</h1>

      {/* Stepper */}
      <ol className="flex flex-wrap gap-2 text-xs">
        {STEPS.map((s, i) => (
          <li key={s}>
            <button onClick={() => setStep(i)}
                    className={`px-2 py-1 rounded border ${i === step ? 'bg-blue-600 text-white border-blue-700' : 'bg-white text-slate-600'}`}>
              {s}
            </button>
          </li>
        ))}
      </ol>

      {step === 0 && (
        <div className="space-y-3">
          <input className="w-full rounded border p-2" placeholder="Job name" value={name} onChange={e => setName(e.target.value)} />
          <textarea className="w-full rounded border p-2" placeholder="Description" value={description} onChange={e => setDescription(e.target.value)} />
          <label className="text-sm">Template type
            <select className="w-full rounded border p-2" value={templateType} onChange={e => setTemplateType(e.target.value)}>
              {types.data?.map(t => <option key={t.type} value={t.type}>{t.type}</option>)}
            </select>
          </label>
          {!RendererComp && (
            <div className="text-xs text-amber-700 bg-amber-50 rounded p-2">
              No renderer registered for <code>{templateType}</code>. Workers won't be able
              to label this until you ship one to <code>frontend/src/sdk/renderers/</code>.
            </div>
          )}
        </div>
      )}

      {step === 1 && variant.data && (
        <div className="space-y-3">
          <p className="text-xs text-slate-500">Customize this variant of <b>{variant.data.type}</b>. Each task type exposes its own knobs (no Java needed).</p>
          <VariantConfigForm fields={fields} value={templateConfig} onChange={setTemplateConfig} />
        </div>
      )}

      {step === 2 && (
        <DisplayTemplateEditor value={displayTemplate} onChange={setDisplayTemplate}
                               samplePayload={samplePayload ?? { example: 'upload data first to preview' }} />
      )}

      {step === 3 && (
        <div className="grid grid-cols-2 gap-4">
          <label className="text-sm">Gold cadence
            <input type="number" min={1} className="w-full rounded border-slate-300 border p-2" value={goldCadence}
                   onChange={e => setGoldCadence(+e.target.value)} />
            <p className="text-xs text-slate-500">
              1 gold mixed in per N non-gold tasks. <b>3</b> ≈ 25% gold;
              <b> 4</b> ≈ 20% gold. Worker can't tell which is which.
            </p>
          </label>
          <label className="text-sm">Tasks per page
            <input type="number" min={1} className="w-full rounded border-slate-300 border p-2" value={tasksPerPage}
                   onChange={e => setTasksPerPage(+e.target.value)} />
            <p className="text-xs text-slate-500">
              How many tasks the worker sees at once. Useful for related items
              (e.g. <b>4</b> per page for search-relevance grading).
            </p>
          </label>
          <label className="text-sm">Min judgments per unit
            <input type="number" min={1} className="w-full rounded border-slate-300 border p-2" value={minJudgments}
                   onChange={e => { const v = +e.target.value; setMinJudgments(v); if (maxJudgments < v) setMaxJudgments(v); }} />
            <p className="text-xs text-slate-500">Required minimum. Unit completes once it has this many judgments and they all agree.</p>
          </label>
          <label className="text-sm">Max judgments per unit
            <input type="number" min={minJudgments} className="w-full rounded border-slate-300 border p-2" value={maxJudgments}
                   onChange={e => setMaxJudgments(+e.target.value)} />
            <p className="text-xs text-slate-500">
              Hard cap. Set <b>&gt; min</b> to escalate to additional reviewers when workers disagree.
              Set <b>= min</b> for fixed-N consensus.
            </p>
          </label>
          <label className="text-sm">Trust floor (0–1)
            <input className="w-full rounded border-slate-300 border p-2" value={trustFloor}
                   onChange={e => setTrustFloor(e.target.value)} />
            <p className="text-xs text-slate-500">Workers below this gold accuracy get quarantined.</p>
          </label>
          <label className="text-sm">Promote agreement min
            <input className="w-full rounded border-slate-300 border p-2" value={promoteAgreementMin}
                   onChange={e => setPromoteAgreementMin(e.target.value)} />
          </label>
          <label className="text-sm">End-of-work message
            <select className="w-full rounded border-slate-300 border p-2" value={endOfWorkMessage}
                    onChange={e => setEndOfWorkMessage(e.target.value as 'COME_BACK_LATER' | 'COMPLETE')}>
              <option value="COME_BACK_LATER">Come back later (more work may show up)</option>
              <option value="COMPLETE">Thanks — labeling complete</option>
            </select>
          </label>
          <label className="flex items-center gap-2 text-sm col-span-2">
            <input type="checkbox" checked={autoPromoteToGold} onChange={e => setAutoPromoteToGold(e.target.checked)} />
            Auto-promote high-agreement consensus into gold
          </label>
        </div>
      )}

      {step === 4 && (
        <div className="space-y-3">
          <DatasetFormatHelper
            templateType={templateType}
            templateConfig={templateConfig}
            payloadSchema={variant.data?.payloadSchema}
            datasetText={datasetText}
          />
          <textarea rows={8} className="w-full rounded border p-2 font-mono text-xs"
                    value={datasetText} onChange={e => setDatasetText(e.target.value)} />
          {datasetError && <div className="text-red-600 text-sm">{datasetError}</div>}
          <button onClick={tryParseDataset} className="px-3 py-1.5 rounded bg-blue-600 text-white text-sm">
            Stage {units.length > 0 ? '(replace)' : ''} units
          </button>
          {units.length > 0 && (
            <div className="text-sm text-slate-600">
              Staged {units.length} units ({goldCount} pre-labeled gold).
            </div>
          )}
        </div>
      )}

      {step === 5 && (
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            Pick which staged units should be gold. Click any row to label it inline using the same renderer workers will use.
            Gold rows are highlighted; unlabeled units stay as worker tasks.
          </p>
          <div className="text-sm">{goldCount} of {units.length} staged units have gold answers.</div>
          <div className="space-y-1 max-h-96 overflow-auto border rounded">
            {units.map((u, i) => (
              <div key={i}
                   className={`flex items-center justify-between p-2 cursor-pointer ${u.goldAnswer !== undefined ? 'bg-amber-50' : 'hover:bg-slate-50'}`}
                   onClick={() => setLabelingIdx(i)}>
                <div className="flex-1 min-w-0">
                  <div className="truncate text-sm">{JSON.stringify(u.payload).slice(0, 100)}</div>
                  {u.goldAnswer !== undefined && (
                    <div className="text-xs text-amber-700">gold: {JSON.stringify(u.goldAnswer)}</div>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {u.goldAnswer !== undefined && (
                    <button onClick={(e) => {
                      e.stopPropagation();
                      setUnits(units.map((x, idx) => idx === i ? { ...x, goldAnswer: undefined, goldExplanation: undefined } : x));
                    }} className="text-xs text-red-700">unmark</button>
                  )}
                  <span className="text-xs text-slate-400">label →</span>
                </div>
              </div>
            ))}
            {units.length === 0 && <div className="p-3 text-slate-500 text-sm">No staged units. Go back to the Dataset step.</div>}
          </div>
        </div>
      )}

      {step === 6 && (
        <div className="space-y-3 text-sm">
          <h2 className="font-semibold">Review</h2>
          <Field label="Name" value={name} />
          <Field label="Type" value={templateType} />
          <Field label="Variant config" value={<pre className="text-xs bg-slate-50 p-2 rounded">{JSON.stringify(templateConfig, null, 2)}</pre>} />
          <Field label="Display template" value={displayTemplate || <span className="text-slate-400 italic">(default)</span>} />
          <Field label="Gold ratio / judgments per unit / trust floor / auto-promote"
                 value={
                   `gold every ${goldCadence + 1}th task (cadence ${goldCadence}) · ` +
                   `${tasksPerPage}/page · ` +
                   (minJudgments === maxJudgments
                     ? `${minJudgments} judgments/unit (fixed) · `
                     : `${minJudgments}–${maxJudgments} judgments/unit (escalates) · `) +
                   `trust ≥ ${trustFloor} · auto-promote ${autoPromoteToGold ? 'on' : 'off'} · ` +
                   `end: ${endOfWorkMessage.toLowerCase()}`
                 } />
          <Field label="Dataset" value={`${units.length} units (${goldCount} pre-labeled gold)`} />
          <button onClick={create}
                  disabled={!name || !templateType}
                  className="px-4 py-2 rounded bg-emerald-600 text-white">
            Create job
          </button>
        </div>
      )}

      {/* Footer nav */}
      <div className="flex items-center justify-between pt-3 border-t mt-4">
        <button onClick={() => setStep(s => Math.max(0, s - 1))} disabled={step === 0}
                className="px-3 py-1.5 rounded border text-sm disabled:opacity-40">← Back</button>
        <button onClick={() => setStep(s => Math.min(STEPS.length - 1, s + 1))} disabled={step === STEPS.length - 1}
                className="px-3 py-1.5 rounded border text-sm disabled:opacity-40">Next →</button>
      </div>

      {/* Inline gold labeling modal */}
      {labelingIdx !== null && RendererComp && units[labelingIdx] && (
        <GoldLabelOverlay
          unit={units[labelingIdx]}
          config={templateConfig}
          renderer={RendererComp}
          onSave={(answer, explanation) => {
            setUnits(units.map((u, idx) => idx === labelingIdx
              ? { ...u, goldAnswer: answer, goldExplanation: explanation }
              : u));
            setLabelingIdx(null);
          }}
          onCancel={() => setLabelingIdx(null)}
        />
      )}
    </div>
  );
}

function Field({ label, value }: { label: string; value: any }) {
  return (
    <div>
      <div className="text-xs uppercase text-slate-500">{label}</div>
      <div>{value}</div>
    </div>
  );
}

function GoldLabelOverlay({
  unit, config, renderer: Renderer, onSave, onCancel,
}: {
  unit: StagedUnit;
  config: any;
  renderer: React.ComponentType<any>;
  onSave: (answer: any, explanation?: string) => void;
  onCancel: () => void;
}) {
  const [answer, setAnswer] = useState<any>(unit.goldAnswer);
  const [explanation, setExplanation] = useState<string>(unit.goldExplanation ?? '');

  return (
    <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded shadow-lg max-w-2xl w-full p-5 space-y-4">
        <h3 className="font-semibold">Label as gold</h3>
        <Renderer config={config} payload={unit.payload} value={answer} onChange={setAnswer} />
        <label className="text-sm block">Optional explanation
          <textarea className="w-full rounded border p-2 text-sm" rows={2}
                    placeholder="Why is this the right answer? (shown to workers who got it wrong)"
                    value={explanation} onChange={e => setExplanation(e.target.value)} />
        </label>
        <div className="flex justify-end gap-2">
          <button onClick={onCancel} className="px-3 py-1.5 rounded border text-sm">Cancel</button>
          <button onClick={() => onSave(answer, explanation || undefined)}
                  disabled={answer === undefined}
                  className="px-3 py-1.5 rounded bg-blue-600 text-white text-sm disabled:bg-slate-300">
            Save gold answer
          </button>
        </div>
      </div>
    </div>
  );
}
