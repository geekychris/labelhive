import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobBuilder } from '../pages/admin/JobBuilder';

const fetchMock = vi.fn();

function jsonResp(body: any, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function setup() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/jobs/new']}>
        <Routes>
          <Route path="/jobs/new" element={<JobBuilder />} />
          <Route path="/jobs/:id" element={<div>landed on job page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const variantSchemaResponse = {
  type: 'MULTIPLE_CHOICE',
  variantSchema: {
    fields: [
      { name: 'choices', label: 'Choice options', kind: 'string-list', required: true },
      { name: 'allowMultiple', label: 'Allow multi-select', kind: 'boolean', default: false },
    ],
  },
};

describe('JobBuilder wizard', () => {
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

  it('walks through basics → variant config and reflects schema-driven fields', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/templates') return jsonResp([{ type: 'MULTIPLE_CHOICE' }]);
      if (url === '/api/templates/MULTIPLE_CHOICE') return jsonResp(variantSchemaResponse);
      return new Response('unexpected', { status: 500 });
    });

    setup();

    await userEvent.type(screen.getByPlaceholderText('Job name'), 'My job');
    // Advance to the variant config step.
    await userEvent.click(screen.getByRole('button', { name: '2. Variant config' }));
    await waitFor(() => expect(screen.getByText('Choice options')).toBeInTheDocument());
    expect(screen.getByText('Allow multi-select')).toBeInTheDocument();
  });

  it('stages units pasted in the dataset step', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/templates') return jsonResp([{ type: 'MULTIPLE_CHOICE' }]);
      if (url === '/api/templates/MULTIPLE_CHOICE') return jsonResp(variantSchemaResponse);
      return new Response('unexpected', { status: 500 });
    });

    setup();
    await userEvent.click(screen.getByRole('button', { name: '5. Dataset' }));
    const textareas = screen.getAllByRole('textbox');
    const datasetTa = textareas[textareas.length - 1];
    await userEvent.clear(datasetTa);
    await userEvent.click(datasetTa);
    // Use paste to avoid userEvent's per-key escaping mangling the JSON.
    await userEvent.paste('[{"payload":{"text":"q1"}},{"payload":{"text":"q2"},"goldAnswer":{"choice":"yes"}}]');

    await userEvent.click(screen.getByRole('button', { name: /Stage/ }));
    await waitFor(() => expect(screen.getByText(/Staged 2 units/)).toBeInTheDocument());
    expect(screen.getByText(/1 pre-labeled gold/)).toBeInTheDocument();
  });
});
