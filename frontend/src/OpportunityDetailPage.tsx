import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import {
  fetchOpportunityDetail,
  fetchCallVersions,
  fetchDocumentVersions,
  fetchOpportunityDocuments,
  OpportunityNotFoundError,
  type CallVersion,
  type ExtractedField,
  type OpportunityDocument,
  type ReadPage,
} from './api/opportunities'
import './OpportunityDetailPage.css'
import { safeSourceUrl } from './sourceUrlPolicy'
import { CRITICAL_FIELDS, VALIDATION_STATUS_LABELS, fieldLabel, formatDate, formatMoney, formatPercent, formatPoints, formatTimestamp, qualifierLabel, valueLabel } from './fieldPresentation'

const publicLocatorKeys = new Set([
  'segmentid', 'page', 'section', 'paragraph', 'jsonpointer', 'jsonpointers',
  'characterstart', 'characterend', 'quotehash', 'table', 'row', 'column', 'bbox',
])

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

function Pagination({
  data,
  page,
  disabled,
  label,
  onChange,
}: {
  data: ReadPage<unknown>
  page: number
  disabled: boolean
  label: string
  onChange: (page: number) => void
}) {
  if (data.totalPages <= 1) return null
  return (
    <nav className="history-pagination" aria-label={label}>
      <button className="button button--secondary" type="button" disabled={disabled || page <= 0}
        onClick={() => onChange(page - 1)}>Mais recentes</button>
      <span role="status">Página {page + 1} de {data.totalPages}</span>
      <button className="button button--secondary" type="button" disabled={disabled || page + 1 >= data.totalPages}
        onClick={() => onChange(page + 1)}>Mais antigas</button>
    </nav>
  )
}

const documentTypeLabels: Record<OpportunityDocument['type'], string> = {
  NOTICE: 'Edital',
  ANNEX: 'Anexo',
  FAQ: 'Perguntas frequentes',
  AMENDMENT: 'Retificação',
  RESULT_PRELIMINARY: 'Resultado preliminar',
  RESULT_FINAL: 'Resultado final',
  OTHER: 'Outro documento',
}

function DocumentHistoryCard({ callId, document }: { callId: string; document: OpportunityDocument }) {
  const [expanded, setExpanded] = useState(false)
  const [page, setPage] = useState(0)
  const versionsQuery = useQuery({
    queryKey: ['document-versions', callId, document.id, page],
    queryFn: ({ signal }) => fetchDocumentVersions(callId, document.id, page, signal),
    enabled: expanded && document.versionCount > 0,
    retry: false,
    staleTime: 30_000,
  })
  const sortedVersions = versionsQuery.data
    ? [...versionsQuery.data.content].sort((left, right) =>
        right.downloadedAt.localeCompare(left.downloadedAt) || right.id.localeCompare(left.id))
    : []

  return (
    <article className="history-document">
      <div className="history-document__heading">
        <div>
          <p className="history-document__type">{documentTypeLabels[document.type]}</p>
          <h4>{document.title}</h4>
        </div>
      </div>
      <dl className="history-document__metadata">
        <div><dt>ID do documento</dt><dd><code>{document.id}</code></dd></div>
        <div><dt>ID de origem</dt><dd><code>{document.externalId}</code></dd></div>
        <div><dt>Versões documentais</dt><dd>{document.versionCount}</dd></div>
        <div><dt>Download mais recente</dt><dd>{formatTimestamp(document.latestDownloadedAt)}</dd></div>
      </dl>

      {document.versionCount > 0 ? (
        <>
          <button className="button button--secondary history-document__toggle" type="button"
            aria-expanded={expanded} aria-controls={`document-versions-${document.id}`}
            onClick={() => setExpanded((current) => !current)}>
            {expanded ? 'Ocultar versões documentais' : 'Ver versões documentais'}
          </button>
          {expanded && (
            <div id={`document-versions-${document.id}`} className="history-document__versions">
              {versionsQuery.isPending && <p role="status">Carregando versões documentais…</p>}
              {versionsQuery.isError && (
                <div role="alert">
                  <p>Não foi possível carregar as versões deste documento.</p>
                  <button className="button button--secondary" type="button"
                    onClick={() => void versionsQuery.refetch()}>Tentar novamente</button>
                </div>
              )}
              {versionsQuery.isSuccess && sortedVersions.length === 0 && (
                <p role="status">Nenhuma versão documental encontrada.</p>
              )}
              {versionsQuery.isSuccess && sortedVersions.length > 0 && (
                <>
                  <p className="history-order-note">Versões documentais: mais recentes primeiro.</p>
                  <ol className="document-version-list" aria-label={`Versões do documento ${document.title}`}>
                    {sortedVersions.map((version) => (
                      <li key={version.id}>
                        <h5>Versão documental</h5>
                        <dl className="history-document__metadata">
                          <div><dt>Baixada em</dt><dd>{formatTimestamp(version.downloadedAt)}</dd></div>
                          <div><dt>ID da versão</dt><dd><code>{version.id}</code></dd></div>
                          <div><dt>Tipo de mídia</dt><dd>{version.mediaType || 'Não informado'}</dd></div>
                          <div><dt>Hash SHA-256</dt><dd><code>{version.contentHash}</code></dd></div>
                        </dl>
                      </li>
                    ))}
                  </ol>
                  <Pagination data={versionsQuery.data} page={page} disabled={versionsQuery.isFetching}
                    label={`Paginação das versões documentais de ${document.title}`} onChange={setPage} />
                </>
              )}
            </div>
          )}
        </>
      ) : (
        <p className="history-document__empty">Nenhuma versão documental registrada.</p>
      )}
    </article>
  )
}

function OpportunityHistorySection({
  callId,
  externalId,
  portalEntryId,
}: {
  callId: string
  externalId: string
  portalEntryId: string | null
}) {
  const [expanded, setExpanded] = useState(false)
  const [callPage, setCallPage] = useState(0)
  const [documentPage, setDocumentPage] = useState(0)
  const callVersionsQuery = useQuery({
    queryKey: ['call-versions', callId, callPage],
    queryFn: ({ signal }) => fetchCallVersions(callId, callPage, signal),
    enabled: expanded,
    retry: false,
    staleTime: 30_000,
  })
  const documentsQuery = useQuery({
    queryKey: ['opportunity-documents', callId, documentPage],
    queryFn: ({ signal }) => fetchOpportunityDocuments(callId, documentPage, signal),
    enabled: expanded,
    retry: false,
    staleTime: 30_000,
  })
  const sortedCallVersions: CallVersion[] = callVersionsQuery.data
    ? [...callVersionsQuery.data.content].sort((left, right) =>
        right.versionNumber - left.versionNumber || right.detectedAt.localeCompare(left.detectedAt))
    : []

  return (
    <section className="opportunity-history" aria-labelledby="history-title">
      <div className="detail-section-heading">
        <p className="eyebrow">EVOLUÇÃO DA CHAMADA</p>
        <h2 id="history-title">Documentos, evidências e histórico</h2>
        <p>Histórico da mesma oportunidade: ID de origem {externalId}; registro atual do portal {portalEntryId || 'não informado'}.</p>
        <p>Snapshots da chamada e versões documentais são históricos distintos. Eles não indicam, por si só, quais campos mudaram.</p>
      </div>
      <button className="button button--secondary history-section__toggle" type="button"
        aria-expanded={expanded} aria-controls="opportunity-history-content"
        onClick={() => setExpanded((current) => !current)}>
        {expanded ? 'Ocultar histórico' : 'Consultar histórico e documentos'}
      </button>

      {expanded && (
        <div id="opportunity-history-content" className="opportunity-history__content">
          <section className="call-version-history" aria-labelledby="call-versions-title">
            <div className="history-subheading">
              <h3 id="call-versions-title">Versões da chamada (snapshots)</h3>
              <p>Ordem cronológica: mais recente primeiro.</p>
            </div>
            {callVersionsQuery.isPending && <p role="status">Carregando versões da chamada…</p>}
            {callVersionsQuery.isError && (
              <div role="alert">
                <p>Não foi possível carregar o histórico da chamada.</p>
                <button className="button button--secondary" type="button"
                  onClick={() => void callVersionsQuery.refetch()}>Tentar novamente</button>
              </div>
            )}
            {callVersionsQuery.isSuccess && sortedCallVersions.length === 0 && (
              <p role="status">Nenhuma versão da chamada registrada.</p>
            )}
            {callVersionsQuery.isSuccess && sortedCallVersions.length > 0 && (
              <>
                <ol className="call-version-list" aria-label="Snapshots da chamada, mais recentes primeiro">
                  {sortedCallVersions.map((version) => (
                    <li key={version.id}>
                      <h4>Versão da chamada {version.versionNumber}</h4>
                      <dl className="history-document__metadata">
                        <div><dt>Detectada em</dt><dd>{formatTimestamp(version.detectedAt)}</dd></div>
                        <div><dt>ID do snapshot</dt><dd><code>{version.id}</code></dd></div>
                        <div><dt>Hash SHA-256</dt><dd><code>{version.contentHash}</code></dd></div>
                        {version.supersedesVersionId && (
                          <div><dt>Snapshot substituído</dt><dd><code>{version.supersedesVersionId}</code></dd></div>
                        )}
                      </dl>
                    </li>
                  ))}
                </ol>
                <Pagination data={callVersionsQuery.data} page={callPage} disabled={callVersionsQuery.isFetching}
                  label="Paginação das versões da chamada" onChange={setCallPage} />
              </>
            )}
          </section>

          <section className="document-history" aria-labelledby="documents-title">
            <div className="history-subheading">
              <h3 id="documents-title">Documentos e versões documentais</h3>
              <p>Cada documento mantém seu próprio histórico de versões.</p>
            </div>
            {documentsQuery.isPending && <p role="status">Carregando documentos…</p>}
            {documentsQuery.isError && (
              <div role="alert">
                <p>Não foi possível carregar os documentos da oportunidade.</p>
                <button className="button button--secondary" type="button"
                  onClick={() => void documentsQuery.refetch()}>Tentar novamente</button>
              </div>
            )}
            {documentsQuery.isSuccess && documentsQuery.data.content.length === 0 && (
              <p role="status">Nenhum documento registrado para esta oportunidade.</p>
            )}
            {documentsQuery.isSuccess && documentsQuery.data.content.length > 0 && (
              <>
                <div className="history-document-list" aria-label="Documentos da oportunidade">
                  {documentsQuery.data.content.map((document) => (
                    <DocumentHistoryCard key={document.id} callId={callId} document={document} />
                  ))}
                </div>
                <Pagination data={documentsQuery.data} page={documentPage} disabled={documentsQuery.isFetching}
                  label="Paginação dos documentos da oportunidade" onChange={setDocumentPage} />
              </>
            )}
          </section>
        </div>
      )}
    </section>
  )
}

function renderValue(value: unknown, key?: string, context?: string): ReactNode {
  if (value === null || value === undefined || value === '') return <span>Valor não informado</span>
  if (typeof value === 'boolean') return <span>{value ? 'Sim' : 'Não'}</span>
  if (typeof value === 'number') {
    if (key === 'percentage' || (context === 'percent-range' && (key === 'min' || key === 'max'))) return <span>{formatPercent(value)}</span>
    if (key === 'weight') return <span>{formatPoints(value)}</span>
    return <span>{new Intl.NumberFormat('pt-BR').format(value)}</span>
  }
  if (typeof value === 'string') {
    if (key === 'currency') return <span>{value}</span>
    if (key === 'deadline' || key === 'publicationDate' || key === 'openingDate') return <span>{formatDate(value)}</span>
    if (key === 'percentage' || (context === 'percent-range' && (key === 'min' || key === 'max'))) return <span>{formatPercent(value)}</span>
    if (key === 'amount' && context === 'money') return <span>{formatMoney(value)}</span>
    if (key === 'qualifier') return <span>{qualifierLabel(value)}</span>
    return <span>{value}</span>
  }
  if (Array.isArray(value)) return value.length === 0 ? <span>Não informado</span> : <ul>{value.map((item, index) =>
    <li key={`${key ?? 'item'}-${index}`}>{renderValue(item, undefined,
      key === 'municipalPercentRanges' || key === 'stateAndFederalDistrictPercentRanges' ? 'percent-range'
        : key === 'proposalMaximumByEmployedDoctorCount' ? 'funding-tier' : undefined)}</li>)}</ul>
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (typeof record.amount === 'string' && typeof record.currency === 'string') {
      const amount = Number(record.amount)
      const formatted = Number.isFinite(amount)
        ? formatMoney(amount, record.currency)
        : `${record.currency} ${record.amount}`
      return <span>{formatted}{record.qualifier ? ` (${qualifierLabel(String(record.qualifier))})` : ''}</span>
    }
    return <dl className="detail-field__structured-list">{Object.entries(record).map(([name, child]) =>
      <div key={name}><dt>{valueLabel(name)}</dt><dd>{renderValue(child, name,
        name === 'amount' && typeof record.currency === 'string' ? 'money'
          : (name === 'min' || name === 'max') ? context : undefined)}</dd></div>)}</dl>
  }
  return <span>Valor não disponível</span>
}

function FieldCard({ field }: { field: ExtractedField }) {
  const critical = CRITICAL_FIELDS.has(field.name)
  const hasEvidence = field.evidence.length > 0
  const criticalValidatedWithoutEvidence = critical && field.validationStatus === 'VALIDATED' && !hasEvidence
  const hasUnquotedEvidence = field.evidence.some((evidence) => !evidence.quote?.trim())
  const shownState = criticalValidatedWithoutEvidence
    ? 'VALIDATED reportado pela API; evidência ausente'
    : VALIDATION_STATUS_LABELS[field.validationStatus]

  return (
    <article className={`detail-field detail-field--${field.validationStatus.toLowerCase()}`}>
      <div className="detail-field__heading">
        <h3>{fieldLabel(field.name)}</h3>
        <span className={`validation-badge validation-badge--${field.validationStatus.toLowerCase()}`}>
          {shownState}
        </span>
      </div>

      {field.publicationSource === 'HUMAN' ? (
        <div>
          <p className="detail-field__caution">Valor publicado após revisão humana. Estado técnico da extração: {VALIDATION_STATUS_LABELS[field.validationStatus]}.</p>
          {field.confirmedHumanAbsence ? <p className="detail-field__unavailable">Ausência confirmada em revisão documental humana.</p>
            : renderValue(field.value, field.name)}
          {field.publicationReference && <details>
            <summary>Referência documental da revisão humana</summary>
            <dl className="evidence-item__references">
              <div><dt>Versão documental</dt><dd><code>{field.publicationReference.documentVersionId}</code></dd></div>
              {field.publicationReference.page !== null && <div><dt>Página</dt><dd>{field.publicationReference.page}</dd></div>}
              {field.publicationReference.section && <div><dt>Seção</dt><dd>{field.publicationReference.section}</dd></div>}
            </dl>
            {field.publicationReference.quote
              ? <blockquote className="evidence-item__quote">{field.publicationReference.quote}</blockquote>
              : <p className="detail-field__caution">Citação literal indisponível para a revisão publicada.</p>}
            {safeSourceUrl(field.publicationReference.sourceUrl) && <a href={safeSourceUrl(field.publicationReference.sourceUrl)!}
              target="_blank" rel="noopener noreferrer nofollow" referrerPolicy="no-referrer">
              Abrir documento citado pela revisão (nova aba)
            </a>}
          </details>}
          <details><summary>Ver valor original da extração e metadados</summary>
            <div><strong>Valor original:</strong> {renderValue(field.originalValue, field.name)}</div>
            {field.publishedRevisionId && <p>Revisão publicada: <code>{field.publishedRevisionId}</code></p>}
          </details>
        </div>
      ) : field.validationStatus === 'MISSING' ? (
        <p className="detail-field__unavailable">Não identificado pela extração. Isso não confirma ausência no documento.</p>
      ) : field.validationStatus === 'CONFLICT' ? (
        <div>
          <p className="detail-field__caution">Há valores divergentes; nenhum foi escolhido como definitivo.</p>
          {renderValue(field.value, field.name)}
        </div>
      ) : (
        <div>
          {field.validationStatus === 'EXTRACTED' && (
            <p className="detail-field__caution">Este dado foi extraído e ainda não foi validado.</p>
          )}
          {criticalValidatedWithoutEvidence && (
            <p className="detail-field__caution" role="note">
              A API informa “Validado pela regra técnica”, mas não retornou evidência associada.
            </p>
          )}
          {renderValue(field.value ?? field.rawValue, field.name)}
        </div>
      )}

      <section className="detail-field__evidence" aria-label={`Evidências documentais de ${fieldLabel(field.name)}`}>
        {hasEvidence ? (
          <details>
            <summary>
              Evidências documentais ({field.evidence.length})
              <span className="detail-field__evidence-state">Proveniência documental; estado: {VALIDATION_STATUS_LABELS[field.validationStatus]}</span>
            </summary>
            {field.validationStatus === 'CONFLICT' && (
              <p className="detail-field__caution" role="note">
                As evidências abaixo são concorrentes. Nenhuma foi escolhida como verdadeira.
              </p>
            )}
            {hasUnquotedEvidence && (
              <p className="detail-field__caution" role="note">
                Há referências sem citação literal. A associação registrada não comprova, por si só, que o trecho sustenta o campo.
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
                      <div><dt>Campo</dt><dd>{fieldLabel(evidence.fieldName)}</dd></div>
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
          const officialSourceUrl = safeSourceUrl(detailQuery.data.officialUrl)
          return (
            <>
              <section className="detail-hero" aria-labelledby="opportunity-title">
                <p className="eyebrow">Resumo da oportunidade</p>
                <p className="eyebrow">{opportunity.agency.acronym || opportunity.agency.name}</p>
                <h1 id="opportunity-title">{opportunity.title}</h1>
                <p className="detail-hero__agency">{opportunity.agency.name}</p>
                {!!opportunity.sources?.length && <p className="detail-hero__agency">
                  Fonte publicadora: {opportunity.sources.map((source) => source.name).join(', ')}
                </p>}
                {!!opportunity.responsibleAgencies?.length && <p className="detail-hero__agency">
                  Agências responsáveis: {opportunity.responsibleAgencies.map((agency) => agency.acronym).join(', ')}
                </p>}
                {detailQuery.data.summary && <p className="detail-hero__summary">{detailQuery.data.summary}</p>}
                {officialSourceUrl
                  ? <a className="official-source-link" href={officialSourceUrl} target="_blank"
                      rel="noopener noreferrer nofollow" referrerPolicy="no-referrer">
                      Abrir fonte oficial (nova aba)
                    </a>
                  : <p className="official-source-unavailable">Link para fonte oficial indisponível.</p>}
                <dl className="detail-identity">
                  <div><dt>Programa</dt><dd>{opportunity.program || 'Não informado'}</dd></div>
                  <div><dt>Instrumento</dt><dd>{opportunity.instrumentType || 'Não informado'}</dd></div>
                  <div><dt>Status</dt><dd>{opportunity.status}</dd></div>
                  <div><dt>Prazo de submissão</dt><dd>{opportunity.deadline ? formatDate(opportunity.deadline) : 'Não informado'}</dd></div>
                  <div><dt>ID histórico</dt><dd>{opportunity.externalId}</dd></div>
                  {opportunity.portalEntryId && <div><dt>Identificador do portal</dt><dd>{opportunity.portalEntryId}</dd></div>}
                </dl>
              </section>

              <section className="detail-fields" aria-labelledby="fields-title">
                <div className="detail-section-heading">
                  <p className="eyebrow">INFORMAÇÕES DA CHAMADA</p>
                  <h2 id="fields-title">Condições de participação e outros campos estruturados</h2>
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
              <OpportunityHistorySection
                key={callId}
                callId={callId}
                externalId={opportunity.externalId}
                portalEntryId={opportunity.portalEntryId}
              />
            </>
          )
        })()}
      </div>
    </main>
  )
}
