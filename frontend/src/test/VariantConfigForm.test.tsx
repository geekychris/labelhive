import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VariantConfigForm } from '../components/VariantConfigForm';

describe('VariantConfigForm', () => {
  it('renders string-list, boolean, number, and string fields', () => {
    render(
      <VariantConfigForm
        fields={[
          { name: 'choices', label: 'Choices', kind: 'string-list', required: true },
          { name: 'allowMultiple', label: 'Allow multi', kind: 'boolean', default: false },
          { name: 'minLength', label: 'Min length', kind: 'number', default: 0 },
          { name: 'queryField', label: 'Query field', kind: 'string', default: 'queryId' },
        ]}
        value={{ choices: ['a', 'b'] }}
        onChange={() => {}}
      />,
    );
    expect(screen.getByText('Choices')).toBeInTheDocument();
    expect(screen.getByText('a')).toBeInTheDocument();
    expect(screen.getByText('b')).toBeInTheDocument();
    expect(screen.getByLabelText(/Allow multi/)).toBeInTheDocument();
    expect(screen.getByDisplayValue('queryId')).toBeInTheDocument();
  });

  it('emits an updated string-list when adding a chip', async () => {
    const onChange = vi.fn();
    render(
      <VariantConfigForm
        fields={[{ name: 'choices', label: 'Choices', kind: 'string-list' }]}
        value={{ choices: ['yes'] }}
        onChange={onChange}
      />,
    );
    const input = screen.getByPlaceholderText(/Type and press Enter/);
    await userEvent.type(input, 'no{Enter}');
    expect(onChange).toHaveBeenLastCalledWith({ choices: ['yes', 'no'] });
  });

  it('emits an updated scale when editing a row', async () => {
    const onChange = vi.fn();
    render(
      <VariantConfigForm
        fields={[{
          name: 'scale', label: 'Scale', kind: 'scale',
          default: [{ label: 'a', grade: 0 }, { label: 'b', grade: 1 }],
        }]}
        value={{ scale: [{ label: 'a', grade: 0 }, { label: 'b', grade: 1 }] }}
        onChange={onChange}
      />,
    );
    const inputs = screen.getAllByDisplayValue('a');
    await userEvent.clear(inputs[0]);
    await userEvent.type(inputs[0], 'irrelevant');
    // userEvent fires onChange per keystroke; the final emitted value should
    // contain the typed character.
    const last = onChange.mock.calls[onChange.mock.calls.length - 1][0];
    expect(last.scale[0].label).toContain('t'); // last char typed
  });

  it('falls back to a JSON editor for unknown kinds', () => {
    render(
      <VariantConfigForm
        fields={[{ name: 'mystery', label: 'Mystery', kind: 'something-novel' }]}
        value={{ mystery: { a: 1 } }}
        onChange={() => {}}
      />,
    );
    expect(screen.getByText('Mystery')).toBeInTheDocument();
    // The unknown kind falls through to a JSON textarea bound to the field's value.
    const ta = screen.getByRole('textbox');
    expect(ta).toHaveValue('{\n  "a": 1\n}');
  });
});
