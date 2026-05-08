import { ComponentType } from 'react';

/**
 * Props every renderer receives. The matching backend {@code LabelingTemplate}
 * defines the shapes of {@code config}, {@code payload}, and the answer
 * shape that {@code onChange} emits.
 */
export interface RendererProps {
  config: any;
  payload: any;
  /** Whatever shape the matching backend template's answer schema describes. */
  value: any | undefined;
  onChange: (next: any) => void;
  /** Whether this card has keyboard focus — gates keyboard shortcuts. Defaults to true (single-task pages). */
  focused?: boolean;
  /** Renderer signals "I just produced a complete answer" — TaskView uses this to auto-advance focus. */
  onAnswered?: () => void;
  /**
   * When true, the renderer must display {@code value} but ignore user input — used by the
   * worker job-instructions page to show exemplar gold answers as study material.
   */
  readOnly?: boolean;
}

export { useShortcuts } from './useShortcuts';

/** A renderer is just a React component keyed by a template type id. */
export interface Renderer {
  type: string;
  render: ComponentType<RendererProps>;
}

/**
 * Type-safe constructor for renderer entries — gives you autocomplete on the
 * shape and centralizes the contract should we add fields later
 * (per-renderer keyboard shortcuts, async validation hooks, etc).
 */
export function defineRenderer(r: Renderer): Renderer {
  return r;
}

import { MultipleChoiceRenderer } from './MultipleChoiceRenderer';
import { FreeTextRenderer } from './FreeTextRenderer';
import { ImageClassificationRenderer } from './ImageClassificationRenderer';
import { SearchRelevanceRenderer } from './SearchRelevanceRenderer';
import { SpanAnnotationRenderer } from './SpanAnnotationRenderer';
import { BoundingBoxRenderer } from './BoundingBoxRenderer';

/**
 * Built-in renderers. Add a new entry here to register a new annotation type:
 *
 *   defineRenderer({ type: 'YOUR_TYPE', render: YourRenderer })
 *
 * The worker UI looks up by {@code WorkerUnit.templateType}.
 */
export const rendererRegistry: Renderer[] = [
  defineRenderer({ type: 'MULTIPLE_CHOICE',      render: MultipleChoiceRenderer }),
  defineRenderer({ type: 'FREE_TEXT',            render: FreeTextRenderer }),
  defineRenderer({ type: 'IMAGE_CLASSIFICATION', render: ImageClassificationRenderer }),
  defineRenderer({ type: 'SEARCH_RELEVANCE',     render: SearchRelevanceRenderer }),
  defineRenderer({ type: 'SPAN_ANNOTATION',      render: SpanAnnotationRenderer }),
  defineRenderer({ type: 'BOUNDING_BOX',         render: BoundingBoxRenderer }),
];

/** Convenience map keyed by id, built from {@link rendererRegistry}. */
export const renderers: Record<string, ComponentType<RendererProps>> = Object.fromEntries(
  rendererRegistry.map(r => [r.type, r.render]),
);
