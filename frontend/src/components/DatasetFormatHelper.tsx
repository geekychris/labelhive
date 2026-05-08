import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api } from '../api/client';
import type { DatasetValidation, PayloadSchema } from '../api/types';

interface Props {
  templateType: string;
  templateConfig: any;
  payloadSchema?: PayloadSchema;
  /** Current staged dataset JSON. */
  datasetText: string;
}

/**
 * Reusable "how to format your data + validate it" panel. Shown on the
 * builder dataset step and the JobDetail upload section.
 */
export function DatasetFormatHelper({ templateType, templateConfig, payloadSchema, datasetText }: Props) {
  const [validation, setValidation] = useState<DatasetValidation>();

  const validate = useMutation({
    mutationFn: async () => {
      const units = JSON.parse(datasetText);
      const arr = Array.isArray(units) ? units : (units?.units ?? []);
      return api.validateDataset(templateType, { config: templateConfig, units: arr });
    },
    onSuccess: r => setValidation(r),
    onError: (e: any) => setValidation({ ok: false, summary: { total: 0, valid: 0, invalid: 0, withGold: 0 },
                                         errors: [{ at: 'parse', error: e.message }] }),
  });

  return (
    <div className="bg-stone-50 border border-slate-200 rounded p-3 space-y-3 text-sm">
      <div>
        <div className="font-semibold text-slate-800">Dataset format for {templateType}</div>
        {payloadSchema?.description && (
          <p className="text-xs text-slate-600 mt-1">{payloadSchema.description}</p>
        )}
        {payloadSchema?.requiredFields && payloadSchema.requiredFields.length > 0 && (
          <p className="text-xs text-slate-500 mt-1">
            Required payload fields: {payloadSchema.requiredFields.map(f => <code key={f} className="bg-stone-100 px-1 rounded mr-1">{f}</code>)}
          </p>
        )}
      </div>

      {payloadSchema?.exampleUnit && (
        <details className="text-xs">
          <summary className="cursor-pointer text-slate-700">Example unit JSON</summary>
          <pre className="bg-white border border-slate-200 rounded p-2 mt-1 overflow-auto">
{JSON.stringify(payloadSchema.exampleUnit, null, 2)}
          </pre>
        </details>
      )}

      <details className="text-xs">
        <summary className="cursor-pointer text-slate-700">Code snippet (LabelHive Python SDK)</summary>
        <pre className="bg-white border border-slate-200 rounded p-2 mt-1 overflow-auto">{`from labelhive import LabelHive

DATASET = [
${payloadSchema?.exampleUnit ? '  ' + JSON.stringify(payloadSchema.exampleUnit, null, 2).split('\n').join('\n  ') : '  # ...'}
]

with LabelHive("http://localhost:8080", user="demo") as hive:
    # 1. Validate before upload
    result = hive.validate_dataset(
        "${templateType}",
        config=${JSON.stringify(templateConfig)},
        units=DATASET,
    )
    if not result["ok"]:
        for err in result["errors"]: print(err)
        raise SystemExit(1)

    # 2. Upload
    job = hive.create_job(name="My job",
                          template_type="${templateType}",
                          template_config=${JSON.stringify(templateConfig)})
    hive.upload_units(job["id"], DATASET)`}</pre>
      </details>

      <div className="flex items-center gap-2">
        <button onClick={() => validate.mutate()} disabled={validate.isPending}
                className="px-3 py-1.5 rounded border border-slate-300 text-xs text-slate-700 bg-white hover:bg-slate-50">
          {validate.isPending ? 'Validating…' : 'Validate dataset against template'}
        </button>
        {validation && (
          <span className={`text-xs ${validation.ok ? 'text-emerald-700' : 'text-red-700'}`}>
            {validation.ok
              ? `✓ ${validation.summary.valid} valid · ${validation.summary.withGold} with gold`
              : `✗ ${validation.summary.invalid} of ${validation.summary.total} failed`}
          </span>
        )}
      </div>

      {validation && !validation.ok && (
        <div className="text-xs space-y-1">
          {validation.errors.slice(0, 8).map((e, i) => (
            <div key={i} className="bg-red-50 border border-red-200 rounded px-2 py-1 text-red-800">
              {e.at ? <code className="mr-1">{e.at}</code> : <span className="font-mono mr-1">unit[{e.index}]</span>}
              {e.error}
            </div>
          ))}
          {validation.errors.length > 8 && (
            <div className="text-slate-500">… and {validation.errors.length - 8} more.</div>
          )}
        </div>
      )}
    </div>
  );
}
