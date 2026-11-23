import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { api, nav, resetLoginGuard, setDebugUserId, LOGIN_URL } from '../api/client';

describe('api client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    setDebugUserId(null);
  });
  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  function jsonResponse(body: any, status = 200) {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  it('sends X-User-Id header when debug user is set', async () => {
    setDebugUserId('alice');
    fetchMock.mockResolvedValueOnce(jsonResponse({ authenticated: true, debug: true }));

    await api.me();

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers['X-User-Id']).toBe('alice');
  });

  it('omits X-User-Id when no debug user', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ authenticated: false, debug: false }));
    await api.me();
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers['X-User-Id']).toBeUndefined();
  });

  it('returns undefined for 204 No Content', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    const result = await api.nextTask(1);
    expect(result).toBeUndefined();
  });

  it('throws an error message on non-2xx', async () => {
    fetchMock.mockResolvedValueOnce(new Response('boom', { status: 400 }));
    await expect(api.listJobs()).rejects.toThrow(/400/);
  });

  it('serializes JSON body for POST', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ ok: true, judgmentId: 1 }));
    await api.submit(7, 42, { choice: 'yes' }, 1234);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/jobs/7/tasks/42/judgment');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ answer: { choice: 'yes' }, timeMs: 1234 });
  });

  describe('sign-in', () => {
    beforeEach(() => { resetLoginGuard(); document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'; });

    it('sends the browser to the OIDC sign-in on a 401', async () => {
      const assign = vi.spyOn(nav, 'assign').mockImplementation(() => {});
      fetchMock.mockResolvedValueOnce(new Response('', { status: 401 }));
      await expect(api.listJobs()).rejects.toThrow(/401/);
      expect(LOGIN_URL).toBe('/oauth2/authorization/cloudstack');
      expect(assign).toHaveBeenCalledWith('/oauth2/authorization/cloudstack');
      assign.mockRestore();
    });

    it('does not loop: a second 401 right away does not redirect again', async () => {
      const assign = vi.spyOn(nav, 'assign').mockImplementation(() => {});
      fetchMock.mockImplementation(async () => new Response('', { status: 401 }));
      await expect(api.me()).rejects.toThrow(/401/);
      await expect(api.me()).rejects.toThrow(/401/);
      expect(assign).toHaveBeenCalledTimes(1);
      assign.mockRestore();
    });

    it('sends the XSRF cookie back as X-XSRF-TOKEN on writes only', async () => {
      document.cookie = 'XSRF-TOKEN=abc123';
      fetchMock.mockImplementation(async () => jsonResponse({ ok: true, judgmentId: 1 }));
      await api.submit(1, 2, {}, 3);
      expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('abc123');
      await api.listJobs();
      expect(fetchMock.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBeUndefined();
    });
  });
});
