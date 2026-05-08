import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Monitor } from '../pages/admin/Monitor';

const fetchMock = vi.fn();

function renderMonitor() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/jobs/7/monitor']}>
        <Routes>
          <Route path="/jobs/:id/monitor" element={<Monitor />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('Monitor page', () => {
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

  it('renders completeness, totals, and the worker leaderboard', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({
        jobId: 7,
        totalUnits: 20,
        goldUnits: 5,
        completedUnits: 8,
        openNonGoldUnits: 7,
        judgmentsCollected: 24,
        judgmentsNeeded: 45,
        percentComplete: 0.533,
        workers: [
          { userId: 11, judgmentsTotal: 14, goldAttempts: 5, goldCorrect: 5,
            accuracy: '1.0000', status: 'OK', meanTimeMs: 4500, throughputPerHour: 800.0 },
          { userId: 22, judgmentsTotal: 4,  goldAttempts: 5, goldCorrect: 1,
            accuracy: '0.2000', status: 'QUARANTINED', meanTimeMs: 12000, throughputPerHour: 300.0 },
        ],
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );

    renderMonitor();

    await waitFor(() => expect(screen.queryByText(/Loading/)).not.toBeInTheDocument());
    expect(screen.getByText(/24 \/ 45 judgments collected/)).toBeInTheDocument();
    expect(screen.getByText(/53%/)).toBeInTheDocument();
    expect(screen.getByText('QUARANTINED')).toBeInTheDocument();
    expect(screen.getByText('14')).toBeInTheDocument(); // judgments count for worker 11
    expect(screen.getByText(/5\/5 \(1\.00\)/)).toBeInTheDocument();
  });

  it('handles a job with no worker activity', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({
        jobId: 7, totalUnits: 0, goldUnits: 0, completedUnits: 0, openNonGoldUnits: 0,
        judgmentsCollected: 0, judgmentsNeeded: 0, percentComplete: 1.0, workers: [],
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );

    renderMonitor();
    await waitFor(() => expect(screen.queryByText(/Loading/)).not.toBeInTheDocument());
    expect(screen.getByText(/No worker activity yet/)).toBeInTheDocument();
  });
});
