import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminLogin, adminLogout, createAdminRevision, decideAdminRevision, fetchAdminCsrf, fetchAdminQueue,
  fetchAdminRevisions, fetchAdminSession, fetchDocumentVersions, fetchAdminOpportunityDetail,
  fetchOpportunityDocuments, type AdminRevision, type CsrfBootstrap, type ExtractedField,
} from './api/opportunities'
import './AdminCurationPage.css'

const fieldLabels: Record<string, string> = {
  deadline: 'Prazo de submissão', totalBudget: 'Orçamento', eligibility: 'Elegibilidade',
  counterpart: 'Contrapartida', requiresIct: 'Exigência de ICT', trl: 'TRL',
  instrumentType: 'Instrumento', program: 'Programa', title: 'Título', summary: 'Resumo',
}
const jsonValueFields = new Set([
  'totalBudget', 'projectFunding', 'eligibility', 'regions', 'trl', 'requiresIct', 'counterpart',
  'technologyThemes', 'eligibleExpenses', 'evaluationCriteria', 'requiredDocuments', 'officialLinks',
])

function jsonText(value: unknown): string {
  if (value === null || value === undefined) return ''
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function AdminLogin({ onLogin }: { onLogin: (username: string, password: string) => Promise<void> }) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true)
    const data = new FormData(event.currentTarget)
    try { await onLogin(String(data.get('username') ?? ''), String(data.get('password') ?? '')) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Falha no login.') }
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
  const queue = useQuery({ queryKey: ['admin-queue'], queryFn: () => fetchAdminQueue(), retry: false })
  return <section className="admin-card" aria-labelledby="admin-queue-title">
    <p className="eyebrow">CURADORIA</p><h1 id="admin-queue-title">Oportunidades para revisar</h1>
    {queue.isPending && <p role="status">Carregando fila…</p>}
    {queue.isError && <p role="alert">{queue.error.message}</p>}
    {queue.isSuccess && queue.data.content.length === 0 && <p role="status">Nenhuma oportunidade precisa de revisão.</p>}
    {queue.isSuccess && queue.data.content.length > 0 && <ul className="admin-queue">
      {queue.data.content.map((item) => <li key={item.callId}>
        <div><h2>{item.title}</h2><p>{item.agencyAcronym} · Fonte: {item.publishers.join(', ') || 'não informada'} ·
          {` ${item.reviewFieldCount} campos para revisar`}</p></div>
        <Link className="button button--secondary" to={`/admin/opportunities/${item.callId}`}>Revisar oportunidade</Link>
      </li>)}
    </ul>}
  </section>
}

function FieldReview({ callId, field, csrf }: { callId: string; field: ExtractedField; csrf: CsrfBootstrap }) {
  const [expanded, setExpanded] = useState(false)
  const [historyPage, setHistoryPage] = useState(0)
  const [value, setValue] = useState(jsonText(field.value))
  const [quote, setQuote] = useState(field.evidence.find((entry) => entry.quote)?.quote ?? '')
  const [documentId, setDocumentId] = useState('')
  const [versionId, setVersionId] = useState('')
  const [page, setPage] = useState('')
  const [section, setSection] = useState('')
  const [justification, setJustification] = useState('')
  const [decisionReason, setDecisionReason] = useState('')
  const [confirmedAbsence, setConfirmedAbsence] = useState(false)
  const [message, setMessage] = useState('')
  const client = useQueryClient()
  const docs = useQuery({ queryKey: ['admin-docs', callId],
    queryFn: ({ signal }) => fetchOpportunityDocuments(callId, 0, signal), enabled: expanded })
  const selectedDocumentId = documentId || docs.data?.content.find((document) => document.versionCount > 0)?.id || ''
  const versions = useQuery({ queryKey: ['admin-doc-versions', callId, selectedDocumentId],
    queryFn: ({ signal }) => fetchDocumentVersions(callId, selectedDocumentId, 0, signal),
    enabled: expanded && Boolean(selectedDocumentId) })
  const history = useQuery({ queryKey: ['admin-revisions', callId, field.name, historyPage],
    queryFn: () => fetchAdminRevisions(callId, field.name, historyPage), retry: false, enabled: expanded })
  const create = useMutation({ mutationFn: (payload: unknown) => createAdminRevision(callId, csrf, payload),
    onSuccess: async () => { setMessage('Revisão pendente salva.'); setJustification(''); await client.invalidateQueries({ queryKey: ['admin-revisions', callId, field.name] }) },
    onError: (error: Error) => setMessage(error.message) })
  const decide = useMutation({ mutationFn: ({ revision, approve }: { revision: AdminRevision; approve: boolean }) =>
      decideAdminRevision(revision, approve, decisionReason, csrf),
    onSuccess: async () => { setMessage('Decisão registrada.'); await Promise.all([
      client.invalidateQueries({ queryKey: ['admin-revisions', callId, field.name] }),
      client.invalidateQueries({ queryKey: ['opportunity-detail', callId] }),
      client.invalidateQueries({ queryKey: ['admin-opportunity-detail', callId] }),
      client.invalidateQueries({ queryKey: ['opportunities'] }),
    ]) }, onError: (error: Error) => setMessage(error.message) })
  function submit(event: FormEvent) {
    event.preventDefault(); setMessage('')
    let parsed: unknown = value
    if (!confirmedAbsence && jsonValueFields.has(field.name)) {
      try { parsed = JSON.parse(value) } catch { setMessage('O valor estruturado precisa ser JSON válido.'); return }
    }
    const selectedDoc = docs.data?.content.find((doc) => doc.id === selectedDocumentId)
    const selectedVersion = versions.data?.content.find((version) => version.id === versionId)
    if (!selectedDoc || !selectedVersion) { setMessage('Selecione o documento e sua versão.'); return }
    create.mutate({ fieldName: field.name, valueKind: confirmedAbsence ? 'CONFIRMED_ABSENCE' : 'VALUE',
      value: confirmedAbsence ? null : parsed, justification, documentId: selectedDocumentId,
      documentVersionId: versionId,
      page: page ? Number(page) : null, section: section || null, quote: quote.trim() || null, locator: null,
      absenceReviewComplete: confirmedAbsence })
  }
  return <details className="admin-field" open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}>
    <summary className="admin-field__title"><h2>{fieldLabels[field.name] ?? field.name}</h2>
      <span>{field.validationStatus} · original da extração: {jsonText(field.originalValue ?? field.value) || 'não identificado'}</span></summary>
    {expanded && <>
    <details className="admin-field__evidence">
      <summary>Consultar evidências existentes ({field.evidence.length})</summary>
      {field.evidence.length === 0 && <p>Nenhuma evidência está associada ao registro de extração.</p>}
      <ul>{field.evidence.map((evidence) => <li key={evidence.id}>
        <p>Documento/versão: <code>{evidence.documentVersionId ?? 'não identificado'}</code>
          {evidence.page ? ` · página ${evidence.page}` : ''}{evidence.section ? ` · seção ${evidence.section}` : ''}</p>
        {evidence.quote ? <blockquote>{evidence.quote}</blockquote>
          : <p role="note">Citação literal indisponível. Esta referência, isoladamente, não comprova o valor do campo.</p>}
      </li>)}</ul>
    </details>
    <form onSubmit={(event) => { event.preventDefault(); void submit(event) }}>
      <label>Valor proposto<textarea rows={field.name === 'eligibility' || field.name === 'counterpart' || field.name === 'totalBudget' ? 6 : 2}
        value={value} onChange={(event) => setValue(event.target.value)} disabled={confirmedAbsence} /></label>
      <label className="admin-check"><input type="checkbox" checked={confirmedAbsence} onChange={(event) => setConfirmedAbsence(event.target.checked)} />
        Confirmar que a informação está ausente após revisão documental completa</label>
      <label>Justificativa<textarea required maxLength={4000} value={justification} onChange={(event) => setJustification(event.target.value)} /></label>
      <div className="admin-reference-grid">
        <label>Documento<select required value={selectedDocumentId} onChange={(event) => { setDocumentId(event.target.value); setVersionId('') }}>
          <option value="">Selecione…</option>{docs.data?.content.map((doc) => <option key={doc.id} value={doc.id}>{doc.title}</option>)}</select></label>
        <label>Versão<select required value={versionId} onChange={(event) => setVersionId(event.target.value)}>
          <option value="">Selecione…</option>{versions.data?.content.map((version) => <option key={version.id} value={version.id}>{version.downloadedAt} · {version.contentHash.slice(0, 12)}</option>)}</select></label>
        <label>Página<input type="number" min="1" value={page} onChange={(event) => setPage(event.target.value)} /></label>
        <label>Seção<input value={section} onChange={(event) => setSection(event.target.value)} /></label>
      </div>
      <label>Trecho documental literal{confirmedAbsence && ' (opcional para ausência confirmada)'}
        <textarea required={!confirmedAbsence} maxLength={8000} value={quote}
          onChange={(event) => setQuote(event.target.value)} /></label>
      <button className="button button--primary" disabled={create.isPending}>Salvar revisão pendente</button>
    </form>
    {message && <p role="status">{message}</p>}
    <section aria-label={`Histórico de ${fieldLabels[field.name] ?? field.name}`} className="admin-revision-history">
      <h3>Histórico de revisões</h3>{history.isPending && <p role="status">Carregando histórico…</p>}
      {history.isError && <p role="alert">{history.error.message}</p>}
      {history.data?.content.map((revision) => <article key={revision.id} className="admin-revision">
        <p><strong>{revision.status}</strong> · {revision.createdBy} · {new Date(revision.createdAt).toLocaleString('pt-BR')}</p>
        <p>Proposto: {jsonText(revision.proposedValue) || 'ausência confirmada'}</p>
        <p>Justificativa: {revision.justification}</p>
        {revision.quote ? <blockquote>{revision.quote}</blockquote>
          : <p>Sem citação literal: informação indicada como ausente após revisão documental.</p>}
        {revision.status === 'PENDING' && <div className="admin-actions">
          <label>Motivo da decisão<input value={decisionReason} onChange={(event) => setDecisionReason(event.target.value)} required /></label>
          <button className="button button--primary" disabled={!decisionReason.trim() || decide.isPending}
            onClick={() => decide.mutate({ revision, approve: true })}>Homologar e publicar</button>
          <button className="button button--secondary" disabled={!decisionReason.trim() || decide.isPending}
            onClick={() => decide.mutate({ revision, approve: false })}>Rejeitar</button>
        </div>}
        {revision.decidedBy && <p>Decisão: {revision.status} por {revision.decidedBy} · {revision.decisionReason}</p>}
      </article>)}
      {history.data?.content.length === 0 && <p>Nenhuma revisão anterior.</p>}
      {history.data && history.data.totalElements > history.data.size && <nav aria-label="Paginação do histórico de revisões">
        <p>Página {historyPage + 1} de {Math.ceil(history.data.totalElements / history.data.size)} · {history.data.totalElements} revisões</p>
        <button type="button" className="button button--secondary" disabled={historyPage === 0}
          onClick={() => setHistoryPage((page) => Math.max(0, page - 1))}>Revisões anteriores</button>
        <button type="button" className="button button--secondary"
          disabled={(historyPage + 1) * history.data.size >= history.data.totalElements}
          onClick={() => setHistoryPage((page) => page + 1)}>Revisões seguintes</button>
      </nav>}
    </section>
    </>}
  </details>
}

function AdminOpportunity({ callId, csrf }: { callId: string; csrf: CsrfBootstrap }) {
  const detail = useQuery({ queryKey: ['admin-opportunity-detail', callId],
    queryFn: ({ signal }) => fetchAdminOpportunityDetail(callId, signal) })
  return <section className="admin-card"><Link to="/admin">← Voltar à fila</Link>
    {detail.isPending && <p role="status">Carregando oportunidade…</p>}
    {detail.isError && <p role="alert">{detail.error.message}</p>}
    {detail.data && <><p className="eyebrow">{detail.data.opportunity.agency.acronym} · {detail.data.opportunity.externalId}</p>
      <h1>{detail.data.opportunity.title}</h1><p>Fonte: {detail.data.opportunity.sources?.map((source) => source.name).join(', ')}</p>
      <p>Agências: {detail.data.opportunity.responsibleAgencies?.map((agency) => agency.acronym).join(', ')}</p>
      {detail.data.fields.map((field) => <FieldReview key={field.name} callId={callId} field={field} csrf={csrf} />)}</>}
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
    await client.invalidateQueries({ queryKey: ['admin-csrf'] })
    await client.invalidateQueries({ queryKey: ['admin-session'] })
  }
  async function logout() {
    if (csrfQuery.data) await adminLogout(csrfQuery.data)
    await client.invalidateQueries({ queryKey: ['admin-csrf'] })
    await client.invalidateQueries({ queryKey: ['admin-session'] })
  }
  return <main className="admin-page"><header className="admin-header"><Link className="brand" to="/">fomentiq</Link>
    <nav><Link to="/">Catálogo público</Link>{session.data && <button className="button button--secondary" onClick={() => void logout()}>Sair ({session.data.username})</button>}</nav>
  </header>
    {csrfQuery.isPending && <p role="status">Preparando acesso administrativo…</p>}
    {csrfQuery.isError && <section className="admin-card" role="alert"><h1>Administração indisponível</h1><p>{csrfQuery.error.message}</p></section>}
    {csrfQuery.data && session.isPending && <p role="status">Verificando sessão…</p>}
    {csrfQuery.data && session.isError && <AdminLogin onLogin={login} />}
    {session.data && (callId ? <AdminOpportunity callId={callId} csrf={csrfQuery.data!} /> : <ReviewQueue />)}
  </main>
}
