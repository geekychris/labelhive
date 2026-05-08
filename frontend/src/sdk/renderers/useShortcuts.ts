import { useEffect } from 'react';

/**
 * Tiny shortcut helper for renderers. Wires a window keydown listener while
 * `active` is true; ignores events where the target is an input/textarea so
 * typing into FreeText doesn't trigger shortcuts.
 *
 * @param handlers map from event.key to a thunk
 * @param active whether the listener should be live (e.g. only when this card is focused)
 */
export function useShortcuts(handlers: Record<string, () => void>, active: boolean) {
  useEffect(() => {
    if (!active) return;
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || (t as HTMLElement).isContentEditable)) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const fn = handlers[e.key];
      if (fn) { e.preventDefault(); fn(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [handlers, active]);
}
