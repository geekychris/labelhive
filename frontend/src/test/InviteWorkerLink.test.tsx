import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InviteWorkerLink } from '../components/InviteWorkerLink';
import { AuthProvider } from '../auth/AuthContext';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const fetchMock = vi.fn();

function setup({ debug, debugUsers = [], mail = false, invite }: { debug: boolean; debugUsers?: any[]; mail?: boolean; invite?: (body: any) => Response }) {
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockImplementation(async (url: string, init: any = {}) => {
    if (url === '/api/auth/me') {
      return new Response(JSON.stringify({
        authenticated: true, debug,
        id: 99, username: 'demo', role: 'ADMIN',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (url === '/api/mail/status') {
      return new Response(JSON.stringify(mail ? { enabled: true } : { enabled: false, reason: 'no mail server is set' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    if (url === '/api/jobs/7/invite' && invite) {
      return invite(JSON.parse(init.body));
    }
    if (url === '/api/auth/debug-users') {
      return new Response(JSON.stringify(debugUsers), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return new Response('unexpected', { status: 500 });
  });

  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AuthProvider>
        <InviteWorkerLink jobId={7} />
      </AuthProvider>
    </QueryClientProvider>,
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

  it('hides the email form when mail is not set up', async () => {
    setup({ debug: false });
    await screen.findByDisplayValue(/\/work\/7/);
    expect(screen.queryByLabelText('Email address')).not.toBeInTheDocument();
  });

  it('emails an invite and shows the server error when it fails', async () => {
    const bodies: any[] = [];
    let fail = false;
    setup({
      debug: false, mail: true,
      invite: body => {
        bodies.push(body);
        return fail
          ? new Response(JSON.stringify({ sent: false, error: 'too many emails sent recently; try again later' }), { status: 429 })
          : new Response(JSON.stringify({ sent: true, to: body.to }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      },
    });
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Email address'), 'sam@example.com');
    await user.type(screen.getByLabelText('Note'), 'Thanks!');
    await user.click(screen.getByRole('button', { name: 'Send invite' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Invite sent to sam@example.com.');
    expect(bodies[0]).toEqual({ to: 'sam@example.com', note: 'Thanks!' });

    fail = true;
    await user.type(screen.getByLabelText('Email address'), 'sam@example.com');
    await user.click(screen.getByRole('button', { name: 'Send invite' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('too many emails sent recently');
  });
});
