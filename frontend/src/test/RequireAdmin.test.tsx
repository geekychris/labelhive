import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { RequireAdmin } from '../auth/RequireAdmin';

const fetchMock = vi.fn();

function meResp(role: 'ADMIN' | 'WORKER') {
  return new Response(JSON.stringify({
    authenticated: true, debug: true,
    id: 1, username: role === 'ADMIN' ? 'demo' : 'alice', role,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

function setup(role: 'ADMIN' | 'WORKER') {
  fetchMock.mockImplementation(async (url: string) => {
    if (url === '/api/auth/me') return meResp(role);
    if (url === '/api/auth/debug-users') {
      return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } });
    }
    return new Response('unexpected', { status: 500 });
  });
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<RequireAdmin><div>admin secret</div></RequireAdmin>} />
          <Route path="/work" element={<div>worker home</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('RequireAdmin', () => {
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

  it('renders children for an admin user', async () => {
    setup('ADMIN');
    await waitFor(() => expect(screen.getByText('admin secret')).toBeInTheDocument());
  });

  it('redirects a worker to /work', async () => {
    setup('WORKER');
    await waitFor(() => expect(screen.getByText('worker home')).toBeInTheDocument());
    expect(screen.queryByText('admin secret')).not.toBeInTheDocument();
  });
});
