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
const publicLocatorKeys = new Set([
  'segmentid', 'page', 'section', 'paragraph', 'jsonpointer', 'jsonpointers',
  'characterstart', 'characterend', 'quotehash', 'table', 'row', 'column', 'bbox',
])

function safeSourceUrl(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) return null
    const sensitiveParameters = ['token', 'secret', 'password', 'credential', 'authorization',
      'clientid', 'apikey', 'accesskey', 'privatekey', 'clientkey', 'subscriptionkey',
      'signature', 'policy', 'xamzcredential', 'xamzsecuritytoken', 'awsaccesskeyid',
      'assertion', 'bearer', 'session']
    if (Array.from(url.searchParams.keys()).some((key) => {
      const normalized = key.toLowerCase().replace(/[^a-z0-9]/g, '')
      return ['key', 'code', 'auth', 'sig', 'jwt', 'ticket'].includes(normalized)
        || sensitiveParameters.some((sensitive) => normalized.includes(sensitive))
    })) return null
    const hostname = url.hostname.toLowerCase().replace(/\.$/, '').replace(/^\[|\]$/g, '')
    if (!hostname.includes('.') && !hostname.includes(':')) return null
    if (hostname === 'localhost' || hostname.endsWith('.localhost') || hostname.endsWith('.local')
      || hostname === 'internal' || hostname.endsWith('.internal') || hostname.endsWith('.lan')
      || hostname.endsWith('.home') || hostname.endsWith('.home.arpa') || hostname.endsWith('.test')
      || hostname.endsWith('.invalid') || hostname.endsWith('.example')) return null

    const ipv4 = hostname.split('.').map(Number)
    if (ipv4.length === 4 && ipv4.every((part) => Number.isInteger(part) && part >= 0 && part <= 255)) {
      const [first, second, third] = ipv4
      if (first === 0 || first === 10 || first === 127 || first >= 224
        || (first === 169 && second === 254)
        || (first === 172 && second >= 16 && second <= 31)
        || (first === 192 && second === 168)
        || (first === 100 && second >= 64 && second <= 127)
        || (first === 192 && second === 0 && (third === 0 || third === 2))
        || (first === 192 && second === 88 && third === 99)
        || (first === 198 && (second === 18 || second === 19))
        || (first === 198 && second === 51 && third === 100)
        || (first === 203 && second === 0 && third === 113)) return null
    }
    if (hostname.includes(':') && (!/^[23]/i.test(hostname) || /^2001:db8/i.test(hostname)
      || /^2002:/i.test(hostname))) return null

    return url.href
  } catch {
    return null
  }
}

function sanitizeLocator(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sanitizeLocator)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .filter(([key]) => publicLocatorKeys.has(key.toLowerCase().replace(/[^a-z0-9]/g, '')))
      .map(([key, entry]) => [key, sanitizeLocator(entry)]))
  }
  return value
}

function formatLocator(value: unknown): string | null {
  const sanitized = sanitizeLocator(value)
  if (!sanitized || typeof sanitized !== 'object' || Array.isArray(sanitized)
    || Object.keys(sanitized).length === 0) return null
  return JSON.stringify(sanitized, null, 2)
}

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

      <section className="detail-field__evidence" aria-label={`Evidências documentais de ${labelFor(field.name)}`}>
        {hasEvidence ? (
          <details>
            <summary>
              Evidências documentais ({field.evidence.length})
              <span className="detail-field__evidence-state">Proveniência documental; estado: {stateLabels[field.validationStatus]}</span>
            </summary>
            {field.validationStatus === 'CONFLICT' && (
              <p className="detail-field__caution" role="note">
                As evidências abaixo são concorrentes. Nenhuma foi escolhida como verdadeira.
              </p>
            )}
            <ul className="evidence-list">
              {field.evidence.map((evidence) => {
                const sourceUrl = safeSourceUrl(evidence.sourceUrl)
                const locator = formatLocator(evidence.locator)
                return (
                  <li className="evidence-item" key={evidence.id}>
                    <h4>Evidência <code>{evidence.id}</code></h4>
                    <dl className="evidence-item__references">
                      <div><dt>Campo</dt><dd>{labelFor(evidence.fieldName)}</dd></div>
                      <div><dt>Registro do campo</dt><dd><code>{evidence.extractedFieldId}</code></dd></div>
                      {evidence.documentVersionId
                        ? <div><dt>Versão documental</dt><dd><code>{evidence.documentVersionId}</code></dd></div>
                        : <div><dt>Documento</dt><dd>Identificação da versão documental indisponível</dd></div>}
                      {evidence.page !== null && <div><dt>Página</dt><dd>{evidence.page}</dd></div>}
                      {evidence.section && <div><dt>Seção</dt><dd>{evidence.section}</dd></div>}
                    </dl>

                    {evidence.quote
                      ? <blockquote className="evidence-item__quote">{evidence.quote}</blockquote>
                      : <p className="evidence-item__unavailable">Trecho literal indisponível.</p>}

                    {locator
                      ? <div className="evidence-item__locator"><h5>Localizador</h5><pre>{locator}</pre></div>
                      : <p className="evidence-item__unavailable">Localizador documental indisponível.</p>}

                    {sourceUrl
                      ? <a href={sourceUrl} target="_blank" rel="noopener noreferrer nofollow" referrerPolicy="no-referrer">
                          Abrir fonte pública (nova aba)
                        </a>
                      : <p className="evidence-item__unavailable">Link para a fonte pública indisponível.</p>}
                  </li>
                )
              })}
            </ul>
          </details>
        ) : (
          <p className="detail-field__evidence-empty">
            {critical ? 'Nenhuma evidência documental associada a este campo.' : 'Sem evidências documentais para este campo.'}
          </p>
        )}
      </section>
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
