import type { RendererProps } from '.';
import { MultipleChoiceRenderer } from './MultipleChoiceRenderer';

export function ImageClassificationRenderer(props: RendererProps) {
  const url = props.payload[props.config.imageField ?? 'imageUrl'];
  return (
    <div className="space-y-4">
      <div className="rounded border bg-white p-2 inline-block">
        <img src={url} alt="" className="max-h-96 max-w-full rounded" />
      </div>
      {props.payload.caption && <div className="text-sm text-slate-600">{props.payload.caption}</div>}
      <MultipleChoiceRenderer {...props} />
    </div>
  );
}
