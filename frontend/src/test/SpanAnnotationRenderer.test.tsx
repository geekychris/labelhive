import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SpanAnnotationRenderer } from '../sdk/renderers/SpanAnnotationRenderer';

const config = { labels: ['KEYWORD'], textField: 'text' };

describe('SpanAnnotationRenderer', () => {
  it('tags a clicked word with the active label', async () => {
    const onChange = vi.fn();
    render(
      <SpanAnnotationRenderer
        config={config}
        payload={{ text: 'Anthropic released Claude.' }}
        value={undefined}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByText('Anthropic'));
    expect(onChange).toHaveBeenCalledWith({
      spans: [{ start: 0, end: 9, label: 'KEYWORD' }],
    });
  });

  it('removes a span when the same word is clicked again with the same active label', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <SpanAnnotationRenderer
        config={config}
        payload={{ text: 'hi there' }}
        value={{ spans: [] }}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByText('hi'));
    expect(onChange).toHaveBeenLastCalledWith({ spans: [{ start: 0, end: 2, label: 'KEYWORD' }] });

    rerender(
      <SpanAnnotationRenderer
        config={config}
        payload={{ text: 'hi there' }}
        value={{ spans: [{ start: 0, end: 2, label: 'KEYWORD' }] }}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByText('hi'));
    expect(onChange).toHaveBeenLastCalledWith({ spans: [] });
  });

  it('switches the active label when multiple labels are configured', async () => {
    const cfg = { labels: ['KEYWORD', 'COMPANY'], textField: 'text' };
    const onChange = vi.fn();
    render(
      <SpanAnnotationRenderer
        config={cfg}
        payload={{ text: 'Apple ships fast.' }}
        value={undefined}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /COMPANY/ }));
    await userEvent.click(screen.getByText('Apple'));
    expect(onChange).toHaveBeenCalledWith({
      spans: [{ start: 0, end: 5, label: 'COMPANY' }],
    });
  });

  it('shows a count of tagged spans', () => {
    render(
      <SpanAnnotationRenderer
        config={config}
        payload={{ text: 'hi there friend' }}
        value={{ spans: [{ start: 0, end: 2, label: 'KEYWORD' }, { start: 9, end: 15, label: 'KEYWORD' }] }}
        onChange={() => {}}
      />,
    );
    expect(screen.getByText(/2 spans tagged/)).toBeInTheDocument();
  });

  it('subtracts a single word from a merged span when deselecting', async () => {
    // Two adjacent words were merged into one span [0, 17]. Clicking the first
    // word should leave just the second.
    const onChange = vi.fn();
    render(
      <SpanAnnotationRenderer
        config={config}
        payload={{ text: 'Anthropic shipped Claude.' }}
        value={{ spans: [{ start: 0, end: 17, label: 'KEYWORD' }] }}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByText('Anthropic'));
    expect(onChange).toHaveBeenLastCalledWith({
      spans: [{ start: 10, end: 17, label: 'KEYWORD' }],
    });
  });

  it('removes the entire span when subtracting the only word in it', async () => {
    const onChange = vi.fn();
    render(
      <SpanAnnotationRenderer
        config={config}
        payload={{ text: 'hi there' }}
        value={{ spans: [{ start: 0, end: 2, label: 'KEYWORD' }] }}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByText('hi'));
    expect(onChange).toHaveBeenLastCalledWith({ spans: [] });
  });
});
