import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CloneJobModal } from '../components/CloneJobModal';

describe('CloneJobModal', () => {
  it('renders nothing when closed', () => {
    const { container } = render(<CloneJobModal open={false} onCancel={() => {}} onConfirm={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows three options and confirms with the chosen mode', async () => {
    const onConfirm = vi.fn();
    render(<CloneJobModal open onCancel={() => {}} onConfirm={onConfirm} />);
    expect(screen.getByText('Just settings')).toBeInTheDocument();
    expect(screen.getByText('Settings + units (no gold)')).toBeInTheDocument();
    expect(screen.getByText('Everything')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Everything'));
    await userEvent.click(screen.getByRole('button', { name: 'Clone' }));
    expect(onConfirm).toHaveBeenCalledWith('with-gold');
  });

  it('cancel calls onCancel', async () => {
    const onCancel = vi.fn();
    render(<CloneJobModal open onCancel={onCancel} onConfirm={() => {}} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onCancel).toHaveBeenCalled();
  });
});
