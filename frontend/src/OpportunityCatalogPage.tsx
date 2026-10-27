import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  fetchOpportunities,
  OPPORTUNITIES_PAGE_SIZE,
  type CallStatus,
  type OpportunityFilters,
  type OpportunitySummary,
} from './api/opportunities'
import './OpportunityCatalogPage.css'

const statusLabels: Record<CallStatus, string> = {
  OPEN: 'Aberta',
  UPCOMING: 'Em breve',
  CLOSED: 'Encerrada',
  SUSPENDED: 'Suspensa',
  UNKNOWN: 'Status não informado',
}

interface FilterFormValues {
  q: string
  status: CallStatus | ''
  deadlineFrom: string
  deadlineTo: string
  instrument: string
  region: string
  theme: string
  source: string
  agency: string
}

const EMPTY_FORM: FilterFormValues = {
  q: '',
  status: '',
  deadlineFrom: '',
  deadlineTo: '',
  instrument: '',
  region: '',
  theme: '',
  source: '',
  agency: '',
}

function toOpportunityFilters(values: FilterFormValues): OpportunityFilters {
  const filters: OpportunityFilters = {}
  const q = values.q.trim()
  const instrument = values.instrument.trim()
  const region = values.region.trim()
  const theme = values.theme.trim()

  if (q) filters.q = q
  if (values.status) filters.status = values.status
  if (values.deadlineFrom) filters.deadlineFrom = values.deadlineFrom
  if (values.deadlineTo) filters.deadlineTo = values.deadlineTo
  if (instrument) filters.instrument = instrument
  if (region) filters.region = region
  if (theme) filters.theme = theme
  if (values.source) filters.source = values.source
  if (values.agency) filters.agency = values.agency

  return filters
}

function sameFilters(left: OpportunityFilters, right: OpportunityFilters): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

function formatDeadline(deadline: string | null): string {
  if (!deadline) return 'Prazo não informado'

  const date = new Date(`${deadline}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return 'Prazo não informado'

  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(date)
}

function formatFilterDate(date: string): string {
  const [year, month, day] = date.split('-')
  return `${day}/${month}/${year}`
}

function activeFilterLabels(filters: OpportunityFilters): string[] {
  const labels: string[] = []
  if (filters.q) labels.push(`Busca: ${filters.q}`)
  if (filters.status) labels.push(`Status: ${statusLabels[filters.status]}`)

  if (filters.deadlineFrom && filters.deadlineTo) {
    labels.push(`Prazo: ${formatFilterDate(filters.deadlineFrom)} a ${formatFilterDate(filters.deadlineTo)}`)
  } else if (filters.deadlineFrom) {
    labels.push(`Prazo a partir de: ${formatFilterDate(filters.deadlineFrom)}`)
  } else if (filters.deadlineTo) {
    labels.push(`Prazo até: ${formatFilterDate(filters.deadlineTo)}`)
  }

  if (filters.instrument) labels.push(`Instrumento: ${filters.instrument}`)
  if (filters.region) labels.push(`Região: ${filters.region}`)
  if (filters.theme) labels.push(`Tema: ${filters.theme}`)
  if (filters.source) labels.push(`Fonte: ${filters.source}`)
  if (filters.agency) labels.push(`Agência: ${filters.agency}`)
  return labels
}

function OpportunityCard({ opportunity }: { opportunity: OpportunitySummary }) {
  return (
    <article className="opportunity-card">
      <div className="opportunity-card__topline">
        <span className="agency-label">
          {opportunity.agency.acronym || opportunity.agency.name}
        </span>
        <span className="source-label">{(opportunity.sources ?? []).map((source) => source.code).join(' · ')}</span>
        <span className={`status-badge status-badge--${opportunity.status.toLowerCase()}`}>
          {statusLabels[opportunity.status]}
        </span>
      </div>

      <h2 className="opportunity-card__title">
        <Link to={`/opportunities/${encodeURIComponent(opportunity.id)}`}>{opportunity.title}</Link>
      </h2>
      <p className="opportunity-card__agency">{opportunity.agency.name}</p>
      {opportunity.responsibleAgencies?.length ? (
        <p className="opportunity-card__agency">Agências responsáveis: {opportunity.responsibleAgencies.map((agency) => agency.acronym).join(', ')}</p>
      ) : null}

      <dl className="opportunity-card__details">
        <div>
          <dt>Instrumento</dt>
          <dd>{opportunity.instrumentType || 'Não informado'}</dd>
        </div>
        <div>
          <dt>Prazo de submissão</dt>
          <dd>{formatDeadline(opportunity.deadline)}</dd>
        </div>
      </dl>
    </article>
  )
}

export default function OpportunityCatalogPage() {
  const [page, setPage] = useState(0)
  const [draft, setDraft] = useState<FilterFormValues>(EMPTY_FORM)
  const [appliedFilters, setAppliedFilters] = useState<OpportunityFilters>({})
  const [dateRangeError, setDateRangeError] = useState<string | null>(null)
  const activeLabels = activeFilterLabels(appliedFilters)
  const draftFilters = toOpportunityFilters(draft)
  const hasUnappliedChanges = !sameFilters(draftFilters, appliedFilters)

  const opportunitiesQuery = useQuery({
    queryKey: ['opportunities', page, appliedFilters],
    queryFn: ({ signal }) => fetchOpportunities(page, appliedFilters, signal),
    retry: false,
    staleTime: 30_000,
  })

  function updateDraft(field: keyof FilterFormValues, value: string) {
    setDraft((current) => ({ ...current, [field]: value }))
    setDateRangeError(null)
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (draft.deadlineFrom && draft.deadlineTo && draft.deadlineFrom > draft.deadlineTo) {
      setDateRangeError('A data inicial do prazo deve ser anterior ou igual à data final.')
      return
    }

    setDateRangeError(null)
    const nextFilters = toOpportunityFilters(draft)
    if (sameFilters(nextFilters, appliedFilters)) return

    setPage(0)
    setAppliedFilters(nextFilters)
  }

  function clearFilters() {
    setDraft(EMPTY_FORM)
    setDateRangeError(null)
    if (Object.keys(appliedFilters).length > 0) {
      setPage(0)
      setAppliedFilters({})
    }
  }

  const hasAnyDraft = Object.keys(draftFilters).length > 0

  return (
    <main className="catalog-page">
      <header className="site-header">
        <a className="brand" href="/" aria-label="Fomentiq, início">
          <span className="brand-mark" aria-hidden="true">f</span>
          <span>fomentiq</span>
        </a>
        <span className="site-header__section">Radar de oportunidades</span>
      </header>

      <section className="catalog-content" aria-labelledby="catalog-title">
        <div className="catalog-heading">
          <p className="eyebrow">FOMENTO À INOVAÇÃO</p>
          <h1 id="catalog-title">Oportunidades para transformar ideias em impacto.</h1>
          <p className="catalog-intro">
            Editais e chamadas públicas para projetos de pesquisa, desenvolvimento e inovação.
          </p>
        </div>

        <section className="filter-panel" aria-labelledby="filters-title">
          <div className="filter-panel__heading">
            <div>
              <p className="eyebrow">ENCONTRE UMA CHAMADA</p>
              <h2 id="filters-title">Busca e filtros</h2>
            </div>
            <span className="filter-state">
              {activeLabels.length === 0
                ? 'Nenhum filtro ativo'
                : `${activeLabels.length} ${activeLabels.length === 1 ? 'filtro ativo' : 'filtros ativos'}`}
            </span>
          </div>

          <form className="filter-form" role="search" aria-label="Buscar e filtrar oportunidades" onSubmit={applyFilters}>
            <div className="filter-field filter-field--query">
              <label htmlFor="opportunity-query">Busca textual</label>
              <input
                id="opportunity-query"
                name="q"
                type="search"
                maxLength={200}
                value={draft.q}
                onChange={(event) => updateDraft('q', event.target.value)}
                placeholder="Ex.: inteligência artificial, energia…"
              />
            </div>

            <div className="filter-field">
              <label htmlFor="opportunity-status">Status</label>
              <select
                id="opportunity-status"
                name="status"
                value={draft.status}
                onChange={(event) => updateDraft('status', event.target.value)}
              >
                <option value="">Todos os status</option>
                {Object.entries(statusLabels).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>

            <div className="filter-field">
              <label htmlFor="opportunity-source">Fonte publicadora</label>
              <select id="opportunity-source" name="source" value={draft.source}
                onChange={(event) => updateDraft('source', event.target.value)}>
                <option value="">Todas as fontes</option>
                <option value="FINEP">Finep</option>
                <option value="CNPQ">CNPq</option>
              </select>
            </div>

            <div className="filter-field">
              <label htmlFor="opportunity-agency">Agência responsável</label>
              <select id="opportunity-agency" name="agency" value={draft.agency}
                onChange={(event) => updateDraft('agency', event.target.value)}>
                <option value="">Todas as agências</option>
                <option value="FINEP">Finep</option>
                <option value="CNPq">CNPq</option>
                <option value="CAPES">CAPES</option>
              </select>
            </div>

            <fieldset className="filter-field filter-field--dates">
              <legend>Prazo de submissão</legend>
              <div className="date-range">
                <div>
                  <label htmlFor="deadline-from">De</label>
                  <input
                    id="deadline-from"
                    name="deadlineFrom"
                    type="date"
                    value={draft.deadlineFrom}
                    aria-invalid={dateRangeError !== null}
                    aria-describedby={dateRangeError ? 'deadline-range-error' : undefined}
                    onChange={(event) => updateDraft('deadlineFrom', event.target.value)}
                  />
                </div>
                <div>
                  <label htmlFor="deadline-to">Até</label>
                  <input
                    id="deadline-to"
                    name="deadlineTo"
                    type="date"
                    value={draft.deadlineTo}
                    aria-invalid={dateRangeError !== null}
                    aria-describedby={dateRangeError ? 'deadline-range-error' : undefined}
                    onChange={(event) => updateDraft('deadlineTo', event.target.value)}
                  />
                </div>
              </div>
            </fieldset>

            <div className="filter-field">
              <label htmlFor="opportunity-instrument">Instrumento</label>
              <input
                id="opportunity-instrument"
                name="instrument"
                type="text"
                maxLength={100}
                value={draft.instrument}
                onChange={(event) => updateDraft('instrument', event.target.value)}
                placeholder="Ex.: subvenção econômica"
              />
            </div>

            <div className="filter-field">
              <label htmlFor="opportunity-region">Região</label>
              <input
                id="opportunity-region"
                name="region"
                type="text"
                maxLength={120}
                value={draft.region}
                onChange={(event) => updateDraft('region', event.target.value)}
                placeholder="Ex.: Nordeste"
              />
            </div>

            <div className="filter-field">
              <label htmlFor="opportunity-theme">Tema</label>
              <input
                id="opportunity-theme"
                name="theme"
                type="text"
                maxLength={120}
                value={draft.theme}
                onChange={(event) => updateDraft('theme', event.target.value)}
                placeholder="Ex.: biotecnologia"
              />
            </div>

            {dateRangeError && (
              <p className="filter-form__error" id="deadline-range-error" role="alert">
                {dateRangeError}
              </p>
            )}

            {hasUnappliedChanges && (
              <p className="filter-form__hint" role="status">
                Há alterações não aplicadas. Os resultados continuam usando os filtros ativos abaixo.
              </p>
            )}

            <div className="filter-form__actions">
              <button className="button button--primary" type="submit" disabled={!hasUnappliedChanges}>
                Aplicar filtros
              </button>
              <button
                className="button button--secondary"
                type="button"
                onClick={clearFilters}
                disabled={!hasAnyDraft && activeLabels.length === 0}
              >
                Limpar filtros
              </button>
            </div>
          </form>

          {activeLabels.length > 0 ? (
            <ul className="active-filters" aria-label="Filtros aplicados">
              {activeLabels.map((label) => <li key={label}>{label}</li>)}
            </ul>
          ) : (
            <p className="active-filters__empty">Nenhum filtro aplicado aos resultados.</p>
          )}
        </section>

        {opportunitiesQuery.isPending && (
          <div className="catalog-message" role="status" aria-live="polite">
            <span className="loading-indicator" aria-hidden="true" />
            <p>Carregando oportunidades…</p>
          </div>
        )}

        {opportunitiesQuery.isError && (
          <div className="catalog-message catalog-message--error" role="alert">
            <span className="message-icon" aria-hidden="true">!</span>
            <h2>Não foi possível carregar as oportunidades.</h2>
            <p>Verifique sua conexão e tente novamente.</p>
            <button className="button button--secondary" onClick={() => void opportunitiesQuery.refetch()}>
              Tentar novamente
            </button>
          </div>
        )}

        {opportunitiesQuery.isSuccess && opportunitiesQuery.data.content.length === 0 && (
          <div className="catalog-message" role="status">
            <span className="message-icon message-icon--quiet" aria-hidden="true">↗</span>
            <h2>Nenhuma oportunidade encontrada</h2>
            <p>
              {activeLabels.length > 0
                ? 'Nenhuma oportunidade corresponde aos filtros aplicados.'
                : 'Novas chamadas aparecerão aqui quando forem identificadas.'}
            </p>
          </div>
        )}

        {opportunitiesQuery.isSuccess && opportunitiesQuery.data.content.length > 0 && (
          <>
            <div className="results-heading">
              <h2>Oportunidades encontradas</h2>
              <span>{opportunitiesQuery.data.totalElements} oportunidades</span>
            </div>

            <div className="opportunity-list" aria-label="Lista de oportunidades">
              {opportunitiesQuery.data.content.map((opportunity) => (
                <OpportunityCard key={opportunity.id} opportunity={opportunity} />
              ))}
            </div>

            {opportunitiesQuery.data.totalPages > 1 && (
              <nav className="pagination" aria-label="Paginação das oportunidades">
                <button
                  className="button button--secondary"
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                  disabled={page === 0 || opportunitiesQuery.isFetching}
                >
                  Anterior
                </button>
                <span>Página {page + 1} de {opportunitiesQuery.data.totalPages}</span>
                <button
                  className="button button--secondary"
                  onClick={() => setPage((current) => Math.min(
                    opportunitiesQuery.data.totalPages - 1,
                    current + 1,
                  ))}
                  disabled={page + 1 >= opportunitiesQuery.data.totalPages || opportunitiesQuery.isFetching}
                >
                  Próxima
                </button>
              </nav>
            )}
          </>
        )}

        <span className="sr-only">Até {OPPORTUNITIES_PAGE_SIZE} chamadas por página.</span>
      </section>

      <footer className="site-footer">
        <span>Fomentiq</span>
        <span>Inteligência para financiar a inovação</span>
      </footer>
    </main>
  )
}
