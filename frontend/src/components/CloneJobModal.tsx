import { useState } from 'react';

type CloneMode = 'settings' | 'with-units' | 'with-gold';

interface Props {
  open: boolean;
  onCancel: () => void;
  onConfirm: (mode: CloneMode) => void;
  busy?: boolean;
}

/**
 * Three-way clone picker so the admin doesn't have to chain confirm dialogs.
 */
export function CloneJobModal({ open, onCancel, onConfirm, busy }: Props) {
  const [mode, setMode] = useState<CloneMode>('settings');
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 bg-slate-900/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl border border-slate-200 max-w-md w-full p-5 space-y-4">
        <div>
          <h3 className="font-semibold text-slate-900">Clone job</h3>
          <p className="text-xs text-slate-600">Pick what carries over to the new job.</p>
        </div>
        <div className="space-y-2">
          {([
            ['settings',   'Just settings',
             'Template type, variant config, thresholds, display template. No units.'],
            ['with-units', 'Settings + units (no gold)',
             'Same dataset, but gold answers are stripped — fresh round of labeling on the same data.'],
            ['with-gold',  'Everything',
             'Settings + units + gold answers. Carbon copy.'],
          ] as [CloneMode, string, string][]).map(([id, label, desc]) => (
            <label key={id}
                   className={
                     'flex gap-3 p-3 rounded border cursor-pointer ' +
                     (mode === id ? 'border-amber-400 bg-amber-50' : 'border-slate-200 hover:bg-slate-50')
                   }>
              <input type="radio" name="clone-mode" checked={mode === id} onChange={() => setMode(id)} />
              <div className="text-sm">
                <div className="font-medium text-slate-900">{label}</div>
                <div className="text-xs text-slate-600">{desc}</div>
              </div>
            </label>
          ))}
        </div>
        <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
          <button onClick={onCancel} className="px-3 py-1.5 rounded border border-slate-300 text-sm text-slate-700">
            Cancel
          </button>
          <button onClick={() => onConfirm(mode)} disabled={busy}
                  className="px-3 py-1.5 rounded bg-amber-500 text-slate-900 font-medium text-sm hover:bg-amber-400">
            {busy ? 'Cloning…' : 'Clone'}
          </button>
        </div>
      </div>
    </div>
  );
}

export type { CloneMode };
