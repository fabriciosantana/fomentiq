import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, resolve, sep } from 'node:path'
import { chromium } from '../../frontend/node_modules/@playwright/test/index.mjs'

const root = resolve(new URL('.', import.meta.url).pathname)
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' }
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://127.0.0.1')
    const path = resolve(root, `.${decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)}`)
    if (path !== root && !path.startsWith(root + sep)) throw new Error('path traversal')
    response.writeHead(200, { 'content-type': mime[extname(path)] ?? 'application/octet-stream', 'cache-control': 'no-store' })
    response.end(await readFile(path))
  } catch {
    response.writeHead(404).end('not found')
  }
})
await new Promise((resolveListen, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolveListen) })
const address = server.address()
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
  const externalRequests = []
  page.on('request', (request) => { if (!request.url().startsWith(`http://127.0.0.1:${address.port}/`)) externalRequests.push(request.url()) })
  await page.goto(`http://127.0.0.1:${address.port}/`)
  await assert.doesNotReject(() => page.getByText('alterações não são salvas').waitFor())
  const hiddenLabel = await page.getByText('Buscar oportunidade', { exact: true }).evaluate((element) => {
    const rect = element.getBoundingClientRect()
    return { position: getComputedStyle(element).position, width: rect.width, height: rect.height }
  })
  assert.deepEqual(hiddenLabel, { position: 'absolute', width: 1, height: 1 },
    'search label is available to assistive technology without displacing the search input')
  await page.getByRole('button', { name: 'Nova oportunidade' }).click()
  await page.getByLabel('Título da oportunidade').fill('Edital fictício para teste')
  await page.getByLabel('Instituição responsável').fill('Instituição de Demonstração')
  await page.getByRole('button', { name: 'Catálogo público' }).click()
  assert.equal(await page.getByText('Edital fictício para teste').count(), 0, 'draft is hidden from the public catalog')
  await page.getByRole('button', { name: 'Administração' }).click()
  await page.locator('.opportunity-row').filter({ hasText: 'Edital fictício para teste' })
    .getByRole('button', { name: 'Editar oportunidade' }).click()
  await page.getByRole('button', { name: 'Organizar conteúdo' }).click()
  await page.getByRole('button', { name: 'Adicionar seção' }).click()
  await page.getByLabel('Título da seção 1').fill('Como participar')
  await page.getByRole('button', { name: 'Adicionar informação' }).click()
  await page.getByLabel('Nome do campo 1').fill('Etapas de inscrição')
  await page.getByLabel('Tipo do campo 1').selectOption('list')
  await page.locator('.custom-value').nth(0).fill('Envie o plano\nAguarde o resultado')
  const examples = [
    ['Observação', 'long-text', 'Texto longo demonstrativo.'], ['Data de referência', 'date', '2027-05-14'],
    ['Apoio demonstrativo', 'currency', '125000.50'], ['Página de referência', 'link', 'https://example.org/chamada'],
    ['Contato', 'short-text', 'equipe@example.org'],
  ]
  for (const [index, [name, type, value]] of examples.entries()) {
    await page.getByRole('button', { name: 'Adicionar informação' }).click()
    const row = index + 2
    await page.getByLabel(`Nome do campo ${row}`).fill(name)
    await page.getByLabel(`Tipo do campo ${row}`).selectOption(type)
    await page.locator('.custom-value').nth(row - 1).fill(value)
  }
  await page.getByRole('button', { name: 'Adicionar informação' }).click()
  const removableRow = examples.length + 2
  await page.getByLabel(`Nome do campo ${removableRow}`).fill('Campo removível')
  await page.locator('.custom-value').nth(removableRow - 1).fill('Este texto será removido')
  await page.getByRole('button', { name: 'Mover campo para baixo' }).first().click()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Remover campo' }).last().click()
  await page.getByRole('button', { name: 'Adicionar seção' }).click()
  await page.getByLabel('Título da seção 2').fill('Quem pode participar')
  await page.getByRole('button', { name: 'Adicionar seção' }).click()
  await page.getByLabel('Título da seção 3').fill('Seção para remover')
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Remover seção' }).last().click()
  await page.getByRole('button', { name: 'Mover seção para cima' }).last().click()
  await page.getByRole('button', { name: 'Pré-visualizar página' }).click()
  const preview = page.locator('.preview-page')
  await assert.doesNotReject(() => preview.getByRole('heading', { name: 'Como participar' }).waitFor())
  await assert.doesNotReject(() => preview.getByRole('heading', { name: 'Quem pode participar' }).waitFor())
  const sectionTitles = await preview.locator('.editorial-section h2').allInnerTexts()
  assert.deepEqual(sectionTitles.slice(0, 2), ['Quem pode participar', 'Como participar'], 'section ordering is reflected in preview')
  assert.equal(await preview.getByText('Seção para remover').count(), 0)
  assert.match(await preview.innerText(), /Envie o plano[\s\S]*Aguarde o resultado/)
  assert.match(await preview.innerText(), /PÁGINA DE REFERÊNCIA/)
  assert.doesNotMatch(await preview.innerText(), /Contato/)
  assert.match(await preview.innerText(), /R\$\s*125\.000,50/)
  assert.match(await preview.innerText(), /14 de maio de 2027/)
  assert.match(await preview.innerText(), /equipe@example\.org/)
  assert.ok(await preview.getByRole('link', { name: /example\.org/ }).count())
  await page.getByRole('button', { name: 'Publicar demonstração' }).last().click()
  await page.getByRole('button', { name: 'Catálogo público' }).click()
  await assert.doesNotReject(() => page.getByRole('heading', { name: 'Edital fictício para teste' }).waitFor())
  const newCard = page.locator('.catalog-item').filter({ hasText: 'Edital fictício para teste' })
  await newCard.getByRole('button', { name: /Conhecer oportunidade/ }).click()
  assert.match(await page.locator('#app').innerText(), /Edital fictício para teste/)
  await assert.doesNotReject(() => page.getByRole('heading', { name: 'Como participar' }).waitFor())
  await page.getByRole('button', { name: 'Administração' }).click()
  const createdRow = page.locator('.opportunity-row').filter({ hasText: 'Edital fictício para teste' })
  await createdRow.getByRole('button', { name: 'Editar oportunidade' }).click()
  await page.getByRole('button', { name: 'Retirar da publicação' }).click()
  await page.getByRole('button', { name: 'Catálogo público' }).click()
  await assert.rejects(() => page.getByRole('heading', { name: 'Edital fictício para teste' }).waitFor({ timeout: 250 }))
  await page.setViewportSize({ width: 360, height: 800 })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'catalog has no horizontal overflow')
  assert.deepEqual(externalRequests, [], 'prototype must not make external requests')
  console.log('PASS: authoring, sections/fields, preview, publish/unpublish, public visibility, responsive layout, no external requests')
} finally {
  await browser.close()
  await new Promise((resolveClose) => server.close(resolveClose))
}
