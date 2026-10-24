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

function apiResponse(body: unknown, ok = true): Response {
  return {
    ok,
    json: async () => body,
  } as Response
}

function renderApp() {
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
