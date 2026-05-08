import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MultipleChoiceRenderer } from '../sdk/renderers/MultipleChoiceRenderer';

describe('MultipleChoiceRenderer', () => {
  it('renders the prompt text and choices', () => {
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['yes', 'no', 'maybe'] }}
        payload={{ text: 'Is this a test?' }}
        value={undefined}
        onChange={() => {}}
      />,
    );
    expect(screen.getByText('Is this a test?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /\byes\b/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /\bmaybe\b/ })).toBeInTheDocument();
  });

  it('emits {choice} on single-select click', async () => {
    const onChange = vi.fn();
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['yes', 'no'] }}
        payload={{ text: 'q' }}
        value={undefined}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /\byes\b/ }));
    expect(onChange).toHaveBeenCalledWith({ choice: 'yes' });
  });

  it('emits {choices: []} for multi-select and toggles', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <MultipleChoiceRenderer
        config={{ choices: ['a', 'b', 'c'], allowMultiple: true }}
        payload={{ text: 'q' }}
        value={{ choices: [] }}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /\ba\b/ }));
    expect(onChange).toHaveBeenCalledWith({ choices: ['a'] });

    rerender(
      <MultipleChoiceRenderer
        config={{ choices: ['a', 'b', 'c'], allowMultiple: true }}
        payload={{ text: 'q' }}
        value={{ choices: ['a'] }}
        onChange={onChange}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /\ba\b/ }));
    expect(onChange).toHaveBeenLastCalledWith({ choices: [] });
  });

  it('highlights the selected choice', () => {
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['yes', 'no'] }}
        payload={{ text: 'q' }}
        value={{ choice: 'yes' }}
        onChange={() => {}}
      />,
    );
    const yes = screen.getByRole('button', { name: /\byes\b/ });
    expect(yes.className).toMatch(/bg-amber-500/);
  });

  it('triggers onAnswered after a single-select choice', async () => {
    const onChange = vi.fn();
    const onAnswered = vi.fn();
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['yes', 'no'] }}
        payload={{ text: 'q' }}
        value={undefined}
        onChange={onChange}
        onAnswered={onAnswered}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /\byes\b/ }));
    expect(onAnswered).toHaveBeenCalled();
  });

  it('does NOT trigger onAnswered for multi-select toggles', async () => {
    const onAnswered = vi.fn();
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['a', 'b'], allowMultiple: true }}
        payload={{ text: 'q' }}
        value={{ choices: [] }}
        onChange={() => {}}
        onAnswered={onAnswered}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: /\ba\b/ }));
    expect(onAnswered).not.toHaveBeenCalled();
  });

  it('responds to number-key shortcut when focused', async () => {
    const onChange = vi.fn();
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['yes', 'no'] }}
        payload={{ text: 'q' }}
        value={undefined}
        onChange={onChange}
        focused={true}
      />,
    );
    await userEvent.keyboard('1');
    expect(onChange).toHaveBeenCalledWith({ choice: 'yes' });
  });

  it('ignores number-key shortcut when not focused', async () => {
    const onChange = vi.fn();
    render(
      <MultipleChoiceRenderer
        config={{ choices: ['yes', 'no'] }}
        payload={{ text: 'q' }}
        value={undefined}
        onChange={onChange}
        focused={false}
      />,
    );
    await userEvent.keyboard('1');
    expect(onChange).not.toHaveBeenCalled();
  });
});
