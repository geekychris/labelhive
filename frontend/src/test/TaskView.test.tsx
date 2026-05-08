import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TaskView } from '../pages/worker/TaskView';

const fetchMock = vi.fn();

function jsonResp(body: any, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderTask() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/work/7']}>
        <Routes>
          <Route path="/work/:id" element={<TaskView />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('TaskView (paged)', () => {
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

  it('renders an empty page with the COME_BACK_LATER copy and a refresh button', async () => {
    fetchMock.mockResolvedValueOnce(jsonResp({
      tasks: [],
      code: 'COME_BACK_LATER',
      message: "You're caught up. Check back later — more work may show up.",
    }));

    renderTask();
    await waitFor(() => expect(screen.queryByText(/Loading/)).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: /You're caught up/ })).toBeInTheDocument();
    expect(screen.getByText(/Check back later — more work/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Check again/ })).toBeInTheDocument();
  });

  it('renders an empty page with the COMPLETE copy and no refresh button', async () => {
    fetchMock.mockResolvedValueOnce(jsonResp({
      tasks: [], code: 'COMPLETE', message: 'All done — thanks for your work.',
    }));

    renderTask();
    await waitFor(() => expect(screen.queryByText(/Loading/)).not.toBeInTheDocument());
    expect(screen.getByRole('heading', { name: /All done/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Check again/ })).not.toBeInTheDocument();
  });

  it('renders multiple tasks per page and gates submit until all are answered', async () => {
    fetchMock.mockResolvedValueOnce(jsonResp({
      tasks: [
        { id: 1, jobId: 7, templateType: 'MULTIPLE_CHOICE', templateConfig: { choices: ['yes', 'no'] }, payload: { text: 'q1' } },
        { id: 2, jobId: 7, templateType: 'MULTIPLE_CHOICE', templateConfig: { choices: ['yes', 'no'] }, payload: { text: 'q2' } },
      ],
    }));

    renderTask();
    await waitFor(() => expect(screen.getByText('q1')).toBeInTheDocument());
    expect(screen.getByText('q2')).toBeInTheDocument();
    expect(screen.getByText(/0 \/ 2 answered/)).toBeInTheDocument();
    const submit = screen.getByRole('button', { name: /Submit & next page/ });
    expect(submit).toBeDisabled();

    // Answer one — still gated.
    const allYesButtons = screen.getAllByRole('button', { name: /\byes\b/ });
    await userEvent.click(allYesButtons[0]);
    expect(screen.getByText(/1 \/ 2 answered/)).toBeInTheDocument();
    expect(submit).toBeDisabled();

    // Answer the other — submit unlocks.
    await userEvent.click(allYesButtons[1]);
    expect(screen.getByText(/2 \/ 2 answered/)).toBeInTheDocument();
    expect(submit).not.toBeDisabled();
  });
});
