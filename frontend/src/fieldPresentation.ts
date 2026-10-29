import type { ValidationStatus } from './api/opportunities'

export const FIELD_LABELS: Record<string, string> = {
  agency: 'Agência responsável', program: 'Programa', instrumentType: 'Instrumento', title: 'Título',
  summary: 'Resumo', status: 'Situação', publicationDate: 'Data de publicação', openingDate: 'Data de abertura',
  deadline: 'Prazo de submissão', totalBudget: 'Orçamento total', projectFunding: 'Financiamento por projeto',
  eligibility: 'Elegibilidade', regions: 'Regiões elegíveis', trl: 'Nível de maturidade tecnológica (TRL)',
  requiresIct: 'Necessidade de ICT', counterpart: 'Contrapartida', technologyThemes: 'Áreas tecnológicas',
  eligibleExpenses: 'Despesas elegíveis', evaluationCriteria: 'Critérios de avaliação',
  requiredDocuments: 'Documentos exigidos', officialLinks: 'Links oficiais',
}

export const VALIDATION_STATUS_LABELS: Record<ValidationStatus, string> = {
  VALIDATED: 'Validado pela regra técnica',
  EXTRACTED: 'Extraído, ainda não validado',
  CONFLICT: 'Divergência entre fontes',
  MISSING: 'Não identificado pela extração',
}

export const REVISION_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente', APPROVED: 'Homologada e publicada', REJECTED: 'Rejeitada',
}

export const PUBLICATION_SOURCE_LABELS: Record<string, string> = {
  EXTRACTION: 'Extração automática', HUMAN: 'Revisão humana',
}

export const VALUE_LABELS: Record<string, string> = {
  eligible: 'Quem pode participar', organizations: 'Organizações elegíveis', registration: 'Cadastro exigido', excluded: 'Exclusões',
  exempt: 'Isenções de contrapartida', municipalPercentRanges: 'Municípios',
  stateAndFederalDistrictPercentRanges: 'Estados e Distrito Federal', condition: 'Condição', min: 'Mínimo',
  max: 'Máximo', scope: 'Abrangência', minimumBudgetShare: 'Participação mínima', regions: 'Regiões',
  percentage: 'Percentual', currency: 'Moeda', amount: 'Valor', qualifier: 'Qualificador', criterion: 'Critério',
  weight: 'Peso (pontos)', federalExecutorRequiresSupportInstitution: 'Instituição de apoio para executor federal',
  privateNonprofitMinimumOperatingYears: 'Tempo mínimo de funcionamento', proposalMaximumByEmployedDoctorCount:
    'Limites por quantidade de doutores empregados', subprojectMinimum: 'Valor mínimo por subprojeto',
  basicOrExecutiveDesign: 'Projeto básico ou executivo', other: 'Demais subprojetos', general: 'Documentos gerais',
  conditionalAnnexes: 'Anexos condicionais',
}

export const QUALIFIER_LABELS: Record<string, string> = {
  UP_TO: 'até', RANGE: 'faixa', EXACT: 'valor exato', AT_LEAST: 'a partir de', UNKNOWN: 'qualificador não identificado',
}

export const CRITICAL_FIELDS = new Set(['deadline', 'totalBudget', 'eligibility', 'counterpart', 'trl', 'requiresIct'])

export function fieldLabel(name: string): string {
  return FIELD_LABELS[name] ?? name.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/^./, (letter) => letter.toUpperCase())
}

export function valueLabel(name: string): string {
  return VALUE_LABELS[name] ?? fieldLabel(name)
}

export function qualifierLabel(value: string): string {
  return QUALIFIER_LABELS[value] ?? value
}

export function formatDate(value: string): string {
  const date = new Date(value.length === 10 ? `${value}T12:00:00Z` : value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'long', ...(value.length > 10 ? { timeStyle: 'short' as const } : {}), timeZone: 'America/Sao_Paulo',
  }).format(date)
}

export function formatTimestamp(value: string | null | undefined): string {
  if (!value) return 'Data indisponível'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Data indisponível'
  return `${new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Sao_Paulo',
  }).format(date)} (horário de Brasília)`
}

export function formatDateTimeInput(value: string | null | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(date).reduce<Record<string, string>>((result, part) => {
    if (part.type !== 'literal') result[part.type] = part.value
    return result
  }, {})
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

export function parseDateTimeInput(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value)
  if (!match) return null
  const [, year, month, day, hour, minute] = match
  const wallTimeAsUtc = Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute))
  const represented = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(wallTimeAsUtc)).reduce<Record<string, string>>((result, part) => {
    if (part.type !== 'literal') result[part.type] = part.value
    return result
  }, {})
  const representedAsUtc = Date.UTC(Number(represented.year), Number(represented.month) - 1,
    Number(represented.day), Number(represented.hour), Number(represented.minute))
  return new Date(wallTimeAsUtc + wallTimeAsUtc - representedAsUtc).toISOString()
}

export function formatMoney(amount: unknown, currency = 'BRL'): string {
  const numeric = typeof amount === 'number' ? amount : Number(amount)
  if (!Number.isFinite(numeric)) return `${currency} ${String(amount ?? '')}`.trim()
  try {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(numeric)
  } catch {
    return `${currency} ${new Intl.NumberFormat('pt-BR').format(numeric)}`
  }
}

export function formatPercent(value: unknown): string {
  if (value === null || value === undefined || value === '') return 'Não informado'
  const raw = String(value).trim().replace(/%$/, '').trim()
  const numeric = Number(raw.replace(',', '.'))
  if (!Number.isFinite(numeric)) return `${raw}%`
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 }).format(numeric)}%`
}

export function formatPoints(value: number): string {
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 }).format(value)} pontos`
}

function display(value: unknown, key?: string, context?: string): string {
  if (value === null || value === undefined || value === '') return 'Não informado'
  if (typeof value === 'boolean') return value ? 'Sim' : 'Não'
  if (typeof value === 'number') {
    if (key === 'percentage' || (context === 'percent-range' && (key === 'min' || key === 'max'))) return formatPercent(value)
    if (key === 'weight') return formatPoints(value)
    return new Intl.NumberFormat('pt-BR').format(value)
  }
  if (typeof value === 'string') {
    if (key === 'deadline' || key === 'publicationDate' || key === 'openingDate') return formatDate(value)
    if (key === 'amount' && context === 'money') return formatMoney(value)
    if (key === 'percentage' || (context === 'percent-range' && (key === 'min' || key === 'max'))) return formatPercent(value)
    if (key === 'qualifier') return qualifierLabel(value)
    if (key === 'status') return ({ OPEN: 'Aberta', UPCOMING: 'Em breve', CLOSED: 'Encerrada',
      SUSPENDED: 'Suspensa', UNKNOWN: 'Não identificada' } as Record<string, string>)[value] ?? value
    return value
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return 'Não informado'
    const itemContext = key === 'municipalPercentRanges' || key === 'stateAndFederalDistrictPercentRanges'
      ? 'percent-range' : key === 'proposalMaximumByEmployedDoctorCount' ? 'funding-tier' : undefined
    return value.map((item, index) => `${index + 1}. ${display(item, undefined, itemContext)}`).join('\n')
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    if (typeof record.amount === 'string' && typeof record.currency === 'string') {
      return `${formatMoney(record.amount, record.currency)}${record.qualifier ? ` (${display(record.qualifier, 'qualifier')})` : ''}`
    }
    if (key === 'counterpart') {
      const groups = [
        ['Isenções de contrapartida', record.exempt],
        ['Municípios', record.municipalPercentRanges],
        ['Estados e Distrito Federal', record.stateAndFederalDistrictPercentRanges],
      ] as const
      return groups.filter(([, items]) => Array.isArray(items) && items.length > 0)
        .map(([label, items]) => `${label}:\n${display(items, label === 'Municípios' ? 'municipalPercentRanges' : label === 'Estados e Distrito Federal' ? 'stateAndFederalDistrictPercentRanges' : 'exempt')}`)
        .join('\n') || 'Não informado'
    }
    if (key === 'projectFunding') {
      const currency = typeof record.currency === 'string' ? record.currency : 'BRL'
      const tiers = Array.isArray(record.proposalMaximumByEmployedDoctorCount)
        ? record.proposalMaximumByEmployedDoctorCount as Record<string, unknown>[] : []
      const minimum = record.subprojectMinimum && typeof record.subprojectMinimum === 'object'
        ? record.subprojectMinimum as Record<string, unknown> : {}
      const lines = [
        ...tiers.map((tier) => `Limite para ${display(tier.min)} a ${tier.max == null ? 'acima' : display(tier.max)} doutores empregados: ${formatMoney(tier.amount, currency)}`),
        ...(minimum.basicOrExecutiveDesign != null ? [`Mínimo para projeto básico ou executivo: ${formatMoney(minimum.basicOrExecutiveDesign, currency)}`] : []),
        ...(minimum.other != null ? [`Mínimo para demais subprojetos: ${formatMoney(minimum.other, currency)}`] : []),
      ]
      return lines.join('\n') || 'Não informado'
    }
    return Object.entries(record).map(([name, child]) => {
      const childContext = name === 'amount' && typeof record.currency === 'string' ? 'money'
        : name === 'min' || name === 'max' ? context : undefined
      return `${valueLabel(name)}: ${display(child, name, childContext)}`
    }).join('\n')
  }
  return String(value)
}

export function fieldValueText(fieldName: string, value: unknown): string {
  return display(value, fieldName)
}
