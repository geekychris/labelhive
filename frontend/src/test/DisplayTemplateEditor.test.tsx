import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DisplayTemplateEditor, renderTemplate } from '../components/DisplayTemplateEditor';

describe('renderTemplate', () => {
  it('substitutes top-level fields', () => {
    expect(renderTemplate('hi {{name}}', { name: 'Anthropic' })).toBe('hi Anthropic');
  });

  it('walks dotted paths', () => {
    expect(renderTemplate('{{result.title}}', { result: { title: 'pgvector' } })).toBe('pgvector');
  });

  it('renders missing fields as empty string', () => {
    expect(renderTemplate('q={{q}} t={{t}}', { q: 'x' })).toBe('q=x t=');
  });

  it('stringifies object values', () => {
    expect(renderTemplate('{{x}}', { x: { a: 1 } })).toBe('{"a":1}');
  });
});

describe('DisplayTemplateEditor', () => {
  it('shows live preview against the sample payload', async () => {
    const onChange = vi.fn();
    render(<DisplayTemplateEditor value="hi {{name}}" onChange={onChange} samplePayload={{ name: 'world' }} />);
    expect(screen.getByText('hi world')).toBeInTheDocument();
  });

  it('emits template edits via onChange', async () => {
    const onChange = vi.fn();
    render(<DisplayTemplateEditor value="" onChange={onChange} samplePayload={{}} />);
    const ta = screen.getByRole('textbox');
    await userEvent.type(ta, 'hi');
    expect(onChange).toHaveBeenCalled();
  });
});
