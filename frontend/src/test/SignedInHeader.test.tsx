import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from '../App';
import { AuthProvider } from '../auth/AuthContext';

const fetchMock = vi.fn();

function json(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function setup(me: Record<string, unknown>) {
  fetchMock.mockImplementation(async (url: string) => {
    if (url === '/api/auth/me') return json(me);
    if (url === '/api/auth/debug-users') return json([]);
    return json([]);
  });
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/work']}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('signed-in header', () => {
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

  it('shows the OIDC user, their roles and a Sign out button', async () => {
    setup({ authenticated: true, debug: false, id: 3, username: 'alice', role: 'WORKER', roles: ['labeler'] });
    expect(await screen.findByText('alice')).toBeInTheDocument();
    expect(screen.getByText('labeler')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
  });

  it('has no Sign out button in the debug profile', async () => {
    setup({ authenticated: true, debug: true, id: 1, username: 'demo', role: 'ADMIN', roles: ['admin'] });
    expect(await screen.findByText('demo')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign out' })).toBeNull();
  });
});
