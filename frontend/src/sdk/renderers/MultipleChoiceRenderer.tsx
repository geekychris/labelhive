import type { RendererProps } from '.';
import { useShortcuts } from './useShortcuts';

export function MultipleChoiceRenderer({ config, payload, value, onChange, focused = true, onAnswered }: RendererProps) {
  const multi = !!config.allowMultiple;
  const choices: string[] = config.choices ?? [];
  const text = payload.text ?? payload.question ?? JSON.stringify(payload);

  const selected: Set<string> = new Set(multi ? value?.choices ?? [] : value?.choice ? [value.choice] : []);

  const choose = (c: string) => {
    if (multi) {
      const next = new Set(selected);
      if (next.has(c)) next.delete(c); else next.add(c);
      onChange({ choices: Array.from(next) });
    } else {
      onChange({ choice: c });
      onAnswered?.();
    }
  };

  // Number-key shortcuts: 1..9 then 0 → choices[0..9].
  const shortcuts: Record<string, () => void> = {};
  choices.slice(0, 10).forEach((c, i) => {
    const key = i === 9 ? '0' : String(i + 1);
    shortcuts[key] = () => choose(c);
  });
  useShortcuts(shortcuts, focused);

  return (
    <div className="space-y-3">
      <div className="rounded border bg-white p-4 whitespace-pre-wrap">{text}</div>
      {payload.answer && (
        <div className="rounded border bg-slate-50 p-4 text-sm">
          <div className="font-semibold mb-1">Candidate answer</div>
          <div className="whitespace-pre-wrap">{payload.answer}</div>
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        {choices.map((c, i) => {
          const key = i < 10 ? (i === 9 ? '0' : String(i + 1)) : null;
          return (
            <button key={c} type="button" onClick={() => choose(c)}
                    className={`px-3 py-2 rounded border text-sm flex items-center gap-2 ${selected.has(c) ? 'bg-amber-500 text-slate-900 border-amber-600' : 'bg-white hover:bg-slate-100'}`}>
              {key && <kbd className="text-[10px] bg-slate-100 text-slate-600 border border-slate-200 rounded px-1">{key}</kbd>}
              {c}
            </button>
          );
        })}
      </div>
      {choices.length > 0 && choices.length <= 10 && (
        <Hint>
          Press <kbd className="kbd">1</kbd>{choices.length > 1 ? <>–<kbd className="kbd">{choices.length === 10 ? '0' : choices.length}</kbd></> : null}
          {' '}to {multi ? 'toggle' : 'pick'}.
        </Hint>
      )}
    </div>
  );
}

export function Hint({ children }: { children: React.ReactNode }) {
  return <div className="text-[11px] text-slate-500">{children}</div>;
}
