import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  fetchOpportunityDetail,
  OpportunityNotFoundError,
  type ExtractedField,
  type ValidationStatus,
} from './api/opportunities'
import './OpportunityDetailPage.css'

const fieldLabels: Record<string, string> = {
  agency: 'Agência',
  program: 'Programa',
  instrumentType: 'Instrumento',
  title: 'Título',
  summary: 'Resumo',
  status: 'Status',
  publicationDate: 'Data de publicação',
  openingDate: 'Abertura',
  deadline: 'Prazo de submissão',
  totalBudget: 'Orçamento total',
  projectFunding: 'Financiamento por projeto',
  eligibility: 'Elegibilidade',
  regions: 'Regiões elegíveis',
  trl: 'Nível de maturidade tecnológica (TRL)',
  requiresIct: 'Necessidade de ICT',
  counterpart: 'Contrapartida',
  technologyThemes: 'Áreas tecnológicas',
  eligibleExpenses: 'Despesas elegíveis',
  evaluationCriteria: 'Critérios de avaliação',
  requiredDocuments: 'Documentos exigidos',
  officialLinks: 'Links oficiais',
}

const stateLabels: Record<ValidationStatus, string> = {
  VALIDATED: 'Validado',
  EXTRACTED: 'Extraído, ainda não validado',
  CONFLICT: 'Divergência entre fontes',
  MISSING: 'Não informado',
}

const criticalFields = new Set(['deadline', 'totalBudget', 'eligibility', 'counterpart', 'trl', 'requiresIct'])

function labelFor(name: string): string {
  return fieldLabels[name] ?? name.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (letter) => letter.toUpperCase())
}

function formatDate(value: string | null): string {
  if (!value) return 'Não informado'
  const date = new Date(value.length === 10 ? `${value}T00:00:00Z` : value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'long',
    ...(value.length > 10 ? { timeStyle: 'short' as const } : {}),
    timeZone: 'UTC',
  }).format(date)
}

function renderValue(value: unknown) {
  if (value === null || value === undefined || value === '') return <span>Valor não informado</span>
  if (typeof value === 'boolean') return <span>{value ? 'Sim' : 'Não'}</span>
  if (typeof value === 'string' || typeof value === 'number') return <span>{String(value)}</span>
  return <pre className="detail-field__structured-value">{JSON.stringify(value, null, 2)}</pre>
}

function FieldCard({ field }: { field: ExtractedField }) {
  const critical = criticalFields.has(field.name)
  const hasEvidence = field.evidence.length > 0
  const criticalValidatedWithoutEvidence = critical && field.validationStatus === 'VALIDATED' && !hasEvidence
  const shownState = criticalValidatedWithoutEvidence
    ? 'VALIDATED reportado pela API; evidência ausente'
    : stateLabels[field.validationStatus]

  return (
    <article className={`detail-field detail-field--${field.validationStatus.toLowerCase()}`}>
      <div className="detail-field__heading">
        <h3>{labelFor(field.name)}</h3>
        <span className={`validation-badge validation-badge--${field.validationStatus.toLowerCase()}`}>
          {shownState}
        </span>
      </div>

      {field.validationStatus === 'MISSING' ? (
        <p className="detail-field__unavailable">Informação não encontrada nos dados disponíveis.</p>
      ) : field.validationStatus === 'CONFLICT' ? (
        <div>
          <p className="detail-field__caution">Há valores divergentes; nenhum foi escolhido como definitivo.</p>
          {renderValue(field.value)}
        </div>
      ) : (
        <div>
          {field.validationStatus === 'EXTRACTED' && (
            <p className="detail-field__caution">Este dado foi extraído e ainda não foi validado.</p>
          )}
          {criticalValidatedWithoutEvidence && (
            <p className="detail-field__caution" role="note">
              A API informa o estado VALIDATED, mas não retornou evidência associada.
            </p>
          )}
          {renderValue(field.value ?? field.rawValue)}
        </div>
      )}

      {critical && (
        <div className="detail-field__evidence">
          <p>{hasEvidence
            ? `${field.evidence.length} ${field.evidence.length === 1 ? 'evidência associada' : 'evidências associadas'}`
            : 'Nenhuma evidência associada nos dados retornados'}</p>
          {hasEvidence && (
            <details>
              <summary>Referências de evidência</summary>
              <ul>
                {field.evidence.map((evidence) => (
                  <li key={evidence.id}>
                    <span>Referência <code>{evidence.id}</code></span>
                    {evidence.documentVersionId && (
                      <span>Versão documental <code>{evidence.documentVersionId}</code></span>
                    )}
                    {evidence.page !== null && <span>Página {evidence.page}</span>}
                    {evidence.section && <span>Seção: {evidence.section}</span>}
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </article>
  )
}

export default function OpportunityDetailPage() {
  const { callId = '' } = useParams()
  const detailQuery = useQuery({
    queryKey: ['opportunity-detail', callId],
    queryFn: ({ signal }) => fetchOpportunityDetail(callId, signal),
    enabled: Boolean(callId),
    retry: false,
  })

  return (
    <main className="detail-page">
      <header className="site-header">
        <Link className="brand" to="/" aria-label="Fomentiq, catálogo">
          <span className="brand-mark" aria-hidden="true">f</span>
          <span>fomentiq</span>
        </Link>
        <span className="site-header__section">Detalhe da oportunidade</span>
      </header>

      <div className="detail-content">
        <Link className="detail-back-link" to="/">← Voltar ao catálogo</Link>

        {detailQuery.isPending && (
          <div className="detail-message" role="status" aria-live="polite">
            <span className="loading-indicator" aria-hidden="true" />
            <p>Carregando oportunidade…</p>
          </div>
        )}

        {detailQuery.isError && detailQuery.error instanceof OpportunityNotFoundError && (
          <section className="detail-message" role="alert">
            <h1>Oportunidade não encontrada</h1>
            <p>Ela pode ter sido removida ou o endereço pode estar incorreto.</p>
          </section>
        )}

        {detailQuery.isError && !(detailQuery.error instanceof OpportunityNotFoundError) && (
          <section className="detail-message detail-message--error" role="alert">
            <h1>Não foi possível carregar esta oportunidade.</h1>
            <p>Verifique sua conexão e tente novamente.</p>
            <button className="button button--secondary" onClick={() => void detailQuery.refetch()}>
              Tentar novamente
            </button>
          </section>
        )}

        {detailQuery.isSuccess && (() => {
          const { opportunity } = detailQuery.data
          return (
            <>
              <section className="detail-hero" aria-labelledby="opportunity-title">
                <p className="eyebrow">{opportunity.agency.acronym || opportunity.agency.name}</p>
                <h1 id="opportunity-title">{opportunity.title}</h1>
                <p className="detail-hero__agency">{opportunity.agency.name}</p>
                {detailQuery.data.summary && <p className="detail-hero__summary">{detailQuery.data.summary}</p>}
                <dl className="detail-identity">
                  <div><dt>Programa</dt><dd>{opportunity.program || 'Não informado'}</dd></div>
                  <div><dt>Instrumento</dt><dd>{opportunity.instrumentType || 'Não informado'}</dd></div>
                  <div><dt>Status</dt><dd>{opportunity.status}</dd></div>
                  <div><dt>Prazo de submissão</dt><dd>{formatDate(opportunity.deadline)}</dd></div>
                  <div><dt>ID histórico</dt><dd>{opportunity.externalId}</dd></div>
                  {opportunity.portalEntryId && <div><dt>Identificador do portal</dt><dd>{opportunity.portalEntryId}</dd></div>}
                </dl>
              </section>

              <section className="detail-fields" aria-labelledby="fields-title">
                <div className="detail-section-heading">
                  <p className="eyebrow">INFORMAÇÕES DA CHAMADA</p>
                  <h2 id="fields-title">Campos estruturados</h2>
                  <p>Os estados indicam o nível de validação disponível para cada informação.</p>
                </div>
                {detailQuery.data.fields.length > 0 ? (
                  <div className="detail-fields__grid">
                    {detailQuery.data.fields.map((field) => <FieldCard key={field.name} field={field} />)}
                  </div>
                ) : (
                  <p className="detail-fields__empty" role="status">Nenhum campo estruturado disponível.</p>
                )}
              </section>
            </>
          )
        })()}
      </div>
    </main>
  )
}
