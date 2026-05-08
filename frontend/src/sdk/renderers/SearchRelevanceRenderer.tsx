import type { RendererProps } from '.';
import { useShortcuts } from './useShortcuts';
import { Hint } from './MultipleChoiceRenderer';

export function SearchRelevanceRenderer({ config, payload, value, onChange, focused = true, onAnswered }: RendererProps) {
  const scale: { label: string; grade: number }[] = config.scale ?? [];
  const result = payload.result ?? {};

  const choose = (grade: number) => {
    onChange({ grade });
    onAnswered?.();
  };

  const shortcuts: Record<string, () => void> = {};
  scale.slice(0, 9).forEach((s, i) => {
    shortcuts[String(i + 1)] = () => choose(s.grade);
  });
  useShortcuts(shortcuts, focused);

  return (
    <div className="space-y-3">
      <div className="rounded border bg-slate-50 p-4">
        <div className="text-xs uppercase text-slate-500">Query</div>
        <div className="text-base">{payload.query}</div>
      </div>
      <div className="rounded border bg-white p-4">
        <a href={result.url} target="_blank" rel="noreferrer" className="text-blue-700 font-semibold">
          {result.title}
        </a>
        <div className="text-xs text-slate-500">{result.url}</div>
        <div className="mt-2 whitespace-pre-wrap text-sm">{result.snippet}</div>
      </div>
      <div className="flex flex-wrap gap-2">
        {scale.map((s, i) => {
          const key = i < 9 ? String(i + 1) : null;
          return (
            <button key={s.grade} type="button" onClick={() => choose(s.grade)}
                    className={`px-3 py-2 rounded border text-sm flex items-center gap-2 ${value?.grade === s.grade ? 'bg-amber-500 text-slate-900 border-amber-600' : 'bg-white hover:bg-slate-100'}`}>
              {key && <kbd className="text-[10px] bg-slate-100 text-slate-600 border border-slate-200 rounded px-1">{key}</kbd>}
              {s.label} ({s.grade})
            </button>
          );
        })}
      </div>
      {scale.length > 0 && (
        <Hint>
          Press <kbd className="kbd">1</kbd>{scale.length > 1 ? <>–<kbd className="kbd">{Math.min(scale.length, 9)}</kbd></> : null} to grade.
        </Hint>
      )}
    </div>
  );
}
