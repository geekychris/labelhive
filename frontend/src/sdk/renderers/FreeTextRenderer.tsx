import type { RendererProps } from '.';

export function FreeTextRenderer({ config, payload, value, onChange }: RendererProps) {
  const text = payload.text ?? payload.prompt ?? JSON.stringify(payload);
  return (
    <div className="space-y-4">
      <div className="rounded border bg-white p-4 whitespace-pre-wrap">{text}</div>
      <textarea
        className="w-full min-h-[8rem] rounded border p-3"
        placeholder={`Type your answer (${config.minLength ?? 0}–${config.maxLength ?? '∞'} chars)`}
        value={value?.text ?? ''}
        onChange={e => onChange({ text: e.target.value })}
      />
    </div>
  );
}
