# Authoring a new annotation type — tutorial

The platform's annotation types are pluggable. Adding a new one is two files: a
`LabelingTemplate` on the backend and a renderer on the frontend. No changes to
the database, no migrations, no edits to the worker feed, no edits to the
agreement / trust / gold-promotion / MCP layers — they all key off your type id.

This guide walks through writing a **rating slider** type from scratch
(0–10 numeric rating). By the end you'll have a fully working type, mixable
with gold units, scored against trust, and surfaced in the admin monitor.

---

## 1. Pick a type id

Any non-empty `[A-Z0-9_]+` string. We'll use `RATING_SLIDER`.

You don't need to add it to any enum. Built-in ids live in
`com.datalabel.sdk.TemplateType` for convenience but the system stores ids as
plain strings — third-party JARs can register their own ids the same way.

## 2. Decide on shapes

Three JSON shapes need to be defined:

| | example |
|--|--|
| **Job config** | `{"min": 0, "max": 10, "step": 1, "prompt": "How relevant?"}` |
| **Unit payload** | `{"text": "Some claim to rate"}` |
| **Worker answer** | `{"rating": 7}` |

Pick shapes that:
- Validate cheaply (no external HTTP calls in `validateAnswer`).
- Are easy to render — your config schema directly drives the UI.
- Aggregate meaningfully — if there's no clean consensus you can't auto-promote.

## 3. Implement the backend template

`backend/src/main/java/com/example/RatingSliderTemplate.java`:

```java
@Component
public class RatingSliderTemplate implements LabelingTemplate {
    @Override public String type() { return "RATING_SLIDER"; }

    @Override public void validateConfig(JsonNode c) {
        if (c == null || !c.has("min") || !c.has("max"))
            throw new IllegalArgumentException("min and max required");
        if (c.get("min").asInt() >= c.get("max").asInt())
            throw new IllegalArgumentException("min must be less than max");
    }

    @Override public void validateAnswer(JsonNode config, JsonNode answer) {
        if (answer == null || !answer.has("rating"))
            throw new IllegalArgumentException("rating required");
        int v = answer.get("rating").asInt();
        if (v < config.get("min").asInt() || v > config.get("max").asInt())
            throw new IllegalArgumentException("rating out of range");
    }

    @Override public boolean answersEqual(JsonNode c, JsonNode a, JsonNode b) {
        return a.path("rating").asInt() == b.path("rating").asInt();
    }

    /** Linear partial credit: 1 - |a-b| / range. */
    @Override public double agreementScore(JsonNode config, JsonNode a, JsonNode b) {
        int range = Math.max(1, config.get("max").asInt() - config.get("min").asInt());
        return 1.0 - Math.min(1.0, Math.abs(a.get("rating").asInt() - b.get("rating").asInt()) / (double) range);
    }

    @Override public JsonNode aggregate(JsonNode c, List<JsonNode> answers) {
        if (answers.isEmpty()) return null;
        var sorted = answers.stream().mapToInt(a -> a.get("rating").asInt()).sorted().toArray();
        int median = sorted[sorted.length / 2];
        return JsonNodeFactory.instance.objectNode().put("rating", median);
    }
}
```

Spring's component scan picks it up. The registry now knows the id.

## 4. Implement the frontend renderer

`frontend/src/sdk/renderers/RatingSliderRenderer.tsx`:

```tsx
import type { RendererProps } from '.';

export function RatingSliderRenderer({ config, payload, value, onChange }: RendererProps) {
  return (
    <div className="space-y-3">
      <div className="rounded border bg-white p-4">{payload.text}</div>
      <div className="text-xs text-slate-500">{config.prompt ?? 'Rate this'}</div>
      <input type="range"
             min={config.min} max={config.max} step={config.step ?? 1}
             value={value?.rating ?? config.min}
             onChange={e => onChange({ rating: Number(e.target.value) })}
             className="w-full" />
      <div className="text-center text-2xl">{value?.rating ?? '—'}</div>
    </div>
  );
}
```

Register it in `frontend/src/sdk/renderers/index.ts`:

```ts
import { RatingSliderRenderer } from './RatingSliderRenderer';

export const rendererRegistry: Renderer[] = [
  // ...existing
  defineRenderer({ type: 'RATING_SLIDER', render: RatingSliderRenderer }),
];
```

## 5. Use it

Create a job:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: demo' \
  -d '{
    "name": "Rate the claim",
    "templateType": "RATING_SLIDER",
    "templateConfig": {"min": 0, "max": 10, "prompt": "How believable?"}
  }'
```

Upload a couple of units:

```bash
curl -X POST http://localhost:8080/api/jobs/7/units \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: demo' \
  -d '[
    {"payload": {"text": "Java is faster than Python."}, "goldAnswer": {"rating": 8}},
    {"payload": {"text": "Bigger context windows always beat RAG."}}
  ]'
```

Now it shows up in the worker feed at `/work` exactly like the built-ins.

## 6. What you got for free

By writing those two files you also got:

- **Gold mixing** — units with `goldAnswer` are dealt into the worker feed at the
  job's gold ratio, opaque to workers.
- **Trust scoring** — your `answersEqual` decides whether a worker got the gold
  unit right; their accuracy rolls up and quarantines them below
  `trustFloor`.
- **Auto-promotion** — when worker mean pairwise `agreementScore` exceeds
  `promoteAgreementMin`, your `aggregate` is called and the consensus answer
  becomes a new gold unit, attributed to the first contributor whose answer
  matched the consensus.
- **Job monitor** — the admin page shows completeness, throughput, and per-
  worker accuracy on this job, no extra wiring.
- **MCP exposure** — an LLM agent connecting to the MCP server can list the
  job, fetch tasks, and submit `{"rating": N}` answers.
- **REST API** — the same `/api/jobs/{id}/tasks/next`, `/judgment`, and
  `/reports/agreement` work without modification.

## 7. Where to peek for harder cases

If your shape is more complex than a scalar:

- **Structural answers (lists, maps, trees)** — see
  `SpanAnnotationTemplate` (token-level F1 over span tuples) and
  `BoundingBoxTemplate` (mean best-IOU per box).
- **Ordinal scales with custom ranges** — see `SearchRelevanceTemplate`.
- **Composition (reuse another template's logic)** — see
  `ImageClassificationTemplate` (delegates to multi-choice, only changes the
  payload field).

That's the SDK. Two files, one type id, the rest is free.
