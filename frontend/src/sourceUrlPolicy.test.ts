import { describe, expect, it } from 'vitest'
import { safeSourceUrl } from './sourceUrlPolicy'

describe('safe public source URLs', () => {
  it('allows public https URLs without credentials', () => {
    expect(safeSourceUrl('https://www.gov.br/cnpq/chamada')).toBe('https://www.gov.br/cnpq/chamada')
  })

  it.each([
    'javascript:alert(1)',
    'https://user:password@example.gov.br/notice',
    'https://example.gov.br/notice?access_key=private',
    'http://127.0.0.1/admin',
    'https://storage.internal/document',
  ])('rejects unsafe URL %s', (value) => {
    expect(safeSourceUrl(value)).toBeNull()
  })
})
