import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DatasetFormatHelper } from '../components/DatasetFormatHelper';

const fetchMock = vi.fn();

function jsonResp(body: any, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderHelper(props: { datasetText: string; payloadSchema?: any }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <DatasetFormatHelper
        templateType="MULTIPLE_CHOICE"
        templateConfig={{ choices: ['yes', 'no'] }}
        datasetText={props.datasetText}
        payloadSchema={props.payloadSchema}
      />
    </QueryClientProvider>,
  );
}

describe('DatasetFormatHelper', () => {
  beforeEach(() => vi.stubGlobal('fetch', fetchMock));
  afterEach(() => { fetchMock.mockReset(); vi.unstubAllGlobals(); });

  it('renders the description and required fields', () => {
    renderHelper({
      datasetText: '[]',
      payloadSchema: {
        description: 'Pick yes or no.',
        requiredFields: ['text'],
        examplePayload: { text: 'is this a test' },
        exampleAnswer: { choice: 'yes' },
        exampleUnit: { payload: { text: 'is this a test' }, goldAnswer: { choice: 'yes' } },
      },
    });
    expect(screen.getByText('Pick yes or no.')).toBeInTheDocument();
    expect(screen.getByText('text')).toBeInTheDocument();
  });

  it('shows row-level errors when validation fails', async () => {
    fetchMock.mockResolvedValueOnce(jsonResp({
      ok: false,
      summary: { total: 2, valid: 1, invalid: 1, withGold: 1 },
      errors: [{ index: 1, error: 'invalid choice: maybe' }],
    }));

    renderHelper({
      datasetText: '[{"payload":{"text":"a"}},{"payload":{"text":"b"},"goldAnswer":{"choice":"maybe"}}]',
    });

    await userEvent.click(screen.getByRole('button', { name: /Validate/ }));
    await waitFor(() => expect(screen.getByText(/1 of 2 failed/)).toBeInTheDocument());
    expect(screen.getByText(/invalid choice: maybe/)).toBeInTheDocument();
  });

  it('shows a success summary when validation passes', async () => {
    fetchMock.mockResolvedValueOnce(jsonResp({
      ok: true,
      summary: { total: 1, valid: 1, invalid: 0, withGold: 1 },
      errors: [],
    }));

    renderHelper({ datasetText: '[{"payload":{"text":"a"},"goldAnswer":{"choice":"yes"}}]' });
    await userEvent.click(screen.getByRole('button', { name: /Validate/ }));
    await waitFor(() => expect(screen.getByText(/✓ 1 valid/)).toBeInTheDocument());
  });
});
