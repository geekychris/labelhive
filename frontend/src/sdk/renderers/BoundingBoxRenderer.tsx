import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { RendererProps } from '.';

/**
 * Bounding-box renderer. Worker drags on the image to draw a rectangle, picks a class
 * label from {@code config.labels}, then can keep adding boxes or delete one. Coordinates
 * are stored in image-pixel units (matching {@code BoundingBoxTemplate}'s expectation),
 * so the same answer JSON renders correctly at any container size.
 */
type Box = { x: number; y: number; w: number; h: number; label: string };

const MIN_BOX_PX = 4; // ignore stray click-without-drag

export function BoundingBoxRenderer({ config, payload, value, onChange, readOnly }: RendererProps) {
  const labels: string[] = Array.isArray(config?.labels) && config.labels.length > 0
    ? config.labels
    : ['object'];
  const imageField: string = (config?.imageField ?? 'imageUrl') as string;
  const imageUrl: string = payload?.[imageField] ?? '';

  const boxes: Box[] = Array.isArray(value?.boxes) ? value.boxes : [];
  const setBoxes = useCallback(
    (next: Box[]) => onChange({ boxes: next }),
    [onChange],
  );

  const [activeLabel, setActiveLabel] = useState<string>(labels[0]);
  // Recorded once the image loads, used to map between image-pixel coordinates (the stored
  // shape) and rendered-pixel coordinates (mouse events).
  const [natural, setNatural] = useState<{ w: number; h: number } | null>(null);
  // Drag in progress: from-point + current point in image-pixel space.
  const [drag, setDrag] = useState<{ x0: number; y0: number; x1: number; y1: number } | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);

  // Default the dropdown to a valid label when config.labels changes.
  useEffect(() => {
    if (!labels.includes(activeLabel)) setActiveLabel(labels[0]);
  }, [labels, activeLabel]);

  // Convert from rendered (CSS) pixels to image-natural pixels. Measured per event because
  // the image element can be resized by the layout.
  const toImageCoords = useCallback((clientX: number, clientY: number) => {
    const img = imgRef.current;
    if (!img || !natural) return null;
    const rect = img.getBoundingClientRect();
    const sx = natural.w / rect.width;
    const sy = natural.h / rect.height;
    return {
      x: Math.max(0, Math.min(natural.w, (clientX - rect.left) * sx)),
      y: Math.max(0, Math.min(natural.h, (clientY - rect.top) * sy)),
    };
  }, [natural]);

  const renderScale = useMemo(() => {
    if (!imgRef.current || !natural) return { x: 1, y: 1 };
    const r = imgRef.current.getBoundingClientRect();
    return { x: r.width / natural.w, y: r.height / natural.h };
    // re-measure when boxes/drag/natural change so overlays stay aligned across re-renders.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [natural, drag, boxes.length]);

  const onMouseDown = (e: React.MouseEvent) => {
    if (readOnly || !natural) return;
    const p = toImageCoords(e.clientX, e.clientY);
    if (!p) return;
    setDrag({ x0: p.x, y0: p.y, x1: p.x, y1: p.y });
  };
  const onMouseMove = (e: React.MouseEvent) => {
    if (readOnly || !drag) return;
    const p = toImageCoords(e.clientX, e.clientY);
    if (!p) return;
    setDrag({ ...drag, x1: p.x, y1: p.y });
  };
  const onMouseUp = () => {
    if (readOnly || !drag) return;
    const x = Math.min(drag.x0, drag.x1);
    const y = Math.min(drag.y0, drag.y1);
    const w = Math.abs(drag.x1 - drag.x0);
    const h = Math.abs(drag.y1 - drag.y0);
    setDrag(null);
    if (w < MIN_BOX_PX || h < MIN_BOX_PX) return; // ignore accidental clicks
    setBoxes([
      ...boxes,
      { x: Math.round(x), y: Math.round(y), w: Math.round(w), h: Math.round(h), label: activeLabel },
    ]);
  };

  const removeBox = (idx: number) => {
    if (readOnly) return;
    setBoxes(boxes.filter((_, i) => i !== idx));
  };
  const relabelBox = (idx: number, label: string) => {
    if (readOnly) return;
    setBoxes(boxes.map((b, i) => (i === idx ? { ...b, label } : b)));
  };

  if (!imageUrl) {
    return (
      <div className="rounded border bg-amber-50 p-3 text-sm">
        No image URL on payload.{imageField} — check job config.
      </div>
    );
  }

  const dragRect = drag
    ? {
        left: Math.min(drag.x0, drag.x1) * renderScale.x,
        top: Math.min(drag.y0, drag.y1) * renderScale.y,
        width: Math.abs(drag.x1 - drag.x0) * renderScale.x,
        height: Math.abs(drag.y1 - drag.y0) * renderScale.y,
      }
    : null;

  return (
    <div className="space-y-3">
      {!readOnly && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-gray-600">Label:</span>
          {labels.map((l) => (
            <button
              key={l}
              type="button"
              onClick={() => setActiveLabel(l)}
              className={`rounded px-2 py-1 text-xs ${
                activeLabel === l ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {l}
            </button>
          ))}
          <span className="ml-auto text-xs text-gray-500">
            {boxes.length} box{boxes.length === 1 ? '' : 'es'}
          </span>
        </div>
      )}

      <div
        className="relative inline-block select-none"
        onMouseDown={onMouseDown}
        onMouseMove={onMouseMove}
        onMouseUp={onMouseUp}
        onMouseLeave={onMouseUp}
        style={{ cursor: readOnly ? 'default' : 'crosshair' }}
      >
        <img
          ref={imgRef}
          src={imageUrl}
          alt="task"
          draggable={false}
          className="max-w-full rounded border"
          onLoad={(e) => {
            const t = e.currentTarget;
            setNatural({ w: t.naturalWidth, h: t.naturalHeight });
          }}
        />
        {natural &&
          boxes.map((b, i) => (
            <BoxOverlay
              key={i}
              box={b}
              scale={renderScale}
              readOnly={!!readOnly}
              labels={labels}
              onRemove={() => removeBox(i)}
              onRelabel={(l) => relabelBox(i, l)}
            />
          ))}
        {dragRect && (
          <div className="absolute border-2 border-blue-500 bg-blue-500/20 pointer-events-none" style={dragRect} />
        )}
      </div>

      {!readOnly && boxes.length === 0 && (
        <p className="text-xs text-gray-500">
          Drag on the image to draw a box. Boxes are stored in image-pixel coordinates.
        </p>
      )}
    </div>
  );
}

function BoxOverlay({
  box, scale, readOnly, labels, onRemove, onRelabel,
}: {
  box: Box;
  scale: { x: number; y: number };
  readOnly: boolean;
  labels: string[];
  onRemove: () => void;
  onRelabel: (label: string) => void;
}) {
  return (
    <div
      className="absolute border-2 border-emerald-500 bg-emerald-500/10"
      style={{
        left: box.x * scale.x,
        top: box.y * scale.y,
        width: box.w * scale.x,
        height: box.h * scale.y,
      }}
      // Don't start a new drag when interacting with the per-box controls.
      onMouseDown={(e) => e.stopPropagation()}
    >
      <div className="absolute -top-6 left-0 flex items-center gap-1 text-[10px]">
        {readOnly ? (
          <span className="rounded bg-emerald-600 px-1.5 py-0.5 text-white">{box.label}</span>
        ) : (
          <select
            value={box.label}
            onChange={(e) => onRelabel(e.target.value)}
            className="rounded bg-emerald-600 px-1 py-0.5 text-white"
          >
            {labels.map((l) => (
              <option key={l} value={l}>{l}</option>
            ))}
          </select>
        )}
        {!readOnly && (
          <button
            type="button"
            onClick={onRemove}
            className="rounded bg-red-600 px-1 py-0.5 text-white"
            title="Remove"
          >
            ×
          </button>
        )}
      </div>
    </div>
  );
}
