import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { OpportunityPage } from './api/opportunities'

function opportunityPage(overrides: Partial<OpportunityPage> = {}): OpportunityPage {
  return {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    ...overrides,
  }
}

function apiResponse(body: unknown, ok = true, status = ok ? 200 : 500): Response {
  return {
    ok,
    status,
    json: async () => body,
  } as Response
}

function renderApp(initialPath = '/') {
  window.history.replaceState({}, '', initialPath)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  )
}

let fetchMock: ReturnType<typeof vi.fn>

function lastRequestParams(): URLSearchParams {
  const requestUrl = fetchMock.mock.calls.at(-1)?.[0] as string
  return new URL(requestUrl, 'http://localhost').searchParams
}

beforeEach(() => {
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('opportunity catalog', () => {
  it('loads a page from the read-only opportunities endpoint', async () => {
    let resolveRequest: ((response: Response) => void) | undefined
    fetchMock.mockImplementation(() => new Promise((resolve: (response: Response) => void) => {
      resolveRequest = resolve
    }))

    renderApp()

    expect(screen.getByRole('status')).toHaveTextContent('Carregando oportunidades')
    expect(fetchMock).toHaveBeenCalledWith('/api/opportunities?page=0&size=20', {
      signal: expect.any(AbortSignal),
    })

    await act(async () => {
      resolveRequest?.(apiResponse(opportunityPage()))
    })
  })

  it('shows opportunity title, agency, status, instrument, and available deadline', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage({
      content: [
        {
          id: 'a1',
          externalId: '751',
          portalEntryId: '751092',
          title: 'PROINFRA 2024 Expansão',
          agency: { name: 'Financiadora de Estudos e Projetos', acronym: 'Finep' },
          program: 'Infraestrutura de pesquisa',
          status: 'OPEN',
          deadline: '2026-12-15',
          instrumentType: 'Subvenção econômica',
        },
        {
          id: 'a2',
          externalId: '752',
          portalEntryId: null,
          title: 'Chamada para inovação regional',
          agency: { name: 'Agência de Fomento', acronym: 'AF' },
          program: null,
          status: 'UNKNOWN',
          deadline: null,
          instrumentType: null,
        },
      ],
      totalElements: 2,
      totalPages: 1,
    })))

    renderApp()

    expect(await screen.findByRole('heading', { name: 'PROINFRA 2024 Expansão' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Oportunidades encontradas' })).toBeInTheDocument()
    expect(screen.getByText('Financiadora de Estudos e Projetos')).toBeInTheDocument()
    expect(screen.getByText('Aberta', { selector: 'span.status-badge' })).toBeInTheDocument()
    expect(screen.getByText('Subvenção econômica')).toBeInTheDocument()
    expect(screen.getByText('15 de dezembro de 2026')).toBeInTheDocument()
    expect(screen.getByText('Prazo não informado')).toBeInTheDocument()
    expect(screen.getAllByText('Instrumento', { selector: '.opportunity-card__details dt' }))
      .toHaveLength(2)
    expect(screen.getByText('Não informado')).toBeInTheDocument()
  })

  it('shows an empty state when the API returns no opportunities', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage()))

    renderApp()

    expect(await screen.findByRole('heading', { name: 'Nenhuma oportunidade encontrada' }))
      .toBeInTheDocument()
  })

  it('shows a safe error message and lets the user retry', async () => {
    fetchMock
      .mockResolvedValueOnce(apiResponse({ message: 'internal server detail' }, false))
      .mockResolvedValueOnce(apiResponse(opportunityPage({
        content: [{
          id: 'a1',
          externalId: '751',
          portalEntryId: null,
          title: 'PROINFRA 2024 Expansão',
          agency: { name: 'Finep', acronym: 'Finep' },
          program: null,
          status: 'OPEN',
          deadline: null,
          instrumentType: null,
        }],
        totalElements: 1,
        totalPages: 1,
      })))

    renderApp()

    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar as oportunidades')
    expect(screen.queryByText('internal server detail')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByRole('heading', { name: 'PROINFRA 2024 Expansão' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('retains API page navigation alongside the search and filters', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage({
      content: [{
        id: 'a1',
        externalId: '751',
        portalEntryId: null,
        title: 'Primeira oportunidade',
        agency: { name: 'Finep', acronym: 'Finep' },
        program: null,
        status: 'OPEN',
        deadline: null,
        instrumentType: null,
      }],
      totalElements: 21,
      totalPages: 2,
    })))

    renderApp()
    await screen.findByRole('heading', { name: 'Primeira oportunidade' })
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }))

    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/opportunities?page=1&size=20',
      { signal: expect.any(AbortSignal) },
    ))
    expect(screen.getByRole('searchbox', { name: 'Busca textual' })).toBeInTheDocument()
  })

  it('applies only supported filters, shows active criteria, and clears them', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage()))

    renderApp()
    await screen.findByRole('heading', { name: 'Nenhuma oportunidade encontrada' })

    fireEvent.change(screen.getByRole('searchbox', { name: 'Busca textual' }), {
      target: { value: '  energia solar  ' },
    })
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'OPEN' } })
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-01-01' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-12-31' } })
    fireEvent.change(screen.getByLabelText('Instrumento'), {
      target: { value: 'subvenção econômica' },
    })
    fireEvent.change(screen.getByLabelText('Região'), { target: { value: 'Nordeste' } })
    fireEvent.change(screen.getByLabelText('Tema'), { target: { value: 'biotecnologia' } })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(screen.getByText(/Há alterações não aplicadas/, { selector: '.filter-form__hint' }))
      .toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(Object.fromEntries(lastRequestParams())).toEqual({
      page: '0',
      size: '20',
      q: 'energia solar',
      status: 'OPEN',
      deadlineFrom: '2026-01-01',
      deadlineTo: '2026-12-31',
      instrument: 'subvenção econômica',
      region: 'Nordeste',
      theme: 'biotecnologia',
    })
    expect(screen.getByRole('list', { name: 'Filtros aplicados' })).toHaveTextContent('Busca: energia solar')
    expect(screen.getByRole('list', { name: 'Filtros aplicados' })).toHaveTextContent('Status: Aberta')
    expect(screen.getByRole('list', { name: 'Filtros aplicados' })).toHaveTextContent('Prazo: 01/01/2026 a 31/12/2026')
    expect(await screen.findByText('Nenhuma oportunidade corresponde aos filtros aplicados.'))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aplicar filtros' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }))
    expect(screen.getByText('Nenhum filtro aplicado aos resultados.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('resets to the first page when an applied filter changes', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage({
      content: [{
        id: 'a1',
        externalId: '751',
        portalEntryId: null,
        title: 'Oportunidade paginada',
        agency: { name: 'Finep', acronym: 'Finep' },
        program: null,
        status: 'OPEN',
        deadline: null,
        instrumentType: null,
      }],
      totalElements: 40,
      totalPages: 2,
    })))

    renderApp()
    await screen.findByRole('heading', { name: 'Oportunidade paginada' })
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    await waitFor(() => expect(lastRequestParams().get('page')).toBe('1'))
    await screen.findByText('Página 2 de 2')

    fireEvent.change(screen.getByRole('searchbox', { name: 'Busca textual' }), {
      target: { value: 'energia' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      expect(lastRequestParams().get('page')).toBe('0')
      expect(lastRequestParams().get('q')).toBe('energia')
    })
    expect(await screen.findByText('Página 1 de 2')).toBeInTheDocument()
  })

  it('rejects an inverted deadline range without making a request', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage()))

    renderApp()
    await screen.findByRole('heading', { name: 'Nenhuma oportunidade encontrada' })
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-12-31' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-01-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(screen.getByRole('alert')).toHaveTextContent('A data inicial do prazo deve ser anterior')
    expect(screen.getByLabelText('De')).toHaveAttribute('aria-invalid', 'true')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('opportunity detail', () => {
  const detailResponse = {
    opportunity: {
      id: 'call-751',
      externalId: '751',
      portalEntryId: '751092',
      title: 'PROINFRA 2024 Expansão',
      agency: { name: 'Financiadora de Estudos e Projetos', acronym: 'Finep' },
      program: 'Infraestrutura de pesquisa',
      status: 'OPEN' as const,
      deadline: '2026-12-15',
      instrumentType: 'Subvenção econômica',
    },
    summary: 'Apoio à infraestrutura de pesquisa.',
    officialUrl: 'https://example.invalid/call',
    currentCallVersion: 2,
    fields: [
      {
        name: 'eligibility', value: { organizations: ['ICT'] }, rawValue: 'ICTs elegíveis',
        validationStatus: 'VALIDATED' as const, createdAt: '2026-10-24T12:00:00Z',
        evidence: [{ id: 'ev-1', extractedFieldId: 'field-1', fieldName: 'eligibility', documentVersionId: 'doc-v-1', sourceUrl: null, page: 4, section: '2.1', quote: 'sensitive quote omitted', locator: {} }],
      },
      { name: 'totalBudget', value: { amount: '1000000', currency: 'BRL' }, rawValue: null, validationStatus: 'CONFLICT' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [] },
      { name: 'counterpart', value: '10%', rawValue: '10%', validationStatus: 'EXTRACTED' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [] },
      { name: 'trl', value: null, rawValue: null, validationStatus: 'MISSING' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [] },
      { name: 'requiresIct', value: true, rawValue: 'Sim', validationStatus: 'VALIDATED' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [] },
    ],
  }

  it('navigates from a catalog opportunity and consumes only the existing detail endpoint', async () => {
    fetchMock
      .mockResolvedValueOnce(apiResponse(opportunityPage({
        content: [{ ...detailResponse.opportunity, program: 'Infraestrutura de pesquisa' }],
        totalElements: 1,
        totalPages: 1,
      })))
      .mockResolvedValueOnce(apiResponse(detailResponse))

    renderApp()
    fireEvent.click(await screen.findByRole('link', { name: 'PROINFRA 2024 Expansão' }))

    expect(await screen.findByRole('heading', { name: 'Campos estruturados' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/opportunities/call-751', {
      signal: expect.any(AbortSignal),
    })
    expect(screen.getByText('751', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('751092', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('15 de dezembro de 2026')).toBeInTheDocument()
    expect(screen.queryByText('sensitive quote omitted')).not.toBeInTheDocument()
    expect(screen.queryByText('https://example.invalid/call')).not.toBeInTheDocument()
  })

  it('communicates all validation states without presenting unvalidated data as confirmed', async () => {
    fetchMock.mockResolvedValue(apiResponse(detailResponse))
    renderApp('/opportunities/call-751')

    expect(await screen.findByText('Validado', { selector: '.validation-badge' })).toBeInTheDocument()
    expect(screen.getByText('Divergência entre fontes')).toBeInTheDocument()
    expect(screen.getByText('Extraído, ainda não validado')).toBeInTheDocument()
    expect(screen.getByText('Não informado', { selector: '.validation-badge' })).toBeInTheDocument()
    expect(screen.getByText(/nenhum foi escolhido como definitivo/i)).toBeInTheDocument()
    expect(screen.getByText(/ainda não foi validado/i)).toBeInTheDocument()
    expect(screen.getByText(/Informação não encontrada/)).toBeInTheDocument()
    expect(screen.getByText(/A API informa o estado VALIDATED, mas não retornou evidência/)).toBeInTheDocument()
  })

  it('preserves compact evidence references for critical fields without exposing quote or source URL', async () => {
    fetchMock.mockResolvedValue(apiResponse(detailResponse))
    renderApp('/opportunities/call-751')

    expect(await screen.findByText('1 evidência associada')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Referências de evidência'))
    expect(screen.getByText('ev-1')).toBeInTheDocument()
    expect(screen.getByText('doc-v-1')).toBeInTheDocument()
    expect(screen.getByText('Página 4')).toBeInTheDocument()
    expect(screen.getByText('Seção: 2.1')).toBeInTheDocument()
    expect(screen.queryByText('sensitive quote omitted')).not.toBeInTheDocument()
  })

  it('shows loading, safe retryable errors, and a distinct not-found state', async () => {
    let resolveRequest: ((response: Response) => void) | undefined
    fetchMock.mockImplementationOnce(() => new Promise((resolve: (response: Response) => void) => {
      resolveRequest = resolve
    }))
    renderApp('/opportunities/call-751')
    expect(screen.getByRole('status')).toHaveTextContent('Carregando oportunidade')
    await act(async () => resolveRequest?.(apiResponse({ message: 'private server detail' }, false)))
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível carregar esta oportunidade')
    expect(screen.queryByText('private server detail')).not.toBeInTheDocument()

    fetchMock.mockResolvedValueOnce(apiResponse({}, false, 404))
    fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByRole('heading', { name: 'Oportunidade não encontrada' })).toBeInTheDocument()
  })
})
