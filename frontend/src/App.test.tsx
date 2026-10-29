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

  it('applies publisher and responsible-agency filters together', async () => {
    fetchMock.mockResolvedValue(apiResponse(opportunityPage()))
    renderApp()
    await screen.findByRole('heading', { name: 'Nenhuma oportunidade encontrada' })

    fireEvent.change(screen.getByLabelText('Fonte publicadora'), { target: { value: 'CNPQ' } })
    fireEvent.change(screen.getByLabelText('Agência responsável'), { target: { value: 'CAPES' } })
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      expect(lastRequestParams().get('source')).toBe('CNPQ')
      expect(lastRequestParams().get('agency')).toBe('CAPES')
    })
    expect(screen.getByText('Fonte: CNPQ')).toBeInTheDocument()
    expect(screen.getByText('Agência: CAPES')).toBeInTheDocument()
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

    expect(await screen.findByRole('heading', { name: /Condições de participação/ })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/opportunities/call-751', {
      signal: expect.any(AbortSignal),
    })
    expect(screen.getByText('751', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('751092', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('15 de dezembro de 2026')).toBeInTheDocument()
    expect(screen.queryByText('sensitive quote omitted')).not.toBeInTheDocument()
    expect(screen.queryByText('https://example.invalid/call')).not.toBeInTheDocument()
  })

  it('opens the official opportunity source safely when the existing detail DTO provides a public URL', async () => {
    fetchMock.mockResolvedValue(apiResponse({
      ...detailResponse,
      officialUrl: 'https://finep.gov.br/editais/proinfra-2024?origem=portal',
    }))
    renderApp('/opportunities/call-751')

    const sourceLink = await screen.findByRole('link', { name: 'Abrir fonte oficial (nova aba)' })
    expect(sourceLink).toHaveAttribute('href', 'https://finep.gov.br/editais/proinfra-2024?origem=portal')
    expect(sourceLink).toHaveAttribute('target', '_blank')
    expect(sourceLink).toHaveAttribute('rel', 'noopener noreferrer nofollow')
    expect(sourceLink).toHaveAttribute('referrerPolicy', 'no-referrer')
  })

  it.each([
    { label: 'absent', url: null },
    { label: 'unsafe scheme', url: 'javascript:alert(1)' },
    { label: 'local host', url: 'http://192.168.1.10/call' },
    { label: 'legacy octal loopback host', url: 'http://0177.0.0.1/private' },
    { label: 'legacy hexadecimal loopback host', url: 'http://0x7f.0.0.1/private' },
    { label: 'credential query', url: 'https://finep.gov.br/call?access_token=private' },
  ])('does not invent or render an official source link when the URL is $label', async ({ url }) => {
    fetchMock.mockResolvedValue(apiResponse({ ...detailResponse, officialUrl: url }))
    renderApp('/opportunities/call-751')

    expect(await screen.findByText('Link para fonte oficial indisponível.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Abrir fonte oficial (nova aba)' })).not.toBeInTheDocument()
  })

  it('communicates all validation states without presenting unvalidated data as confirmed', async () => {
    fetchMock.mockResolvedValue(apiResponse(detailResponse))
    renderApp('/opportunities/call-751')

    expect(await screen.findByText('Validado pela regra técnica', { selector: '.validation-badge' })).toBeInTheDocument()
    expect(screen.getByText('Divergência entre fontes')).toBeInTheDocument()
    expect(screen.getByText('Extraído, ainda não validado')).toBeInTheDocument()
    expect(screen.getByText('Não identificado pela extração', { selector: '.validation-badge' })).toBeInTheDocument()
    expect(screen.getByText(/nenhum foi escolhido como definitivo/i)).toBeInTheDocument()
    expect(screen.getByText(/ainda não foi validado/i)).toBeInTheDocument()
    expect(screen.getByText('Não identificado pela extração. Isso não confirma ausência no documento.'))
      .toBeInTheDocument()
    expect(screen.getByText(/A API informa “Validado pela regra técnica”, mas não retornou evidência/)).toBeInTheDocument()
  })

  it('shows human publication separately from unchanged extraction state and value', async () => {
    const curated = {
      ...detailResponse,
      fields: detailResponse.fields.map((field) => field.name === 'eligibility' ? {
        ...field, value: { organizations: ['ICT pública'] }, originalValue: { organizations: ['ICT'] },
        publicationSource: 'HUMAN' as const, publishedRevisionId: 'revision-1',
      } : field),
    }
    fetchMock.mockResolvedValue(apiResponse(curated))
    renderApp('/opportunities/call-751')
    const card = (await screen.findByRole('heading', { name: 'Elegibilidade' })).closest<HTMLElement>('.detail-field')!
    expect(within(card).getByText('Valor publicado após revisão humana. Estado técnico da extração: Validado pela regra técnica.'))
      .toBeInTheDocument()
    expect(within(card).getByText('ICT pública')).toBeInTheDocument()
    fireEvent.click(within(card).getByText('Ver valor original da extração e metadados'))
    expect(within(card).getByText('ICT')).toBeInTheDocument()
    expect(within(card).getByText('revision-1')).toBeInTheDocument()
    expect(within(card).queryByText(/"organizations"/)).not.toBeInTheDocument()
  })

  it('presents evaluation criterion weights as points in the public detail', async () => {
    fetchMock.mockResolvedValue(apiResponse({
      ...detailResponse,
      fields: [...detailResponse.fields, {
        name: 'evaluationCriteria', value: [{ criterion: 'Impacto', weight: 5 }], rawValue: null,
        validationStatus: 'EXTRACTED' as const, createdAt: '2026-10-24T12:00:00Z', evidence: [],
      }],
    }))
    renderApp('/opportunities/call-751')
    const card = (await screen.findByRole('heading', { name: 'Critérios de avaliação' }))
      .closest<HTMLElement>('.detail-field')!
    expect(within(card).getByText('5 pontos')).toBeInTheDocument()
    expect(within(card).queryByText('500%')).not.toBeInTheDocument()
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
    await screen.findByRole('heading', { name: /Condições de participação/ })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Consultar histórico e documentos' }))

    const history = await screen.findByRole('region', { name: 'Documentos, evidências e histórico' })
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
    await screen.findByRole('heading', { name: /Condições de participação/ })
    fireEvent.click(screen.getByRole('button', { name: 'Consultar histórico e documentos' }))

    const history = await screen.findByRole('region', { name: 'Documentos, evidências e histórico' })
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
    await screen.findByRole('heading', { name: /Condições de participação/ })
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

describe('administrative curation access', () => {
  function adminOpportunity(fields: unknown[], responsibleAgencies = [
    { name: 'Conselho Nacional de Desenvolvimento Científico e Tecnológico', acronym: 'CNPq', primary: true },
    { name: 'Coordenação de Aperfeiçoamento de Pessoal de Nível Superior', acronym: 'CAPES', primary: false },
  ]) {
    return {
      opportunity: {
        id: 'call-cnpq', externalId: '30/2026', portalEntryId: null, title: 'Chamada CNPq/CAPES 30/2026',
        agency: { name: 'Conselho Nacional de Desenvolvimento Científico e Tecnológico', acronym: 'CNPq' },
        program: 'MAI/DAI', status: 'OPEN', deadline: null, instrumentType: 'Cooperação',
        sources: [{ code: 'CNPQ', name: 'CNPq' }], responsibleAgencies,
      },
      summary: 'Chamada pública', officialUrl: null, currentCallVersion: 1, fields,
    }
  }

  function adminPage(content: unknown[]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 }
  }

  function authenticatedAdminApi(detail: unknown, documents = [
    { id: 'doc-a', externalId: 'notice-a', type: 'NOTICE', title: 'Edital original', sourceUrl: null, versionCount: 1, latestDownloadedAt: '2026-10-25T12:00:00Z' },
    { id: 'doc-b', externalId: 'amendment-b', type: 'AMENDMENT', title: 'Retificação', sourceUrl: null, versionCount: 1, latestDownloadedAt: '2026-10-26T12:00:00Z' },
  ], mediaTypes: Record<string, string> = {}, versionPages: Record<string, unknown[][]> = {}) {
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/admin/csrf') return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
      if (url.pathname === '/api/admin/session') return apiResponse({ username: 'reviewer' })
      if (url.pathname === '/api/admin/opportunities/call-cnpq') return apiResponse(detail)
      if (url.pathname === '/api/opportunities/call-cnpq/documents') {
        return apiResponse(adminPage(documents))
      }
      const versionsPath = /\/documents\/([^/]+)\/versions$/.exec(url.pathname)
      if (versionsPath) {
        const documentId = versionsPath[1]
        const pages = versionPages[documentId]
        if (pages) {
          const page = Number(url.searchParams.get('page') ?? 0)
          return apiResponse({ content: pages[page] ?? [], page, size: 20,
            totalElements: pages.reduce((count, entries) => count + entries.length, 0), totalPages: pages.length })
        }
        return apiResponse(adminPage([{
          id: documentId === 'doc-a' ? 'version-a' : 'version-b',
          contentHash: (documentId === 'doc-a' ? 'a' : 'b').repeat(64),
          mediaType: mediaTypes[documentId] ?? 'application/pdf', sourceUrl: null,
          downloadedAt: documentId === 'doc-a' ? '2026-10-25T12:00:00Z' : '2026-10-26T12:00:00Z',
        }]))
      }
      if (url.pathname.endsWith('/revisions') && init?.method === 'POST') {
        return apiResponse({ accepted: true }, true, 200)
      }
      if (url.pathname.endsWith('/revisions')) return apiResponse(adminPage([]))
      return apiResponse({}, false, 404)
    })
  }

  it('fails closed when the manual-demo CSRF bootstrap is unavailable', async () => {
    fetchMock.mockResolvedValue(apiResponse({ message: 'forbidden' }, false, 403))
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'Administração indisponível' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Senha')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('uses the CSRF-backed session login and renders the review queue', async () => {
    let sessionReads = 0
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/admin/csrf') return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
      if (url.pathname === '/api/admin/session') {
        sessionReads += 1
        return sessionReads === 1 ? apiResponse({}, false, 401) : apiResponse({ username: 'reviewer' })
      }
      if (url.pathname === '/api/admin/login') return apiResponse(null, true, 204)
      if (url.pathname === '/api/admin/opportunities') return apiResponse({ content: [
        { callId: 'call-1', externalId: '751', portalEntryId: '751092', title: 'Chamada Finep', agencyName: 'Finep',
          agencyAcronym: 'FINEP', publishers: ['FINEP'], agencies: ['FINEP'], reviewFieldCount: 2,
          opportunityStatus: 'OPEN', deadline: '2026-12-15' },
      ], page: 0, size: 20, totalElements: 1 })
      return apiResponse({}, false)
    })
    renderApp('/admin')
    await screen.findByLabelText('Usuário')
    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'reviewer' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'demo-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('heading', { name: 'O que precisa da sua atenção?' })).toBeInTheDocument()
    const loginCall = fetchMock.mock.calls.find(([input]) => String(input).includes('/api/admin/login'))
    expect(loginCall?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-test' })
    expect(await screen.findByText('Chamada Finep')).toBeInTheDocument()
    expect(screen.getByText(/Agências responsáveis: FINEP/)).toBeInTheDocument()
    expect(screen.getByText('Aberta')).toBeInTheDocument()
    expect(screen.getByText(/Prazo: 15 de dezembro de 2026/)).toBeInTheDocument()
  })

  it('searches only the loaded queue page and presents real agency and status values', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/admin/csrf') return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
      if (url.pathname === '/api/admin/session') return apiResponse({ username: 'reviewer' })
      if (url.pathname === '/api/admin/opportunities') return apiResponse({ content: [
        { callId: 'finep', externalId: '751', portalEntryId: '751092', title: 'PROINFRA 2024 Expansão', agencyName: 'Finep',
          agencyAcronym: 'FINEP', publishers: ['FINEP'], agencies: ['FINEP'], reviewFieldCount: 4, opportunityStatus: 'OPEN', deadline: '2026-12-15' },
        { callId: 'cnpq', externalId: '30/2026', portalEntryId: null, title: 'Chamada CNPq/CAPES 30/2026', agencyName: 'CNPq',
          agencyAcronym: 'CNPq', publishers: ['CNPQ'], agencies: ['CNPq', 'CAPES'], reviewFieldCount: 2, opportunityStatus: 'UPCOMING', deadline: null },
      ], page: 0, size: 20, totalElements: 2 })
      return apiResponse({}, false)
    })
    renderApp('/admin')
    expect(await screen.findByText('PROINFRA 2024 Expansão')).toBeInTheDocument()
    expect(screen.getByText('Chamada CNPq/CAPES 30/2026')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'CAPES' } })
    expect(screen.queryByText('PROINFRA 2024 Expansão')).not.toBeInTheDocument()
    expect(screen.getByText('Chamada CNPq/CAPES 30/2026')).toBeInTheDocument()
    expect(screen.getByText(/A busca considera os resultados da página atual/)).toBeInTheDocument()
  })

  it('shows an explicit empty queue state', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/admin/csrf') return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
      if (url.pathname === '/api/admin/session') return apiResponse({ username: 'reviewer' })
      if (url.pathname === '/api/admin/opportunities') return apiResponse({ content: [], page: 0, size: 20, totalElements: 0 })
      return apiResponse({}, false)
    })
    renderApp('/admin')
    expect(await screen.findByText('Nenhuma oportunidade precisa de revisão neste momento.')).toBeInTheDocument()
  })

  it('shows a safe retry path when the queue request fails', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/admin/csrf') return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
      if (url.pathname === '/api/admin/session') return apiResponse({ username: 'reviewer' })
      if (url.pathname === '/api/admin/opportunities') return apiResponse({ error: 'private backend details' }, false, 500)
      return apiResponse({}, false)
    })
    renderApp('/admin')
    expect(await screen.findByRole('alert')).toHaveTextContent('A sessão pode ter expirado')
    expect(screen.queryByText('private backend details')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Atualizar e entrar novamente' })).toBeInTheDocument()
  })

  it('shows agencies as read-only relations and provides one focused Portuguese field editor', async () => {
    const fields = [
      { name: 'agency', value: 'CNPq', originalValue: 'CNPq', publicationSource: 'EXTRACTION',
        validationStatus: 'EXTRACTED', createdAt: '2026-10-25T12:00:00Z', evidence: [] },
      { name: 'counterpart', value: {
        exempt: ['ICT federal'],
        municipalPercentRanges: [{ condition: 'até 50 mil habitantes', min: '0.1', max: '4' },
          { condition: 'área prioritária', min: '0.2', max: '8' }],
        stateAndFederalDistrictPercentRanges: [{ condition: 'demais estados', min: '1', max: '10' }],
      }, originalValue: null, publicationSource: 'EXTRACTION', validationStatus: 'EXTRACTED',
        createdAt: '2026-10-25T12:00:00Z', evidence: [{ id: 'e1', extractedFieldId: 'f1', fieldName: 'counterpart',
          documentVersionId: 'version-a', sourceUrl: null, page: 2, section: '4.1',
          quote: 'Faixa municipal conforme porte.', locator: {} }] },
      { name: 'eligibility', value: { eligible: ['ICT pública'], registration: null, excluded: ['Unidades restritas'],
        federalExecutorRequiresSupportInstitution: true, privateNonprofitMinimumOperatingYears: null },
        originalValue: null, publicationSource: 'EXTRACTION', validationStatus: 'CONFLICT',
        createdAt: '2026-10-25T12:00:00Z', evidence: [] },
      { name: 'trl', value: null, originalValue: null, publicationSource: 'HUMAN', publishedRevisionId: 'rev-trl',
        confirmedHumanAbsence: true, validationStatus: 'MISSING', createdAt: '2026-10-25T12:00:00Z', evidence: [] },
    ]
    authenticatedAdminApi(adminOpportunity(fields))
    renderApp('/admin/opportunities/call-cnpq')

    expect(await screen.findByRole('heading', { name: 'Chamada CNPq/CAPES 30/2026' })).toBeInTheDocument()
    expect(screen.getByText(/CNPq \(principal\), CAPES \(corresponsável\)/)).toBeInTheDocument()
    expect(screen.getByText(/1 de 3/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Agência responsável/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Valor proposto')).not.toBeInTheDocument()

    fireEvent.click(within(screen.getByRole('region', { name: 'Quem pode participar' }))
      .getByRole('button', { name: /Conferir informação/ }))
    expect(await screen.findByRole('heading', { name: /informação “elegibilidade” está correta/i })).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText(/Corrigir o valor/))
    expect(screen.getByLabelText('Públicos elegíveis, item 1')).toHaveValue('ICT pública')
    expect(screen.getByLabelText('Cadastro exigido')).toHaveValue('')
    expect(screen.getByLabelText('Executor federal precisa de instituição de apoio')).toHaveValue('true')
    expect(screen.getByLabelText('Tempo mínimo de funcionamento (anos)')).toHaveValue(null)
    expect(screen.getByRole('button', { name: 'Voltar à oportunidade' })).toBeInTheDocument()
  })

  it('preserves every counterpart condition while editing and binds the selected version to its document', async () => {
    const counterpart = {
      exempt: ['ICT federal como proponente'],
      municipalPercentRanges: [{ condition: 'até 50 mil habitantes', min: '0.1', max: '4' },
        { condition: 'área prioritária', min: '0.2', max: '8' }],
      stateAndFederalDistrictPercentRanges: [{ condition: 'demais estados', min: '1', max: '10' }],
    }
    authenticatedAdminApi(adminOpportunity([{
      name: 'counterpart', value: counterpart, originalValue: counterpart, publicationSource: 'EXTRACTION',
      validationStatus: 'EXTRACTED', createdAt: '2026-10-25T12:00:00Z', evidence: [],
    }]))
    renderApp('/admin/opportunities/call-cnpq')

    await screen.findByText('Contrapartida')
    fireEvent.click(screen.getByRole('button', { name: /Conferir informação/ }))
    await screen.findByText('Contrapartida · INFORMAÇÃO CRÍTICA')
    fireEvent.click(screen.getByLabelText(/Corrigir o valor/))
    fireEvent.click(screen.getByRole('button', { name: 'Selecionar outro documento' }))
    fireEvent.change(screen.getAllByLabelText('Condição')[0], { target: { value: 'até 80 mil habitantes' } })
    fireEvent.change(screen.getByLabelText('Justificativa'), { target: { value: 'Conferido na retificação.' } })
    fireEvent.change(screen.getByLabelText('Documento de referência'), { target: { value: 'doc-a' } })
    await waitFor(() => expect(screen.getByLabelText('Versão do documento')).toBeEnabled())
    fireEvent.change(screen.getByLabelText('Versão do documento'), { target: { value: 'version-a' } })
    fireEvent.change(screen.getByLabelText('Documento de referência'), { target: { value: 'doc-b' } })
    expect(screen.getByLabelText('Versão do documento')).toHaveValue('version-b')
    expect(await screen.findByRole('option', { name: /26\/10\/2026/ })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Versão do documento'), { target: { value: 'version-b' } })
    fireEvent.change(screen.getByLabelText('Trecho literal da fonte'), { target: { value: 'Trecho literal da nova faixa.' } })
    const saveButton = screen.getByRole('button', { name: 'Revisar resumo' })
    expect([...saveButton.closest('form')!.querySelectorAll(':invalid')].map((element) => ({
      tag: element.tagName, type: (element as HTMLInputElement).type, value: (element as HTMLInputElement).value,
    }))).toEqual([])
    fireEvent.click(saveButton)
    fireEvent.click(await screen.findByRole('button', { name: 'Enviar para aprovação' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) =>
      new URL(String(input), 'http://localhost').pathname.endsWith('/api/admin/opportunities/call-cnpq/revisions')
      && init?.method === 'POST')).toBe(true))
    const request = fetchMock.mock.calls.find(([input, init]) =>
      new URL(String(input), 'http://localhost').pathname.endsWith('/api/admin/opportunities/call-cnpq/revisions')
      && init?.method === 'POST')
    const payload = JSON.parse(String(request?.[1]?.body)) as { value: typeof counterpart; documentId: string; documentVersionId: string }
    expect(payload.documentId).toBe('doc-b')
    expect(payload.documentVersionId).toBe('version-b')
    expect(payload.value.exempt).toEqual(counterpart.exempt)
    expect(payload.value.municipalPercentRanges).toEqual([
      { condition: 'até 80 mil habitantes', min: '0.1', max: '4' },
      counterpart.municipalPercentRanges[1],
    ])
    expect(payload.value.stateAndFederalDistrictPercentRanges).toEqual(counterpart.stateAndFederalDistrictPercentRanges)
  })

  it('shows field-specific evidence and sends confirmation as an unpublished pending VALUE revision', async () => {
    authenticatedAdminApi(adminOpportunity([{
      name: 'program', value: 'MAI/DAI', originalValue: 'MAI/DAI', publicationSource: 'EXTRACTION',
      validationStatus: 'EXTRACTED', createdAt: '2026-10-25T12:00:00Z', evidence: [{ id: 'evidence-a',
        extractedFieldId: 'field-program', fieldName: 'program', documentVersionId: 'version-a',
        sourceUrl: 'https://www.gov.br/cnpq/chamada', page: 2, section: '1.1', quote: 'Programa MAI/DAI.', locator: {} }],
    }]))
    renderApp('/admin/opportunities/call-cnpq')
    await screen.findByText('Programa')
    fireEvent.click(screen.getByRole('button', { name: /Conferir informação/ }))
    await screen.findByRole('link', { name: 'Abrir fonte pública em nova aba' })
    expect(screen.getByLabelText('Trecho literal da fonte')).toHaveValue('Programa MAI/DAI.')
    const viewer = screen.getByRole('region', { name: 'Visualizador documental' })
    expect(await within(viewer).findByText('Edital original')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Abrir fonte pública em nova aba' })).toHaveAttribute('rel', 'noopener noreferrer nofollow')
    expect(screen.getByLabelText('Justificativa')).toBeRequired()
    fireEvent.change(screen.getByLabelText('Justificativa'), { target: { value: 'Confirmação da redação no edital.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Selecionar outro documento' }))
    await waitFor(() => expect(screen.getByLabelText('Versão do documento')).toBeEnabled())
    fireEvent.change(screen.getByLabelText('Versão do documento'), { target: { value: 'version-a' } })
    fireEvent.click(screen.getByRole('button', { name: 'Revisar resumo' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Enviar para aprovação' }))
    await screen.findByText('Revisão enviada para aprovação. O catálogo público ainda não foi alterado.')
    const createRequest = fetchMock.mock.calls.find(([input, init]) =>
      new URL(String(input), 'http://localhost').pathname.endsWith('/api/admin/opportunities/call-cnpq/revisions')
      && init?.method === 'POST')
    expect(JSON.parse(String(createRequest?.[1]?.body))).toEqual(expect.objectContaining({
      fieldName: 'program', valueKind: 'VALUE', value: 'MAI/DAI', documentId: 'doc-a', documentVersionId: 'version-a',
    }))
    expect(fetchMock.mock.calls.some(([input]) => new URL(String(input), 'http://localhost').pathname.includes('/approve'))).toBe(false)
  })

  it('opens the evidence-linked preserved PDF at its page and allows another version to be selected', async () => {
    authenticatedAdminApi(adminOpportunity([{
      name: 'program', value: 'MAI/DAI', originalValue: 'MAI/DAI', publicationSource: 'EXTRACTION',
      validationStatus: 'EXTRACTED', createdAt: '2026-10-25T12:00:00Z', evidence: [{ id: 'evidence-a',
        extractedFieldId: 'field-program', fieldName: 'program', documentVersionId: 'version-a',
        sourceUrl: null, page: 7, section: '1.2', quote: 'Programa MAI/DAI.', locator: {} }],
    }]))
    renderApp('/admin/opportunities/call-cnpq')
    await screen.findByText('Programa')
    fireEvent.click(screen.getByRole('button', { name: /Conferir informação/ }))

    const viewer = screen.getByRole('region', { name: 'Visualizador documental' })
    expect(await within(viewer).findByText('Edital original')).toBeInTheDocument()
    expect(await screen.findByText('Página indicada pela evidência: 7')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Visualizar documento' }))
    const preview = await screen.findByTitle('Visualização de Edital original')
    expect(preview).toHaveAttribute('src', '/api/admin/opportunities/call-cnpq/documents/doc-a/versions/version-a/content#page=7')
    expect(screen.getByRole('link', { name: 'Abrir em nova aba' })).toHaveAttribute('rel', 'noopener noreferrer')

    fireEvent.click(screen.getByRole('button', { name: 'Selecionar outro documento' }))
    fireEvent.change(screen.getByLabelText('Documento de referência'), { target: { value: 'doc-b' } })
    await waitFor(() => expect(screen.getByLabelText('Versão do documento')).toBeEnabled())
    fireEvent.change(screen.getByLabelText('Versão do documento'), { target: { value: 'version-b' } })
    expect(await screen.findByText(/não está associada como evidência específica/)).toBeInTheDocument()
    expect(screen.getByText(/Selecioná-la como referência não cria evidência/)).toBeInTheDocument()
  })

  it('offers the preserved DOCX as a download and explains that inline preview is unavailable', async () => {
    authenticatedAdminApi(adminOpportunity([{
      name: 'program', value: 'MAI/DAI', originalValue: 'MAI/DAI', publicationSource: 'EXTRACTION',
      validationStatus: 'EXTRACTED', createdAt: '2026-10-25T12:00:00Z', evidence: [],
    }]), undefined, { 'doc-a': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
    renderApp('/admin/opportunities/call-cnpq')
    await screen.findByText('Programa')
    fireEvent.click(screen.getByRole('button', { name: /Conferir informação/ }))

    expect(await screen.findByText(/Este formato não pode ser visualizado aqui/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Baixar documento preservado' })).toHaveAttribute('href',
      '/api/admin/opportunities/call-cnpq/documents/doc-a/versions/version-a/content')
    expect(screen.getByRole('button', { name: 'Visualizar documento' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Selecionar outro documento' })).toBeInTheDocument()
  })

  it('resolves an evidence-linked version on a later document page before enabling review', async () => {
    const newest = Array.from({ length: 20 }, (_, index) => ({ id: `new-version-${index}`,
      contentHash: 'a'.repeat(64), mediaType: 'application/pdf', sourceUrl: null, downloadedAt: '2026-10-26T12:00:00Z' }))
    const olderEvidenceVersion = { id: 'version-old-evidence', contentHash: 'b'.repeat(64),
      mediaType: 'application/pdf', sourceUrl: null, downloadedAt: '2024-10-25T12:00:00Z' }
    authenticatedAdminApi(adminOpportunity([{
      name: 'program', value: 'MAI/DAI', originalValue: 'MAI/DAI', publicationSource: 'EXTRACTION',
      validationStatus: 'EXTRACTED', createdAt: '2026-10-25T12:00:00Z', evidence: [{ id: 'evidence-old',
        extractedFieldId: 'field-program', fieldName: 'program', documentVersionId: 'version-old-evidence',
        sourceUrl: null, page: 12, section: '1.2', quote: 'Citação da versão antiga.', locator: {} }],
    }]), undefined, {}, { 'doc-a': [newest, [olderEvidenceVersion]] })
    renderApp('/admin/opportunities/call-cnpq')
    await screen.findByText('Programa')
    fireEvent.click(screen.getByRole('button', { name: /Conferir informação/ }))

    expect(await screen.findByText('Versão de 25/10/2024, 09:00 (horário de Brasília)')).toBeInTheDocument()
    expect(screen.getByLabelText('Trecho literal da fonte')).toHaveValue('Citação da versão antiga.')
    expect(screen.getByRole('button', { name: 'Revisar resumo' })).toBeEnabled()
    expect(fetchMock.mock.calls.some(([input]) => new URL(String(input), 'http://localhost').searchParams.get('page') === '1'
      && new URL(String(input), 'http://localhost').pathname.endsWith('/documents/doc-a/versions'))).toBe(true)
  })

  it('submits a null field only when absence is explicitly confirmed and localized', async () => {
    authenticatedAdminApi(adminOpportunity([{
      name: 'trl', value: null, originalValue: null, publicationSource: 'EXTRACTION', validationStatus: 'MISSING',
      createdAt: '2026-10-25T12:00:00Z', evidence: [],
    }]))
    renderApp('/admin/opportunities/call-cnpq')
    await screen.findByText('Nível de maturidade tecnológica (TRL)')
    fireEvent.click(screen.getAllByRole('button', { name: /Conferir informação/ })[0])
    await screen.findByText('Nível de maturidade tecnológica (TRL) · INFORMAÇÃO CRÍTICA')
    fireEvent.click(screen.getByLabelText(/Registrar que não foi localizado/))
    fireEvent.click(screen.getByLabelText(/Revisei os documentos e versões aplicáveis/))
    fireEvent.change(screen.getByLabelText('Justificativa'), { target: { value: 'Edital e retificações revisados.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Selecionar outro documento' }))
    await waitFor(() => expect(screen.getByLabelText('Versão do documento')).toBeEnabled())
    fireEvent.change(screen.getByLabelText('Versão do documento'), { target: { value: 'version-a' } })
    fireEvent.click(screen.getByRole('button', { name: 'Revisar resumo' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Enviar para aprovação' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) =>
      new URL(String(input), 'http://localhost').pathname.endsWith('/api/admin/opportunities/call-cnpq/revisions')
      && init?.method === 'POST')).toBe(true))
    const request = fetchMock.mock.calls.find(([input, init]) =>
      new URL(String(input), 'http://localhost').pathname.endsWith('/api/admin/opportunities/call-cnpq/revisions')
      && init?.method === 'POST')
    const payload = JSON.parse(String(request?.[1]?.body)) as { value: unknown; valueKind: string; absenceReviewComplete: boolean }
    expect(payload).toEqual(expect.objectContaining({ value: null, valueKind: 'CONFIRMED_ABSENCE', absenceReviewComplete: true }))
  })

  it('reports a missing opportunity without exposing API internals', async () => {
    fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost')
      if (url.pathname === '/api/admin/csrf') return apiResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test' })
      if (url.pathname === '/api/admin/session') return apiResponse({ username: 'reviewer' })
      if (url.pathname.endsWith('/api/admin/opportunities/missing-call')) return apiResponse({}, false, 404)
      return apiResponse({}, false)
    })
    renderApp('/admin/opportunities/missing-call')
    expect(await screen.findByRole('heading', { name: 'Oportunidade não encontrada' })).toBeInTheDocument()
    expect(screen.queryByText(/internal|SQL|storageKey/i)).not.toBeInTheDocument()
  })
})
