import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  fetchOpportunities,
  OPPORTUNITIES_PAGE_SIZE,
  type CallStatus,
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

function OpportunityCard({ opportunity }: { opportunity: OpportunitySummary }) {
  return (
    <article className="opportunity-card">
      <div className="opportunity-card__topline">
        <span className="agency-label">
          {opportunity.agency.acronym || opportunity.agency.name}
        </span>
        <span className={`status-badge status-badge--${opportunity.status.toLowerCase()}`}>
          {statusLabels[opportunity.status]}
        </span>
      </div>

      <h2 className="opportunity-card__title">{opportunity.title}</h2>
      <p className="opportunity-card__agency">{opportunity.agency.name}</p>

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
  const opportunitiesQuery = useQuery({
    queryKey: ['opportunities', page],
    queryFn: ({ signal }) => fetchOpportunities(page, signal),
    retry: false,
  })

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
            <p>Novas chamadas aparecerão aqui quando forem identificadas.</p>
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
