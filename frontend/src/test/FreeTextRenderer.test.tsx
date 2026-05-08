import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FreeTextRenderer } from '../sdk/renderers/FreeTextRenderer';

describe('FreeTextRenderer', () => {
  it('renders the prompt and emits {text} as the user types', async () => {
    const onChange = vi.fn();
    render(
      <FreeTextRenderer
        config={{ minLength: 1, maxLength: 100 }}
        payload={{ text: 'Summarize:' }}
        value={undefined}
        onChange={onChange}
      />,
    );
    expect(screen.getByText('Summarize:')).toBeInTheDocument();
    const ta = screen.getByRole('textbox');
    await userEvent.type(ta, 'hi');
    // userEvent fires onChange per keystroke; last call should have the latest text.
    expect(onChange).toHaveBeenLastCalledWith({ text: 'i' });
  });

  it('reflects an existing value', () => {
    render(
      <FreeTextRenderer
        config={{}}
        payload={{ text: 'q' }}
        value={{ text: 'previous' }}
        onChange={() => {}}
      />,
    );
    expect(screen.getByRole('textbox')).toHaveValue('previous');
  });
});
