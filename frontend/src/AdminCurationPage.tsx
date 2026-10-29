import { useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminLogin, adminLogout, createAdminRevision, decideAdminRevision, fetchAdminCsrf, fetchAdminQueue,
  fetchAdminRevisions, fetchAdminSession, fetchDocumentVersions, findDocumentVersion, fetchAdminOpportunityDetail,
  fetchOpportunityDocuments, OpportunityNotFoundError, preservedDocumentContentUrl,
  type AdminRevision, type CsrfBootstrap, type DocumentVersion, type ExtractedField, type OpportunityDocument,
} from './api/opportunities'
import {
  CRITICAL_FIELDS, PUBLICATION_SOURCE_LABELS, REVISION_STATUS_LABELS, VALIDATION_STATUS_LABELS,
  fieldLabel, fieldValueText, formatDate, formatDateTimeInput, formatTimestamp, parseDateTimeInput,
} from './fieldPresentation'
import { safeSourceUrl } from './sourceUrlPolicy'
import './AdminCurationPage.css'

const TEXT_FIELDS = new Set(['program', 'instrumentType', 'title', 'summary', 'status'])
const DATE_FIELDS = new Set(['publicationDate', 'openingDate'])
const LIST_FIELDS = new Set(['technologyThemes', 'eligibleExpenses', 'officialLinks'])
const EDITABLE_FIELDS = new Set([
  ...TEXT_FIELDS, ...DATE_FIELDS, 'deadline', 'totalBudget', 'projectFunding', 'eligibility', 'regions', 'trl',
  'requiresIct', 'counterpart', ...LIST_FIELDS, 'evaluationCriteria', 'requiredDocuments',
])

type ObjectValue = Record<string, unknown>
type RangeValue = { condition: string; min: string; max: string }

function record(value: unknown): ObjectValue {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as ObjectValue : {}
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function lines(value: unknown): string[] {
  return list(value).map((entry) => String(entry ?? ''))
}

function statusLabel(status: ExtractedField['validationStatus']): string {
  return VALIDATION_STATUS_LABELS[status]
}

function AdminLogin({ onLogin }: { onLogin: (username: string, password: string) => Promise<void> }) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true)
    const data = new FormData(event.currentTarget)
    try { await onLogin(String(data.get('username') ?? ''), String(data.get('password') ?? '')) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Falha ao entrar.') }
    finally { setBusy(false) }
  }
  return <main className="admin-page"><section className="admin-card" aria-labelledby="admin-login-title">
    <p className="eyebrow">DEMONSTRAÇÃO ISOLADA</p><h1 id="admin-login-title">Acesso administrativo</h1>
    <p>Use as credenciais temporárias configuradas no ambiente local.</p>
    <form onSubmit={(event) => void submit(event)}>
      <label>Usuário<input name="username" autoComplete="username" required /></label>
      <label>Senha<input name="password" type="password" autoComplete="current-password" required /></label>
      {error && <p role="alert">{error}</p>}
      <button className="button button--primary" disabled={busy}>{busy ? 'Entrando…' : 'Entrar'}</button>
    </form>
  </section></main>
}

function ReviewQueue() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const queue = useQuery({ queryKey: ['admin-queue', page], queryFn: () => fetchAdminQueue(page), retry: false })
  const filtered = (queue.data?.content ?? []).filter((item) =>
    `${item.title} ${item.agencyName} ${item.agencyAcronym} ${item.agencies.join(' ')} ${item.publishers.join(' ')}`
      .toLocaleLowerCase('pt-BR').includes(search.trim().toLocaleLowerCase('pt-BR')))
  const pageCount = queue.data ? Math.max(1, Math.ceil(queue.data.totalElements / queue.data.size)) : 1
  return <section className="curation-shell" aria-labelledby="admin-queue-title">
    <div className="curation-page-heading"><div><p className="eyebrow">CENTRAL DE CURADORIA</p>
      <h1 id="admin-queue-title">O que precisa da sua atenção?</h1>
      <p>Confira as informações de cada oportunidade com apoio dos documentos preservados.</p></div></div>
    <label className="curation-search"><span aria-hidden="true">⌕</span><span className="sr-only">Buscar oportunidade ou instituição nesta página</span>
      <input type="search" placeholder="Buscar oportunidade ou instituição nesta página" value={search}
        onChange={(event) => setSearch(event.target.value)} /></label>
    {queue.isPending && <p className="curation-state" role="status">Carregando oportunidades…</p>}
    {queue.isError && <div className="curation-state curation-state--error" role="alert"><p>Não foi possível carregar as oportunidades. A sessão pode ter expirado.</p>
      <button className="button button--secondary" onClick={() => window.location.reload()}>Atualizar e entrar novamente</button></div>}
    {queue.isSuccess && queue.data.content.length === 0 && <p className="curation-state">Nenhuma oportunidade precisa de revisão neste momento.</p>}
    {queue.isSuccess && queue.data.content.length > 0 && filtered.length === 0 &&
      <p className="curation-state">Nenhuma oportunidade desta página corresponde à busca.</p>}
    {queue.isSuccess && filtered.length > 0 && <div className="curation-queue" aria-label="Oportunidades para revisar">
      {filtered.map((item) => <article className="curation-queue-card" key={item.callId}>
        <div className="curation-queue-card__main"><p className="curation-source">
          {item.publishers.length ? item.publishers.join(' · ') : item.agencyAcronym}
          {item.agencies.length > 0 && <> · Agências responsáveis: {item.agencies.join(', ')}</>}</p>
          <h2>{item.title}</h2>
          <div className="curation-card-meta"><span>{fieldValueText('status', item.opportunityStatus)}</span>
            <span>Prazo: {item.deadline ? formatDate(item.deadline) : 'Não informado'}</span></div>
        </div>
        <div className="curation-queue-card__action">
          <span className={item.reviewFieldCount > 0 ? 'curation-pill curation-pill--attention' : 'curation-pill'}>
            {item.reviewFieldCount > 0 ? `${item.reviewFieldCount} ${item.reviewFieldCount === 1 ? 'informação para conferir' : 'informações para conferir'}` : 'Sem pendências técnicas'}
          </span>
          <Link className="button button--primary" to={`/admin/opportunities/${item.callId}`}>
            {item.reviewFieldCount > 0 ? 'Continuar revisão' : 'Ver oportunidade'} <span aria-hidden="true">→</span></Link>
        </div>
      </article>)}
    </div>}
    {queue.data && queue.data.totalElements > queue.data.size && <nav className="curation-pagination" aria-label="Paginação das oportunidades">
      <span>Página {page + 1} de {pageCount} · {queue.data.totalElements} oportunidades na fila</span>
      <div><button className="button button--secondary" disabled={page === 0} onClick={() => { setPage((current) => current - 1); setSearch('') }}>Anterior</button>
        <button className="button button--secondary" disabled={page + 1 >= pageCount}
          onClick={() => { setPage((current) => current + 1); setSearch('') }}>Próxima</button></div>
    </nav>}
    <p className="curation-search-note">A busca considera os resultados da página atual. Use a paginação para consultar os demais resultados.</p>
  </section>
}

function StringListEditor({ label, value, onChange }: { label: string; value: unknown; onChange: (next: string[]) => void }) {
  const entries = lines(value)
  return <fieldset className="admin-structured-group"><legend>{label}</legend>
    {entries.map((entry, index) => <div className="admin-list-entry" key={`${label}-${index}`}>
      <label>{label}, item {index + 1}<input value={entry} onChange={(event) => {
        const next = [...entries]; next[index] = event.target.value; onChange(next)
      }} /></label>
      <button type="button" className="button button--secondary" aria-label={`Remover ${label}, item ${index + 1}`}
        onClick={() => onChange(entries.filter((_, itemIndex) => itemIndex !== index))}>Remover</button>
    </div>)}
    <button type="button" className="button button--secondary" onClick={() => onChange([...entries, ''])}>Adicionar item</button>
  </fieldset>
}

function RangeEditor({ label, value, onChange }: { label: string; value: unknown; onChange: (next: RangeValue[]) => void }) {
  const ranges = list(value).map((entry) => record(entry) as RangeValue)
  function update(index: number, key: keyof RangeValue, next: string) {
    onChange(ranges.map((range, rangeIndex) => rangeIndex === index ? { ...range, [key]: next } : range))
  }
  return <fieldset className="admin-structured-group"><legend>{label}</legend>
    {ranges.map((range, index) => <div className="admin-rule-row" key={`${label}-${index}`}>
      <label>Condição<input value={range.condition ?? ''} onChange={(event) => update(index, 'condition', event.target.value)} /></label>
      <label>Mínimo (%)<input inputMode="decimal" value={range.min ?? ''} onChange={(event) => update(index, 'min', event.target.value)} /></label>
      <label>Máximo (%)<input inputMode="decimal" value={range.max ?? ''} onChange={(event) => update(index, 'max', event.target.value)} /></label>
      <button type="button" className="button button--secondary" onClick={() => onChange(ranges.filter((_, i) => i !== index))}
        aria-label={`Remover faixa de ${label}, item ${index + 1}`}>Remover faixa</button>
    </div>)}
    <button type="button" className="button button--secondary" onClick={() => onChange([...ranges, { condition: '', min: '', max: '' }])}>
      Adicionar faixa
    </button>
  </fieldset>
}

function CriteriaEditor({ value, onChange }: { value: unknown; onChange: (next: { criterion: string; weight: number }[]) => void }) {
  const criteria = list(value).map((entry) => record(entry) as { criterion: string; weight: number })
  function update(index: number, key: 'criterion' | 'weight', next: string) {
    onChange(criteria.map((item, itemIndex) => itemIndex === index
      ? { ...item, [key]: key === 'weight' ? Number(next) : next } : item))
  }
  return <fieldset className="admin-structured-group"><legend>Critérios de avaliação</legend>
    {criteria.map((item, index) => <div className="admin-rule-row" key={`criterion-${index}`}>
      <label>Critério {index + 1}<input value={item.criterion ?? ''} onChange={(event) => update(index, 'criterion', event.target.value)} /></label>
      <label>Peso (pontos)<input type="number" step="any" value={item.weight ?? ''}
        onChange={(event) => update(index, 'weight', event.target.value)} /></label>
      <button type="button" className="button button--secondary" aria-label={`Remover critério ${index + 1}`}
        onClick={() => onChange(criteria.filter((_, itemIndex) => itemIndex !== index))}>Remover</button>
    </div>)}
    <button type="button" className="button button--secondary"
      onClick={() => onChange([...criteria, { criterion: '', weight: 0 }])}>Adicionar critério</button>
  </fieldset>
}

function StructuredEditor({ field, value, onChange }: { field: string; value: unknown; onChange: (next: unknown) => void }) {
  const defaults: Record<string, ObjectValue> = {
    totalBudget: { currency: 'BRL', amount: '', qualifier: 'UNKNOWN' },
    eligibility: { eligible: [], registration: null, excluded: [], federalExecutorRequiresSupportInstitution: null,
      privateNonprofitMinimumOperatingYears: null },
    counterpart: { exempt: [], municipalPercentRanges: [], stateAndFederalDistrictPercentRanges: [] },
    regions: { scope: null, minimumBudgetShare: { regions: [], percentage: null, condition: null } },
    projectFunding: { currency: 'BRL', proposalMaximumByEmployedDoctorCount: [],
      subprojectMinimum: { basicOrExecutiveDesign: null, other: null } },
    requiredDocuments: { general: [], conditionalAnnexes: [] },
    trl: { min: null, max: null },
  }
  const base = { ...(defaults[field] ?? {}), ...record(value) }
  const object = base
  const update = (key: string, next: unknown) => onChange({ ...base, [key]: next })
  const numberOrNull = (raw: string) => raw === '' ? null : Number(raw)
  switch (field) {
    case 'totalBudget': {
      const money = base
      return <fieldset className="admin-structured-group"><legend>Orçamento</legend>
        <label>Moeda<select value={String(money.currency ?? 'BRL')} onChange={(event) => update('currency', event.target.value)}>
          {['BRL', 'USD', 'EUR'].map((currency) => <option key={currency} value={currency}>{currency}</option>)}
        </select></label>
        <label>Valor decimal<input inputMode="decimal" value={String(money.amount ?? '')}
          onChange={(event) => update('amount', event.target.value)} /></label>
        <label>Qualificador<select value={String(money.qualifier ?? 'UNKNOWN')} onChange={(event) => update('qualifier', event.target.value)}>
          <option value="UP_TO">Até</option><option value="RANGE">Faixa</option><option value="EXACT">Valor exato</option>
          <option value="AT_LEAST">A partir de</option><option value="UNKNOWN">Não identificado</option>
        </select></label>
      </fieldset>
    }
    case 'eligibility':
      return <div className="admin-structured-editor">
        <StringListEditor label="Públicos elegíveis" value={object.eligible} onChange={(next) => update('eligible', next)} />
        <label>Cadastro exigido<input value={String(object.registration ?? '')}
          onChange={(event) => update('registration', event.target.value || null)} /></label>
        <StringListEditor label="Exclusões" value={object.excluded} onChange={(next) => update('excluded', next)} />
        <label>Executor federal precisa de instituição de apoio
          <select value={object.federalExecutorRequiresSupportInstitution == null ? '' : String(object.federalExecutorRequiresSupportInstitution)}
            onChange={(event) => update('federalExecutorRequiresSupportInstitution', event.target.value === '' ? null : event.target.value === 'true')}>
            <option value="">Não informado</option><option value="true">Sim</option><option value="false">Não</option>
          </select>
        </label>
        <label>Tempo mínimo de funcionamento (anos)
          <input type="number" min="0" step="1" value={String(object.privateNonprofitMinimumOperatingYears ?? '')}
            onChange={(event) => update('privateNonprofitMinimumOperatingYears', numberOrNull(event.target.value))} />
        </label>
      </div>
    case 'counterpart':
      return <div className="admin-structured-editor">
        <StringListEditor label="Isenções de contrapartida" value={object.exempt} onChange={(next) => update('exempt', next)} />
        <RangeEditor label="Faixas para municípios" value={object.municipalPercentRanges}
          onChange={(next) => update('municipalPercentRanges', next)} />
        <RangeEditor label="Faixas para estados e Distrito Federal" value={object.stateAndFederalDistrictPercentRanges}
          onChange={(next) => update('stateAndFederalDistrictPercentRanges', next)} />
      </div>
    case 'regions': {
      const share = { regions: [], percentage: null, condition: null, ...record(object.minimumBudgetShare) }
      const updateShare = (key: string, next: unknown) => update('minimumBudgetShare', { ...share, [key]: next })
      return <div className="admin-structured-editor">
        <label>Abrangência<input value={String(object.scope ?? '')} onChange={(event) => update('scope', event.target.value || null)} /></label>
        <StringListEditor label="Regiões da participação mínima" value={share.regions}
          onChange={(next) => updateShare('regions', next)} />
        <label>Percentual mínimo (%)<input inputMode="decimal" value={String(share.percentage ?? '')}
          onChange={(event) => updateShare('percentage', event.target.value || null)} /></label>
        <label>Condição<input value={String(share.condition ?? '')}
          onChange={(event) => updateShare('condition', event.target.value || null)} /></label>
      </div>
    }
    case 'projectFunding': {
      const minimum = { basicOrExecutiveDesign: null, other: null, ...record(object.subprojectMinimum) }
      const tiers = list(object.proposalMaximumByEmployedDoctorCount).map((entry) => record(entry))
      const setTier = (index: number, key: string, next: unknown) => update('proposalMaximumByEmployedDoctorCount',
        tiers.map((tier, tierIndex) => tierIndex === index ? { ...tier, [key]: next } : tier))
      return <div className="admin-structured-editor">
        <label>Moeda<select value={String(object.currency ?? 'BRL')} onChange={(event) => update('currency', event.target.value)}>
          {['BRL', 'USD', 'EUR'].map((currency) => <option key={currency} value={currency}>{currency}</option>)}
        </select></label>
        <fieldset className="admin-structured-group"><legend>Limites por quantidade de doutores empregados</legend>
          {tiers.map((tier, index) => <div className="admin-rule-row" key={`funding-tier-${index}`}>
            <label>Quantidade mínima<input type="number" min="0" step="1" value={String(tier.min ?? '')}
              onChange={(event) => setTier(index, 'min', Number(event.target.value))} /></label>
            <label>Quantidade máxima<input type="number" min="0" step="1" value={String(tier.max ?? '')}
              onChange={(event) => setTier(index, 'max', numberOrNull(event.target.value))} /></label>
            <label>Valor máximo<input inputMode="decimal" value={String(tier.amount ?? '')}
              onChange={(event) => setTier(index, 'amount', event.target.value)} /></label>
            <button type="button" className="button button--secondary" onClick={() => update('proposalMaximumByEmployedDoctorCount',
              tiers.filter((_, tierIndex) => tierIndex !== index))}>Remover faixa</button>
          </div>)}
          <button type="button" className="button button--secondary" onClick={() => update('proposalMaximumByEmployedDoctorCount',
            [...tiers, { min: 0, max: null, amount: '' }])}>Adicionar faixa</button>
        </fieldset>
        <fieldset className="admin-structured-group"><legend>Valores mínimos por subprojeto</legend>
          <label>Projeto básico ou executivo<input inputMode="decimal" value={String(minimum.basicOrExecutiveDesign ?? '')}
            onChange={(event) => update('subprojectMinimum', { ...minimum, basicOrExecutiveDesign: event.target.value || null })} /></label>
          <label>Demais subprojetos<input inputMode="decimal" value={String(minimum.other ?? '')}
            onChange={(event) => update('subprojectMinimum', { ...minimum, other: event.target.value || null })} /></label>
        </fieldset>
      </div>
    }
    case 'requiredDocuments':
      return <div className="admin-structured-editor">
        <StringListEditor label="Documentos gerais" value={object.general} onChange={(next) => update('general', next)} />
        <StringListEditor label="Anexos condicionais" value={object.conditionalAnnexes}
          onChange={(next) => update('conditionalAnnexes', next)} />
      </div>
    case 'evaluationCriteria':
      return <CriteriaEditor value={value} onChange={onChange as (next: { criterion: string; weight: number }[]) => void} />
    case 'trl':
      return <fieldset className="admin-structured-group"><legend>Faixa TRL</legend>
        <label>TRL mínimo<input type="number" min="1" max="9" step="1" value={String(object.min ?? '')}
          onChange={(event) => update('min', numberOrNull(event.target.value))} /></label>
        <label>TRL máximo<input type="number" min="1" max="9" step="1" value={String(object.max ?? '')}
          onChange={(event) => update('max', numberOrNull(event.target.value))} /></label>
      </fieldset>
    default:
      return <p role="alert">Este campo estruturado não possui editor disponível.</p>
  }
}

function FieldValueEditor({ field, value, onChange }: { field: ExtractedField; value: unknown; onChange: (next: unknown) => void }) {
  if (field.name === 'totalBudget' || field.name === 'eligibility' || field.name === 'counterpart'
    || field.name === 'regions' || field.name === 'projectFunding' || field.name === 'requiredDocuments'
    || field.name === 'evaluationCriteria' || field.name === 'trl') {
    return <StructuredEditor field={field.name} value={value} onChange={onChange} />
  }
  if (field.name === 'requiresIct') {
    return <label>Necessidade de ICT
      <select value={value == null ? '' : String(value)} onChange={(event) => onChange(event.target.value === '' ? null : event.target.value === 'true')}>
        <option value="">Não informado</option><option value="true">Sim</option><option value="false">Não</option>
      </select>
    </label>
  }
  if (LIST_FIELDS.has(field.name)) {
    return <StringListEditor label={fieldLabel(field.name)} value={value} onChange={onChange as (next: string[]) => void} />
  }
  if (DATE_FIELDS.has(field.name)) return <label>{fieldLabel(field.name)}<input type="date" value={String(value ?? '')}
    onChange={(event) => onChange(event.target.value || null)} /></label>
  if (field.name === 'status') return <label>Situação
    <select value={String(value ?? 'UNKNOWN')} onChange={(event) => onChange(event.target.value)}>
      <option value="OPEN">Aberta</option><option value="UPCOMING">Em breve</option>
      <option value="CLOSED">Encerrada</option><option value="SUSPENDED">Suspensa</option>
      <option value="UNKNOWN">Não identificada</option>
    </select>
  </label>
  if (field.name === 'deadline') return <label>{fieldLabel(field.name)} (data e horário de Brasília)
    <input type="datetime-local" value={formatDateTimeInput(value == null ? null : String(value))}
      onChange={(event) => onChange(parseDateTimeInput(event.target.value))} />
  </label>
  if (TEXT_FIELDS.has(field.name)) return <label>{fieldLabel(field.name)}
    <textarea rows={field.name === 'summary' ? 5 : 2} value={String(value ?? '')}
      onChange={(event) => onChange(event.target.value)} />
  </label>
  return <p role="alert">Este campo não pode ser editado nesta interface.</p>
}

const FIELD_GROUPS = [
  { id: 'general', title: 'Informações gerais', names: ['title', 'summary', 'program', 'instrumentType', 'status', 'publicationDate', 'openingDate', 'deadline'] },
  { id: 'participation', title: 'Quem pode participar', names: ['eligibility', 'regions', 'requiresIct', 'trl'] },
  { id: 'financial', title: 'Condições financeiras', names: ['totalBudget', 'projectFunding', 'counterpart'] },
  { id: 'documents', title: 'Documentos e requisitos', names: ['technologyThemes', 'eligibleExpenses', 'evaluationCriteria', 'requiredDocuments', 'officialLinks'] },
]

function EvidenceContext({ callId, field, documents }: { callId: string; field: ExtractedField; documents: OpportunityDocument[] }) {
  const versions = useQueries({ queries: documents.map((document) => ({
    queryKey: ['admin-doc-versions', callId, document.id],
    queryFn: ({ signal }: { signal: AbortSignal }) => fetchDocumentVersions(callId, document.id, 0, signal),
    enabled: field.evidence.some((evidence) => evidence.documentVersionId !== null),
  })) })
  if (field.evidence.length === 0) return <p className="curation-evidence-empty">Não há evidência específica associada a esta informação. Um documento selecionado como referência não substitui uma citação que comprove o valor.</p>
  return <ul className="curation-evidence-list">{field.evidence.map((evidence) => {
    const match = versions.flatMap((query, index) => (query.data?.content ?? [])
      .filter((version) => version.id === evidence.documentVersionId)
      .map((version) => ({ document: documents[index], version })))[0]
    const url = safeSourceUrl(evidence.sourceUrl)
    return <li key={evidence.id}>
      <strong>{match?.document.title ?? 'Documento associado à oportunidade'}</strong>
      {match && <p>{formatTimestamp(match.version.downloadedAt)}{match.document.versionCount > 1 ? ` · ${match.document.versionCount} versões disponíveis` : ''}</p>}
      {(evidence.page || evidence.section) && <p>{evidence.page ? `Página ${evidence.page}` : ''}{evidence.page && evidence.section ? ' · ' : ''}{evidence.section ? `Seção ${evidence.section}` : ''}</p>}
      {evidence.quote ? <blockquote>{evidence.quote}</blockquote> : <p className="curation-evidence-empty">A citação literal não está disponível. Esta referência não comprova sozinha o valor.</p>}
      {url && <a href={url} target="_blank" rel="noopener noreferrer nofollow" referrerPolicy="no-referrer">Abrir fonte pública em nova aba</a>}
      <details><summary>Detalhes da referência</summary><p>Identificador da versão: <code>{evidence.documentVersionId ?? 'não disponível'}</code></p></details>
    </li>
  })}</ul>
}

function PreservedDocumentPanel({ callId, documents, field, documentId, versionId, versions, versionsPending,
  versionsError, page, onDocumentChange, onVersionChange }: {
  callId: string; documents: OpportunityDocument[]; field: ExtractedField
  documentId: string; versionId: string; versions: DocumentVersion[]
  versionsPending: boolean; versionsError: boolean; page: string
  onDocumentChange: (value: string) => void; onVersionChange: (value: string) => void
}) {
  const [showSelection, setShowSelection] = useState(false)
  const [showPreview, setShowPreview] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [previewFailed, setPreviewFailed] = useState(false)
  const document = documents.find((item) => item.id === documentId)
  const version = versions.find((item) => item.id === versionId)
  const contentUrl = document && version ? preservedDocumentContentUrl(callId, document.id, version.id) : null
  const isPdf = version?.mediaType === 'application/pdf'
  const isDocx = version?.mediaType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  const officialUrl = safeSourceUrl(version?.sourceUrl ?? document?.sourceUrl ?? null)
  const pageNumber = /^\d+$/.test(page) && Number(page) > 0 ? Number(page) : null
  const evidencePage = field.publicationReference?.documentVersionId === versionId
    ? field.publicationReference.page : field.evidence.find((item) => item.documentVersionId === versionId)?.page
  const targetPage = pageNumber ?? evidencePage
  const evidenced = field.publicationReference?.documentVersionId === versionId
    || field.evidence.some((item) => item.documentVersionId === versionId)
  const previewUrl = contentUrl && targetPage ? `${contentUrl}#page=${targetPage}` : contentUrl

  return <section className={`curation-document-panel${expanded ? ' curation-document-panel--expanded' : ''}`}
    aria-label="Visualizador documental">
    <header className="curation-document-panel__header"><div><p className="eyebrow">DOCUMENTO PRESERVADO</p>
      <strong>{document?.title ?? 'Selecione um documento'}</strong>
      {version && <span>Versão de {formatTimestamp(version.downloadedAt)}</span>}</div>
      <div className="curation-document-panel__controls">
        <button type="button" className="button button--secondary" aria-expanded={expanded}
          onClick={() => setExpanded((current) => !current)}>{expanded ? 'Reduzir painel' : 'Ampliar painel'}</button>
        <button type="button" className="button button--secondary" aria-expanded={showPreview}
          onClick={() => setShowPreview((current) => !current)}>{showPreview ? 'Recolher documento' : 'Mostrar documento'}</button>
      </div>
    </header>
    <p className="curation-document-panel__note">Consultar o documento não confirma que ele sustenta esta informação.</p>
    <div className="curation-document-panel__actions">
      <button type="button" className="button button--primary" disabled={!contentUrl || !isPdf}
        onClick={() => { setPreviewFailed(false); setShowPreview(true) }}>Visualizar documento</button>
      {contentUrl && <a className="button button--secondary" href={contentUrl} target="_blank"
        rel="noopener noreferrer" referrerPolicy="no-referrer">
        {isPdf ? 'Abrir em nova aba' : 'Baixar documento preservado'}
      </a>}
      <button type="button" className="button button--secondary" aria-expanded={showSelection}
        onClick={() => setShowSelection((current) => !current)}>Selecionar outro documento</button>
      {targetPage && isPdf && <button type="button" className="button button--secondary"
        disabled={!showPreview} onClick={() => { setPreviewFailed(false); setShowPreview(true) }}>Ir à página {targetPage}</button>}
      {officialUrl && <a href={officialUrl} target="_blank" rel="noopener noreferrer nofollow"
        referrerPolicy="no-referrer">Abrir publicação oficial</a>}
    </div>
    {showSelection && <div className="curation-document-panel__selection">
      <label>Documento de referência<select value={documentId} onChange={(event) => onDocumentChange(event.target.value)}>
        <option value="">Selecione um documento</option>
        {documents.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
      </select></label>
      <label>Versão do documento<select value={versionId} disabled={!documentId || versionsPending}
        onChange={(event) => onVersionChange(event.target.value)}>
        <option value="">{versionsPending ? 'Carregando versões…' : 'Selecione uma versão'}</option>
        {versions.map((item) => <option key={item.id} value={item.id}>{formatTimestamp(item.downloadedAt)}</option>)}
      </select></label>
    </div>}
    {versionsPending && <p role="status">Carregando versões do documento…</p>}
    {versionsError && <p role="alert">Não foi possível carregar as versões deste documento.</p>}
    {document && version && !isPdf && <p className="curation-evidence-empty">{isDocx
      ? 'Este formato não pode ser visualizado aqui. Baixe a versão preservada ou abra a publicação oficial.'
      : 'Este formato não pode ser visualizado com segurança. Consulte a publicação oficial, se disponível.'}</p>}
    {previewFailed && <p role="alert">Não foi possível abrir o documento preservado. Tente em nova aba ou consulte a publicação oficial.</p>}
    {showPreview && isPdf && previewUrl && <iframe key={previewUrl} className="curation-document-preview"
      title={`Visualização de ${document?.title ?? 'documento'}`} src={previewUrl}
      sandbox="allow-scripts" referrerPolicy="no-referrer" onError={() => setPreviewFailed(true)} />}
    {!evidenced && version && <p className="curation-evidence-empty">
      Esta versão não está associada como evidência específica ao campo. Selecioná-la como referência não cria evidência.
    </p>}
    {evidenced && targetPage && <p className="curation-evidence-locator">Página indicada pela evidência: {targetPage}</p>}
  </section>
}

function FieldEditor({ callId, field, csrf, documents, onBack, onNext }: {
  callId: string; field: ExtractedField; csrf: CsrfBootstrap
  documents: OpportunityDocument[]
  onBack: () => void; onNext: () => void
}) {
  const initialReference = field.publicationReference ?? field.evidence.find((entry) => entry.quote && entry.documentVersionId)
    ?? field.evidence.find((entry) => entry.documentVersionId)
  const [action, setAction] = useState<'confirm' | 'correct' | 'absence'>('confirm')
  const [value, setValue] = useState<unknown>(field.value)
  const [quote, setQuote] = useState(initialReference?.quote ?? '')
  const [documentId, setDocumentId] = useState('')
  const [versionId, setVersionId] = useState('')
  const [page, setPage] = useState(initialReference?.page?.toString() ?? '')
  const [section, setSection] = useState(initialReference?.section ?? '')
  const [justification, setJustification] = useState('')
  const [absenceReviewComplete, setAbsenceReviewComplete] = useState(false)
  const [decisionReason, setDecisionReason] = useState('')
  const [feedback, setFeedback] = useState('')
  const [showSummary, setShowSummary] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [historyPage, setHistoryPage] = useState(0)
  const client = useQueryClient()
  const versionQueries = useQueries({ queries: documents.map((document) => ({
    queryKey: ['admin-doc-versions', callId, document.id],
    queryFn: ({ signal }: { signal: AbortSignal }) => fetchDocumentVersions(callId, document.id, 0, signal),
  })) })
  const evidenceVersionId = initialReference?.documentVersionId ?? ''
  const firstPageMatchIndex = versionQueries.findIndex((query) => query.data?.content.some((version) => version.id === evidenceVersionId))
  const evidenceVersionLookup = useQuery({
    queryKey: ['admin-evidence-version', callId, evidenceVersionId, documents.map((document) => document.id)],
    queryFn: () => findDocumentVersion(callId, documents, evidenceVersionId, versionQueries.map((query) => query.data)),
    enabled: Boolean(evidenceVersionId) && firstPageMatchIndex < 0 && versionQueries.length > 0
      && versionQueries.every((query) => query.isSuccess),
    retry: false,
  })
  const evidenceDocumentId = initialReference && 'documentId' in initialReference
    ? initialReference.documentId
    : firstPageMatchIndex >= 0 ? documents[firstPageMatchIndex]?.id : evidenceVersionLookup.data?.documentId ?? ''
  const selectedDocumentId = documentId || evidenceDocumentId
    || (evidenceVersionId ? '' : documents.find((document) => document.versionCount > 0)?.id || '')
  const selectedDocument = documents.find((document) => document.id === selectedDocumentId)
  const selectedVersionQueryIndex = documents.findIndex((document) => document.id === selectedDocumentId)
  const selectedVersionQuery = selectedVersionQueryIndex >= 0 ? versionQueries[selectedVersionQueryIndex] : undefined
  const availableVersions = selectedVersionQuery?.data?.content ?? []
  const selectedVersionId = versionId || (selectedDocumentId === evidenceDocumentId
    ? evidenceVersionId : availableVersions[0]?.id ?? '')
  const selectedVersion = availableVersions.find((version) => version.id === selectedVersionId)
    ?? (evidenceVersionLookup.data?.version.id === selectedVersionId ? evidenceVersionLookup.data.version : undefined)
  const versionsPending = (selectedVersionQuery?.isPending ?? false) || evidenceVersionLookup.isFetching
  const versionsError = (selectedVersionQuery?.isError ?? false) || evidenceVersionLookup.isError
  const history = useQuery({ queryKey: ['admin-revisions', callId, field.name, historyPage],
    queryFn: () => fetchAdminRevisions(callId, field.name, historyPage), retry: false, enabled: showHistory })
  const create = useMutation({ mutationFn: (payload: unknown) => createAdminRevision(callId, csrf, payload),
    onSuccess: async () => {
      setFeedback('Revisão enviada para aprovação. O catálogo público ainda não foi alterado.')
      setShowSummary(false); setShowHistory(true)
      await Promise.all([client.invalidateQueries({ queryKey: ['admin-revisions', callId, field.name] }),
        client.invalidateQueries({ queryKey: ['admin-queue'] })])
    }, onError: (error: Error) => setFeedback(error.message) })
  const decide = useMutation({ mutationFn: ({ revision, approve }: { revision: AdminRevision; approve: boolean }) =>
      decideAdminRevision(revision, approve, decisionReason, csrf),
    onSuccess: async (revision) => {
      setFeedback(revision.status === 'APPROVED' ? 'Informação homologada e publicada.' : 'Proposta rejeitada; o valor público foi mantido.')
      await Promise.all([client.invalidateQueries({ queryKey: ['admin-revisions', callId, field.name] }),
        client.invalidateQueries({ queryKey: ['admin-opportunity-detail', callId] }),
        client.invalidateQueries({ queryKey: ['opportunity-detail', callId] }),
        client.invalidateQueries({ queryKey: ['opportunities'] }), client.invalidateQueries({ queryKey: ['admin-queue'] })])
    }, onError: (error: Error) => setFeedback(error.message) })

  function prepareSubmission(event: FormEvent) {
    event.preventDefault(); setFeedback('')
    if (action !== 'absence' && value == null) { setFeedback('Não há valor atual para confirmar. Escolha corrigir ou registrar ausência.'); return }
    if (action === 'correct' && JSON.stringify(value) === JSON.stringify(field.value)) { setFeedback('O valor não mudou. Para manter o valor atual, escolha “Confirmar este valor”.'); return }
    if (action === 'absence' && !absenceReviewComplete) { setFeedback('Confirme que revisou os documentos e versões aplicáveis antes de registrar a ausência.'); return }
    if (!justification.trim()) { setFeedback('Informe a justificativa da revisão.'); return }
    if (!selectedDocument || !selectedVersion) {
      setFeedback('Selecione um documento e uma versão correspondente.'); return
    }
    if (action !== 'absence' && !quote.trim()) { setFeedback('Informe um trecho literal que sustente a confirmação ou correção.'); return }
    setShowSummary(true)
  }
  function submitRevision() {
    if (!selectedDocument || !selectedVersion) { setFeedback('Selecione um documento e uma versão correspondente.'); return }
    create.mutate({ fieldName: field.name, valueKind: action === 'absence' ? 'CONFIRMED_ABSENCE' : 'VALUE',
      value: action === 'absence' ? null : value, justification: justification.trim(), documentId: selectedDocument.id,
      documentVersionId: selectedVersion.id, page: page ? Number(page) : null, section: section.trim() || null,
      quote: quote.trim() || null, locator: null, absenceReviewComplete: action === 'absence' && absenceReviewComplete })
  }
  const technicalStatus = statusLabel(field.validationStatus)
  const currentValue = fieldValueText(field.name, field.value)
  return <section className="curation-review" aria-labelledby="review-question">
    <nav className="curation-breadcrumb" aria-label="Navegação"><button onClick={onBack}>Visão geral</button><span aria-hidden="true">/</span><span>Revisar informação</span></nav>
    <div className="curation-review-heading"><p className="eyebrow">{fieldLabel(field.name)}{CRITICAL_FIELDS.has(field.name) ? ' · INFORMAÇÃO CRÍTICA' : ''}</p>
      <h1 id="review-question">{field.name === 'deadline' ? 'O prazo de inscrição está correto?' : `A informação “${fieldLabel(field.name).toLocaleLowerCase('pt-BR')}” está correta?`}</h1>
      <p>Compare o valor com a fonte. A decisão será enviada para aprovação e não publicará automaticamente.</p></div>
    <div className="curation-review-grid">
      <div className="curation-review-main">
        <section className="curation-current-value"><p className="curation-source">VALOR ATUAL · {PUBLICATION_SOURCE_LABELS[field.publicationSource ?? 'EXTRACTION']}</p>
          <strong>{currentValue}</strong><span className={`validation-badge validation-badge--${field.validationStatus.toLowerCase()}`}>{technicalStatus}</span>
          <details><summary>Ver extração original e estado técnico</summary><dl>
            <div><dt>Valor original extraído</dt><dd>{fieldValueText(field.name, field.originalValue ?? field.value)}</dd></div>
            <div><dt>Estado técnico</dt><dd>{technicalStatus}</dd></div>
            {field.publishedRevisionId && <div><dt>Revisão publicada</dt><dd><code>{field.publishedRevisionId}</code></dd></div>}
          </dl><p>A revisão humana não altera o estado técnico da extração.</p></details>
        </section>
        <form className="curation-action-form" onSubmit={(event) => { void prepareSubmission(event) }}>
          <fieldset><legend>O que você concluiu?</legend>
            <label className="curation-choice"><input type="radio" name="curation-action" value="confirm" checked={action === 'confirm'}
              onChange={() => { setAction('confirm'); setValue(field.value); setQuote((field.publicationReference?.documentVersionId === selectedVersionId
                ? field.publicationReference.quote : field.evidence.find((entry) => entry.documentVersionId === selectedVersionId)?.quote) ?? ''); setShowSummary(false) }} />
              <span><strong>Confirmar este valor</strong><small>A fonte sustenta o valor atual.</small></span></label>
            <label className="curation-choice"><input type="radio" name="curation-action" value="correct" checked={action === 'correct'}
              onChange={() => { setAction('correct'); setShowSummary(false) }} />
              <span><strong>Corrigir o valor</strong><small>O documento apresenta outra informação.</small></span></label>
            <label className="curation-choice"><input type="radio" name="curation-action" value="absence" checked={action === 'absence'}
              onChange={() => { setAction('absence'); setValue(null); setQuote(''); setShowSummary(false) }} />
              <span><strong>Registrar que não foi localizado</strong><small>Use somente depois de revisar os documentos e versões aplicáveis.</small></span></label>
          </fieldset>
          {action === 'correct' && <div className="curation-editor"><FieldValueEditor field={field} value={value} onChange={setValue} /></div>}
          {action === 'absence' && <label className="curation-acknowledgement"><input type="checkbox" checked={absenceReviewComplete}
            onChange={(event) => setAbsenceReviewComplete(event.target.checked)} />
            Revisei os documentos e versões aplicáveis da oportunidade e não localizei esta informação.</label>}
          <label>Justificativa<textarea required maxLength={4000} value={justification} onChange={(event) => setJustification(event.target.value)}
            placeholder={action === 'absence' ? 'Registre quais documentos e versões foram consultados.' : 'Explique como a fonte sustenta esta decisão.'} /></label>
          {documents.length === 0 && <p className="curation-evidence-empty" role="alert">Não há documentos disponíveis para vincular à revisão. Não é possível enviá-la com segurança.</p>}
          <div className="curation-reference-grid">
            <label>Página, se aplicável<input type="number" min="1" value={page} onChange={(event) => setPage(event.target.value)} /></label>
            <label>Seção, se aplicável<input value={section} onChange={(event) => setSection(event.target.value)} /></label>
          </div>
          <label>Trecho literal da fonte{action === 'absence' ? ' (opcional)' : ''}<textarea required={action !== 'absence'} maxLength={8000}
            value={quote} onChange={(event) => setQuote(event.target.value)} /></label>
          {versionsError && <p role="alert">Não foi possível carregar as versões deste documento.</p>}
          {feedback && <p className="curation-feedback" role="status">{feedback}</p>}
          {showSummary ? <section className="curation-summary" aria-live="polite"><h2>Confira antes de enviar</h2>
            <p><strong>Ação:</strong> {action === 'confirm' ? 'confirmar o valor atual' : action === 'correct' ? 'propor correção' : 'registrar ausência documental'}</p>
            <p><strong>Valor proposto:</strong> {action === 'absence' ? 'Não localizado após revisão documental' : fieldValueText(field.name, value)}</p>
            <p><strong>Referência:</strong> {selectedDocument?.title} · {page ? `página ${page}` : 'sem página indicada'}{section ? ` · ${section}` : ''}</p>
            <blockquote>{quote || 'Nenhuma citação literal (registro de ausência).'}</blockquote><p><strong>Justificativa:</strong> {justification}</p>
            <div className="curation-action-buttons"><button type="button" className="button button--secondary" onClick={() => setShowSummary(false)}>Voltar e editar</button>
              <button type="button" className="button button--primary" disabled={create.isPending} onClick={submitRevision}>{create.isPending ? 'Enviando…' : 'Enviar para aprovação'}</button></div>
          </section> : <button className="button button--primary" disabled={documents.length === 0 || versionsPending || !selectedVersion}>Revisar resumo</button>}
        </form>
        <details className="curation-history" open={showHistory} onToggle={(event) => setShowHistory(event.currentTarget.open)}>
          <summary>Histórico desta informação</summary>
          {showHistory && <div>{history.isPending && <p role="status">Carregando histórico…</p>}
            {history.isError && <p role="alert">Não foi possível carregar o histórico. Tente novamente.</p>}
            {history.data?.content.map((revision) => <article className="curation-revision" key={revision.id}>
              <div><strong>{REVISION_STATUS_LABELS[revision.status]}</strong><span>{formatTimestamp(revision.createdAt)}</span></div>
              <p>{revision.valueKind === 'CONFIRMED_ABSENCE' ? 'Ausência registrada após revisão documental' : fieldValueText(field.name, revision.proposedValue)}</p>
              <p>{revision.justification}</p>{revision.quote && <blockquote>{revision.quote}</blockquote>}
              <p>Registrada por {revision.createdBy}{revision.decidedBy ? ` · decisão por ${revision.decidedBy}` : ''}</p>
              {revision.status === 'PENDING' && <div className="curation-decision"><label>Motivo da decisão<textarea required value={decisionReason}
                onChange={(event) => setDecisionReason(event.target.value)} /></label>
                <button type="button" className="button button--primary" disabled={!decisionReason.trim() || decide.isPending}
                  onClick={() => decide.mutate({ revision, approve: true })}>Aprovar e publicar</button>
                <button type="button" className="button button--secondary" disabled={!decisionReason.trim() || decide.isPending}
                  onClick={() => decide.mutate({ revision, approve: false })}>Rejeitar</button></div>}
            </article>)}
            {history.data?.content.length === 0 && <p>Ainda não há revisões para esta informação.</p>}
            {history.data && history.data.totalElements > history.data.size && <nav className="curation-pagination" aria-label="Paginação do histórico">
              <span>Página {historyPage + 1} · {history.data.totalElements} revisões</span>
              <button className="button button--secondary" disabled={historyPage === 0} onClick={() => setHistoryPage((page) => page - 1)}>Anteriores</button>
              <button className="button button--secondary" disabled={(historyPage + 1) * history.data.size >= history.data.totalElements}
                onClick={() => setHistoryPage((page) => page + 1)}>Próximas</button></nav>}
          </div>}
        </details>
      </div>
      <aside className="curation-review-aside"><p className="eyebrow">REFERÊNCIA DOCUMENTAL</p>
        <EvidenceContext callId={callId} field={field} documents={documents} />
        {evidenceVersionLookup.isFetching && <p role="status">Localizando a versão documental associada à evidência…</p>}
        {evidenceVersionLookup.isSuccess && evidenceVersionLookup.data === null && evidenceVersionId &&
          <p role="alert">A versão associada à evidência não foi localizada. Selecione explicitamente um documento e uma versão; a citação existente não será atribuída a outra fonte.</p>}
        <PreservedDocumentPanel callId={callId} documents={documents} field={field} documentId={selectedDocumentId}
          versionId={selectedVersionId} versions={selectedVersionId === evidenceVersionLookup.data?.version.id
            ? [...availableVersions, evidenceVersionLookup.data.version] : availableVersions} versionsPending={versionsPending}
          versionsError={versionsError} page={page}
          onDocumentChange={(nextId) => { setDocumentId(nextId); setVersionId(''); setQuote(''); setPage(''); setSection('') }}
          onVersionChange={(nextId) => {
            setVersionId(nextId)
            const nextReference = field.publicationReference?.documentVersionId === nextId
              ? field.publicationReference : field.evidence.find((entry) => entry.documentVersionId === nextId)
            setQuote(nextReference?.quote ?? ''); setPage(nextReference?.page?.toString() ?? '')
            setSection(nextReference?.section ?? '')
          }} />
        <details><summary>Sobre os estados</summary><p>“Extraído” descreve o resultado técnico. A homologação humana é registrada separadamente e não transforma o estado técnico da IA.</p></details></aside>
    </div>
    <div className="curation-review-footer"><button className="button button--secondary" onClick={onBack}>Voltar à oportunidade</button>
      <button className="button button--secondary" onClick={onNext}>Próxima informação para conferir →</button></div>
  </section>
}

function AdminOpportunity({ callId, csrf }: { callId: string; csrf: CsrfBootstrap }) {
  const detail = useQuery({ queryKey: ['admin-opportunity-detail', callId],
    queryFn: ({ signal }) => fetchAdminOpportunityDetail(callId, signal) })
  const docs = useQuery({ queryKey: ['admin-docs', callId], queryFn: ({ signal }) => fetchOpportunityDocuments(callId, 0, signal) })
  const [searchParams, setSearchParams] = useSearchParams()
  const allFields = (detail.data?.fields ?? []).filter((field) => EDITABLE_FIELDS.has(field.name))
  const selectedName = searchParams.get('revisar')
  const selectedField = allFields.find((field) => field.name === selectedName)
  const agencies = detail.data?.opportunity.responsibleAgencies ?? []
  const criticalFields = allFields.filter((field) => CRITICAL_FIELDS.has(field.name))
  const reviewedCritical = criticalFields.filter((field) => field.publicationSource === 'HUMAN').length
  const grouped = FIELD_GROUPS.map((group) => ({ ...group, fields: allFields.filter((field) => group.names.includes(field.name)) }))
  const knownNames = new Set(FIELD_GROUPS.flatMap((group) => group.names))
  const ungrouped = allFields.filter((field) => !knownNames.has(field.name))
  if (ungrouped.length) grouped.push({ id: 'other', title: 'Outras informações', names: [], fields: ungrouped })
  const openReview = (field: ExtractedField) => setSearchParams({ revisar: field.name })
  const closeReview = () => setSearchParams({})
  const nextReview = () => {
    const next = allFields.find((field) => field.name !== selectedField?.name && field.publicationSource !== 'HUMAN')
      ?? allFields.find((field) => field.name !== selectedField?.name)
    if (next) openReview(next)
    else closeReview()
  }
  return <section className="curation-shell curation-opportunity" aria-labelledby="admin-opportunity-title">
    {detail.isPending && <p role="status">Carregando oportunidade…</p>}
    {detail.isError && <div className="curation-state curation-state--error" role="alert"><h1>{detail.error instanceof OpportunityNotFoundError ? 'Oportunidade não encontrada' : 'Não foi possível carregar a oportunidade'}</h1>
      <p>{detail.error instanceof OpportunityNotFoundError ? 'Esta oportunidade não está disponível para revisão.' : 'A sessão pode ter expirado. Volte à fila ou atualize a página para entrar novamente.'}</p>
      <Link to="/admin" className="button button--secondary">Voltar à fila</Link></div>}
    {detail.data && !selectedField && <>
      <nav className="curation-breadcrumb" aria-label="Navegação"><Link to="/admin">Fila de oportunidades</Link><span aria-hidden="true">/</span><span>Visão geral</span></nav>
      <div className="curation-opportunity-heading"><div><p className="curation-source">{detail.data.opportunity.sources?.map((source) => source.name).join(' · ') || detail.data.opportunity.agency.acronym}</p>
        <h1 id="admin-opportunity-title">{detail.data.opportunity.title}</h1>
        <p>{agencies.length ? `Agências responsáveis: ${agencies.map((agency) => `${agency.acronym} (${agency.primary ? 'principal' : 'corresponsável'})`).join(', ')}` : detail.data.opportunity.agency.name}</p></div>
        <Link className="button button--secondary" to={`/opportunities/${callId}`}>Ver página pública</Link></div>
      <dl className="curation-facts"><div><dt>Situação</dt><dd>{fieldValueText('status', detail.data.opportunity.status)}</dd></div>
        <div><dt>Prazo</dt><dd>{detail.data.opportunity.deadline ? formatDate(detail.data.opportunity.deadline) : 'Não informado'}</dd></div>
        <div><dt>Orçamento</dt><dd>{fieldValueText('totalBudget', allFields.find((field) => field.name === 'totalBudget')?.value)}</dd></div>
        <div><dt>Instrumento</dt><dd>{detail.data.opportunity.instrumentType ?? 'Não informado'}</dd></div></dl>
      <section className="curation-progress" aria-label="Progresso de revisão humana"><div><strong>{reviewedCritical} de {criticalFields.length}</strong><span>informações críticas homologadas por revisão humana</span></div>
        <progress value={reviewedCritical} max={Math.max(1, criticalFields.length)} aria-label="Progresso de homologação humana" />
        <p>Este progresso não altera os estados técnicos da extração.</p></section>
      <div className="curation-section-list">{grouped.map((group) => <section className="curation-field-group" key={group.id} aria-labelledby={`group-${group.id}`}>
        <header><div><h2 id={`group-${group.id}`}>{group.title}</h2><span>{group.fields.length} {group.fields.length === 1 ? 'informação' : 'informações'}</span></div>
          {group.fields.some((field) => field.publicationSource !== 'HUMAN') && <span className="curation-pill curation-pill--attention">Precisa de atenção</span>}
        </header>
        {group.fields.length === 0 ? <p className="curation-state">Nenhuma informação disponível nesta seção.</p> : <ul>
          {group.fields.map((field) => <li key={field.name}><div><h3>{fieldLabel(field.name)} {CRITICAL_FIELDS.has(field.name) && <span className="curation-critical">Crítica</span>}</h3>
            <p>{fieldValueText(field.name, field.value)}</p><div className="curation-field-meta"><span className={`validation-badge validation-badge--${field.validationStatus.toLowerCase()}`}>{statusLabel(field.validationStatus)}</span>
              <span>{field.publicationSource === 'HUMAN' ? 'Revisão humana publicada' : 'Aguardando revisão humana'}</span>
              {field.evidence.length > 0 && <span>{field.evidence.length} {field.evidence.length === 1 ? 'evidência associada' : 'evidências associadas'}</span>}</div></div>
            <button className="button button--secondary" onClick={() => openReview(field)}>{field.publicationSource === 'HUMAN' ? 'Ver revisão' : 'Conferir informação'} →</button></li>)}</ul>}
      </section>)}</div>
      <section className="curation-documents"><header><div><h2>Documentos da oportunidade</h2><p>Fontes preservadas para consulta durante a revisão.</p></div></header>
        {docs.isPending && <p role="status">Carregando documentos…</p>}{docs.isError && <p role="alert">Não foi possível carregar os documentos desta oportunidade.</p>}
        {docs.data?.content.map((document) => { const url = safeSourceUrl(document.sourceUrl); return <article key={document.id}>
          <div><strong>{document.title}</strong><span>{document.versionCount} {document.versionCount === 1 ? 'versão' : 'versões'} preservadas</span></div>
          {url && <a href={url} target="_blank" rel="noopener noreferrer nofollow" referrerPolicy="no-referrer">Abrir fonte pública</a>}
        </article>})}
        {docs.data?.content.length === 0 && <p>Nenhum documento foi associado a esta oportunidade.</p>}
      </section>
      {allFields.length === 0 && <p className="curation-state">Nenhum campo compatível com a revisão está disponível.</p>}
    </>}
    {detail.data && selectedField && <FieldEditor key={`${callId}-${selectedField.name}`} callId={callId} field={selectedField} csrf={csrf}
      documents={docs.data?.content ?? []} onBack={closeReview} onNext={nextReview} />}
  </section>
}

export default function AdminCurationPage() {
  const { callId } = useParams()
  const client = useQueryClient()
  const csrfQuery = useQuery({ queryKey: ['admin-csrf'], queryFn: fetchAdminCsrf, retry: false })
  const session = useQuery({ queryKey: ['admin-session'], queryFn: fetchAdminSession, retry: false, enabled: csrfQuery.isSuccess })
  async function login(username: string, password: string) {
    if (!csrfQuery.data) throw new Error('Proteção CSRF indisponível.')
    await adminLogin(username, password, csrfQuery.data)
    await client.invalidateQueries({ queryKey: ['admin-csrf'] }); await client.invalidateQueries({ queryKey: ['admin-session'] })
  }
  async function logout() {
    if (csrfQuery.data) await adminLogout(csrfQuery.data)
    await client.invalidateQueries({ queryKey: ['admin-csrf'] }); await client.invalidateQueries({ queryKey: ['admin-session'] })
  }
  return <main className="admin-page"><header className="admin-header"><Link className="brand" to="/">fomentiq</Link>
    <nav><Link to="/">Catálogo público</Link>{session.data && <button className="button button--secondary" onClick={() => void logout()}>Sair</button>}</nav>
  </header>
    {csrfQuery.isPending && <p role="status">Preparando acesso administrativo…</p>}
    {csrfQuery.isError && <section className="admin-card" role="alert"><h1>Administração indisponível</h1><p>{csrfQuery.error.message}</p></section>}
    {csrfQuery.data && session.isPending && <p role="status">Verificando sessão…</p>}
    {csrfQuery.data && session.isError && <AdminLogin onLogin={login} />}
    {session.data && (callId ? <AdminOpportunity callId={callId} csrf={csrfQuery.data!} /> : <ReviewQueue />)}
  </main>
}
