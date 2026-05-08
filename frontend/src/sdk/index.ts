/**
 * Frontend SDK entrypoint — what plugin authors import to add a new annotation type.
 *
 * Two things you'll typically reach for:
 *
 *   1. {@link defineRenderer} to register a React component for a template type id.
 *   2. {@link Renderer} / {@link RendererProps} types so your component is typed.
 *
 * Then update {@link rendererRegistry} (in `renderers/index.ts`) to include your
 * new entry — that single map drives the worker UI.
 *
 * @see ../../../docs/templates/CUSTOM_TYPE_TUTORIAL.md
 */
export type { Renderer, RendererProps } from './renderers';
export { defineRenderer, rendererRegistry } from './renderers';
