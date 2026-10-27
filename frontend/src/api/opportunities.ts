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
