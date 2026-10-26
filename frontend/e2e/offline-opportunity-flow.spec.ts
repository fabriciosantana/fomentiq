import { expect, test } from '@playwright/test'

test('catalog opens the persisted fixture opportunity and its document evidence', async ({ page }) => {
  const apiResponses: Array<{ url: string; status: number }> = []
  page.on('response', (response) => {
    if (new URL(response.url()).pathname.startsWith('/api/')) {
      apiResponses.push({ url: new URL(response.url()).pathname, status: response.status() })
    }
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: /oportunidades/i })).toBeVisible()
  await expect(page.getByRole('link', { name: /PROINFRA 2024 EXPANSÃO/i })).toBeVisible()
  await page.getByRole('link', { name: /PROINFRA 2024 EXPANSÃO/i }).click()

  await expect(page.getByRole('heading', { name: /PROINFRA 2024 EXPANSÃO/i })).toBeVisible()
  await expect(page.getByText('751', { exact: true })).toBeVisible()
  await expect(page.getByText('751092', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Prazo de submissão' })).toBeVisible()
  await expect(page.getByText('Extraído, ainda não validado', { exact: true }).first()).toBeVisible()
  const missingField = page.locator('.detail-field').filter({ has: page.getByRole('heading', { name: 'Nível de maturidade tecnológica (TRL)' }) })
  await expect(missingField.getByText('Não informado', { exact: true })).toBeVisible()

  const evidencedField = page.locator('.detail-field').filter({ has: page.getByRole('heading', { name: 'Programa' }) })
  const evidenceDisclosure = evidencedField.locator('details').filter({ hasText: 'Evidências documentais' })
  await expect(evidenceDisclosure).toBeVisible()
  await evidenceDisclosure.locator('summary').click()
  await expect(evidenceDisclosure.getByText('PROINFRA 2024 EXPANSÃO', { exact: true })).toBeVisible()
  await expect(evidenceDisclosure.getByRole('link', { name: /Abrir fonte pública/ }).first()).toHaveAttribute(
    'rel', /noopener noreferrer/,
  )

  expect(apiResponses.length).toBeGreaterThanOrEqual(2)
  expect(apiResponses.every((response) => response.status === 200)).toBeTruthy()
})
