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
