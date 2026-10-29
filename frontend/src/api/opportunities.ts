export type CallStatus = 'OPEN' | 'UPCOMING' | 'CLOSED' | 'SUSPENDED' | 'UNKNOWN'

export interface OpportunitySummary {
  id: string
  externalId: string
  portalEntryId: string | null
  title: string
  agency: {
    name: string
    acronym: string
  }
  program: string | null
  status: CallStatus
  deadline: string | null
  instrumentType: string | null
  sources?: { code: 'FINEP' | 'CNPQ' | string; name: string }[]
  responsibleAgencies?: { name: string; acronym: string; primary: boolean }[]
}

export interface OpportunityPage {
  content: OpportunitySummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface OpportunityEvidence {
  id: string
  extractedFieldId: string
  fieldName: string
  documentVersionId: string | null
  sourceUrl: string | null
  page: number | null
  section: string | null
  quote: string | null
  locator: unknown
}

export type ValidationStatus = 'EXTRACTED' | 'VALIDATED' | 'CONFLICT' | 'MISSING'

export interface ExtractedField {
  name: string
  value: unknown
  rawValue: string | null
  validationStatus: ValidationStatus
  createdAt: string
  evidence: OpportunityEvidence[]
  publicationSource?: 'EXTRACTION' | 'HUMAN'
  publishedRevisionId?: string | null
  originalValue?: unknown
  confirmedHumanAbsence?: boolean
  publicationReference?: {
    documentId: string
    documentVersionId: string
    sourceUrl: string | null
    page: number | null
    section: string | null
    quote: string | null
  } | null
}

export interface AdminRevision {
  id: string
  callId: string
  fieldName: string
  valueKind: 'VALUE' | 'CONFIRMED_ABSENCE'
  proposedValue: unknown
  previousPublishedValue: unknown
  previousRevisionId: string | null
  justification: string
  documentId: string
  documentVersionId: string
  page: number | null
  section: string | null
  quote: string | null
  locator: unknown
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdBy: string
  createdAt: string
  decidedBy: string | null
  decidedAt: string | null
  decisionReason: string | null
  rowVersion: number
}

export interface AdminQueueItem {
  callId: string
  externalId: string
  portalEntryId: string | null
  title: string
  agencyName: string
  agencyAcronym: string
  publishers: string[]
  agencies: string[]
  reviewFieldCount: number
  opportunityStatus: CallStatus
  deadline: string | null
}

export interface AdminQueue {
  content: AdminQueueItem[]
  page: number
  size: number
  totalElements: number
}

export interface AdminRevisionPage {
  content: AdminRevision[]
  page: number
  size: number
  totalElements: number
}

export interface CsrfBootstrap { headerName: string; token: string }

export async function fetchAdminCsrf(): Promise<CsrfBootstrap> {
  const response = await fetch('/api/admin/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('A administração só está disponível na demonstração isolada.')
  return (await response.json()) as CsrfBootstrap
}

export async function adminLogin(username: string, password: string, csrf: CsrfBootstrap): Promise<void> {
  const body = new URLSearchParams({ username, password })
  const response = await fetch('/api/admin/login', { method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token }, body })
  if (!response.ok) throw new Error('Não foi possível autenticar. Confira o usuário e a senha.')
}

export async function fetchAdminSession(): Promise<{ username: string }> {
  const response = await fetch('/api/admin/session', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('Sessão administrativa não autenticada.')
  return (await response.json()) as { username: string }
}

export async function adminLogout(csrf: CsrfBootstrap): Promise<void> {
  const response = await fetch('/api/admin/logout', { method: 'POST', credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token } })
  if (!response.ok) throw new Error('Não foi possível encerrar a sessão.')
}

export async function fetchAdminQueue(page = 0): Promise<AdminQueue> {
  const response = await fetch(`/api/admin/opportunities?page=${page}&size=20`, { credentials: 'same-origin' })
  if (!response.ok) throw new Error('Não foi possível carregar a fila administrativa.')
  return (await response.json()) as AdminQueue
}

export async function fetchAdminOpportunityDetail(callId: string, signal?: AbortSignal): Promise<OpportunityDetail> {
  const response = await fetch(`/api/admin/opportunities/${encodeURIComponent(callId)}`, {
    credentials: 'same-origin', signal,
  })
  if (response.status === 404) throw new OpportunityNotFoundError()
  if (!response.ok) throw new Error('Não foi possível carregar a análise administrativa.')
  return (await response.json()) as OpportunityDetail
}

export async function fetchAdminRevisions(callId: string, field: string, page = 0): Promise<AdminRevisionPage> {
  const params = new URLSearchParams({ field, page: String(page), size: '20' })
  const response = await fetch(`/api/admin/opportunities/${encodeURIComponent(callId)}/revisions?${params}`,
    { credentials: 'same-origin' })
  if (!response.ok) throw new Error('Não foi possível carregar o histórico de revisão.')
  return (await response.json()) as AdminRevisionPage
}

export async function createAdminRevision(callId: string, csrf: CsrfBootstrap, payload: unknown): Promise<AdminRevision> {
  const response = await fetch(`/api/admin/opportunities/${encodeURIComponent(callId)}/revisions`, {
    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new Error(response.status === 400 ? 'Revise o valor e a referência documental.' : 'Não foi possível salvar a revisão.')
  return (await response.json()) as AdminRevision
}

export async function decideAdminRevision(revision: AdminRevision, approve: boolean, reason: string,
  csrf: CsrfBootstrap): Promise<AdminRevision> {
  const action = approve ? 'approve' : 'reject'
  const response = await fetch(`/api/admin/revisions/${encodeURIComponent(revision.id)}/${action}`, {
    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify({ rowVersion: revision.rowVersion, reason }),
  })
  if (!response.ok) throw new Error(response.status === 409 ? 'A revisão ficou desatualizada; atualize o histórico.' : 'Não foi possível concluir a decisão.')
  return (await response.json()) as AdminRevision
}

export interface OpportunityDetail {
  opportunity: OpportunitySummary
  summary: string | null
  officialUrl: string | null
  currentCallVersion: number | null
  fields: ExtractedField[]
}

export interface ReadPage<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CallVersion {
  id: string
  versionNumber: number
  contentHash: string
  detectedAt: string
  supersedesVersionId: string | null
}

export interface OpportunityDocument {
  id: string
  externalId: string
  type: 'NOTICE' | 'ANNEX' | 'FAQ' | 'AMENDMENT' | 'RESULT_PRELIMINARY' | 'RESULT_FINAL' | 'OTHER'
  title: string
  sourceUrl: string | null
  versionCount: number
  latestDownloadedAt: string | null
}

export interface DocumentVersion {
  id: string
  contentHash: string
  mediaType: string
  sourceUrl: string | null
  downloadedAt: string
}

export const HISTORY_PAGE_SIZE = 20

export class OpportunityNotFoundError extends Error {
  constructor() {
    super('Oportunidade não encontrada.')
    this.name = 'OpportunityNotFoundError'
  }
}

export interface OpportunityFilters {
  q?: string
  status?: CallStatus
  deadlineFrom?: string
  deadlineTo?: string
  instrument?: string
  region?: string
  theme?: string
  source?: string
  agency?: string
}

export const OPPORTUNITIES_PAGE_SIZE = 20

export async function fetchOpportunities(
  page: number,
  filters: OpportunityFilters = {},
  signal?: AbortSignal,
): Promise<OpportunityPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(OPPORTUNITIES_PAGE_SIZE),
  })
  Object.entries(filters).forEach(([name, value]) => {
    if (value) params.set(name, value)
  })
  const response = await fetch(`/api/opportunities?${params.toString()}`, { signal })

  if (!response.ok) {
    throw new Error('Não foi possível carregar as oportunidades.')
  }

  return (await response.json()) as OpportunityPage
}

export async function fetchOpportunityDetail(
  callId: string,
  signal?: AbortSignal,
): Promise<OpportunityDetail> {
  const response = await fetch(`/api/opportunities/${encodeURIComponent(callId)}`, { signal })
  if (response.status === 404) throw new OpportunityNotFoundError()
  if (!response.ok) throw new Error('Não foi possível carregar esta oportunidade.')
  return (await response.json()) as OpportunityDetail
}

async function fetchReadPage<T>(path: string, signal?: AbortSignal): Promise<ReadPage<T>> {
  const response = await fetch(path, { signal })
  if (!response.ok) throw new Error('Não foi possível carregar o histórico desta oportunidade.')
  return (await response.json()) as ReadPage<T>
}

function pagedPath(path: string, page: number): string {
  const params = new URLSearchParams({ page: String(page), size: String(HISTORY_PAGE_SIZE) })
  return `${path}?${params.toString()}`
}

export function fetchCallVersions(
  callId: string,
  page: number,
  signal?: AbortSignal,
): Promise<ReadPage<CallVersion>> {
  return fetchReadPage(pagedPath(`/api/opportunities/${encodeURIComponent(callId)}/versions`, page), signal)
}

export function fetchOpportunityDocuments(
  callId: string,
  page: number,
  signal?: AbortSignal,
): Promise<ReadPage<OpportunityDocument>> {
  return fetchReadPage(pagedPath(`/api/opportunities/${encodeURIComponent(callId)}/documents`, page), signal)
}

export function fetchDocumentVersions(
  callId: string,
  documentId: string,
  page: number,
  signal?: AbortSignal,
): Promise<ReadPage<DocumentVersion>> {
  const path = `/api/opportunities/${encodeURIComponent(callId)}/documents/${encodeURIComponent(documentId)}/versions`
  return fetchReadPage(pagedPath(path, page), signal)
}

/** Finds a specific evidence-linked version without assuming it is on the newest page. */
export async function findDocumentVersion(callId: string, documents: OpportunityDocument[], versionId: string,
  firstPages: Array<ReadPage<DocumentVersion> | undefined>): Promise<{ documentId: string; version: DocumentVersion; page: number } | null> {
  for (const [index, document] of documents.entries()) {
    const firstPage = firstPages[index]
    if (!firstPage) throw new Error('Não foi possível carregar as versões documentais.')
    const firstMatch = firstPage.content.find((version) => version.id === versionId)
    if (firstMatch) return { documentId: document.id, version: firstMatch, page: 0 }
    for (let page = 1; page < firstPage.totalPages; page += 1) {
      const result = await fetchDocumentVersions(callId, document.id, page)
      const match = result.content.find((version) => version.id === versionId)
      if (match) return { documentId: document.id, version: match, page }
    }
  }
  return null
}

export function preservedDocumentContentUrl(callId: string, documentId: string, versionId: string): string {
  return `/api/admin/opportunities/${encodeURIComponent(callId)}/documents/${encodeURIComponent(documentId)}`
    + `/versions/${encodeURIComponent(versionId)}/content`
}
