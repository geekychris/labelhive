import { useMemo, useState } from 'react';
import type { RendererProps } from '.';
import { useShortcuts } from './useShortcuts';

interface Span { start: number; end: number; label: string }

/**
 * Word-level span tagger:
 *
 * - Click a word to attach the active label.
 * - Click again to remove it. Clicking with a different active label switches.
 * - Each label gets its own color and a small inline pill so the workspace
 *   makes sense at a glance even with several label types in play.
 *
 * Adjacent same-label tokens are merged for tidy output; deselect correctly
 * subtracts a token from a merged span (splits if needed).
 */
export function SpanAnnotationRenderer({ config, payload, value, onChange, focused = true }: RendererProps) {
  const text: string = payload[config.textField ?? 'text'] ?? '';
  const labels: string[] = config.labels ?? [];
  const [active, setActive] = useState<string>(labels[0] ?? '');

  // Keys 1..9 switch the active label by index.
  const shortcuts: Record<string, () => void> = {};
  labels.slice(0, 9).forEach((l, i) => {
    shortcuts[String(i + 1)] = () => setActive(l);
  });
  useShortcuts(shortcuts, focused);

  const tokens = useMemo(() => {
    const out: { text: string; start: number; end: number; isWord: boolean }[] = [];
    const re = /\S+|\s+/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) !== null) {
      out.push({ text: m[0], start: m.index, end: m.index + m[0].length, isWord: !/^\s+$/.test(m[0]) });
    }
    return out;
  }, [text]);

  const spans: Span[] = value?.spans ?? [];
  const labelAt = (offset: number) =>
    spans.find(s => offset >= s.start && offset < s.end);

  const toggleToken = (start: number, end: number) => {
    const containing = spans.find(s => s.start <= start && s.end >= end);
    const left  = (s: Span) => trimToWord(text, { start: s.start, end: start, label: s.label });
    const right = (s: Span) => trimToWord(text, { start: end,    end: s.end,  label: s.label });
    const keep  = (p: Span) => p.end > p.start && !isAllWhitespace(text, p.start, p.end);

    let next: Span[];
    if (containing && containing.label === active) {
      next = spans.filter(s => s !== containing);
      if (keep(left(containing)))  next.push(left(containing));
      if (keep(right(containing))) next.push(right(containing));
    } else if (containing) {
      next = spans.filter(s => s !== containing);
      if (keep(left(containing)))  next.push(left(containing));
      next.push({ start, end, label: active });
      if (keep(right(containing))) next.push(right(containing));
    } else {
      next = [...spans, { start, end, label: active }];
    }

    next.sort((a, b) => a.start - b.start);
    const merged: Span[] = [];
    for (const s of next) {
      const last = merged[merged.length - 1];
      if (last && last.label === s.label && last.end <= s.start
          && /^\s*$/.test(text.slice(last.end, s.start))) {
        last.end = s.end;
      } else {
        merged.push({ ...s });
      }
    }
    onChange({ spans: merged });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2 items-center">
        <span className="text-sm text-slate-500">Active label:</span>
        {labels.map((l, i) => {
          const c = colorFor(i);
          const isActive = active === l;
          const shortcutKey = i < 9 ? String(i + 1) : null;
          return (
            <button key={l} type="button" onClick={() => setActive(l)}
                    className={
                      `px-2 py-1 rounded border text-xs font-medium transition flex items-center gap-1 ` +
                      (isActive
                        ? `${c.activeBg} ${c.activeText} ${c.activeBorder} ring-2 ring-offset-1 ring-offset-white ${c.ring}`
                        : `bg-white ${c.text} ${c.border} hover:${c.hoverBg}`)
                    }>
              <span className={`inline-block w-2 h-2 rounded-full ${c.dot}`} />
              {l}
              {shortcutKey && <kbd className="text-[9px] bg-white/70 text-slate-700 border border-slate-200 rounded px-1 ml-0.5">{shortcutKey}</kbd>}
            </button>
          );
        })}
      </div>
      {labels.length > 0 && (
        <div className="text-[11px] text-slate-500">
          Press <kbd className="kbd">1</kbd>{labels.length > 1 ? <>–<kbd className="kbd">{Math.min(labels.length, 9)}</kbd></> : null}
          {' '}to switch active label · click a word to tag · click again to remove.
        </div>
      )}

      <div className="rounded border bg-white p-4 leading-8 select-none">
        {tokens.map((t, i) => {
          if (!t.isWord) return <span key={i}>{t.text}</span>;
          const span = labelAt(t.start);
          const colorIdx = span ? labels.indexOf(span.label) : -1;
          const c = colorIdx >= 0 ? colorFor(colorIdx) : null;
          const isLastTokenInSpan = !!span && t.end >= span.end;
          return (
            <span key={i}
                  onClick={() => toggleToken(t.start, t.end)}
                  className={
                    `cursor-pointer rounded px-0.5 ` +
                    (c ? `${c.bg} ${c.border} border-b-2` : 'hover:bg-slate-100')
                  }
                  title={span?.label ?? 'click to tag'}>
              {t.text}
              {isLastTokenInSpan && c && (
                <sup className={`ml-1 px-1 rounded text-[9px] uppercase font-semibold align-middle ${c.chip} ${c.text}`}>
                  {span.label}
                </sup>
              )}
            </span>
          );
        })}
      </div>

      {spans.length > 0 && (
        <div className="text-xs text-slate-500">
          {spans.length} span{spans.length === 1 ? '' : 's'} tagged.
        </div>
      )}
    </div>
  );
}

/**
 * Per-label colour palette. Tailwind needs literal class names to keep them
 * in the output stylesheet, so we list everything explicitly.
 */
const PALETTE = [
  { dot: 'bg-amber-500',   bg: 'bg-amber-200',   border: 'border-amber-500',   text: 'text-amber-900',   chip: 'bg-amber-300',   hoverBg: 'bg-amber-50',
    activeBg: 'bg-amber-500',   activeBorder: 'border-amber-700',   activeText: 'text-slate-900', ring: 'ring-amber-300' },
  { dot: 'bg-sky-500',     bg: 'bg-sky-200',     border: 'border-sky-500',     text: 'text-sky-900',     chip: 'bg-sky-300',     hoverBg: 'bg-sky-50',
    activeBg: 'bg-sky-500',     activeBorder: 'border-sky-700',     activeText: 'text-white',     ring: 'ring-sky-300' },
  { dot: 'bg-emerald-500', bg: 'bg-emerald-200', border: 'border-emerald-500', text: 'text-emerald-900', chip: 'bg-emerald-300', hoverBg: 'bg-emerald-50',
    activeBg: 'bg-emerald-500', activeBorder: 'border-emerald-700', activeText: 'text-white',     ring: 'ring-emerald-300' },
  { dot: 'bg-violet-500',  bg: 'bg-violet-200',  border: 'border-violet-500',  text: 'text-violet-900',  chip: 'bg-violet-300',  hoverBg: 'bg-violet-50',
    activeBg: 'bg-violet-500',  activeBorder: 'border-violet-700',  activeText: 'text-white',     ring: 'ring-violet-300' },
  { dot: 'bg-rose-500',    bg: 'bg-rose-200',    border: 'border-rose-500',    text: 'text-rose-900',    chip: 'bg-rose-300',    hoverBg: 'bg-rose-50',
    activeBg: 'bg-rose-500',    activeBorder: 'border-rose-700',    activeText: 'text-white',     ring: 'ring-rose-300' },
  { dot: 'bg-cyan-500',    bg: 'bg-cyan-200',    border: 'border-cyan-500',    text: 'text-cyan-900',    chip: 'bg-cyan-300',    hoverBg: 'bg-cyan-50',
    activeBg: 'bg-cyan-500',    activeBorder: 'border-cyan-700',    activeText: 'text-white',     ring: 'ring-cyan-300' },
] as const;

function colorFor(idx: number): typeof PALETTE[number] {
  return PALETTE[((idx % PALETTE.length) + PALETTE.length) % PALETTE.length];
}

function isAllWhitespace(text: string, start: number, end: number): boolean {
  return /^\s*$/.test(text.slice(start, end));
}

function trimToWord(text: string, s: Span): Span {
  let { start, end } = s;
  while (start < end && /\s/.test(text[start]))   start++;
  while (end > start && /\s/.test(text[end - 1])) end--;
  return { ...s, start, end };
}
