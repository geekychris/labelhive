import { useMemo } from 'react';

/**
 * Tiny Mustache-style interpolator: replaces {{path.to.field}} in a template
 * with the corresponding value from `data`. Outputs HTML — caller is
 * responsible for sanitising `template`. We don't wire this up to dangerous
 * UI by default; only the admin builder previews it inside a sandboxed DOM
 * subtree.
 */
export function renderTemplate(template: string, data: any): string {
  if (!template) return '';
  return template.replace(/\{\{\s*([\w.]+)\s*\}\}/g, (_, path: string) => {
    const value = path.split('.').reduce<any>((acc, k) => acc == null ? acc : acc[k], data);
    if (value == null) return '';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  });
}

interface Props {
  value: string;
  onChange: (v: string) => void;
  /** Sample payload to render the live preview against. */
  samplePayload: any;
}

export function DisplayTemplateEditor({ value, onChange, samplePayload }: Props) {
  const rendered = useMemo(() => renderTemplate(value, samplePayload), [value, samplePayload]);
  return (
    <div className="grid md:grid-cols-2 gap-3">
      <div>
        <div className="text-xs text-slate-500 mb-1">
          Template — use <code>{'{{field}}'}</code> to interpolate from each unit's payload.
        </div>
        <textarea rows={8} className="w-full rounded border p-2 text-xs font-mono"
                  value={value} onChange={e => onChange(e.target.value)}
                  placeholder='<b>{{query}}</b><br/>{{result.title}}' />
      </div>
      <div>
        <div className="text-xs text-slate-500 mb-1">Live preview (against sample payload)</div>
        <div className="rounded border bg-white p-3 min-h-[8rem] text-sm"
             dangerouslySetInnerHTML={{ __html: rendered }} />
        {samplePayload && (
          <details className="mt-2">
            <summary className="text-xs text-slate-500 cursor-pointer">Sample payload</summary>
            <pre className="text-[10px] bg-slate-50 p-2 rounded mt-1 overflow-auto">
{JSON.stringify(samplePayload, null, 2)}
            </pre>
          </details>
        )}
      </div>
    </div>
  );
}
