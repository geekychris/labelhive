import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchRelevanceRenderer } from '../sdk/renderers/SearchRelevanceRenderer';

const config = {
  scale: [
    { label: 'irrelevant', grade: 0 },
    { label: 'relevant', grade: 2 },
    { label: 'perfect', grade: 3 },
  ],
};

const payload = {
  query: 'best vector database',
  result: { title: 'pgvector 1.0', url: 'https://x.test/p', snippet: 'native postgres vector search' },
};

describe('SearchRelevanceRenderer', () => {
  it('renders query, result, and the scale buttons', () => {
    render(<SearchRelevanceRenderer config={config} payload={payload} value={undefined} onChange={() => {}} />);
    expect(screen.getByText('best vector database')).toBeInTheDocument();
    expect(screen.getByText('pgvector 1.0')).toBeInTheDocument();
    expect(screen.getByText('native postgres vector search')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /irrelevant/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /perfect/ })).toBeInTheDocument();
  });

  it('emits {grade} on click', async () => {
    const onChange = vi.fn();
    render(<SearchRelevanceRenderer config={config} payload={payload} value={undefined} onChange={onChange} />);
    await userEvent.click(screen.getByRole('button', { name: /perfect/ }));
    expect(onChange).toHaveBeenCalledWith({ grade: 3 });
  });

  it('marks the active grade', () => {
    render(<SearchRelevanceRenderer config={config} payload={payload} value={{ grade: 2 }} onChange={() => {}} />);
    const relevant = screen.getByRole('button', { name: /relevant \(2\)/ });
    expect(relevant.className).toMatch(/bg-amber-500/);
  });
});
