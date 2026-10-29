import { describe, expect, it } from 'vitest'
import { fieldLabel, fieldValueText, formatDateTimeInput, formatPercent, parseDateTimeInput } from './fieldPresentation'

describe('field presentation in Brazilian Portuguese', () => {
  it('uses friendly labels and units without changing internal field names', () => {
    expect(fieldLabel('agency')).toBe('Agência responsável')
    expect(fieldLabel('totalBudget')).toBe('Orçamento total')
    expect(fieldValueText('evaluationCriteria', [{ criterion: 'Impacto', weight: 5 }])).toContain('5 pontos')
    expect(formatPercent('12,5')).toBe('12,5%')
  })

  it('round trips deadline instants through Brasília local time', () => {
    const instant = '2025-06-06T17:00:00-03:00'
    const input = formatDateTimeInput(instant)
    expect(input).toBe('2025-06-06T17:00')
    expect(parseDateTimeInput(input)).toBe('2025-06-06T20:00:00.000Z')
  })
})
