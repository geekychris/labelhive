import { useState } from 'react';
import type { VariantField } from '../api/types';

interface Props {
  fields: VariantField[];
  value: any;
  onChange: (next: any) => void;
}

/**
 * Renders a per-template-type config form from the variant schema. New field
 * kinds are supported by adding a case below; everything else falls through to
 * a raw JSON textarea so plugin authors aren't blocked.
 */
export function VariantConfigForm({ fields, value, onChange }: Props) {
  const set = (name: string, v: any) => onChange({ ...value, [name]: v });

  return (
    <div className="space-y-4">
      {fields.map(f => (
        <FieldRow key={f.name} field={f} value={value?.[f.name]} onChange={v => set(f.name, v)} />
      ))}
      <details className="text-xs text-slate-500">
        <summary className="cursor-pointer">Raw JSON (advanced)</summary>
        <pre className="bg-slate-100 p-2 rounded overflow-auto mt-1">
{JSON.stringify(value ?? {}, null, 2)}
        </pre>
      </details>
    </div>
  );
}

function FieldRow({ field, value, onChange }: { field: VariantField; value: any; onChange: (v: any) => void }) {
  const labelEl = (
    <div className="text-sm font-medium">
      {field.label}{field.required && <span className="text-red-600 ml-0.5">*</span>}
      {field.help && <div className="text-xs text-slate-500 font-normal">{field.help}</div>}
    </div>
  );
  const v = value ?? field.default;

  switch (field.kind) {
    case 'string':
      return (
        <label className="block">{labelEl}
          <input className="w-full rounded border p-2 text-sm" value={v ?? ''}
                 onChange={e => onChange(e.target.value)} />
        </label>
      );
    case 'number':
      return (
        <label className="block">{labelEl}
          <input type="number" className="w-full rounded border p-2 text-sm" value={v ?? ''}
                 onChange={e => onChange(Number(e.target.value))} />
        </label>
      );
    case 'boolean':
      return (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={!!v} onChange={e => onChange(e.target.checked)} />
          {field.label}
          {field.help && <span className="text-xs text-slate-500">— {field.help}</span>}
        </label>
      );
    case 'string-list':
      return <StringListField label={labelEl} value={Array.isArray(v) ? v : []} onChange={onChange} />;
    case 'scale':
      return <ScaleField label={labelEl} value={Array.isArray(v) ? v : (field.default ?? [])} onChange={onChange} />;
    default:
      return <JsonField label={labelEl} value={v} onChange={onChange} />;
  }
}

function StringListField({ label, value, onChange }: { label: any; value: string[]; onChange: (v: string[]) => void }) {
  const [draft, setDraft] = useState('');
  return (
    <div>{label}
      <div className="flex flex-wrap gap-1 mt-1">
        {value.map((s, i) => (
          <span key={i} className="bg-slate-100 rounded px-2 py-0.5 text-xs flex items-center gap-1">
            {s}
            <button type="button" className="text-slate-400 hover:text-red-600"
                    onClick={() => onChange(value.filter((_, idx) => idx !== i))}>×</button>
          </span>
        ))}
      </div>
      <div className="flex gap-2 mt-2">
        <input className="flex-1 rounded border p-1.5 text-sm" value={draft}
               onChange={e => setDraft(e.target.value)}
               onKeyDown={e => {
                 if (e.key === 'Enter' && draft.trim()) {
                   e.preventDefault();
                   onChange([...value, draft.trim()]);
                   setDraft('');
                 }
               }}
               placeholder="Type and press Enter" />
        <button type="button" className="px-2 rounded border text-sm"
                onClick={() => { if (draft.trim()) { onChange([...value, draft.trim()]); setDraft(''); } }}>
          Add
        </button>
      </div>
    </div>
  );
}

interface ScaleEntry { label: string; grade: number }

function ScaleField({ label, value, onChange }: { label: any; value: ScaleEntry[]; onChange: (v: ScaleEntry[]) => void }) {
  const update = (i: number, patch: Partial<ScaleEntry>) =>
    onChange(value.map((row, idx) => idx === i ? { ...row, ...patch } : row));
  const addRow = () => onChange([...value, { label: 'new', grade: (value[value.length - 1]?.grade ?? 0) + 1 }]);
  const remove = (i: number) => onChange(value.filter((_, idx) => idx !== i));

  return (
    <div>{label}
      <table className="text-xs w-full mt-1">
        <thead><tr className="text-left text-slate-500"><th className="p-1">label</th><th className="p-1">grade</th><th /></tr></thead>
        <tbody>
          {value.map((row, i) => (
            <tr key={i}>
              <td className="p-1"><input className="w-full rounded border p-1" value={row.label}
                                          onChange={e => update(i, { label: e.target.value })} /></td>
              <td className="p-1 w-24"><input type="number" className="w-full rounded border p-1" value={row.grade}
                                          onChange={e => update(i, { grade: Number(e.target.value) })} /></td>
              <td className="p-1"><button type="button" className="text-slate-400 hover:text-red-600"
                                          onClick={() => remove(i)}>remove</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <button type="button" className="text-blue-700 text-xs mt-1" onClick={addRow}>+ add row</button>
    </div>
  );
}

function JsonField({ label, value, onChange }: { label: any; value: any; onChange: (v: any) => void }) {
  const [text, setText] = useState(JSON.stringify(value ?? null, null, 2));
  const [err, setErr] = useState<string>();
  return (
    <div>{label}
      <textarea rows={4} className="w-full rounded border p-2 text-xs font-mono"
                value={text}
                onChange={e => {
                  setText(e.target.value);
                  try { onChange(JSON.parse(e.target.value)); setErr(undefined); }
                  catch (ex: any) { setErr(ex.message); }
                }} />
      {err && <div className="text-xs text-red-600">{err}</div>}
    </div>
  );
}
