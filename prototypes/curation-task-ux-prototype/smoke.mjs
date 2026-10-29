import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from '../../frontend/node_modules/playwright/index.mjs'

const root = resolve(fileURLToPath(new URL('.', import.meta.url)))
const server = createServer(async (request, response) => {
  const path = decodeURIComponent(new URL(request.url || '/', 'http://localhost').pathname)
  const file = resolve(root, path === '/' ? 'index.html' : `.${path}`)
  if (!file.startsWith(`${root}${sep}`) && file !== resolve(root, 'index.html')) {
    response.writeHead(404).end()
    return
  }
  try {
    const body = await readFile(file)
    const type = ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' })[extname(file)]
    response.writeHead(type ? 200 : 415, type ? { 'content-type': type } : {}).end(type ? body : undefined)
  } catch {
    response.writeHead(404).end()
  }
})
await new Promise((resolveServer, reject) => {
  server.once('error', reject)
  server.listen(0, '127.0.0.1', resolveServer)
})
const origin = `http://127.0.0.1:${server.address().port}`
let browser
try {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try { if ((await fetch(origin)).ok) break } catch { await delay(100) }
  }
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 1280, height: 880 } })
  page.setDefaultTimeout(6000)
  const errors = []
  const external = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('request', (request) => { if (new URL(request.url()).origin !== origin) external.push(request.url()) })

  await page.goto(`${origin}/#fila`)
  await page.getByRole('heading', { name: 'O que precisa da sua atenção?' }).waitFor()
  await page.getByRole('searchbox', { name: 'Buscar oportunidade' }).fill('Campo Claro')
  await page.getByRole('link', { name: 'Continuar revisão' }).click()
  await page.getByRole('heading', { name: 'Pesquisa aplicada em tecnologias sustentáveis' }).waitFor()
  const campoDocument = await page.locator('.document-summary').innerText()
  if (!campoDocument.includes('Comunicado complementar de demonstração')) throw new Error('A oportunidade Campo Claro não mostra seu documento de demonstração.')
  if (campoDocument.includes('Edital de apoio a pesquisa e infraestrutura')) throw new Error('A oportunidade Campo Claro mostrou o documento fictício de outra oportunidade.')
  await page.getByRole('button', { name: 'Revisar próxima informação' }).click()
  await page.getByRole('heading', { name: 'A informação “prazo de inscrição” está correta?' }).waitFor()
  await page.getByRole('heading', { name: 'Comunicado complementar de demonstração' }).waitFor()
  const campoReference = await page.locator('.panel__body .current-value').nth(1).innerText()
  if (!campoReference.includes('Comunicado complementar de demonstração') || campoReference.includes('Edital de apoio a pesquisa')) {
    throw new Error('A referência da revisão Campo Claro não corresponde ao comunicado exibido.')
  }

  await page.goto(`${origin}/#fila`)
  await page.getByRole('searchbox', { name: 'Buscar oportunidade' }).fill('infraestrutura')
  await page.getByRole('link', { name: 'Continuar revisão' }).click()
  await page.getByRole('heading', { name: 'Apoio à infraestrutura de pesquisa' }).waitFor()
  for (const section of ['Informações gerais', 'Quem pode participar', 'Condições financeiras', 'Documentos e requisitos']) {
    await page.getByRole('heading', { name: section }).waitFor()
  }
  await page.getByRole('button', { name: 'Revisar próxima informação' }).click()
  await page.getByRole('heading', { name: 'O prazo de inscrição está correto?' }).waitFor()
  await page.getByText('As propostas poderão ser enviadas até 31 de agosto de 2026', { exact: false }).waitFor()
  await page.getByText('Ver histórico desta informação').click()
  await page.getByRole('radio', { name: /Corrigir o valor/ }).check()
  await page.locator('#corrected-value').fill('01/09/2026 às 18:00')
  await page.locator('#justification').fill('Conferido no documento fictício.')
  await page.getByRole('radio', { name: /Confirmar este valor/ }).check()
  await page.getByRole('radio', { name: /Corrigir o valor/ }).check()
  if (await page.locator('#corrected-value').inputValue() !== '01/09/2026 às 18:00') throw new Error('A correção foi perdida ao trocar a ação.')
  await page.getByRole('button', { name: /Ver resumo da alteração/ }).click()
  await page.getByRole('heading', { name: 'Confira antes de publicar' }).waitFor()
  if (!(await page.locator('.summary-card').innerText()).includes('01/09/2026 às 18:00')) throw new Error('Resumo sem o valor corrigido.')
  await page.getByRole('button', { name: 'Simular registro' }).click()

  await page.goto(`${origin}/#revisar/trl`)
  await page.getByText('Nenhum trecho foi selecionado para este exemplo.', { exact: false }).waitFor()
  await page.getByRole('radio', { name: /Registrar que não foi localizado/ }).check()
  if (await page.locator('#justification').inputValue()) throw new Error('A justificativa de outro campo foi reutilizada.')
  await page.getByRole('button', { name: /Ver resumo da alteração/ }).click()
  if (await page.getByRole('heading', { name: 'Confira antes de publicar' }).count()) throw new Error('Ausência sem justificativa foi aceita.')
  await page.locator('#justification').fill('Revisei as versões fictícias disponíveis.')
  await page.getByRole('button', { name: /Ver resumo da alteração/ }).click()
  const summary = await page.locator('.summary-card').innerText()
  if (!summary.includes('Informação não localizada após revisão documental.')) throw new Error('Resumo não preserva o resultado de ausência não localizada.')
  if (summary.includes('Não previsto no documento')) throw new Error('Resumo afirma ausência documental sem comprovação.')

  if (errors.length) throw new Error(`Erros de navegador: ${errors.join('; ')}`)
  if (external.length) throw new Error(`Requisições externas inesperadas: ${external.join(', ')}`)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto(`${origin}/#oportunidade/aurora`)
  const size = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }))
  if (size.document > size.viewport) throw new Error(`Transbordamento horizontal em mobile: ${JSON.stringify(size)}`)
  console.log('PASS: busca, fila, visão geral, fonte contextual, histórico, confirmação/correção/ausência, justificativa obrigatória para ausência, resumo fiel, rascunhos isolados, sem chamadas externas e layout 375px.')
} finally {
  await browser?.close()
  await new Promise((resolveServer) => server.close(resolveServer))
}
