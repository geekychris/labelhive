import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { api, setDebugUserId } from '../api/client';

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
});
