import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
        evidence: [{ id: 'ev-1', extractedFieldId: 'field-1', fieldName: 'eligibility', documentVersionId: 'doc-v-1', sourceUrl: 'https://finep.gov.br/docs/notice.pdf', page: 4, section: '2.1', quote: 'Trecho literal da regra de elegibilidade.', locator: { page: 4, paragraph: 2, storageKey: 'internal/private/object' } }],
      },
      {
        name: 'totalBudget', value: [{ amount: '1000000', currency: 'BRL' }, { amount: '1200000', currency: 'BRL' }], rawValue: null,
        validationStatus: 'CONFLICT' as const, createdAt: '2026-10-24T12:00:00Z',
        evidence: [
          { id: 'ev-budget-1', extractedFieldId: 'field-budget', fieldName: 'totalBudget', documentVersionId: 'doc-v-1', sourceUrl: 'javascript:alert(1)', page: 2, section: null, quote: 'Orçamento informado no edital original.', locator: { page: 2 } },
          { id: 'ev-budget-2', extractedFieldId: 'field-budget', fieldName: 'totalBudget', documentVersionId: 'doc-v-2', sourceUrl: null, page: 1, section: 'Errata', quote: null, locator: {} },
        ],
      },
      { name: 'counterpart', value: '10%', rawValue: '10%', validationStatus: 'EXTRACTED' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [
        { id: 'ev-counterpart', extractedFieldId: 'field-counterpart', fieldName: 'counterpart', documentVersionId: null, sourceUrl: 'http://127.0.0.1/private.pdf', page: null, section: null, quote: null, locator: { storageKey: 'internal/private/object' } },
        { id: 'ev-counterpart-credential', extractedFieldId: 'field-counterpart', fieldName: 'counterpart', documentVersionId: null, sourceUrl: 'https://finep.gov.br/docs?access_token=private', page: null, section: null, quote: null, locator: {} },
        { id: 'ev-counterpart-key', extractedFieldId: 'field-counterpart', fieldName: 'counterpart', documentVersionId: 'doc-v-key', sourceUrl: 'https://finep.gov.br/docs?key=private', page: null, section: null, quote: null, locator: {} },
        { id: 'ev-counterpart-reserved-ip', extractedFieldId: 'field-counterpart', fieldName: 'counterpart', documentVersionId: 'doc-v-reserved-ip', sourceUrl: 'http://192.0.0.1/resource.pdf', page: null, section: null, quote: null, locator: {} },
        { id: 'ev-counterpart-internal-host', extractedFieldId: 'field-counterpart', fieldName: 'counterpart', documentVersionId: 'doc-v-internal', sourceUrl: 'https://service.internal/resource.pdf', page: null, section: null, quote: null, locator: {} },
      ] },
      { name: 'trl', value: null, rawValue: null, validationStatus: 'MISSING' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [] },
      { name: 'requiresIct', value: true, rawValue: 'Sim', validationStatus: 'VALIDATED' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [] },
    ],
  }

  function readPage(content: unknown[], page: number, totalElements: number, totalPages: number) {
    return { content, page, size: 20, totalElements, totalPages }
  }

  function callSnapshot(versionNumber: number) {
    const suffix = String(versionNumber).padStart(12, '0')
    return {
      id: `00000000-0000-4000-8000-${suffix}`,
      versionNumber,
      contentHash: versionNumber.toString(16).padStart(2, '0').repeat(32),
      detectedAt: `2026-10-${String(versionNumber).padStart(2, '0')}T12:00:00Z`,
      supersedesVersionId: versionNumber > 1
        ? `00000000-0000-4000-8000-${String(versionNumber - 1).padStart(12, '0')}`
        : null,
    }
  }

  function documentVersion(versionNumber: number) {
    const suffix = String(versionNumber).padStart(12, '0')
    return {
      id: `10000000-0000-4000-8000-${suffix}`,
      contentHash: versionNumber.toString(16).padStart(2, '0').repeat(32),
      mediaType: 'application/pdf',
      sourceUrl: 'https://documents.example.test/file.pdf',
      downloadedAt: `2026-10-${String(versionNumber).padStart(2, '0')}T10:00:00Z`,
    }
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

  it('shows literal quotes, field/document references and locators while opening safe external links', async () => {
    fetchMock.mockResolvedValue(apiResponse(detailResponse))
    renderApp('/opportunities/call-751')

    const eligibilityCard = (await screen.findByRole('heading', { name: 'Elegibilidade' }))
      .closest<HTMLElement>('.detail-field')!
    fireEvent.click(eligibilityCard.querySelector('summary')!)
    expect(within(eligibilityCard).getByText('ev-1')).toBeInTheDocument()
    expect(within(eligibilityCard).getByText('field-1')).toBeInTheDocument()
    expect(within(eligibilityCard).getByText('doc-v-1')).toBeInTheDocument()
    expect(within(eligibilityCard).getByText('Trecho literal da regra de elegibilidade.')).toBeInTheDocument()
    expect(within(eligibilityCard).getByText(/"paragraph": 2/)).toBeInTheDocument()
    expect(screen.queryByText('internal/private/object')).not.toBeInTheDocument()
    const sourceLink = within(eligibilityCard).getByRole('link', { name: 'Abrir fonte pública (nova aba)' })
    expect(sourceLink).toHaveAttribute('href', 'https://finep.gov.br/docs/notice.pdf')
    expect(sourceLink).toHaveAttribute('target', '_blank')
    expect(sourceLink).toHaveAttribute('rel', 'noopener noreferrer nofollow')
    expect(sourceLink).toHaveAttribute('referrerPolicy', 'no-referrer')
  })

  it('shows all competing conflict evidence and never promotes a source as true', async () => {
    fetchMock.mockResolvedValue(apiResponse(detailResponse))
    renderApp('/opportunities/call-751')

    const budgetCard = (await screen.findByRole('heading', { name: 'Orçamento total' }))
      .closest<HTMLElement>('.detail-field')!
    fireEvent.click(budgetCard.querySelector('summary')!)
    expect(within(budgetCard).getByText('As evidências abaixo são concorrentes. Nenhuma foi escolhida como verdadeira.'))
      .toBeInTheDocument()
    expect(within(budgetCard).getByText('ev-budget-1')).toBeInTheDocument()
    expect(within(budgetCard).getByText('ev-budget-2')).toBeInTheDocument()
    expect(within(budgetCard).getByText('Orçamento informado no edital original.')).toBeInTheDocument()
    expect(within(budgetCard).getByText('Trecho literal indisponível.')).toBeInTheDocument()
    expect(within(budgetCard).getAllByText('Link para a fonte pública indisponível.')).toHaveLength(2)
  })

  it('explains missing evidence and refuses unsafe, local, or credential-bearing URLs', async () => {
    fetchMock.mockResolvedValue(apiResponse(detailResponse))
    renderApp('/opportunities/call-751')

    const trlCard = (await screen.findByRole('heading', { name: /TRL/ })).closest<HTMLElement>('.detail-field')!
    expect(within(trlCard).getByText('Nenhuma evidência documental associada a este campo.'))
      .toBeInTheDocument()
    const counterpartCard = screen.getByRole('heading', { name: 'Contrapartida' }).closest<HTMLElement>('.detail-field')
    const counterpartDisclosure = counterpartCard?.querySelector('summary')
    expect(counterpartDisclosure).toHaveTextContent('Evidências documentais (5)')
    fireEvent.click(counterpartDisclosure!)
    expect(within(counterpartCard!).getAllByText('Identificação da versão documental indisponível')).toHaveLength(2)
    expect(within(counterpartCard!).getAllByText('Link para a fonte pública indisponível.')).toHaveLength(5)
    expect(within(counterpartCard!).queryByRole('link', { name: 'Abrir fonte pública (nova aba)' }))
      .not.toBeInTheDocument()
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

  it('shows separate chronological call and document histories with bounded pagination', async () => {
    const callPage0 = Array.from({ length: 20 }, (_, index) => callSnapshot(index + 2))
    const documentPage0 = Array.from({ length: 20 }, (_, index) => ({
      id: `20000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
      externalId: `finep-doc-${String(index + 1).padStart(2, '0')}`,
      type: index === 0 ? 'NOTICE' as const : 'ANNEX' as const,
      title: index === 0 ? 'Edital original' : `Anexo ${String(index + 1).padStart(2, '0')}`,
      sourceUrl: 'https://documents.example.test/original.pdf',
      versionCount: index === 0 ? 21 : 0,
      latestDownloadedAt: '2026-10-20T10:00:00Z',
    }))
    let resolveDocumentVersions: ((response: Response) => void) | undefined
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname.endsWith('/documents/20000000-0000-4000-8000-000000000001/versions')) {
        if (url.searchParams.get('page') === '0') {
          return new Promise((resolve: (response: Response) => void) => { resolveDocumentVersions = resolve })
        }
        return Promise.resolve(apiResponse(readPage([documentVersion(1)], 1, 21, 2)))
      }
      if (url.pathname.endsWith('/documents')) {
        return Promise.resolve(apiResponse(url.searchParams.get('page') === '0'
          ? readPage(documentPage0, 0, 21, 2)
          : readPage([{
              id: '20000000-0000-4000-8000-000000000021', externalId: 'finep-doc-21', type: 'ANNEX',
              title: 'Anexo 21', sourceUrl: null, versionCount: 0, latestDownloadedAt: null,
            }], 1, 21, 2)))
      }
      if (url.pathname.endsWith('/versions')) {
        return Promise.resolve(apiResponse(url.searchParams.get('page') === '0'
          ? readPage(callPage0, 0, 21, 2)
          : readPage([callSnapshot(1)], 1, 21, 2)))
      }
      return Promise.resolve(apiResponse(detailResponse))
    })

    renderApp('/opportunities/call-751')
    await screen.findByRole('heading', { name: 'Campos estruturados' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Consultar histórico e documentos' }))

    const history = await screen.findByRole('region', { name: 'Histórico e documentos' })
    expect(history).toHaveTextContent('ID de origem 751; registro atual do portal 751092')
    expect(history).toHaveTextContent('não indicam, por si só, quais campos mudaram')
    const callSection = within(history).getByRole('region', { name: 'Versões da chamada (snapshots)' })
    const callItems = await within(callSection).findAllByRole('heading', { level: 4 })
    expect(callItems[0]).toHaveTextContent('Versão da chamada 21')
    expect(callItems.at(-1)).toHaveTextContent('Versão da chamada 2')
    expect(within(callSection).getByText('Ordem cronológica: mais recente primeiro.')).toBeInTheDocument()
    expect(within(callSection).getAllByText('Hash SHA-256')).toHaveLength(20)
    fireEvent.click(within(callSection).getByRole('button', { name: 'Mais antigas' }))
    expect(await within(callSection).findByRole('heading', { name: 'Versão da chamada 1' })).toBeInTheDocument()
    expect(within(callSection).getByText('Página 2 de 2')).toBeInTheDocument()

    const documentSection = within(history).getByRole('region', { name: 'Documentos e versões documentais' })
    const documentCard = (await within(documentSection).findByRole('heading', { name: 'Edital original' }))
      .closest<HTMLElement>('.history-document')!
    expect(within(documentCard).getByText('finep-doc-01')).toBeInTheDocument()
    expect(within(documentCard).getByText('21')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /fonte original/i })).not.toBeInTheDocument()
    fireEvent.click(within(documentCard).getByRole('button', { name: 'Ver versões documentais' }))
    expect(await within(documentCard).findByRole('status')).toHaveTextContent('Carregando versões documentais')

    const documentVersions0 = Array.from({ length: 20 }, (_, index) => documentVersion(index + 2))
    await act(async () => resolveDocumentVersions?.(apiResponse(readPage(documentVersions0, 0, 21, 2))))
    const versionList = await within(documentCard).findByRole('list', { name: 'Versões do documento Edital original' })
    const documentVersionItems = within(versionList).getAllByRole('listitem')
    expect(documentVersionItems[0]).toHaveTextContent('10000000-0000-4000-8000-000000000021')
    expect(documentVersionItems.at(-1)).toHaveTextContent('10000000-0000-4000-8000-000000000002')
    const versionSection = documentCard.querySelector<HTMLElement>('.history-document__versions')!
    fireEvent.click(within(versionSection).getByRole('button', { name: 'Mais antigas' }))
    expect(await within(versionSection).findByText('10000000-0000-4000-8000-000000000001')).toBeInTheDocument()

    const documentsPagination = within(documentSection)
      .getByRole('navigation', { name: 'Paginação dos documentos da oportunidade' })
    fireEvent.click(within(documentsPagination).getByRole('button', { name: 'Mais antigas' }))
    expect(await within(documentSection).findByRole('heading', { name: 'Anexo 21' })).toBeInTheDocument()
  })

  it('shows independent loading, errors, retries, and empty states for call and document histories', async () => {
    const emptyPage = readPage([], 0, 0, 0)
    const attempts = new Map<string, number>()
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      const kind = url.pathname.endsWith('/versions') ? 'call' : url.pathname.endsWith('/documents') ? 'documents' : 'detail'
      if (kind === 'detail') return Promise.resolve(apiResponse(detailResponse))
      const attempt = (attempts.get(kind) ?? 0) + 1
      attempts.set(kind, attempt)
      if (attempt === 1) return Promise.resolve(apiResponse({ message: 'internal history error' }, false))
      return Promise.resolve(apiResponse(emptyPage))
    })

    renderApp('/opportunities/call-751')
    await screen.findByRole('heading', { name: 'Campos estruturados' })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar histórico e documentos' }))

    const history = await screen.findByRole('region', { name: 'Histórico e documentos' })
    const callSection = within(history).getByRole('region', { name: 'Versões da chamada (snapshots)' })
    const documentSection = within(history).getByRole('region', { name: 'Documentos e versões documentais' })
    expect(await within(callSection).findByRole('alert')).toHaveTextContent('Não foi possível carregar o histórico')
    expect(await within(documentSection).findByRole('alert')).toHaveTextContent('Não foi possível carregar os documentos')
    expect(screen.queryByText('internal history error')).not.toBeInTheDocument()

    fireEvent.click(within(callSection).getByRole('button', { name: 'Tentar novamente' }))
    fireEvent.click(within(documentSection).getByRole('button', { name: 'Tentar novamente' }))
    expect(await within(callSection).findByRole('status')).toHaveTextContent('Nenhuma versão da chamada registrada')
    expect(await within(documentSection).findByRole('status')).toHaveTextContent('Nenhum documento registrado')
  })

  it('retries a failed document-version request and reports an empty version history', async () => {
    const documentId = '20000000-0000-4000-8000-000000000001'
    let documentVersionAttempts = 0
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname.endsWith(`/documents/${documentId}/versions`)) {
        documentVersionAttempts += 1
        return Promise.resolve(documentVersionAttempts === 1
          ? apiResponse({ message: 'private document history error' }, false)
          : apiResponse(readPage([], 0, 0, 0)))
      }
      if (url.pathname.endsWith('/documents')) {
        return Promise.resolve(apiResponse(readPage([{
          id: documentId, externalId: 'finep-doc-01', type: 'NOTICE', title: 'Edital original',
          sourceUrl: null, versionCount: 1, latestDownloadedAt: '2026-10-20T10:00:00Z',
        }], 0, 1, 1)))
      }
      if (url.pathname.endsWith('/versions')) return Promise.resolve(apiResponse(readPage([], 0, 0, 0)))
      return Promise.resolve(apiResponse(detailResponse))
    })

    renderApp('/opportunities/call-751')
    await screen.findByRole('heading', { name: 'Campos estruturados' })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar histórico e documentos' }))
    const documentCard = (await screen.findByRole('heading', { name: 'Edital original' }))
      .closest<HTMLElement>('.history-document')!
    fireEvent.click(within(documentCard).getByRole('button', { name: 'Ver versões documentais' }))
    expect(await within(documentCard).findByRole('alert'))
      .toHaveTextContent('Não foi possível carregar as versões deste documento')
    expect(screen.queryByText('private document history error')).not.toBeInTheDocument()
    fireEvent.click(within(documentCard).getByRole('button', { name: 'Tentar novamente' }))
    expect(await within(documentCard).findByRole('status')).toHaveTextContent('Nenhuma versão documental encontrada')
  })
})
