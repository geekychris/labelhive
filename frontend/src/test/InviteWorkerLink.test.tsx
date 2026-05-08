import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InviteWorkerLink } from '../components/InviteWorkerLink';
import { AuthProvider } from '../auth/AuthContext';

const fetchMock = vi.fn();

function setup({ debug, debugUsers = [] }: { debug: boolean; debugUsers?: any[] }) {
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockImplementation(async (url: string) => {
    if (url === '/api/auth/me') {
      return new Response(JSON.stringify({
        authenticated: true, debug,
        id: 99, username: 'demo', role: 'ADMIN',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (url === '/api/auth/debug-users') {
      return new Response(JSON.stringify(debugUsers), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return new Response('unexpected', { status: 500 });
  });

  return render(
    <AuthProvider>
      <InviteWorkerLink jobId={7} />
    </AuthProvider>,
  );
}

describe('InviteWorkerLink', () => {
  it('renders a plain /work/:jobId link in non-debug mode', async () => {
    setup({ debug: false });
    const input = await screen.findByDisplayValue(/\/work\/7/);
    expect(input).toHaveValue('http://localhost:3000/work/7');
    // Username field is debug-only.
    expect(screen.queryByPlaceholderText('alice')).not.toBeInTheDocument();
  });

  it('appends ?as=<username> in debug mode when a username is set', async () => {
    setup({ debug: true, debugUsers: [
      { id: 2, username: 'alice', role: 'WORKER' },
      { id: 3, username: 'bob',   role: 'WORKER' },
    ]});
    const input = await screen.findByPlaceholderText('alice');
    await userEvent.type(input, 'alice');
    const linkInput = screen.getByDisplayValue(/\/work\/7\?as=alice/);
    expect(linkInput).toHaveValue('http://localhost:3000/work/7?as=alice');
  });

  it('quick-pick buttons set the username', async () => {
    setup({ debug: true, debugUsers: [
      { id: 2, username: 'alice', role: 'WORKER' },
    ]});
    await screen.findByPlaceholderText('alice');
    const pickAlice = screen.getByRole('button', { name: 'alice' });
    await userEvent.click(pickAlice);
    expect(screen.getByDisplayValue(/\/work\/7\?as=alice/)).toBeInTheDocument();
  });
});
