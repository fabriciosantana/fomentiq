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
  await expect(page.getByText('Resumo da oportunidade')).toBeVisible()
  await expect(page.getByRole('heading', { name: /Condições de participação/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Documentos, evidências e histórico' })).toBeVisible()
  await expect(page.getByText('751', { exact: true })).toBeVisible()
  await expect(page.getByText('751092', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Prazo de submissão' })).toBeVisible()
  await expect(page.getByText('Extraído, ainda não validado', { exact: true }).first()).toBeVisible()
  const missingField = page.locator('.detail-field').filter({ has: page.getByRole('heading', { name: 'Nível de maturidade tecnológica (TRL)' }) })
  await expect(missingField.getByText('Não identificado pela extração. Isso não confirma ausência no documento.'))
    .toBeVisible()

  const evidencedField = page.locator('.detail-field').filter({ has: page.getByRole('heading', { name: 'Programa' }) })
  const evidenceDisclosure = evidencedField.locator('details').filter({ hasText: 'Evidências documentais' })
  await expect(evidenceDisclosure).toBeVisible()
  await evidenceDisclosure.locator('summary').click()
  await expect(evidenceDisclosure.getByText('PROINFRA 2024 EXPANSÃO', { exact: true })).toBeVisible()
  await expect(evidenceDisclosure.getByRole('link', { name: /Abrir fonte pública/ }).first()).toHaveAttribute(
    'rel', /noopener noreferrer/,
  )

  const regionsField = page.locator('.detail-field').filter({ has: page.getByRole('heading', { name: 'Regiões elegíveis' }) })
  await expect(regionsField).toBeVisible()
  await expect(regionsField.getByText('Sem evidências documentais para este campo.')).toBeVisible()

  expect(apiResponses.length).toBeGreaterThanOrEqual(2)
  expect(apiResponses.every((response) => response.status === 200)).toBeTruthy()
})

test('catalog filters CNPq and opens its persisted documents and evidence', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Fonte publicadora').selectOption('CNPQ')
  await page.getByRole('button', { name: 'Aplicar filtros' }).click()
  const callLink = page.getByRole('link', { name: /Chamada Pública CNPq\/CAPES Nº 30\/2026/i })
  await expect(callLink).toBeVisible()
  await expect(page.getByText('CNPQ', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Agências responsáveis: CNPq, CAPES')).toBeVisible()
  await callLink.click()

  await expect(page.getByRole('heading', { name: /Chamada Pública CNPq\/CAPES Nº 30\/2026/i })).toBeVisible()
  await expect(page.getByText('30/2026', { exact: true })).toBeVisible()
  await expect(page.getByText('CNPq', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Prazo de submissão' })).toBeVisible()
  await page.getByRole('button', { name: 'Consultar histórico e documentos' }).click()

  const documents = page.locator('.document-history')
  const documentTitles = documents.locator('.history-document h4')
  await expect(documentTitles.first()).toBeVisible()
  await expect(documentTitles).not.toHaveCount(0)
  const programField = page.locator('.detail-field').filter({ has: page.getByRole('heading', { name: 'Programa' }) })
  const evidenceDisclosure = programField.locator('details').filter({ hasText: 'Evidências documentais' })
  await expect(evidenceDisclosure).toBeVisible()
  await evidenceDisclosure.locator('summary').click()
  await expect(evidenceDisclosure.getByText('MAI/DAI', { exact: true })).toBeVisible()
  await expect(evidenceDisclosure.getByRole('link', { name: /Abrir fonte pública/ }).first()).toHaveAttribute(
    'rel', /noopener noreferrer/,
  )
})

test('demo admin publishes confirmed TRL absence and rejects an unsupported deadline proposal', async ({ page }) => {
  await page.goto('/admin')
  await page.getByLabel('Usuário').fill('offline-reviewer')
  await page.getByLabel('Senha').fill(process.env.FOMENTIQ_ADMIN_PASSWORD || '')
  await page.getByRole('button', { name: 'Entrar' }).click()
  const sessionCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'JSESSIONID')
  expect(sessionCookie?.httpOnly).toBeTruthy()
  expect(sessionCookie?.sameSite).toBe('Lax')
  expect(sessionCookie?.secure).toBeTruthy()

  await expect(page.getByRole('heading', { name: 'Oportunidades para revisar' })).toBeVisible()
  const finepItem = page.locator('.admin-queue li').filter({ hasText: 'PROINFRA 2024 EXPANSÃO' })
  await finepItem.getByRole('link', { name: 'Revisar oportunidade' }).click()
  await expect(page.getByRole('heading', { name: 'PROINFRA 2024 EXPANSÃO' })).toBeVisible()

  const trl = page.locator('.admin-field').filter({ has: page.getByRole('heading', { name: 'TRL', exact: true }) })
  await trl.locator(':scope > summary.admin-field__title').click()
  let actorSpoofed = false
  await page.route('**/api/admin/opportunities/*/revisions', async (route) => {
    const request = route.request()
    if (!actorSpoofed && request.method() === 'POST') {
      actorSpoofed = true
      const payload = request.postDataJSON() as Record<string, unknown>
      const spoofedBody = JSON.stringify({ ...payload, actor: 'forged-reviewer' })
      await route.continue({ postData: spoofedBody, headers: {
        ...request.headers(), 'content-length': String(new TextEncoder().encode(spoofedBody).length),
      } })
    } else {
      await route.continue()
    }
  })
  await trl.locator('.admin-check input').check()
  await trl.getByLabel('Justificativa').fill('Edital e retificações revisados; não foi identificado requisito de TRL.')
  await trl.getByLabel('Documento').selectOption({ index: 1 })
  await expect(trl.getByLabel('Versão')).toBeEnabled()
  await trl.getByLabel('Versão').selectOption({ index: 1 })
  const firstCreate = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith('/revisions'))
  await trl.getByRole('button', { name: 'Salvar revisão pendente' }).click()
  const firstCreateResponse = await firstCreate
  const firstCreateText = await firstCreateResponse.text()
  expect(firstCreateResponse.status() === 200, `Create returned ${firstCreateResponse.status()}: ${firstCreateText}`)
    .toBeTruthy()
  await expect(trl.getByText('PENDING', { exact: true })).toBeVisible()
  await trl.getByLabel('Motivo da decisão').fill('Revisão integral das versões documentais concluída.')
  await trl.getByRole('button', { name: 'Homologar e publicar' }).click()
  const approvedRevision = trl.locator('.admin-revision').filter({ hasText: 'APPROVED' })
  await expect(approvedRevision).toContainText('offline-reviewer')
  await expect(trl.getByText('forged-reviewer', { exact: true })).toHaveCount(0)
  await page.unroute('**/api/admin/opportunities/*/revisions')

  const deadline = page.locator('.admin-field').filter({ has: page.getByRole('heading', { name: 'Prazo de submissão' }) })
  await deadline.locator(':scope > summary.admin-field__title').click()
  await deadline.locator('textarea').nth(0).fill('2030-01-01T00:00:00-03:00')
  await deadline.getByLabel('Justificativa').fill('Proposta de regressão para demonstrar rejeição auditável.')
  await deadline.getByLabel('Documento').selectOption({ index: 1 })
  await expect(deadline.getByLabel('Versão')).toBeEnabled()
  await deadline.getByLabel('Versão').selectOption({ index: 1 })
  await deadline.getByRole('button', { name: 'Salvar revisão pendente' }).click()
  await expect(deadline.getByText('PENDING', { exact: true })).toBeVisible()
  await deadline.getByLabel('Motivo da decisão').last().fill('Proposta sem suporte documental; não publicar.')
  await deadline.getByRole('button', { name: 'Rejeitar' }).last().click()
  await expect(deadline.getByText('REJECTED', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Catálogo público' }).click()
  await page.getByRole('link', { name: /PROINFRA 2024 EXPANSÃO/i }).click()
  const publicTrl = page.locator('.detail-field').filter({
    has: page.getByRole('heading', { name: 'Nível de maturidade tecnológica (TRL)', exact: true }),
  })
  await expect(publicTrl.getByText('Ausência confirmada em revisão documental humana.')).toBeVisible()
  await expect(publicTrl.getByText(/Valor publicado após revisão humana/)).toBeVisible()
  await publicTrl.locator('details').filter({ hasText: 'Ver valor original da extração e metadados' })
    .locator('summary').click()
  await expect(publicTrl.getByText('Valor original:')).toBeVisible()
  await expect(publicTrl.getByText(/Revisão publicada:/)).toBeVisible()
})
